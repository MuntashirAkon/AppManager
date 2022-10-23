// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.util.SparseIntArray;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

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
import java.util.ListIterator;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.apk.parser.AndroidBinXmlParser;
import io.github.muntashirakon.AppManager.apk.signing.SigSchemes;
import io.github.muntashirakon.AppManager.apk.signing.Signer;
import io.github.muntashirakon.AppManager.apk.splitapk.ApksMetadata;
import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.VMRuntime;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.unapkm.api.UnApkm;
import io.github.muntashirakon.util.LocalizedString;

import static io.github.muntashirakon.AppManager.apk.ApkUtils.getDensityFromName;
import static io.github.muntashirakon.AppManager.apk.ApkUtils.getManifestAttributes;
import static io.github.muntashirakon.AppManager.apk.ApkUtils.getManifestFromApk;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

public final class ApkFile implements AutoCloseable {
    public static final String TAG = "ApkFile";

    private static final String IDSIG_FILE = "Signature.idsig";
    private static final String ANDROID_XML_NAMESPACE = "http" + "://schemas.android.com/apk/res/android";
    private static final String ATTR_IS_FEATURE_SPLIT = ANDROID_XML_NAMESPACE + ":isFeatureSplit";
    private static final String ATTR_IS_SPLIT_REQUIRED = ANDROID_XML_NAMESPACE + ":isSplitRequired";
    private static final String ATTR_ISOLATED_SPLIT = ANDROID_XML_NAMESPACE + ":isolatedSplits";
    private static final String ATTR_CONFIG_FOR_SPLIT = "configForSplit";
    private static final String ATTR_SPLIT = "split";
    private static final String ATTR_PACKAGE = "package";
    private static final String CONFIG_PREFIX = "config.";

    private static final String UN_APKM_PKG = "io.github.muntashirakon.unapkm";

    // There's hardly any chance of using multiple instances of ApkFile but still kept for convenience
    private static final SparseArray<ApkFile> apkFiles = new SparseArray<>(3);
    private static final SparseIntArray instanceCount = new SparseIntArray(3);
    private static final SparseIntArray advancedInstanceCount = new SparseIntArray(3);

    @AnyThread
    @NonNull
    public static ApkFile getInstance(int sparseArrayKey) {
        ApkFile apkFile = apkFiles.get(sparseArrayKey);
        if (apkFile == null) {
            throw new IllegalArgumentException("ApkFile not found for key " + sparseArrayKey);
        }
        synchronized (instanceCount) {
            int advancedCount = advancedInstanceCount.get(sparseArrayKey);
            if (advancedCount > 0) {
                // One or more instances are requested in advance, decrement the advanced instance counter
                advancedInstanceCount.put(sparseArrayKey, advancedCount - 1);
            } else {
                // No advanced instance, increment the number of active instances
                instanceCount.put(sparseArrayKey, instanceCount.get(sparseArrayKey) + 1);
            }
        }
        return apkFile;
    }

    /**
     * Request a new instance in advance, thereby, preventing any attempt at closing the APK file via {@link #close()}.
     */
    @AnyThread
    public static void getInAdvance(int sparseArrayKey) {
        synchronized (instanceCount) {
            // Add this to the instance count to avoid closing when close() is called
            instanceCount.put(sparseArrayKey, instanceCount.get(sparseArrayKey) + 1);
            // Add this to the advance instance counter
            advancedInstanceCount.put(sparseArrayKey, advancedInstanceCount.get(sparseArrayKey) + 1);
        }
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
        SUPPORTED_MIMES.add("application/vnd.android.package-archive");
        SUPPORTED_MIMES.add("application/vnd.apkm");
        SUPPORTED_MIMES.add("application/xapk-package-archive");
    }

