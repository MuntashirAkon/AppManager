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
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.SparseArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.apk.apkm.UnApkm;
import io.github.muntashirakon.AppManager.apk.signing.SigSchemes;
import io.github.muntashirakon.AppManager.apk.signing.SignUtils;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.io.ProxyFile;

import static io.github.muntashirakon.AppManager.apk.ApkUtils.getDensityFromName;
import static io.github.muntashirakon.AppManager.apk.ApkUtils.getManifestAttributes;
import static io.github.muntashirakon.AppManager.apk.ApkUtils.getManifestFromApk;

public final class ApkFile implements AutoCloseable {
    public static final String TAG = "ApkFile";

    private static final String IDSIG_FILE = "Signature.idsig";
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
    private static final SparseArray<ApkFile> apkFiles = new SparseArray<>(2);

    @NonNull
    public static ApkFile getInstance(int sparseArrayKey) {
        ApkFile apkFile = apkFiles.get(sparseArrayKey);
        if (apkFile == null) {
            throw new IllegalArgumentException("ApkFile not found for key " + sparseArrayKey);
        }
        return apkFile;
    }

    @WorkerThread
    public static int createInstance(Uri apkUri, @Nullable String mimeType) throws ApkFileException {
        int key = ThreadLocalRandom.current().nextInt();
        ApkFile apkFile = new ApkFile(apkUri, mimeType, key);
        apkFiles.put(key, apkFile);
        return key;
    }

