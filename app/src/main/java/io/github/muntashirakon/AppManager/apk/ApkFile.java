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

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.SparseArray;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.types.PrivilegedFile;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public final class ApkFile implements AutoCloseable {
    public static final String TAG = "ApkFile";

    private static final String MANIFEST_FILE = "AndroidManifest.xml";
    private static final String ANDROID_XML_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_IS_FEATURE_SPLIT = ANDROID_XML_NAMESPACE + ":isFeatureSplit";
    private static final String ATTR_IS_SPLIT_REQUIRED = ANDROID_XML_NAMESPACE + ":isSplitRequired";
    private static final String ATTR_ISOLATED_SPLIT = ANDROID_XML_NAMESPACE + ":isolatedSplits";
    private static final String ATTR_CONFIG_FOR_SPLIT = "configForSplit";
    private static final String ATTR_SPLIT = "split";
    private static final String ATTR_PACKAGE = "package";
    private static final String CONFIG_PREFIX = "config.";
    private static final String OBB_DIR = "Android/obb";

    // There's hardly any chance of using multiple instances of ApkFile but still kept for convenience
    private static SparseArray<ApkFile> apkFiles = new SparseArray<>(2);

    @NonNull
    public static ApkFile getInstance(int sparseArrayKey) {
        ApkFile apkFile = apkFiles.get(sparseArrayKey);
        if (apkFile == null) {
            throw new IllegalArgumentException("ApkFile not found for key " + sparseArrayKey);
        }
        return apkFile;
    }

    @WorkerThread
    public static int createInstance(Uri apkUri) throws ApkFileException {
        int key = ThreadLocalRandom.current().nextInt();
        ApkFile apkFile = new ApkFile(apkUri, key);
        apkFiles.put(key, apkFile);
        return key;
    }

    @IntDef(value = {
            APK_BASE,
            APK_SPLIT_FEATURE,
            APK_SPLIT_ABI,
            APK_SPLIT_DENSITY,
            APK_SPLIT_LOCALE,
            APK_SPLIT_UNKNOWN,
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
    public static final int APK_SPLIT_UNKNOWN = 5;
    public static final int APK_SPLIT = 6;

    public static List<String> SUPPORTED_EXTENSIONS = new ArrayList<>();

    static {
        SUPPORTED_EXTENSIONS.add("apk");
        SUPPORTED_EXTENSIONS.add("apks");
        SUPPORTED_EXTENSIONS.add("xapk");
    }

    private int sparseArrayKey;
    @NonNull
    private List<Entry> entries = new ArrayList<>();
    private Entry baseEntry;
    @NonNull
    private String packageName;
    private boolean hasObb = false;
    @NonNull
    private List<ZipEntry> obbFiles = new ArrayList<>();
    @NonNull
    private File cacheFilePath;
    @NonNull
    private ParcelFileDescriptor fd;
    @Nullable
    private ZipFile zipFile;

    private ApkFile(@NonNull Uri apkUri, int sparseArrayKey) throws ApkFileException {
        this.sparseArrayKey = sparseArrayKey;
        Context context = AppManager.getContext();
        ContentResolver cr = context.getContentResolver();
        // Check extension
        String name = IOUtils.getFileName(cr, apkUri);
        if (name == null)
            throw new ApkFileException("Could not extract package name from the URI.");
        String extension;
        try {
            extension = IOUtils.getExtension(name).toLowerCase(Locale.ROOT);
            if (!SUPPORTED_EXTENSIONS.contains(extension)) {
                throw new ApkFileException("Invalid package extension.");
            }
        } catch (IndexOutOfBoundsException e) {
            throw new ApkFileException("Invalid package extension.");
        }
        // Open file descriptor
        try {
            ParcelFileDescriptor fd = cr.openFileDescriptor(apkUri, "r");
            if (fd == null) {
                throw new FileNotFoundException("Could not get file descriptor from the Uri");
            }
            this.fd = fd;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new ApkFileException(e);
        }
        this.cacheFilePath = IOUtils.getFileFromFd(fd);
        if (!this.cacheFilePath.exists() || !this.cacheFilePath.canRead()) {
            // Cache manually
            try {
                this.cacheFilePath = IOUtils.getCachedFile(cr.openInputStream(apkUri));
            } catch (IOException e) {
                throw new ApkFileException("Could not cache the input file.");
            }
        }
        String packageName = null;
        // Check for splits
        if (extension.equals("apk")) {
            // Cache the apk file
            try {
                baseEntry = new Entry(cacheFilePath);
            } catch (Exception e) {
                e.printStackTrace();
                throw new ApkFileException("Base APK not found.");
            }
            entries.add(baseEntry);
            // Extract manifest file
            ByteBuffer manifestBytes = getManifestFromApk(cacheFilePath);
            if (manifestBytes == null)
                throw new ApkFileException("Manifest not found for base APK.");
            // Get manifest attributes
            HashMap<String, String> manifestAttrs;
            try {
                manifestAttrs = getManifestAttributes(manifestBytes);
                if (!manifestAttrs.containsKey(ATTR_PACKAGE)) {
                    throw new IllegalArgumentException("Manifest doesn't contain any package name.");
                }
                packageName = manifestAttrs.get(ATTR_PACKAGE);
            } catch (AndroidBinXmlParser.XmlParserException e) {
                e.printStackTrace();
                throw new ApkFileException(e);
            }
        } else {
            boolean foundBaseApk = false;
            File destDir = context.getExternalFilesDir("apks");
            if (destDir == null || !Environment.getExternalStorageState(destDir).equals(Environment.MEDIA_MOUNTED))
                throw new RuntimeException("External media not present");
            if (!destDir.exists()) //noinspection ResultOfMethodCallIgnored
                destDir.mkdirs();
            try {
                zipFile = new ZipFile(cacheFilePath);
            } catch (IOException e) {
                e.printStackTrace();
                throw new ApkFileException(e);
            }
            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
            String fileName;
            while (zipEntries.hasMoreElements()) {
                ZipEntry zipEntry = zipEntries.nextElement();
                if (zipEntry.isDirectory()) continue;
                fileName = IOUtils.getFileNameFromZipEntry(zipEntry);
                if (fileName.endsWith(".apk")) {
                    try (InputStream zipInputStream = zipFile.getInputStream(zipEntry)) {
                        // Extract manifest file
                        ByteBuffer manifestBytes = getManifestFromApk(zipInputStream);
                        if (manifestBytes == null) throw new Exception("Manifest not found.");
                        // Get manifest attributes
                        HashMap<String, String> manifestAttrs = getManifestAttributes(manifestBytes);
                        if (manifestAttrs.containsKey("split")) {
                            // TODO: check for duplicates
                            Entry entry = new Entry(fileName, zipEntry, APK_SPLIT, manifestAttrs);
                            entries.add(entry);
                        } else {
                            if (foundBaseApk) {
                                throw new RuntimeException("Duplicate base apk found.");
                            }
                            baseEntry = new Entry(fileName, zipEntry, APK_BASE, manifestAttrs);
                            entries.add(baseEntry);
                            if (manifestAttrs.containsKey(ATTR_PACKAGE)) {
                                packageName = manifestAttrs.get(ATTR_PACKAGE);
                            } else throw new RuntimeException("Package name not found.");
                            foundBaseApk = true;
                        }
                    } catch (Exception e) {
                        throw new ApkFileException(e);
                    }
                } else if (fileName.endsWith(".obb")) {
                    hasObb = true;
                    obbFiles.add(zipEntry);
                }
            }
            if (baseEntry == null) throw new ApkFileException("No base apk found.");
            // Sort the entries based on type
            Collections.sort(entries, (o1, o2) -> {
                Integer int1 = o1.type;
                int int2 = o2.type;
                int typeCmp;
                if ((typeCmp = int1.compareTo(int2)) != 0) return typeCmp;
                int1 = o1.rank;
                int2 = o2.rank;
                return int1.compareTo(int2);
            });
        }
        if (packageName == null) throw new ApkFileException("Package name not found.");
        this.packageName = packageName;
    }

    public Entry getBaseEntry() {
        return baseEntry;
    }

    @NonNull
    public List<Entry> getEntries() {
        return entries;
    }

    @NonNull
    public List<Entry> getSelectedEntries() {
        List<Entry> selectedEntries = new ArrayList<>();
        ListIterator<Entry> it = entries.listIterator();
        Entry tmpEntry;
        while (it.hasNext()) {
            tmpEntry = it.next();
            if (tmpEntry.isSelected() || tmpEntry.isRequired()) selectedEntries.add(tmpEntry);
        }
        return selectedEntries;
    }

    @NonNull
    public String getPackageName() {
        return packageName;
    }

    public boolean isSplit() {
        return entries.size() > 1;
    }

    public boolean hasObb() {
        return hasObb;
    }

    @WorkerThread
    public boolean extractObb() {
        if (!hasObb || zipFile == null) return true;
        try {
            PrivilegedFile[] extDirs = OsEnvironment.buildExternalStoragePublicDirs();
            PrivilegedFile writableExtDir = null;
            for (PrivilegedFile extDir : extDirs) {
                if (!extDir.exists()) {
                    continue;
                }
                writableExtDir = extDir;
                break;
            }
            if (writableExtDir == null) throw new IOException("Couldn't find any writable Obb dir");
            final PrivilegedFile writableObbDir = new PrivilegedFile(writableExtDir.getAbsolutePath() + "/" + OBB_DIR + "/" + packageName);
            if (writableObbDir.exists()) {
                PrivilegedFile[] oldObbFiles = writableObbDir.listFiles();
                // Delete old files
                if (oldObbFiles != null) {
                    for (PrivilegedFile oldFile : oldObbFiles) {
                        //noinspection ResultOfMethodCallIgnored
                        oldFile.delete();
                    }
                }
            } else {
                if (!writableObbDir.mkdirs()) return false;
            }

            if (AppPref.isRootOrAdbEnabled()) {
                for (ZipEntry obbEntry : obbFiles) {
                    String fileName = IOUtils.getFileNameFromZipEntry(obbEntry);
                    if (cacheFilePath.getAbsolutePath().startsWith("/proc")) {
                        // Normal way won't work for FD
                        File obbCacheFile = IOUtils.getCachedFile(zipFile.getInputStream(obbEntry));
                        if (AppPref.isAdbEnabled()) {
                            boolean result = RunnerUtils.cp(obbCacheFile, new File(writableObbDir, fileName));
                            IOUtils.deleteSilently(obbCacheFile);
                            return result;
                        } else {
                            return RunnerUtils.mv(obbCacheFile, new File(writableObbDir, fileName));
                        }
                    } else {
                        if (!Runner.runCommand(new String[]{"unzip", cacheFilePath.getAbsolutePath(),
                                obbEntry.getName(), "-d", obbEntry.getName().startsWith(OBB_DIR) ?
                                writableExtDir.getAbsolutePath() : writableObbDir.getAbsolutePath()}
                        ).isSuccessful()) {
                            return false;
                        }
                    }
                }
            } else {
                for (ZipEntry obbEntry : obbFiles) {
                    String fileName = IOUtils.getFileNameFromZipEntry(obbEntry);
                    if (fileName.endsWith(".obb")) {
                        // Extract obb file to the destination directory
                        try (InputStream zipInputStream = zipFile.getInputStream(obbEntry)) {
                            IOUtils.saveZipFile(zipInputStream, writableObbDir, fileName);
                        }
                    }
                }
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void select(int entry) {
        Entry tmpEntry = entries.get(entry);
        tmpEntry.selected = true;
        entries.set(entry, tmpEntry);
    }

    public void deselect(int entry) {
        Entry tmpEntry = entries.get(entry);
        tmpEntry.selected = false;
        entries.set(entry, tmpEntry);
    }

    @Override
    public void close() {
        apkFiles.delete(sparseArrayKey);
        for (Entry entry : entries) {
            entry.close();
        }
        IOUtils.closeSilently(zipFile);
        IOUtils.closeSilently(fd);
        IOUtils.deleteSilently(cacheFilePath);
        // Ensure that entries are not accessible if accidentally accessed
        entries.clear();
        baseEntry = null;
        obbFiles.clear();
    }

    @Nullable
    private ByteBuffer getManifestFromApk(File apkFile) {
        try (FileInputStream apkInputStream = new FileInputStream(apkFile)) {
            return getManifestFromApk(apkInputStream);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    private ByteBuffer getManifestFromApk(InputStream apkInputStream) {
        try (ZipInputStream zipInputStream = new ZipInputStream(apkInputStream)) {
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
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @NonNull
    private HashMap<String, String> getManifestAttributes(@NonNull ByteBuffer manifestBytes)
            throws ApkFileException, AndroidBinXmlParser.XmlParserException {
        HashMap<String, String> manifestAttrs = new HashMap<>();
        AndroidBinXmlParser parser = new AndroidBinXmlParser(manifestBytes);
        int eventType = parser.getEventType();
        boolean seenManifestElement = false;
        while (eventType != AndroidBinXmlParser.EVENT_END_DOCUMENT) {
            if (eventType == AndroidBinXmlParser.EVENT_START_ELEMENT) {
                if (parser.getName().equals("manifest") && parser.getDepth() == 1 && parser.getNamespace().isEmpty()) {
                    if (seenManifestElement) {
                        throw new ApkFileException("Duplicate manifest found.");
                    }
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
        if (!seenManifestElement) throw new ApkFileException("No manifest found.");
        return manifestAttrs;
    }

    public class Entry implements AutoCloseable {
        /**
         * Name of the file, for split apk, name of the split instead
         */
        @NonNull
        public String name;
        @ApkType
        public final int type;
        @Nullable
        public final HashMap<String, String> manifest;
        @Nullable
        public String splitSuffix;
        @Nullable
        public String forFeature = null;

        @Nullable
        private File cachedFile;
        @Nullable
        private ZipEntry zipEntry;
        @Nullable
        private File source;
        private boolean selected = false;
        private final boolean required;
        private final boolean isolated;

        /**
         * Rank for a certain {@link #type} to create a priority list. This is applicable for
         * {@link #APK_SPLIT_ABI}, {@link #APK_SPLIT_DENSITY} and {@link #APK_SPLIT_LOCALE}.
         */
        public int rank = Integer.MAX_VALUE;

        Entry(@NonNull File source) {
            this.name = "Base.apk";
            this.source = source;
            this.type = APK_BASE;
            this.selected = this.required = true;
            this.isolated = false;
            this.manifest = null;
        }

        Entry(@NonNull String name, @NonNull ZipEntry zipEntry, @ApkType int type, @NonNull HashMap<String, String> manifest) {
            this.name = name;
            this.zipEntry = zipEntry;
            this.manifest = manifest;
            if (type == APK_BASE) {
                this.selected = this.required = true;
                this.isolated = false;
                this.type = APK_BASE;
            } else if (type == APK_SPLIT) {
                String splitName = manifest.get(ATTR_SPLIT);
                if (splitName == null) throw new RuntimeException("Split name is empty.");
                this.name = splitName;
                // Check if required
                if (manifest.containsKey(ATTR_IS_SPLIT_REQUIRED)) {
                    String value = manifest.get(ATTR_IS_SPLIT_REQUIRED);
                    this.selected = this.required = value != null && Boolean.parseBoolean(value);
                } else this.required = false;
                // Check if isolated
                if (manifest.containsKey(ATTR_ISOLATED_SPLIT)) {
                    String value = manifest.get(ATTR_ISOLATED_SPLIT);
                    this.isolated = value != null && Boolean.parseBoolean(value);
                } else this.isolated = false;
                // Infer types
                if (manifest.containsKey(ATTR_IS_FEATURE_SPLIT)) {
                    this.type = APK_SPLIT_FEATURE;
                } else {
                    if (manifest.containsKey(ATTR_CONFIG_FOR_SPLIT)) {
                        this.forFeature = manifest.get(ATTR_CONFIG_FOR_SPLIT);
                        if (TextUtils.isEmpty(this.forFeature)) this.forFeature = null;
                    }
                    int configPartIndex = this.name.lastIndexOf(CONFIG_PREFIX);
                    if (configPartIndex == -1 || (configPartIndex != 0 && this.name.charAt(configPartIndex - 1) != '.')) {
                        this.type = APK_SPLIT_UNKNOWN;
                        return;
                    }
                    splitSuffix = this.name.substring(configPartIndex + (CONFIG_PREFIX.length()));
                    if (StaticDataset.ALL_ABIS.containsKey(splitSuffix)) {
                        // This split is an ABI
                        this.type = APK_SPLIT_ABI;
                        int index = ArrayUtils.indexOf(Build.SUPPORTED_ABIS, StaticDataset.ALL_ABIS.get(splitSuffix));
                        if (index != -1) this.rank = index;
                    } else if (StaticDataset.DENSITY_NAME_TO_DENSITY.containsKey(splitSuffix)) {
                        // This split is for Screen Density
                        this.type = APK_SPLIT_DENSITY;
                        //noinspection ConstantConditions
                        this.rank = Math.abs(StaticDataset.DEVICE_DENSITY - StaticDataset.DENSITY_NAME_TO_DENSITY.get(splitSuffix));
                    } else if (LangUtils.isValidLocale(splitSuffix)) {
                        // This split is for Locale
                        this.type = APK_SPLIT_LOCALE;
                        Integer rank = StaticDataset.LOCALE_RANKING.get(splitSuffix);
                        if (rank != null) this.rank = rank;
                    } else this.type = APK_SPLIT_UNKNOWN;
                }
            } else {
                this.type = APK_SPLIT_UNKNOWN;
                this.required = this.isolated = false;
            }
        }

        @NonNull
        public String getFileName() {
            if (cachedFile != null && cachedFile.exists()) return cachedFile.getName();
            if (zipEntry != null) return IOUtils.getFileNameFromZipEntry(zipEntry);
            if (source != null && source.exists()) return name;
            else throw new RuntimeException("Neither zipEntry nor source is defined.");
        }

        public long getFileSize() {
            if (cachedFile != null && cachedFile.exists()) return cachedFile.length();
            if (zipEntry != null) return zipEntry.getSize();
            if (source != null && source.exists()) return source.length();
            else throw new RuntimeException("Neither zipEntry nor source is defined.");
        }

        @NonNull
        public InputStream getInputStream() throws IOException {
            if (cachedFile != null && cachedFile.exists()) return new FileInputStream(cachedFile);
            if (zipEntry != null) return Objects.requireNonNull(zipFile).getInputStream(zipEntry);
            if (source != null && source.exists()) return new FileInputStream(source);
            else throw new IOException("Neither zipEntry nor source is defined.");
        }

        @Override
        public void close() {
            IOUtils.deleteSilently(cachedFile);
            if (source != null && !source.getAbsolutePath().startsWith("/proc/self")) {
                IOUtils.deleteSilently(source);
            }
        }

        @WorkerThread
        public File getCachedFile() throws IOException {
            File destDir = AppManager.getContext().getExternalFilesDir("apks");
            if (destDir == null || !Environment.getExternalStorageState(destDir).equals(Environment.MEDIA_MOUNTED))
                throw new RuntimeException("External media not present");
            if (!destDir.exists()) //noinspection ResultOfMethodCallIgnored
                destDir.mkdirs();
            return cachedFile = IOUtils.saveZipFile(getInputStream(), destDir, name);
        }

        public boolean isSelected() {
            return selected;
        }

        public boolean isRequired() {
            return required;
        }

        public boolean isIsolated() {
            return isolated;
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

    public static class ApkFileException extends Throwable {
        public ApkFileException(String message) {
            super(message);
        }

        public ApkFileException(Throwable throwable) {
            super(throwable);
        }
    }
}