    private final int sparseArrayKey;
    @NonNull
    private final List<Entry> entries = new ArrayList<>();
    private Entry baseEntry;
    @Nullable
    private Path idsigFile;
    @Nullable
    private ApksMetadata apksMetadata;
    @NonNull
    private final String packageName;
    @NonNull
    private final List<ZipEntry> obbFiles = new ArrayList<>();
    @NonNull
    private final File cacheFilePath;
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
            String name = FileUtils.getFileName(cr, apkUri);
            if (name == null) {
                throw new ApkFileException("Could not extract package name from the URI.");
            }
            extension = FileUtils.getExtension(name).toLowerCase(Locale.ROOT);
            if (!SUPPORTED_EXTENSIONS.contains(extension)) {
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
                if (FileUtils.isInputFileZip(cr, apkUri)) {
                    // DRM-free APKM file, mark it as APKS
                    // FIXME(#227): Give it a special name and verify integrity
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
                this.cacheFilePath = FileUtils.getTempFile(".apks");
                try (ParcelFileDescriptor inputFD = cr.openFileDescriptor(apkUri, "r");
                     OutputStream outputStream = new FileOutputStream(this.cacheFilePath)) {
                    if (inputFD == null) {
                        throw new IOException("Apk URI inaccessible or empty.");
                    }
                    UnApkm unApkm = new UnApkm(context, UN_APKM_PKG);
                    unApkm.decryptFile(inputFD, outputStream);
                }
            } catch (IOException | RemoteException e) {
                throw new ApkFileException(e);
            }
        } else {
            // Open file descriptor
            if (!FmProvider.AUTHORITY.equals(apkUri.getAuthority())) {
                try {
                    ParcelFileDescriptor fd = cr.openFileDescriptor(apkUri, "r");
                    if (fd == null) {
                        throw new FileNotFoundException("Could not get file descriptor from the Uri");
                    }
                    this.fd = fd;
                } catch (FileNotFoundException e) {
                    throw new ApkFileException(e);
                } catch (SecurityException e) {
                    Log.e(TAG, e);
                }
            }
            File cacheFilePath = this.fd != null ? FileUtils.getFileFromFd(fd) : null;
            if (cacheFilePath == null || !cacheFilePath.canRead()) {
                // Cache manually
                try (InputStream is = cr.openInputStream(apkUri)) {
                    this.cacheFilePath = FileUtils.getCachedFile(is);
                } catch (IOException e) {
                    throw new ApkFileException("Could not cache the input file.", e);
                }
            } else this.cacheFilePath = cacheFilePath;
        }
        String packageName = null;
        // Check for splits
        if (extension.equals("apk")) {
            // Get manifest attributes
            ByteBuffer manifest;
            HashMap<String, String> manifestAttrs;
            try {
                manifest = getManifestFromApk(cacheFilePath);
                manifestAttrs = getManifestAttributes(manifest);
            } catch (IOException | AndroidBinXmlParser.XmlParserException e) {
                throw new ApkFileException("Manifest not found for base APK.", e);
            }
            if (!manifestAttrs.containsKey(ATTR_PACKAGE)) {
                throw new IllegalArgumentException("Manifest doesn't contain any package name.");
            }
            packageName = manifestAttrs.get(ATTR_PACKAGE);
            baseEntry = new Entry(cacheFilePath, manifest);
            entries.add(baseEntry);
        } else {
            getCachePath();
            try {
                zipFile = new ZipFile(cacheFilePath);
            } catch (IOException e) {
                throw new ApkFileException(e);
            }
            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry zipEntry = zipEntries.nextElement();
                if (zipEntry.isDirectory()) continue;
                String fileName = FileUtils.getFileNameFromZipEntry(zipEntry);
                if (fileName.endsWith(".apk")) { // APK is more likely to match
                    try (InputStream zipInputStream = zipFile.getInputStream(zipEntry)) {
                        // Get manifest attributes
                        ByteBuffer manifest;
                        HashMap<String, String> manifestAttrs;
                        try {
                            manifest = getManifestFromApk(zipInputStream);
                            manifestAttrs = getManifestAttributes(manifest);
                        } catch (IOException | AndroidBinXmlParser.XmlParserException e) {
                            throw new ApkFileException("Manifest not found.", e);
                        }
                        if (manifestAttrs.containsKey("split")) {
                            // TODO: check for duplicates
                            Entry entry = new Entry(fileName, zipEntry, APK_SPLIT, manifest, manifestAttrs);
                            entries.add(entry);
                        } else {
                            if (baseEntry != null) {
                                throw new RuntimeException("Duplicate base apk found.");
                            }
                            baseEntry = new Entry(fileName, zipEntry, APK_BASE, manifest, manifestAttrs);
                            entries.add(baseEntry);
                            if (manifestAttrs.containsKey(ATTR_PACKAGE)) {
                                packageName = manifestAttrs.get(ATTR_PACKAGE);
                            } else throw new RuntimeException("Package name not found.");
                        }
                    } catch (IOException e) {
                        throw new ApkFileException(e);
                    }
                } else if (fileName.equals(ApksMetadata.META_FILE)) {
                    try {
                        String jsonString = FileUtils.getInputStreamContent(zipFile.getInputStream(zipEntry));
                        apksMetadata = new ApksMetadata();
                        apksMetadata.readMetadata(jsonString);
                    } catch (IOException | JSONException e) {
                        apksMetadata = null;
                        throw new ApkFileException(e);
                    }
                } else if (fileName.endsWith(".obb")) {
                    obbFiles.add(zipEntry);
                } else if (fileName.endsWith(".idsig")) {
                    try {
                        idsigFile = FileUtils.saveZipFile(zipFile.getInputStream(zipEntry), getCachePath(), IDSIG_FILE);
                    } catch (IOException e) {
                        throw new ApkFileException(e);
                    }
                }
            }
            if (baseEntry == null) throw new ApkFileException("No base apk found.");
            // Sort the entries based on type and rank
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
            // Old file structure (storing APK files at /data/app)
            try {
                entries.add(baseEntry = new Entry(cacheFilePath, getManifestFromApk(cacheFilePath)));
            } catch (IOException e) {
                throw new ApkFileException("Manifest not found.", e);
            }
        } else {
            File[] apks = sourceDir.listFiles((dir, name) -> name.endsWith(".apk"));
            if (apks == null) {
                // Directory might be inaccessible
                Log.w(TAG, "No apk files found in " + sourceDir.getAbsolutePath() + ". Using default.");
                List<File> allApks = new ArrayList<>();
                allApks.add(cacheFilePath);
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
                fileName = FileUtils.getLastPathComponent(apk.getAbsolutePath());
                // Get manifest attributes
                ByteBuffer manifest;
                HashMap<String, String> manifestAttrs;
                try {
                    manifest = getManifestFromApk(apk);
                    manifestAttrs = getManifestAttributes(manifest);
                } catch (IOException | AndroidBinXmlParser.XmlParserException e) {
                    throw new ApkFileException("Manifest not found.", e);
                }
                if (manifestAttrs.containsKey("split")) {
                    Entry entry = new Entry(fileName, apk, APK_SPLIT, manifest, manifestAttrs);
                    entries.add(entry);
                } else {
                    // Could be a base entry, check package name
                    if (!manifestAttrs.containsKey(ATTR_PACKAGE)) {
                        throw new IllegalArgumentException("Manifest doesn't contain any package name.");
                    }
                    String newPackageName = manifestAttrs.get(ATTR_PACKAGE);
                    if (packageName.equals(newPackageName)) {
                        if (baseEntry != null) {
                            throw new RuntimeException("Duplicate base apk found.");
                        }
                        baseEntry = new Entry(fileName, apk, APK_BASE, manifest, manifestAttrs);
                        entries.add(baseEntry);
                    } // else continue;
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
        if (idsigFile != null) {
            return idsigFile.getFile();
        }
        return null;
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

    @Nullable
    public ApksMetadata getApksMetadata() {
        return apksMetadata;
    }

    @NonNull
    public String getPackageName() {
        return packageName;
    }

    public boolean isSplit() {
        return entries.size() > 1;
    }

    public boolean hasObb() {
        return obbFiles.size() > 0;
    }

    @WorkerThread
    public void extractObb(Path writableObbDir) throws IOException {
        if (!hasObb() || zipFile == null) return;
        for (ZipEntry obbEntry : obbFiles) {
            String fileName = FileUtils.getFileNameFromZipEntry(obbEntry);
            // Extract obb file to the destination directory
            try (InputStream zipInputStream = zipFile.getInputStream(obbEntry)) {
                FileUtils.saveZipFile(zipInputStream, writableObbDir, fileName);
            }
        }
    }

    public void select(int entry) {
        entries.get(entry).selected = true;
    }

    public void deselect(int entry) {
        entries.get(entry).selected = false;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean needSigning() {
        return AppPref.canSignApk();
    }

    @Override
    public void close() {
        synchronized (instanceCount) {
            if (instanceCount.get(sparseArrayKey) > 1) {
                // This isn't the only instance, do not close yet
                instanceCount.put(sparseArrayKey, instanceCount.get(sparseArrayKey) - 1);
                return;
            }
            // Only this instance remained
            instanceCount.delete(sparseArrayKey);
        }
        apkFiles.delete(sparseArrayKey);
        for (Entry entry : entries) {
            entry.close();
        }
        FileUtils.closeQuietly(zipFile);
        FileUtils.closeQuietly(fd);
        FileUtils.deleteSilently(idsigFile);
        if (!cacheFilePath.getAbsolutePath().startsWith("/data/app")) {
            FileUtils.deleteSilently(cacheFilePath);
        }
        // Ensure that entries are not accessible if accidentally accessed
        entries.clear();
        baseEntry = null;
        obbFiles.clear();
    }

    @NonNull
    private Path getCachePath() {
        File destDir = AppManager.getContext().getExternalCacheDir();
        if (destDir == null || !Environment.getExternalStorageState(destDir).equals(Environment.MEDIA_MOUNTED))
            throw new RuntimeException("External media not present");
        if (!destDir.exists()) //noinspection ResultOfMethodCallIgnored
            destDir.mkdirs();
        return Paths.get(destDir);
    }

    public class Entry implements AutoCloseable, LocalizedString {
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
        private String splitSuffix;
        @Nullable
        private String forFeature = null;
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

        Entry(@NonNull File source, @NonNull ByteBuffer manifest) {
            this.name = "Base.apk";
            this.source = Objects.requireNonNull(source);
            this.type = APK_BASE;
            this.selected = this.required = true;
            this.isolated = false;
            this.manifest = Objects.requireNonNull(manifest);
        }

        Entry(@NonNull String name,
              @NonNull ZipEntry zipEntry,
              @ApkType int type,
              @NonNull ByteBuffer manifest,
              @NonNull HashMap<String, String> manifestAttrs) {
            this(name, type, manifest, manifestAttrs);
            this.zipEntry = Objects.requireNonNull(zipEntry);
        }

        Entry(@NonNull String name,
              @NonNull File source,
              @ApkType int type,
              @NonNull ByteBuffer manifest,
              @NonNull HashMap<String, String> manifestAttrs) {
            this(name, type, manifest, manifestAttrs);
            this.source = Objects.requireNonNull(source);
        }

        private Entry(@NonNull String name,
                      @ApkType int type,
                      @NonNull ByteBuffer manifest,
                      @NonNull HashMap<String, String> manifestAttrs) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(manifest);
            Objects.requireNonNull(manifestAttrs);
            this.manifest = manifest;
            if (type == APK_BASE) {
                this.name = name;
                this.selected = this.required = true;
                this.isolated = false;
                this.type = APK_BASE;
            } else if (type == APK_SPLIT) {
                String splitName = manifestAttrs.get(ATTR_SPLIT);
                if (splitName == null) throw new RuntimeException("Split name is empty.");
                this.name = splitName;
                // Check if required
                if (manifestAttrs.containsKey(ATTR_IS_SPLIT_REQUIRED)) {
                    String value = manifestAttrs.get(ATTR_IS_SPLIT_REQUIRED);
                    this.selected = this.required = value != null && Boolean.parseBoolean(value);
                } else this.required = false;
                // Check if isolated
                if (manifestAttrs.containsKey(ATTR_ISOLATED_SPLIT)) {
                    String value = manifestAttrs.get(ATTR_ISOLATED_SPLIT);
                    this.isolated = value != null && Boolean.parseBoolean(value);
                } else this.isolated = false;
                // Infer types
                if (manifestAttrs.containsKey(ATTR_IS_FEATURE_SPLIT)) {
                    this.type = APK_SPLIT_FEATURE;
                } else {
                    if (manifestAttrs.containsKey(ATTR_CONFIG_FOR_SPLIT)) {
                        this.forFeature = manifestAttrs.get(ATTR_CONFIG_FOR_SPLIT);
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

        /**
         * Get filename of the entry. This does not necessarily exist as a real file.
         */
        @NonNull
        public String getFileName() {
            if (cachedFile != null && cachedFile.exists()) return cachedFile.getName();
            if (zipEntry != null) return FileUtils.getFileNameFromZipEntry(zipEntry);
            if (source != null && source.exists()) return source.getName();
            else throw new RuntimeException("Neither zipEntry nor source is defined.");
        }

        /**
         * Get size of the entry.
         */
        public long getFileSize() {
            if (cachedFile != null && cachedFile.exists()) return cachedFile.length();
            if (zipEntry != null) return zipEntry.getSize();
            if (source != null && source.exists()) return source.length();
            else throw new RuntimeException("Neither zipEntry nor source is defined.");
        }

        /**
         * Get signed APK file if required based on user preferences.
         *
         * @throws IOException If the APK cannot be signed or cached.
         */
        public File getSignedFile() throws IOException {
            if (signedFile != null) return signedFile;
            File realFile = getRealCachedFile();
            if (!needSigning()) {
                // Return original/real file if signing is not requested
                return realFile;
            }
            signedFile = FileUtils.getTempFile();
            SigSchemes sigSchemes = SigSchemes.fromPref();
            try {
                Signer signer = Signer.getInstance(sigSchemes);
                if (signer.isV4SchemeEnabled()) {
                    idsigFile = FileUtils.getTempFile();
                    signer.setIdsigFile(idsigFile);
                }
                if (signer.sign(realFile, signedFile)) {
                    if (Signer.verify(sigSchemes, signedFile, idsigFile)) {
                        return signedFile;
                    }
                }
                throw new IOException("Failed to sign " + realFile);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        /**
         * Same as {@link #getSignedFile()} except that it returns an {@link InputStream}.
         *
         * @throws IOException If the APK cannot be signed or cached.
         */
        public InputStream getSignedInputStream() throws IOException {
            if (!needSigning()) {
                // Return original/real input stream if signing is not requested
                return getRealInputStream();
            }
            return new FileInputStream(getSignedFile());
        }

        /**
         * Get the APK file source if it has a physical location.
         *
         * @return Absolute path to the APK file.
         */
        @Nullable
        public String getApkSource() {
            return source == null ? null : source.getAbsolutePath();
        }

        /**
         * Close this entry i.e. delete the cached files. Called automatically if {@link ApkFile#close()} is called.
         */
        @Override
        public void close() {
            FileUtils.deleteSilently(cachedFile);
            FileUtils.deleteSilently(idsigFile);
            FileUtils.deleteSilently(signedFile);
            if (source != null && !source.getAbsolutePath().startsWith("/proc/self")
                    && !source.getAbsolutePath().startsWith("/data/app")) {
                FileUtils.deleteSilently(source);
            }
        }

        /**
         * Get input stream of the entry. It does not sign the APK based on user preferences. It also does not cache
         * the APK file, but tries to reuse existing cache file.
         *
         * @throws IOException If I/O error occurs.
         */
        @NonNull
        public InputStream getRealInputStream() throws IOException {
            if (cachedFile != null && cachedFile.exists()) return new FileInputStream(cachedFile);
            if (zipEntry != null) return Objects.requireNonNull(zipFile).getInputStream(zipEntry);
            if (source != null && source.exists()) return new FileInputStream(source);
            else throw new IOException("Neither zipEntry nor source is defined.");
        }

        /**
         * Get a readable file of the entry, cached if necessary. It does not sign the APK based on user preferences.
         *
         * @throws IOException If an I/O error occurs while caching the APK.
         */
        @WorkerThread
        public File getRealCachedFile() throws IOException {
            if (source != null && source.canRead() && !source.getAbsolutePath().startsWith("/proc/self")) {
                return source;
            }
            if (cachedFile != null) {
                if (cachedFile.canRead()) {
                    return cachedFile;
                } else FileUtils.deleteSilently(cachedFile);
            }
            try (InputStream is = getRealInputStream()) {
                cachedFile = FileUtils.saveZipFile(is, getCachePath(), name).getFile();
                return Objects.requireNonNull(cachedFile);
            }
        }

        /**
         * Whether the entry has been selected. Selected entries can be retrieved using {@link #getSelectedEntries()}.
         */
        public boolean isSelected() {
            return selected;
        }

        /**
         * Whether the entry is a required entry i.e. it must be installed along with the base APK.
         */
        public boolean isRequired() {
            return required;
        }

        /**
         * Whether the entry is an isolated entry.
         */
        public boolean isIsolated() {
            return isolated;
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
                return Objects.requireNonNull(StaticDataset.ALL_ABIS.get(splitSuffix));
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
                return getDensityFromName(splitSuffix);
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
                return new Locale.Builder().setLanguageTag(Objects.requireNonNull(splitSuffix)).build();
            }
            throw new RuntimeException("Attempt to fetch Locale for invalid apk");
        }

        @Nullable
        public String getFeature() {
            if (type == APK_SPLIT_FEATURE) {
                return name;
            }
            return forFeature;
        }

        public boolean isForFeature() {
            return forFeature != null;
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
                builder.setSpan(new ForegroundColorSpan(MaterialColors.getColor(context, R.attr.colorError, "null")),
                        start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return new SpannableStringBuilder(localizedString).append("\n").append(getSmallerText(builder));
        }

        public CharSequence toShortLocalizedString(Context context) {
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