    @WorkerThread
    public static int createInstance(ApplicationInfo info) throws ApkFileException {
        int key = ThreadLocalRandom.current().nextInt();
        ApkFile apkFile = new ApkFile(info, key);
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
    public static List<String> SUPPORTED_MIMES = new ArrayList<>();

    static {
        SUPPORTED_EXTENSIONS.add("apk");
        SUPPORTED_EXTENSIONS.add("apkm");
        SUPPORTED_EXTENSIONS.add("apks");
        SUPPORTED_EXTENSIONS.add("xapk");
        SUPPORTED_MIMES.add("application/vnd.android.package-archive");
        SUPPORTED_MIMES.add("application/vnd.apkm");
        SUPPORTED_MIMES.add("application/xapk-package-archive");
    }

    private final int sparseArrayKey;
    @NonNull
    private final List<Entry> entries = new ArrayList<>();
    private Entry baseEntry;
    @Nullable
    private File idsigFile;
    @NonNull
    private final String packageName;
    private boolean hasObb = false;
    @NonNull
    private final List<ZipEntry> obbFiles = new ArrayList<>();
    @NonNull
    private File cacheFilePath;
    @Nullable
    private ParcelFileDescriptor fd;
    @Nullable
    private ZipFile zipFile;

    private ApkFile(@NonNull Uri apkUri, @Nullable String mimeType, int sparseArrayKey) throws ApkFileException {
        this.sparseArrayKey = sparseArrayKey;
        Context context = AppManager.getContext();
        ContentResolver cr = context.getContentResolver();
        @NonNull String extension;
        // Check type
        if (mimeType == null) mimeType = cr.getType(apkUri);
        if (mimeType == null || !SUPPORTED_MIMES.contains(mimeType)) {
            Log.e(TAG, "Invalid mime: " + mimeType);
            // Check extension
            String name = IOUtils.getFileName(cr, apkUri);
            if (name == null) {
                throw new ApkFileException("Could not extract package name from the URI.");
            }
            try {
                extension = IOUtils.getExtension(name).toLowerCase(Locale.ROOT);
                if (!SUPPORTED_EXTENSIONS.contains(extension)) {
                    throw new ApkFileException("Invalid package extension.");
                }
            } catch (IndexOutOfBoundsException e) {
                throw new ApkFileException("Invalid package extension.");
            }
        } else {
            if (mimeType.equals("application/xapk-package-archive")) {
                extension = "xapk";
            } else if (mimeType.equals("application/vnd.apkm")) {
                extension = "apkm";
            } else extension = "apk";
        }
        if (extension.equals("apkm")) {
            try {
                if (IOUtils.isInputFileZip(cr, apkUri)) {
                    // DRM-free APKM file, mark it as APKS
                    // FIXME(15/1/21): Give it an special name and verify integrity
                    extension = "apks";
                }
            } catch (IOException e) {
                throw new ApkFileException(e);
            }
        }
        // Cache the file or use file descriptor for non-APKM files
        if (extension.equals("apkm")) {
            // Convert to APKS
            try {
                this.cacheFilePath = IOUtils.getTempFile();
                try (InputStream inputStream = cr.openInputStream(apkUri);
                     OutputStream outputStream = new FileOutputStream(this.cacheFilePath)) {
                    if (inputStream == null)
                        throw new IOException("Apk URI inaccessible or empty.");
                    UnApkm.decryptFile(inputStream, outputStream);
                }
            } catch (IOException e) {
                throw new ApkFileException(e);
            }
        } else {
            // Open file descriptor
            try {
                ParcelFileDescriptor fd = cr.openFileDescriptor(apkUri, "r");
                if (fd == null) {
                    throw new FileNotFoundException("Could not get file descriptor from the Uri");
                }
                this.fd = fd;
            } catch (FileNotFoundException e) {
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
        }
        String packageName = null;
        // Check for splits
        if (extension.equals("apk")) {
            // Cache the apk file
            try {
                baseEntry = new Entry(cacheFilePath);
            } catch (Exception e) {
                throw new ApkFileException("Base APK not found.", e);
            }
            entries.add(baseEntry);
            // Extract manifest file
            ByteBuffer manifestBytes;
            try {
                manifestBytes = getManifestFromApk(cacheFilePath);
            } catch (IOException e) {
                throw new ApkFileException("Manifest not found for base APK.", e);
            }
            // Get manifest attributes
            HashMap<String, String> manifestAttrs;
            try {
                manifestAttrs = getManifestAttributes(manifestBytes);
                if (!manifestAttrs.containsKey(ATTR_PACKAGE)) {
                    throw new IllegalArgumentException("Manifest doesn't contain any package name.");
                }
                packageName = manifestAttrs.get(ATTR_PACKAGE);
            } catch (AndroidBinXmlParser.XmlParserException e) {
                throw new ApkFileException(e);
            }
        } else {
            boolean foundBaseApk = false;
            getCachePath();
            try {
                zipFile = new ZipFile(cacheFilePath);
            } catch (IOException e) {
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
                        ByteBuffer manifestBytes;
                        try {
                            manifestBytes = getManifestFromApk(zipInputStream);
                        } catch (IOException e) {
                            throw new ApkFileException("Manifest not found.", e);
                        }
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
                    } catch (IOException | AndroidBinXmlParser.XmlParserException e) {
                        throw new ApkFileException(e);
                    }
                } else if (fileName.endsWith(".obb")) {
                    hasObb = true;
                    obbFiles.add(zipEntry);
                } else if (fileName.endsWith(".idsig")) {
                    try {
                        idsigFile = IOUtils.saveZipFile(zipFile.getInputStream(zipEntry), getCachePath(), IDSIG_FILE);
                    } catch (IOException e) {
                        throw new ApkFileException(e);
                    }
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

    public ApkFile(@NonNull ApplicationInfo info, int sparseArrayKey) throws ApkFileException {
        this.sparseArrayKey = sparseArrayKey;
        this.packageName = info.packageName;
        this.cacheFilePath = new File(info.publicSourceDir);
        File sourceDir = cacheFilePath.getParentFile();
        if (sourceDir == null || "/data/app".equals(sourceDir.getAbsolutePath())) {
            entries.add(baseEntry = new Entry(cacheFilePath));
        } else {
            File[] apks = sourceDir.listFiles((dir, name) -> name.endsWith(".apk"));
            if (apks == null) throw new ApkFileException("No apk files found");
            boolean foundBaseApk = false;
            String fileName;
            for (File apk : apks) {
                fileName = IOUtils.getLastPathComponent(apk.getAbsolutePath());
                try {
                    // Extract manifest file
                    ByteBuffer manifestBytes;
                    try {
                        manifestBytes = getManifestFromApk(apk);
                    } catch (IOException e) {
                        throw new ApkFileException("Manifest not found.", e);
                    }
                    // Get manifest attributes
                    HashMap<String, String> manifestAttrs = getManifestAttributes(manifestBytes);
                    if (manifestAttrs.containsKey("split")) {
                        Entry entry = new Entry(fileName, apk, APK_SPLIT, manifestAttrs);
                        entries.add(entry);
                    } else {
                        // Could be a base entry, check package name
                        if (!manifestAttrs.containsKey(ATTR_PACKAGE)) {
                            throw new IllegalArgumentException("Manifest doesn't contain any package name.");
                        }
                        String newPackageName = manifestAttrs.get(ATTR_PACKAGE);
                        if (packageName.equals(newPackageName)) {
                            if (foundBaseApk) {
                                throw new RuntimeException("Duplicate base apk found.");
                            }
                            baseEntry = new Entry(fileName, apk, APK_BASE, manifestAttrs);
                            entries.add(baseEntry);
                            foundBaseApk = true;
                        } // else continue;
                    }
                } catch (AndroidBinXmlParser.XmlParserException e) {
                    throw new ApkFileException(e);
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
    }

    public Entry getBaseEntry() {
        return baseEntry;
    }

    @NonNull
    public List<Entry> getEntries() {
        return entries;
    }

    @Nullable
    public File getIdsigFile() {
        return idsigFile;
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
            ProxyFile[] extDirs = OsEnvironment.buildExternalStoragePublicDirs();
            ProxyFile writableExtDir = null;
            for (ProxyFile extDir : extDirs) {
                if (!extDir.exists()) {
                    continue;
                }
                writableExtDir = extDir;
                break;
            }
            if (writableExtDir == null) throw new IOException("Couldn't find any writable Obb dir");
            final ProxyFile writableObbDir = new ProxyFile(writableExtDir.getAbsolutePath() + "/" + OBB_DIR + "/" + packageName);
            if (writableObbDir.exists()) {
                ProxyFile[] oldObbFiles = writableObbDir.listFiles();
                // Delete old files
                if (oldObbFiles != null) {
                    for (ProxyFile oldFile : oldObbFiles) {
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

    public boolean needSigning() {
        return (boolean) AppPref.get(AppPref.PrefKey.PREF_INSTALLER_SIGN_APK_BOOL);
    }

    @Override
    public void close() {
        apkFiles.delete(sparseArrayKey);
        for (Entry entry : entries) {
            entry.close();
        }
        IOUtils.closeQuietly(zipFile);
        IOUtils.closeQuietly(fd);
        IOUtils.deleteSilently(idsigFile);
        if (!cacheFilePath.getAbsolutePath().startsWith("/data/app")) {
            IOUtils.deleteSilently(cacheFilePath);
        }
        // Ensure that entries are not accessible if accidentally accessed
        entries.clear();
        baseEntry = null;
        obbFiles.clear();
    }

    @NonNull
    private File getCachePath() {
        File destDir = AppManager.getContext().getExternalFilesDir("apks");
        if (destDir == null || !Environment.getExternalStorageState(destDir).equals(Environment.MEDIA_MOUNTED))
            throw new RuntimeException("External media not present");
        if (!destDir.exists()) //noinspection ResultOfMethodCallIgnored
            destDir.mkdirs();
        return destDir;
    }

    public class Entry implements AutoCloseable {
        /**
         * Name of the file, for split apk, name of the split instead
         */
        @NonNull
        public final String name;
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
        @Nullable
        private File signedFile;
        @Nullable
        private File idsigFile;
        private boolean selected = false;
        private final boolean required;
        private final boolean isolated;

        /**
         * Rank for a certain {@link #type} to create a priority list. This is applicable for
         * {@link #APK_SPLIT_ABI}, {@link #APK_SPLIT_DENSITY} and {@link #APK_SPLIT_LOCALE}.
         * Smallest rank number denotes highest rank.
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
            this(name, type, manifest);
            this.zipEntry = zipEntry;
        }

        Entry(@NonNull String name, @NonNull File source, @ApkType int type, @NonNull HashMap<String, String> manifest) {
            this(name, type, manifest);
            this.source = source;
        }

        private Entry(@NonNull String name, @ApkType int type, @NonNull HashMap<String, String> manifest) {
            this.manifest = manifest;
            if (type == APK_BASE) {
                this.name = name;
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
                        if (index != -1) {
                            this.rank = index;
                            if (this.forFeature == null) {
                                // Increment rank for base APK
                                this.rank -= 1000;
                            }
                        }
                    } else if (StaticDataset.DENSITY_NAME_TO_DENSITY.containsKey(splitSuffix)) {
                        // This split is for Screen Density
                        this.type = APK_SPLIT_DENSITY;
                        this.rank = Math.abs(StaticDataset.DEVICE_DENSITY - getDensityFromName(splitSuffix));
                        if (this.forFeature == null) {
                            // Increment rank for base APK
                            this.rank -= 1000;
                        }
                    } else if (LangUtils.isValidLocale(splitSuffix)) {
                        // This split is for Locale
                        this.type = APK_SPLIT_LOCALE;
                        Integer rank = StaticDataset.LOCALE_RANKING.get(splitSuffix);
                        if (rank != null) {
                            this.rank = rank;
                            if (this.forFeature == null) {
                                // Increment rank for base APK
                                this.rank -= 1000;
                            }
                        }
                    } else this.type = APK_SPLIT_UNKNOWN;
                }
            } else {
                this.name = name;
                this.type = APK_SPLIT_UNKNOWN;
                this.required = this.isolated = false;
            }
        }

        @NonNull
        public String getFileName() {
            if (cachedFile != null && cachedFile.exists()) return cachedFile.getName();
            if (zipEntry != null) return IOUtils.getFileNameFromZipEntry(zipEntry);
            if (source != null && source.exists()) return source.getName();
            else throw new RuntimeException("Neither zipEntry nor source is defined.");
        }

        public long getFileSize() {
            if (cachedFile != null && cachedFile.exists()) return cachedFile.length();
            if (zipEntry != null) return zipEntry.getSize();
            if (source != null && source.exists()) return source.length();
            else throw new RuntimeException("Neither zipEntry nor source is defined.");
        }

        public File getSignedFile(Context context) throws IOException {
            if (signedFile != null) return signedFile;
            File realFile = getRealCachedFile();
            if (!needSigning()) {
                // Return original/real file if signing is not requested
                return realFile;
            }
            signedFile = IOUtils.getTempFile();
            SigSchemes sigSchemes = SigSchemes.fromPref();
            try {
                SignUtils signUtils = SignUtils.getInstance(sigSchemes, context);
                if (signUtils.isV4SchemeEnabled()) {
                    idsigFile = IOUtils.getTempFile();
                    signUtils.setIdsigFile(idsigFile);
                }
                if (signUtils.sign(realFile, signedFile, -1)) {
                    if (SignUtils.verify(sigSchemes, signedFile, idsigFile)) {
                        return signedFile;
                    }
                }
                throw new IOException("Failed to sign " + realFile);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        public InputStream getSignedInputStream(Context context) throws IOException {
            if (!needSigning()) {
                // Return original/real input stream if signing is not requested
                return getRealInputStream();
            }
            return new FileInputStream(getSignedFile(context));
        }

        @Nullable
        public String getApkSource() {
            return source == null ? null : source.getAbsolutePath();
        }

        @Override
        public void close() {
            IOUtils.deleteSilently(cachedFile);
            IOUtils.deleteSilently(idsigFile);
            IOUtils.deleteSilently(signedFile);
            if (source != null && !source.getAbsolutePath().startsWith("/proc/self")
                    && !source.getAbsolutePath().startsWith("/data/app")) {
                IOUtils.deleteSilently(source);
            }
        }

        @NonNull
        private InputStream getRealInputStream() throws IOException {
            if (cachedFile != null && cachedFile.exists()) return new FileInputStream(cachedFile);
            if (zipEntry != null) return Objects.requireNonNull(zipFile).getInputStream(zipEntry);
            if (source != null && source.exists()) return new FileInputStream(source);
            else throw new IOException("Neither zipEntry nor source is defined.");
        }

        @WorkerThread
        public File getRealCachedFile() throws IOException {
            if (source != null && source.canRead() && !source.getAbsolutePath().startsWith("/proc/self")) return source;
            if (cachedFile != null) {
                if (cachedFile.canRead()) return cachedFile;
                else IOUtils.deleteSilently(cachedFile);
            }
            try (InputStream is = getRealInputStream()) {
                return cachedFile = IOUtils.saveZipFile(is, getCachePath(), name);
            }
        }

        public boolean isSelected() {
            return selected;
        }

        public boolean isRequired() {
            return required;
        }

        @SuppressWarnings("unused")
        public boolean isIsolated() {
            return isolated;
        }

        @NonNull
        public String getAbi() {
            if (type == APK_SPLIT_ABI) {
                return Objects.requireNonNull(splitSuffix);
            }
            throw new RuntimeException("Attempt to fetch ABI for invalid apk");
        }

        public int getDensity() {
            if (type == APK_SPLIT_DENSITY) {
                return getDensityFromName(splitSuffix);
            }
            throw new RuntimeException("Attempt to fetch Density for invalid apk");
        }

        @NonNull
        public Locale getLocale() {
            if (type == APK_SPLIT_LOCALE) {
                return new Locale.Builder().setLanguageTag(Objects.requireNonNull(splitSuffix)).build();
            }
            throw new RuntimeException("Attempt to fetch Locale for invalid apk");
        }

        public String toLocalizedString(Context context) {
            switch (type) {
                case ApkFile.APK_BASE:
                    return context.getString(R.string.base_apk);
                case ApkFile.APK_SPLIT_DENSITY:
                    if (forFeature != null) {
                        return context.getString(R.string.density_split_for_feature, splitSuffix, getDensity(), forFeature);
                    } else {
                        return context.getString(R.string.density_split_for_base_apk, splitSuffix, getDensity());
                    }
                case ApkFile.APK_SPLIT_ABI:
                    if (forFeature != null) {
                        return context.getString(R.string.abi_split_for_feature, getAbi(), forFeature);
                    } else {
                        return context.getString(R.string.abi_split_for_base_apk, getAbi());
                    }
                case ApkFile.APK_SPLIT_LOCALE:
                    if (forFeature != null) {
                        return context.getString(R.string.locale_split_for_feature, getLocale().getDisplayLanguage(), forFeature);
                    } else {
                        return context.getString(R.string.locale_split_for_base_apk, getLocale().getDisplayLanguage());
                    }
                case ApkFile.APK_SPLIT_FEATURE:
                    return context.getString(R.string.split_feature_name, name);
                case ApkFile.APK_SPLIT_UNKNOWN:
                    return name;
                case ApkFile.APK_SPLIT:
                    if (forFeature != null) {
                        return context.getString(R.string.unknown_split_for_feature, name, forFeature);
                    } else {
                        return context.getString(R.string.unknown_split_for_base_apk, name);
                    }
                default:
                    throw new RuntimeException("Invalid split type.");
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof String) return name.equals(o);
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

        public ApkFileException(String message, Throwable throwable) {
            super(message, throwable);
        }

        public ApkFileException(Throwable throwable) {
            super(throwable);
        }
    }
}
