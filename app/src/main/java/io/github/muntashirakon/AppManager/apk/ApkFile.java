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
import android.util.Log;

import com.google.classysharkandroid.utils.UriUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

public class ApkFile implements AutoCloseable {
    public static final String TAG = "TAG";

    @IntDef(value = {
            APK_BASE,
            APK_SPLIT,
            APK_SPLIT_FEATURE,
            APK_SPLIT_ABI,
            APK_SPLIT_DENSITY,
            APK_SPLIT_LOCALE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ApkType {
    }

    public static final int APK_BASE = 0;
    public static final int APK_SPLIT = 1;
    public static final int APK_SPLIT_FEATURE = 2;
    public static final int APK_SPLIT_ABI = 3;
    public static final int APK_SPLIT_DENSITY = 4;
    public static final int APK_SPLIT_LOCALE = 5;

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
    private boolean hasObb = false;
    private boolean isSplit = false;
    private Uri apkUri;

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
        } else {
            isSplit = true;
            File destDir = new File(context.getFilesDir(), "apks");
            if (!destDir.exists()) //noinspection ResultOfMethodCallIgnored
                destDir.mkdirs();
            try (ZipFile zipFile = new ZipFile(filePath)) {
                Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
                File apkFile;
                String fileName;
                while (zipEntries.hasMoreElements()) {
                    ZipEntry zipEntry = zipEntries.nextElement();
                    if (zipEntry.isDirectory()) continue;
                    fileName = Utils.getFileNameFromZipEntry(zipEntry);
                    if (fileName.endsWith(".apk")) {
                        // Extract the apk file
                        apkFile = IOUtils.saveZipFile(zipFile.getInputStream(zipEntry), destDir, fileName);
                        Log.e(TAG, "Apk File: " + apkFile);
                        try {
                            // Extract manifest file
                            ByteBuffer manifestBytes = getManifestFromApk(apkFile);
                            if (manifestBytes == null) throw new Exception("Manifest not found.");
                            // Get manifest attributes
                            HashMap<String, String> manifestAttrs = getManifestAttributes(manifestBytes);
                            if (manifestAttrs.containsKey("split")) {
                                entries.add(new Entry(fileName, apkFile, APK_SPLIT, manifestAttrs));
                            } else {
                                baseEntry = new Entry(fileName, apkFile, APK_BASE, manifestAttrs);
                                entries.add(baseEntry);
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
        }
    }

    public Entry getBaseEntry() {
        return baseEntry;
    }

    @NonNull
    public List<Entry> getEntries() {
        return entries;
    }

    public boolean isSplit() {
        return isSplit;
    }

    public boolean hasObb() {
        return hasObb;
    }

    @Override
    public void close() {
        for (Entry entry : entries) {
            if (entry.source.exists()) //noinspection ResultOfMethodCallIgnored
                entry.source.delete();
        }
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

    public static class Entry {
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
        @Nullable public String splitSuffix;

        Entry(@NonNull String name, @NonNull File source, @ApkType int type) throws Exception {
            this.name = name;
            this.source = source;
            this.type = type;
            if (type != APK_BASE)
                throw new Exception("Constructor can only be called for base apk");
        }

        Entry(@NonNull String name, @NonNull File source, @ApkType int type, @NonNull HashMap<String, String> manifest) {
            this.name = name;
            this.source = source;
            this.type = type;
            this.manifest = manifest;
            if (type == APK_SPLIT) {
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
    }
}
