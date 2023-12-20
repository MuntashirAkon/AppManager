// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk;

import static io.github.muntashirakon.AppManager.apk.ApkUtils.getDensityFromName;
import static io.github.muntashirakon.AppManager.apk.ApkUtils.getManifestAttributes;
import static io.github.muntashirakon.AppManager.apk.ApkUtils.getManifestFromApk;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.SparseIntArray;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.collection.SparseArrayCompat;

import com.google.android.material.color.MaterialColors;

import org.json.JSONException;

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
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.apk.signing.SigSchemes;
import io.github.muntashirakon.AppManager.apk.signing.Signer;
import io.github.muntashirakon.AppManager.apk.splitapk.ApksMetadata;
import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.VMRuntime;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.unapkm.api.UnApkm;
import io.github.muntashirakon.util.LocalizedString;

public final class ApkFile implements AutoCloseable {
    public static final String TAG = "ApkFile";

    private static final String ATTR_IS_FEATURE_SPLIT = "android:isFeatureSplit";
    private static final String ATTR_IS_SPLIT_REQUIRED = "android:isSplitRequired";
    private static final String ATTR_ISOLATED_SPLIT = "android:isolatedSplits";
    private static final String ATTR_CONFIG_FOR_SPLIT = "configForSplit";
    private static final String ATTR_SPLIT = "split";
    private static final String ATTR_PACKAGE = "package";
    private static final String CONFIG_PREFIX = "config.";

    private static final String UN_APKM_PKG = "io.github.muntashirakon.unapkm";

    // There's hardly any chance of using multiple instances of ApkFile but still kept for convenience
    private static final SparseArrayCompat<ApkFile> sApkFiles = new SparseArrayCompat<>(3);
    private static final SparseIntArray sInstanceCount = new SparseIntArray(3);

    @AnyThread
    @Nullable
    static ApkFile getInstance(int sparseArrayKey) {
        synchronized (sApkFiles) {
            ApkFile apkFile = sApkFiles.get(sparseArrayKey);
            if (apkFile == null) {
                return null;
            }
            synchronized (sInstanceCount) {
                // Increment the number of active instances
                sInstanceCount.put(sparseArrayKey, sInstanceCount.get(sparseArrayKey) + 1);
            }
            return apkFile;
        }
    }

    @AnyThread
    static int createInstance(Uri apkUri, @Nullable String mimeType) throws ApkFileException {
        synchronized (sApkFiles) {
            int key = getUniqueKey();
            ApkFile apkFile = new ApkFile(apkUri, mimeType, key);
            sApkFiles.put(key, apkFile);
            return key;
        }
    }

    @AnyThread
    static int createInstance(ApplicationInfo info) throws ApkFileException {
        synchronized (sApkFiles) {
            int key = getUniqueKey();
            ApkFile apkFile = new ApkFile(info, key);
            sApkFiles.put(key, apkFile);
            return key;
        }
    }

