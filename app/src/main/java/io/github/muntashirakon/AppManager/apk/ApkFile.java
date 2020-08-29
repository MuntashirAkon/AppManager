/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.apk;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.google.classysharkandroid.utils.UriUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public class ApkFile implements AutoCloseable, Parcelable {
    public static final String TAG = "TAG";

    protected ApkFile(@NonNull Parcel in) {
        entries = Objects.requireNonNull(in.createTypedArrayList(Entry.CREATOR));
        baseEntry = in.readParcelable(Entry.class.getClassLoader());
        packageName = in.readString();
        hasObb = in.readByte() != 0;
        isSplit = in.readByte() != 0;
        apkUri = in.readParcelable(Uri.class.getClassLoader());
    }

    public static final Creator<ApkFile> CREATOR = new Creator<ApkFile>() {
        @NonNull
        @Override
        public ApkFile createFromParcel(Parcel in) {
            return new ApkFile(in);
        }

        @NonNull
        @Override
        public ApkFile[] newArray(int size) {
            return new ApkFile[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeTypedList(entries);
        dest.writeParcelable(baseEntry, flags);
        dest.writeString(packageName);
        dest.writeByte((byte) (hasObb ? 1 : 0));
        dest.writeByte((byte) (isSplit ? 1 : 0));
        dest.writeParcelable(apkUri, flags);
    }

    @IntDef(value = {
            APK_BASE,
            APK_SPLIT_FEATURE,
            APK_SPLIT_ABI,
            APK_SPLIT_DENSITY,
            APK_SPLIT_LOCALE,
            APK_SPLIT,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ApkType {
    }

    public static final int APK_BASE = 0;
    public static final int APK_SPLIT_FEATURE = 1;
    public static final int APK_SPLIT_ABI = 2;
    public static final int APK_SPLIT_DENSITY = 3;
    public static final int APK_SPLIT_LOCALE = 4;
    public static final int APK_SPLIT = 5;

    private static final String APK_FILE = "apk_file.apk";
    private static final String MANIFEST_FILE = "AndroidManifest.xml";
    private static final String ANDROID_XML_NAMESPACE = "http://schemas.android.com/apk/res/android";

    public static List<String> SUPPORTED_EXTENSIONS = new ArrayList<>();

    static {
        SUPPORTED_EXTENSIONS.add("apk");
        SUPPORTED_EXTENSIONS.add("apks");
        SUPPORTED_EXTENSIONS.add("xapk");
    }

    @NonNull
    private List<Entry> entries = new ArrayList<>();
    private Entry baseEntry;
    private String packageName;
    private boolean hasObb = false;
    private boolean isSplit = false;
    private Uri apkUri;
    private File cacheFilePath;

    public ApkFile(Uri apkUri) throws Exception {
        Context context = AppManager.getContext();
        this.apkUri = apkUri;
        // Check extension
        String name = Utils.getName(context.getContentResolver(), apkUri);
        if (name == null) throw new Exception("Could not extract package name from the URI.");
        String extension;
        try {
            extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
            if (!SUPPORTED_EXTENSIONS.contains(extension))
                throw new Exception("Invalid package extension.");
        } catch (IndexOutOfBoundsException e) {
            throw new Exception("Invalid package extension.");
        }
        String filePath = UriUtils.pathUriCache(context, apkUri, APK_FILE);
        if (filePath == null) throw new Exception("Failed to cache the provided apk file.");
        // Check for splits
        if (extension.equals("apk")) {
            // Cache the apk file
            baseEntry = new Entry(APK_FILE, new File(filePath), APK_BASE);
            entries.add(baseEntry);
            // Extract manifest file
            ByteBuffer manifestBytes = getManifestFromApk(new File(filePath));
            if (manifestBytes == null) throw new Exception("Manifest not found.");
            // Get manifest attributes
            HashMap<String, String> manifestAttrs = getManifestAttributes(manifestBytes);
            if (manifestAttrs.containsKey("package")) {
                packageName = manifestAttrs.get("package");
            } else throw new RuntimeException("Package name not found.");
        } else {
            isSplit = true;
            boolean foundBaseApk = false;
            cacheFilePath = new File(filePath);
            File destDir = context.getExternalFilesDir("apks");
            if (destDir == null || !Environment.getExternalStorageState(destDir).equals(Environment.MEDIA_MOUNTED))
                throw new RuntimeException("External media not present");
            if (!destDir.exists()) //noinspection ResultOfMethodCallIgnored
                destDir.mkdirs();
            try (ZipFile zipFile = new ZipFile(cacheFilePath)) {
                Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
                File apkFile;
                String fileName;
                while (zipEntries.hasMoreElements()) {
                    ZipEntry zipEntry = zipEntries.nextElement();
                    if (zipEntry.isDirectory()) continue;
                    fileName = Utils.getFileNameFromZipEntry(zipEntry);
                    if (fileName.endsWith(".apk")) {
                        // Extract the apk file
                        try (InputStream zipInputStream = zipFile.getInputStream(zipEntry)) {
                            apkFile = IOUtils.saveZipFile(zipInputStream, destDir, fileName);
                            Log.e(TAG, "Apk File: " + apkFile);
                        }
                        try {
                            // Extract manifest file
                            ByteBuffer manifestBytes = getManifestFromApk(apkFile);
                            if (manifestBytes == null) throw new Exception("Manifest not found.");
                            // Get manifest attributes
                            HashMap<String, String> manifestAttrs = getManifestAttributes(manifestBytes);
                            if (manifestAttrs.containsKey("split")) {
                                // TODO: check for duplicates
                                entries.add(new Entry(fileName, apkFile, APK_SPLIT, manifestAttrs));
                            } else {
                                if (foundBaseApk) {
                                    throw new RuntimeException("Duplicate base apk found.");
                                }
                                baseEntry = new Entry(fileName, apkFile, APK_BASE, manifestAttrs);
                                entries.add(baseEntry);
                                if (manifestAttrs.containsKey("package")) {
                                    packageName = manifestAttrs.get("package");
                                } else throw new RuntimeException("Package name not found.");
                                foundBaseApk = true;
                            }
                        } catch (Exception e) {
                            //noinspection ResultOfMethodCallIgnored
                            apkFile.delete();
                            throw new Exception(e);
                        }
                    } else if (fileName.endsWith(".obb")) {
                        hasObb = true;
                    }
                }
            }
            if (baseEntry == null) throw new Exception("No base apk found.");
            // Sort the entries based on type
            Collections.sort(entries, (o1, o2) -> {
                Integer o1Type = o1.type;
                Integer o2Type = o2.type;
                return o1Type.compareTo(o2Type);
            });
        }
    }

    public Entry getBaseEntry() {
        return baseEntry;
    }

    @NonNull
    public List<Entry> getEntries() {
        return entries;
    }

    public List<Entry> getSelectedEntries() {
        List<Entry> selectedEntries = new ArrayList<>();
        ListIterator<Entry> it = entries.listIterator();
        Entry tmpEntry;
        while (it.hasNext()) {
            tmpEntry = it.next();
            if (tmpEntry.selected) selectedEntries.add(tmpEntry);
        }
        return selectedEntries;
    }

    public String getPackageName() {
        return packageName;
    }

    public boolean isSplit() {
        return isSplit;
    }

    public boolean hasObb() {
        return hasObb;
    }

    public void select(Entry entry) {
        ListIterator<Entry> it = entries.listIterator();
        Entry tmpEntry;
        while (it.hasNext()) {
            tmpEntry = it.next();
            if (tmpEntry.equals(entry)) {
                tmpEntry.selected = true;
                it.set(tmpEntry);
                break; // FIXME: Currently there can be duplicate entries
            }
        }
    }

    public void deselect(Entry entry) {
        ListIterator<Entry> it = entries.listIterator();
        Entry tmpEntry;
        while (it.hasNext()) {
            tmpEntry = it.next();
            if (tmpEntry.equals(entry) && tmpEntry.type != APK_BASE) {
                tmpEntry.selected = false;
                it.set(tmpEntry);
                break; // FIXME: Currently there can be duplicate entries
            }
        }
    }

    @Override
    public void close() {
        for (Entry entry : entries) {
            if (entry.source.exists()) //noinspection ResultOfMethodCallIgnored
                entry.source.delete();
        }
        if (cacheFilePath != null && cacheFilePath.exists()) //noinspection ResultOfMethodCallIgnored
            cacheFilePath.delete();
    }

    @Nullable
    private ByteBuffer getManifestFromApk(File apkFile) throws IOException {
        try (FileInputStream apkInputStream = new FileInputStream(apkFile);
             ZipInputStream zipInputStream = new ZipInputStream(apkInputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (!zipEntry.getName().equals(MANIFEST_FILE)) {
                    zipInputStream.closeEntry();
                    continue;
                }
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] buf = new byte[1024 * 4];
                int n;
                while (-1 != (n = zipInputStream.read(buf))) {
                    buffer.write(buf, 0, n);
                }
                zipInputStream.closeEntry();
                return ByteBuffer.wrap(buffer.toByteArray());
            }
        }
        return null;
    }

    @NonNull
    private HashMap<String, String> getManifestAttributes(@NonNull ByteBuffer manifestBytes) throws Exception {
        HashMap<String, String> manifestAttrs = new HashMap<>();
        AndroidBinXmlParser parser = new AndroidBinXmlParser(manifestBytes);
        int eventType = parser.getEventType();
        boolean seenManifestElement = false;
        while (eventType != AndroidBinXmlParser.EVENT_END_DOCUMENT) {
            if (eventType == AndroidBinXmlParser.EVENT_START_ELEMENT) {
                if (parser.getName().equals("manifest") && parser.getDepth() == 1 && parser.getNamespace().isEmpty()) {
                    if (seenManifestElement) throw new Exception("Duplicate manifest found.");
                    seenManifestElement = true;
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        if (parser.getAttributeName(i).isEmpty())
                            continue;
                        String namespace = "" + (parser.getAttributeNamespace(i).isEmpty() ? "" : (parser.getAttributeNamespace(i) + ":"));
                        manifestAttrs.put(namespace + parser.getAttributeName(i), parser.getAttributeStringValue(i));
                    }
                }
            }
            eventType = parser.next();
        }
        if (!seenManifestElement) throw new Exception("No manifest found.");
        return manifestAttrs;
    }

    public static class Entry implements Parcelable {
        /**
         * Name of the file, for split apk, name of the split instead
         */
        @NonNull
        public String name;
        @NonNull
        public File source;
        @ApkType
        public int type;
        @Nullable
        public HashMap<String, String> manifest;
        @Nullable
        public String splitSuffix;
        public boolean selected = true;  // FIXME: Should be false

        Entry(@NonNull String name, @NonNull File source, @ApkType int type) throws Exception {
            this.name = name;
            this.source = source;
            this.type = type;
            this.selected = true;
            if (type != APK_BASE)
                throw new Exception("Constructor can only be called for base apk");
        }

        Entry(@NonNull String name, @NonNull File source, @ApkType int type, @NonNull HashMap<String, String> manifest) {
            this.name = name;
            this.source = source;
            this.type = type;
            this.manifest = manifest;
            if (type == APK_BASE) this.selected = true;
            else if (type == APK_SPLIT) {
                // Infer type
                if (manifest.containsKey(ANDROID_XML_NAMESPACE + ":isFeatureSplit")) {
                    this.type = APK_SPLIT_FEATURE;
                } else if (manifest.containsKey("configForSplit")) {
                    String splitName = manifest.get("split");
                    if (splitName == null) throw new RuntimeException("Split name is empty.");
                    this.name = splitName;
                    int configPartIndex = this.name.lastIndexOf("config.");
                    if (configPartIndex == -1 || (configPartIndex != 0 && this.name.charAt(configPartIndex - 1) != '.'))
                        return;
                    splitSuffix = this.name.substring(configPartIndex + ("config.".length()));
                    if (StaticDataset.ALL_ABIS.contains(splitSuffix)) {
                        // Check for ABI
                        this.type = APK_SPLIT_ABI;
                    } else if (StaticDataset.DENSITY_NAME_TO_DENSITY.containsKey(splitSuffix)) {
                        // Check for screen density
                        this.type = APK_SPLIT_DENSITY;
                    } else {
                        // Check locale
                        Locale locale = new Locale.Builder().setLanguageTag(splitSuffix).build();
                        for (Locale validLocale : Locale.getAvailableLocales()) {
                            if (validLocale.equals(locale)) {
                                this.type = APK_SPLIT_LOCALE;
                                break;
                            }
                        }
                    }
                }
            }
        }

        protected Entry(@NonNull Parcel in) {
            name = Objects.requireNonNull(in.readString());
            source = new File(Objects.requireNonNull(in.readString()));
            type = in.readInt();
            splitSuffix = in.readString();
            selected = in.readByte() != 0;
        }

        public static final Creator<Entry> CREATOR = new Creator<Entry>() {
            @NonNull
            @Override
            public Entry createFromParcel(Parcel in) {
                return new Entry(in);
            }

            @NonNull
            @Override
            public Entry[] newArray(int size) {
                return new Entry[size];
            }
        };

        @NonNull
        public String getAbi() {
            if (type == APK_SPLIT_ABI) //noinspection ConstantConditions
                return splitSuffix;
            throw new RuntimeException("Attempt to fetch ABI for invalid apk");
        }

        public int getDensity() {
            if (type == APK_SPLIT_DENSITY)
                //noinspection ConstantConditions
                return StaticDataset.DENSITY_NAME_TO_DENSITY.get(splitSuffix);
            throw new RuntimeException("Attempt to fetch Density for invalid apk");
        }

        @NonNull
        public Locale getLocale() {
            if (splitSuffix != null && type == APK_SPLIT_LOCALE)
                return new Locale.Builder().setLanguageTag(splitSuffix).build();
            throw new RuntimeException("Attempt to fetch Locale for invalid apk");
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString(name);
            dest.writeString(source.getAbsolutePath());
            dest.writeInt(type);
            dest.writeString(splitSuffix);
            dest.writeByte((byte) (selected ? 1 : 0));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Entry)) return false;
            Entry entry = (Entry) o;
            return name.equals(entry.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }
}