    @GuardedBy("sApkFiles")
    private static int getUniqueKey() {
        int key;
        do {
            key = ThreadLocalRandom.current().nextInt();
        } while (sApkFiles.containsKey(key));
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
    /**
     * Generic split type. For internal uses only, never returned by {@link Entry#type}.
     */
    public static final int APK_SPLIT = 6;

    public static List<String> SUPPORTED_EXTENSIONS = new ArrayList<>();
    public static List<String> SUPPORTED_MIMES = new ArrayList<>();

    static {
        SUPPORTED_EXTENSIONS.add("apk");
        SUPPORTED_EXTENSIONS.add("apkm");
        SUPPORTED_EXTENSIONS.add("apks");
        SUPPORTED_EXTENSIONS.add("xapk");
        SUPPORTED_MIMES.add("application/x-apks");
        SUPPORTED_MIMES.add("application/vnd.android.package-archive");
        SUPPORTED_MIMES.add("application/vnd.apkm");
        SUPPORTED_MIMES.add("application/xapk-package-archive");
    }

    private final int mSparseArrayKey;
    @NonNull
    private final List<Entry> mEntries = new ArrayList<>();
    private Entry mBaseEntry;
    @Nullable
    private File mIdsigFile;
    @Nullable
    private ApksMetadata mApksMetadata;
    @NonNull
    private final String mPackageName;
    @NonNull
    private final List<ZipEntry> mObbFiles = new ArrayList<>();
    private final FileCache mFileCache = new FileCache();
    @NonNull
    private final File mCacheFilePath;
    @Nullable
    private ParcelFileDescriptor mFd;
    @Nullable
    private ZipFile mZipFile;
    private boolean mClosed;

    private ApkFile(@NonNull Uri apkUri, @Nullable String mimeType, int sparseArrayKey) throws ApkFileException {
        mSparseArrayKey = sparseArrayKey;
        Context context = ContextUtils.getContext();
        Path apkSource = Paths.get(apkUri);
        @NonNull String extension;
        // Check type
        if (mimeType == null) mimeType = apkSource.getType();
        if (!SUPPORTED_MIMES.contains(mimeType)) {
            Log.e(TAG, "Invalid mime: %s", mimeType);
            // Check extension
            if (!SUPPORTED_EXTENSIONS.contains(apkSource.getExtension())) {
                throw new ApkFileException("Invalid package extension.");
            }
            extension = Objects.requireNonNull(apkSource.getExtension());
        } else {
            switch (mimeType) {
                case "application/x-apks":
                    extension = "apks";
                    break;
                case "application/xapk-package-archive":
                    extension = "xapk";
                    break;
                case "application/vnd.apkm":
                    extension = "apkm";
                    break;
                default:
                    extension = "apk";
                    break;
            }
        }
        if (extension.equals("apkm")) {
            try {
                if (FileUtils.isZip(apkSource)) {
                    // DRM-free APKM file, mark it as APKS
                    // FIXME(#227): Give it a special name and verify integrity
                    extension = "apks";
                }
            } catch (IOException | SecurityException e) {
                throw new ApkFileException(e);
            }
        }
        // Cache the file or use file descriptor for non-APKM files
        if (extension.equals("apkm")) {
            // Convert to APKS
            try {
                mCacheFilePath = mFileCache.createCachedFile("apks");
                try (ParcelFileDescriptor inputFD = FileUtils.getFdFromUri(context, apkUri, "r");
                     OutputStream outputStream = new FileOutputStream(mCacheFilePath)) {
                    UnApkm unApkm = new UnApkm(context, UN_APKM_PKG);
                    unApkm.decryptFile(inputFD, outputStream);
                }
            } catch (IOException | RemoteException e) {
                throw new ApkFileException(e);
            }
        } else {
            // Open file descriptor if necessary
            File cacheFilePath = null;
            if (ContentResolver.SCHEME_FILE.equals(apkUri.getScheme())) {
                // File scheme may not require an FD
                cacheFilePath = new File(apkUri.getPath());
            }
            if (!FmProvider.AUTHORITY.equals(apkUri.getAuthority())) {
                // Content scheme has a third-party authority
                try {
                    mFd = FileUtils.getFdFromUri(context, apkUri, "r");
                    cacheFilePath = FileUtils.getFileFromFd(mFd);
                } catch (FileNotFoundException e) {
                    throw new ApkFileException(e);
                } catch (SecurityException e) {
                    Log.e(TAG, e);
                }
            }
            if (cacheFilePath == null || !FileUtils.canReadUnprivileged(cacheFilePath)) {
                // Cache manually
                try {
                    mCacheFilePath = mFileCache.getCachedFile(apkSource);
                } catch (IOException | SecurityException e) {
                    throw new ApkFileException("Could not cache the input file.", e);
                }
            } else mCacheFilePath = cacheFilePath;
        }
        String packageName = null;
        // Check for splits
        if (extension.equals("apk")) {
            // Get manifest attributes
            ByteBuffer manifest;
            HashMap<String, String> manifestAttrs;
            try {
                manifest = getManifestFromApk(mCacheFilePath);
                manifestAttrs = getManifestAttributes(manifest);
            } catch (IOException e) {
                throw new ApkFileException("Manifest not found for base APK.", e);
            }
            if (!manifestAttrs.containsKey(ATTR_PACKAGE)) {
                throw new IllegalArgumentException("Manifest doesn't contain any package name.");
            }
            packageName = manifestAttrs.get(ATTR_PACKAGE);
            mBaseEntry = new Entry(mCacheFilePath, manifest);
            mEntries.add(mBaseEntry);
        } else {
            try {
                mZipFile = new ZipFile(mCacheFilePath);
            } catch (IOException e) {
                throw new ApkFileException(e);
            }
            Enumeration<? extends ZipEntry> zipEntries = mZipFile.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry zipEntry = zipEntries.nextElement();
                if (zipEntry.isDirectory()) continue;
                String fileName = FileUtils.getFilenameFromZipEntry(zipEntry);
                if (fileName.endsWith(".apk")) { // APK is more likely to match
                    try (InputStream zipInputStream = mZipFile.getInputStream(zipEntry)) {
                        // Get manifest attributes
                        ByteBuffer manifest;
                        HashMap<String, String> manifestAttrs;
                        try {
                            manifest = getManifestFromApk(zipInputStream);
                            manifestAttrs = getManifestAttributes(manifest);
                        } catch (IOException e) {
                            throw new ApkFileException("Manifest not found.", e);
                        }
                        if (manifestAttrs.containsKey("split")) {
                            // TODO: check for duplicates
                            Entry entry = new Entry(fileName, zipEntry, APK_SPLIT, manifest, manifestAttrs);
                            mEntries.add(entry);
                        } else {
                            if (mBaseEntry != null) {
                                throw new RuntimeException("Duplicate base apk found.");
                            }
                            mBaseEntry = new Entry(fileName, zipEntry, APK_BASE, manifest, manifestAttrs);
                            mEntries.add(mBaseEntry);
                            if (manifestAttrs.containsKey(ATTR_PACKAGE)) {
                                packageName = manifestAttrs.get(ATTR_PACKAGE);
                            } else throw new RuntimeException("Package name not found.");
                        }
                    } catch (IOException e) {
                        throw new ApkFileException(e);
                    }
                } else if (fileName.equals(ApksMetadata.META_FILE)) {
                    try {
                        String jsonString = IoUtils.getInputStreamContent(mZipFile.getInputStream(zipEntry));
                        mApksMetadata = new ApksMetadata();
                        mApksMetadata.readMetadata(jsonString);
                    } catch (IOException | JSONException e) {
                        mApksMetadata = null;
                        Log.w(TAG, "The contents of info.json in the bundle is invalid", e);
                    }
                } else if (fileName.endsWith(".obb")) {
                    mObbFiles.add(zipEntry);
                } else if (fileName.endsWith(".idsig")) {
                    try {
                        mIdsigFile = mFileCache.getCachedFile(mZipFile.getInputStream(zipEntry), ".idsig");
                    } catch (IOException e) {
                        throw new ApkFileException(e);
                    }
                }
            }
            if (mBaseEntry == null) throw new ApkFileException("No base apk found.");
            // Sort the entries based on type and rank
            Collections.sort(mEntries, (o1, o2) -> {
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
        mPackageName = packageName;
    }

    private ApkFile(@NonNull ApplicationInfo info, int sparseArrayKey) throws ApkFileException {
        mSparseArrayKey = sparseArrayKey;
        mPackageName = info.packageName;
        mCacheFilePath = new File(info.publicSourceDir);
        File sourceDir = mCacheFilePath.getParentFile();
        if (sourceDir == null || "/data/app".equals(sourceDir.getAbsolutePath())) {
            // Old file structure (storing APK files at /data/app)
            try {
                mEntries.add(mBaseEntry = new Entry(mCacheFilePath, getManifestFromApk(mCacheFilePath)));
            } catch (IOException e) {
                throw new ApkFileException("Manifest not found.", e);
            }
        } else {
            File[] apks = sourceDir.listFiles((dir, name) -> name.endsWith(".apk"));
            if (apks == null) {
                // Directory might be inaccessible
                Log.w(TAG, "No apk files found in %s. Using default.", sourceDir);
                List<File> allApks = new ArrayList<>();
                allApks.add(mCacheFilePath);
                String[] splits = info.splitPublicSourceDirs;
                if (splits != null) {
                    for (String split : splits) {
                        if (split != null) {
                            allApks.add(new File(split));
                        }
                    }
                }
                apks = allApks.toArray(new File[0]);
            }
            String fileName;
            for (File apk : apks) {
                fileName = Paths.getLastPathSegment(apk.getAbsolutePath());
                // Get manifest attributes
                ByteBuffer manifest;
                HashMap<String, String> manifestAttrs;
                try {
                    manifest = getManifestFromApk(apk);
                    manifestAttrs = getManifestAttributes(manifest);
                } catch (IOException e) {
                    throw new ApkFileException("Manifest not found.", e);
                }
                if (manifestAttrs.containsKey("split")) {
                    Entry entry = new Entry(fileName, apk, APK_SPLIT, manifest, manifestAttrs);
                    mEntries.add(entry);
                } else {
                    // Could be a base entry, check package name
                    if (!manifestAttrs.containsKey(ATTR_PACKAGE)) {
                        throw new IllegalArgumentException("Manifest doesn't contain any package name.");
                    }
                    String newPackageName = manifestAttrs.get(ATTR_PACKAGE);
                    if (mPackageName.equals(newPackageName)) {
                        if (mBaseEntry != null) {
                            throw new RuntimeException("Duplicate base apk found.");
                        }
                        mBaseEntry = new Entry(fileName, apk, APK_BASE, manifest, manifestAttrs);
                        mEntries.add(mBaseEntry);
                    } // else continue;
                }
            }
            if (mBaseEntry == null) throw new ApkFileException("No base apk found.");
            // Sort the entries based on type
            Collections.sort(mEntries, (o1, o2) -> {
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
        return mBaseEntry;
    }

    @NonNull
    public List<Entry> getEntries() {
        return mEntries;
    }

    @Nullable
    public File getIdsigFile() {
        if (mIdsigFile != null) {
            return mIdsigFile;
        }
        return null;
    }

    @Nullable
    public ApksMetadata getApksMetadata() {
        return mApksMetadata;
    }

    @NonNull
    public String getPackageName() {
        return mPackageName;
    }

    public boolean isSplit() {
        return mEntries.size() > 1;
    }

    public boolean hasObb() {
        return mObbFiles.size() > 0;
    }

    @WorkerThread
    public void extractObb(Path writableObbDir) throws IOException {
        if (!hasObb() || mZipFile == null) return;
        for (ZipEntry obbEntry : mObbFiles) {
            String fileName = FileUtils.getFilenameFromZipEntry(obbEntry);
            Path obbDir = writableObbDir.findOrCreateFile(fileName, null);
            // Extract obb file to the destination directory
            try (InputStream zipInputStream = mZipFile.getInputStream(obbEntry);
                 OutputStream outputStream = obbDir.openOutputStream()) {
                IoUtils.copy(zipInputStream, outputStream);
            }
        }
    }

    public boolean isClosed() {
        return mClosed;
    }

    @Override
    public void close() {
        synchronized (sInstanceCount) {
            if (sInstanceCount.get(mSparseArrayKey) > 1) {
                // This isn't the only instance, do not close yet
                sInstanceCount.put(mSparseArrayKey, sInstanceCount.get(mSparseArrayKey) - 1);
                return;
            }
            // Only this instance remained
            sInstanceCount.delete(mSparseArrayKey);
        }
        mClosed = true;
        sApkFiles.remove(mSparseArrayKey);
        for (Entry entry : mEntries) {
            entry.close();
        }
        IoUtils.closeQuietly(mZipFile);
        IoUtils.closeQuietly(mFd);
        IoUtils.closeQuietly(mFileCache);
        FileUtils.deleteSilently(mIdsigFile);
        // Ensure that entries are not accessible if accidentally accessed
        mEntries.clear();
        mBaseEntry = null;
        mObbFiles.clear();
    }

    @Override
    protected void finalize() {
        if (!mClosed) {
            close();
        }
    }

    public class Entry implements AutoCloseable, LocalizedString {
        /**
         * Unique identifier capable of persisting across new instances. This is usually the file path (relative or
         * absolute).
         */
        public final String id;
        /**
         * Name of the file, for split apk, name of the split instead
         */
        @NonNull
        public final String name;
        /**
         * Type of the APK (base or split). One of {@link #APK_BASE}, {@link #APK_SPLIT_FEATURE},
         * {@link #APK_SPLIT_ABI}, {@link #APK_SPLIT_DENSITY}, {@link #APK_SPLIT_LOCALE},  {@link #APK_SPLIT_UNKNOWN}.
         */
        @ApkType
        public final int type;
        /**
         * The entire manifest file as {@link ByteBuffer}.
         */
        @NonNull
        public final ByteBuffer manifest;

        @Nullable
        private String mSplitSuffix;
        @Nullable
        private String mForFeature = null;
        @Nullable
        private File mCachedFile;
        @Nullable
        private ZipEntry mZipEntry;
        @Nullable
        private File mSource;
        @Nullable
        private File mSignedFile;
        @Nullable
        private File mIdsigFile;
        private final boolean mRequired;
        private final boolean mIsolated;

        /**
         * Rank for a certain {@link #type} to create a priority list. This is applicable for
         * {@link #APK_SPLIT_ABI}, {@link #APK_SPLIT_DENSITY} and {@link #APK_SPLIT_LOCALE}.
         * Smallest rank number denotes highest rank.
         */
        public int rank = Integer.MAX_VALUE;

        Entry(@NonNull File source, @NonNull ByteBuffer manifest) {
            mSource = Objects.requireNonNull(source);
            id = "base-apk"; // A safe ID since others ends with `.apk`
            name = "Base.apk";
            type = APK_BASE;
            mRequired = true;
            mIsolated = false;
            this.manifest = Objects.requireNonNull(manifest);
        }

        Entry(@NonNull String name,
              @NonNull ZipEntry zipEntry,
              @ApkType int type,
              @NonNull ByteBuffer manifest,
              @NonNull HashMap<String, String> manifestAttrs) {
            this(Objects.requireNonNull(zipEntry).getName(), name, type, manifest, manifestAttrs);
            mZipEntry = Objects.requireNonNull(zipEntry);
        }

        Entry(@NonNull String name,
              @NonNull File source,
              @ApkType int type,
              @NonNull ByteBuffer manifest,
              @NonNull HashMap<String, String> manifestAttrs) {
            this(Objects.requireNonNull(source).getAbsolutePath(), name, type, manifest, manifestAttrs);
            mSource = source;
        }

        private Entry(@NonNull String id,
                      @NonNull String name,
                      @ApkType int type,
                      @NonNull ByteBuffer manifest,
                      @NonNull HashMap<String, String> manifestAttrs) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(manifest);
            Objects.requireNonNull(manifestAttrs);
            this.id = id;
            this.manifest = manifest;
            if (type == APK_BASE) {
                this.name = name;
                mRequired = true;
                mIsolated = false;
                this.type = APK_BASE;
            } else if (type == APK_SPLIT) {
                String splitName = manifestAttrs.get(ATTR_SPLIT);
                if (splitName == null) throw new RuntimeException("Split name is empty.");
                this.name = splitName;
                // Check if required
                if (manifestAttrs.containsKey(ATTR_IS_SPLIT_REQUIRED)) {
                    String value = manifestAttrs.get(ATTR_IS_SPLIT_REQUIRED);
                    mRequired = value != null && Boolean.parseBoolean(value);
                } else mRequired = false;
                // Check if isolated
                if (manifestAttrs.containsKey(ATTR_ISOLATED_SPLIT)) {
                    String value = manifestAttrs.get(ATTR_ISOLATED_SPLIT);
                    mIsolated = value != null && Boolean.parseBoolean(value);
                } else mIsolated = false;
                // Infer types
                if (manifestAttrs.containsKey(ATTR_IS_FEATURE_SPLIT)) {
                    this.type = APK_SPLIT_FEATURE;
                } else {
                    if (manifestAttrs.containsKey(ATTR_CONFIG_FOR_SPLIT)) {
                        mForFeature = manifestAttrs.get(ATTR_CONFIG_FOR_SPLIT);
                        if (TextUtils.isEmpty(mForFeature)) mForFeature = null;
                    }
                    int configPartIndex = this.name.lastIndexOf(CONFIG_PREFIX);
                    if (configPartIndex == -1 || (configPartIndex != 0 && this.name.charAt(configPartIndex - 1) != '.')) {
                        this.type = APK_SPLIT_UNKNOWN;
                        return;
                    }
                    mSplitSuffix = this.name.substring(configPartIndex + (CONFIG_PREFIX.length()));
                    if (StaticDataset.ALL_ABIS.containsKey(mSplitSuffix)) {
                        // This split is an ABI
                        this.type = APK_SPLIT_ABI;
                        String abi = StaticDataset.ALL_ABIS.get(mSplitSuffix);
                        int index = ArrayUtils.indexOf(Build.SUPPORTED_ABIS, Objects.requireNonNull(abi));
                        if (index != -1) {
                            this.rank = index;
                            if (mForFeature == null) {
                                // Increment rank for base APK
                                this.rank -= 1000;
                            }
                        }
                    } else if (StaticDataset.DENSITY_NAME_TO_DENSITY.containsKey(mSplitSuffix)) {
                        // This split is for Screen Density
                        this.type = APK_SPLIT_DENSITY;
                        this.rank = Math.abs(StaticDataset.DEVICE_DENSITY - getDensityFromName(mSplitSuffix));
                        if (mForFeature == null) {
                            // Increment rank for base APK
                            this.rank -= 1000;
                        }
                    } else if (LangUtils.isValidLocale(mSplitSuffix)) {
                        // This split is for Locale
                        this.type = APK_SPLIT_LOCALE;
                        Integer rank = StaticDataset.LOCALE_RANKING.get(mSplitSuffix);
                        if (rank != null) {
                            this.rank = rank;
                            if (mForFeature == null) {
                                // Increment rank for base APK
                                this.rank -= 1000;
                            }
                        }
                    } else this.type = APK_SPLIT_UNKNOWN;
                }
            } else {
                this.name = name;
                this.type = APK_SPLIT_UNKNOWN;
                mRequired = mIsolated = false;
            }
        }

        /**
         * Get filename of the entry. This does not necessarily exist as a real file.
         */
        @NonNull
        public String getFileName() {
            if (Paths.exists(mCachedFile)) return mCachedFile.getName();
            if (mZipEntry != null) return FileUtils.getFilenameFromZipEntry(mZipEntry);
            if (Paths.exists(mSource)) return mSource.getName();
            else throw new RuntimeException("Neither zipEntry nor source is defined.");
        }

        /**
         * Get size of the entry.
         */
        public long getFileSize() {
            if (Paths.exists(mCachedFile)) return mCachedFile.length();
            if (mZipEntry != null) return mZipEntry.getSize();
            if (Paths.exists(mSource)) return mSource.length();
            else throw new RuntimeException("Neither zipEntry nor source is defined.");
        }

        /**
         * Get size of the entry or {@code -1} if unavailable
         */
        @WorkerThread
        public long getFileSize(boolean signed) {
            try {
                return (signed ? getSignedFile() : getRealCachedFile()).length();
            } catch (IOException e) {
                return -1;
            }
        }

        @WorkerThread
        public File getFile(boolean signed) throws IOException {
            return signed ? getSignedFile() : getRealCachedFile();
        }

        @WorkerThread
        public InputStream getInputStream(boolean signed) throws IOException {
            return signed ? getSignedInputStream() : getRealInputStream();
        }

        /**
         * Get signed APK file.
         *
         * @throws IOException If the APK cannot be signed or cached.
         */
        private File getSignedFile() throws IOException {
            File realFile = getRealCachedFile();
            if (Paths.exists(mSignedFile)) return mSignedFile;
            mSignedFile = mFileCache.createCachedFile("apk");
            SigSchemes sigSchemes = Prefs.Signing.getSigSchemes();
            boolean zipAlign = Prefs.Signing.zipAlign();
            try {
                Signer signer = Signer.getInstance(sigSchemes);
                if (signer.isV4SchemeEnabled()) {
                    mIdsigFile = mFileCache.createCachedFile("idsig");
                    signer.setIdsigFile(mIdsigFile);
                }
                if (signer.sign(realFile, mSignedFile, -1, zipAlign)
                        && Signer.verify(sigSchemes, mSignedFile, mIdsigFile)) {
                    return mSignedFile;
                }
                throw new IOException("Failed to sign " + realFile);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        /**
         * Same as {@link #getSignedFile()} except that it returns an {@link InputStream}.
         *
         * @throws IOException If the APK cannot be signed or cached.
         */
        private InputStream getSignedInputStream() throws IOException {
            return new FileInputStream(getSignedFile());
        }

        /**
         * Get the APK file source if it has a physical location.
         *
         * @return Absolute path to the APK file.
         */
        @Nullable
        public String getApkSource() {
            return mSource == null ? null : mSource.getAbsolutePath();
        }

        /**
         * Close this entry i.e. delete the cached files. Called automatically if {@link ApkFile#close()} is called.
         */
        @Override
        public void close() {
            FileUtils.deleteSilently(mCachedFile);
            FileUtils.deleteSilently(mIdsigFile);
            FileUtils.deleteSilently(mSignedFile);
            if (mSource != null && !mSource.getAbsolutePath().startsWith("/proc/self")
                    && !mSource.getAbsolutePath().startsWith("/data/app")) {
                FileUtils.deleteSilently(mSource);
            }
        }

        /**
         * Get input stream of the entry. It does not sign the APK based on user preferences. It also does not cache
         * the APK file, but tries to reuse existing cache file.
         *
         * @throws IOException If I/O error occurs.
         */
        @NonNull
        private InputStream getRealInputStream() throws IOException {
            if (Paths.exists(mCachedFile)) return new FileInputStream(mCachedFile);
            if (mZipEntry != null) return Objects.requireNonNull(mZipFile).getInputStream(mZipEntry);
            if (Paths.exists(mSource)) return new FileInputStream(mSource);
            else throw new IOException("Neither zipEntry nor source is defined.");
        }

        /**
         * Get a readable file of the entry, cached if necessary. It does not sign the APK based on user preferences.
         *
         * @throws IOException If an I/O error occurs while caching the APK.
         */
        @WorkerThread
        private File getRealCachedFile() throws IOException {
            if (mSource != null && mSource.canRead() && !mSource.getAbsolutePath().startsWith("/proc/self")) {
                return mSource;
            }
            if (mCachedFile != null) {
                if (mCachedFile.canRead()) {
                    return mCachedFile;
                } else FileUtils.deleteSilently(mCachedFile);
            }
            try (InputStream is = getRealInputStream()) {
                mCachedFile = mFileCache.getCachedFile(is, "apk");
                return Objects.requireNonNull(mCachedFile);
            }
        }

        /**
         * Whether the entry is a required entry i.e. it must be installed along with the base APK.
         */
        public boolean isRequired() {
            return mRequired;
        }

        /**
         * Whether the entry is an isolated entry.
         */
        public boolean isIsolated() {
            return mIsolated;
        }

        /**
         * Get ABI if the split is an ABI split.
         *
         * @return One of {@link VMRuntime#ABI_ARMEABI_V7A}, {@link VMRuntime#ABI_ARM64_V8A}, {@link VMRuntime#ABI_X86},
         * {@link VMRuntime#ABI_X86_64}.
         * @throws RuntimeException     If split is not an ABI split.
         * @throws NullPointerException If the ABI is not valid.
         */
        @NonNull
        public String getAbi() {
            if (type == APK_SPLIT_ABI) {
                return Objects.requireNonNull(StaticDataset.ALL_ABIS.get(mSplitSuffix));
            }
            throw new RuntimeException("Attempt to fetch ABI for invalid apk");
        }

        /**
         * Get density if the split is a density split.
         *
         * @return One of {@link DisplayMetrics#DENSITY_LOW}, {@link DisplayMetrics#DENSITY_MEDIUM},
         * {@link DisplayMetrics#DENSITY_TV}, {@link DisplayMetrics#DENSITY_HIGH}, {@link DisplayMetrics#DENSITY_XHIGH},
         * {@link DisplayMetrics#DENSITY_XXHIGH}, {@link DisplayMetrics#DENSITY_XXXHIGH}.
         * @throws RuntimeException If split is not a density split, or the density is not valid.
         */
        public int getDensity() {
            if (type == APK_SPLIT_DENSITY) {
                return getDensityFromName(mSplitSuffix);
            }
            throw new RuntimeException("Attempt to fetch Density for invalid apk");
        }

        /**
         * Get locale if the split is a locale split. Each locale can belong to multiple regions.
         *
         * @throws RuntimeException     If the split is not a locale split.
         * @throws NullPointerException If the locale is not valid.
         */
        @NonNull
        public Locale getLocale() {
            if (type == APK_SPLIT_LOCALE) {
                return new Locale.Builder().setLanguageTag(Objects.requireNonNull(mSplitSuffix)).build();
            }
            throw new RuntimeException("Attempt to fetch Locale for invalid apk");
        }

        @Nullable
        public String getFeature() {
            if (type == APK_SPLIT_FEATURE) {
                return name;
            }
            return mForFeature;
        }

        public boolean isForFeature() {
            return mForFeature != null;
        }

        /**
         * Whether the split supported by this platform
         */
        public boolean supported() {
            if (type == APK_SPLIT_ABI) {
                // Not all ABIs are supported by all platforms.
                // This can be deduced by checking the rank of the ABI.
                return rank != Integer.MAX_VALUE;
            }
            return true;
        }

        @Override
        @NonNull
        public CharSequence toLocalizedString(@NonNull Context context) {
            CharSequence localizedString = toShortLocalizedString(context);
            SpannableStringBuilder builder = new SpannableStringBuilder()
                    .append(context.getString(R.string.size)).append(LangUtils.getSeparatorString())
                    .append(Formatter.formatFileSize(context, getFileSize()));
            if (isRequired()) {
                builder.append(", ").append(context.getString(R.string.required));
            }
            if (isIsolated()) {
                builder.append(", ").append(context.getString(R.string.isolated));
            }
            if (!supported()) {
                builder.append(", ");
                int start = builder.length();
                builder.append(context.getString(R.string.unsupported_split_apk));
                builder.setSpan(new ForegroundColorSpan(MaterialColors.getColor(context, androidx.appcompat.R.attr.colorError, "null")),
                        start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return new SpannableStringBuilder(localizedString).append("\n").append(getSmallerText(builder));
        }

        public CharSequence toShortLocalizedString(Context context) {
            switch (type) {
                case ApkFile.APK_BASE:
                    return context.getString(R.string.base_apk);
                case ApkFile.APK_SPLIT_DENSITY:
                    if (mForFeature != null) {
                        return context.getString(R.string.density_split_for_feature, mSplitSuffix, getDensity(), mForFeature);
                    } else {
                        return context.getString(R.string.density_split_for_base_apk, mSplitSuffix, getDensity());
                    }
                case ApkFile.APK_SPLIT_ABI:
                    if (mForFeature != null) {
                        return context.getString(R.string.abi_split_for_feature, getAbi(), mForFeature);
                    } else {
                        return context.getString(R.string.abi_split_for_base_apk, getAbi());
                    }
                case ApkFile.APK_SPLIT_LOCALE:
                    if (mForFeature != null) {
                        return context.getString(R.string.locale_split_for_feature, getLocale().getDisplayLanguage(), mForFeature);
                    } else {
                        return context.getString(R.string.locale_split_for_base_apk, getLocale().getDisplayLanguage());
                    }
                case ApkFile.APK_SPLIT_FEATURE:
                    return context.getString(R.string.split_feature_name, name);
                case ApkFile.APK_SPLIT_UNKNOWN:
                case ApkFile.APK_SPLIT:
                    if (mForFeature != null) {
                        return context.getString(R.string.unknown_split_for_feature, name, mForFeature);
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
