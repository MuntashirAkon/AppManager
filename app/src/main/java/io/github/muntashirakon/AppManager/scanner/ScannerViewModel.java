// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Pair;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.apksig.ApkVerifier;
import com.android.apksig.apk.ApkFormatException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.db.entity.VtFile;
import io.github.muntashirakon.AppManager.fm.ContentType2;
import io.github.muntashirakon.AppManager.scanner.vt.VtFileReport;
import io.github.muntashirakon.AppManager.scanner.vt.VtScanCoordinator;
import io.github.muntashirakon.AppManager.scanner.vt.VtScanRepository;
import io.github.muntashirakon.AppManager.db.entity.VtScanAttempt;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.algo.AhoCorasick;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.fs.DexFileSystem;
import io.github.muntashirakon.io.fs.VirtualFileSystem;

public class ScannerViewModel extends AndroidViewModel {
    private static final Pattern SIG_TO_IGNORE = Pattern.compile("^(android(|x)|com\\.android|com\\.google\\.android|java(|x)|j\\$\\.(util|time)|\\w\\d?(\\.\\w\\d?)+)\\..*$");

    private File mApkFile;
    private boolean mIsSummaryLoaded = false;
    private Uri mApkUri;
    private int mDexVfsId;
    private final VtScanCoordinator mVtCoordinator;
    @Nullable
    private String mPackageName;

    private List<String> mAllClasses;
    private List<String> mTrackerClasses;
    private List<SignatureInfo> mTrackerInfoList;
    private List<SignatureInfo> mLibraryInfoList;
    private ArrayList<String> mMissingClasses;
    private Collection<String> mNativeLibraries;

    private CountDownLatch mWaitForFile;
    @Nullable
    private VtScanAttempt mPendingUploadAttempt;
    private final FileCache mFileCache = new FileCache();
    private final MultithreadedExecutor mExecutor = MultithreadedExecutor.getNewInstance();
    private final MutableLiveData<Pair<String, String>[]> mApkChecksumsLiveData = new MutableLiveData<>();
    private final MutableLiveData<ApkVerifier.Result> mApkVerifierResultLiveData = new MutableLiveData<>();
    private final MutableLiveData<PackageInfo> mPackageInfoLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<String>> mAllClassesLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<SignatureInfo>> mTrackerClassesLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<SignatureInfo>> mLibraryClassesLiveData = new MutableLiveData<>();
    private final MutableLiveData<ArrayList<String>> mMissingClassesLiveData = new MutableLiveData<>();
    // Null = Uploading, NonNull = Queued
    private final MutableLiveData<String> mVtFileUploadLiveData = new MutableLiveData<>();
    // Null = Failed, NonNull = Result generated
    private final MutableLiveData<VtFileReport> mVtFileReportLiveData = new MutableLiveData<>();

    public ScannerViewModel(@NonNull Application application) {
        super(application);
        mVtCoordinator = new VtScanCoordinator(application);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mExecutor.shutdownNow();
        mVtCoordinator.close();
        IoUtils.closeQuietly(mFileCache);
        try {
            VirtualFileSystem.unmount(mDexVfsId);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @AnyThread
    public void loadSummary() {
        if (mIsSummaryLoaded) return;
        mIsSummaryLoaded = true;
        mWaitForFile = new CountDownLatch(1);
        // Cache files
        mExecutor.submit(() -> {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            try {
                cacheFileIfRequired();
            } finally {
                mWaitForFile.countDown();
            }
        });
        // Generate APK checksums
        mExecutor.submit(this::generateApkChecksumsAndFetchScanReports);
        // Verify APK
        mExecutor.submit(this::loadApkVerifierResult);
        // Load package info
        mExecutor.submit(this::loadPackageInfo);
        // Load all classes
        mExecutor.submit(() -> {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            loadAllClasses();
        });
    }

    @NonNull
    public LiveData<Pair<String, String>[]> apkChecksumsLiveData() {
        return mApkChecksumsLiveData;
    }

    @NonNull
    public LiveData<ApkVerifier.Result> apkVerifierResultLiveData() {
        return mApkVerifierResultLiveData;
    }

    @NonNull
    public LiveData<PackageInfo> packageInfoLiveData() {
        return mPackageInfoLiveData;
    }

    @NonNull
    public LiveData<List<String>> allClassesLiveData() {
        return mAllClassesLiveData;
    }

    public LiveData<List<SignatureInfo>> trackerClassesLiveData() {
        return mTrackerClassesLiveData;
    }

    public LiveData<List<SignatureInfo>> libraryClassesLiveData() {
        return mLibraryClassesLiveData;
    }

    public LiveData<ArrayList<String>> missingClassesLiveData() {
        return mMissingClassesLiveData;
    }

    public LiveData<VtFileReport> vtFileReportLiveData() {
        return mVtFileReportLiveData;
    }

    public LiveData<String> vtFileUploadLiveData() {
        return mVtFileUploadLiveData;
    }

    public List<String> getTrackerClasses() {
        return mTrackerClasses;
    }

    public void setTrackerClasses(List<String> trackerClasses) {
        mTrackerClasses = trackerClasses;
    }

    @Nullable
    public File getApkFile() {
        return mApkFile;
    }

    @Nullable
    public String getPackageName() {
        return mPackageName;
    }

    public void setApkFile(@Nullable File apkFile) {
        mApkFile = apkFile;
    }

    public Uri getApkUri() {
        return mApkUri;
    }

    public void setApkUri(@NonNull Uri apkUri) {
        mApkUri = apkUri;
    }

    public List<String> getAllClasses() {
        return mAllClasses;
    }

    public Collection<String> getNativeLibraries() {
        return mNativeLibraries;
    }

    public Uri getUriFromClassName(String className) throws FileNotFoundException {
        Path fsRoot = VirtualFileSystem.getFsRoot(mDexVfsId);
        if (fsRoot == null) {
            throw new FileNotFoundException("FS Root not found.");
        }
        return fsRoot.findFile(className.replace('.', '/') + ".smali").getUri();
    }

    @WorkerThread
    private void cacheFileIfRequired() {
        // Test if this path is readable
        if (mApkFile == null || !FileUtils.canReadUnprivileged(mApkFile)) {
            // Not readable, cache the file
            try {
                mApkFile = mFileCache.getCachedFile(Paths.get(mApkUri));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @WorkerThread
    private void generateApkChecksumsAndFetchScanReports() {
        waitForFile();
        Path file = Paths.getUnprivileged(mApkFile);
        Pair<String, String>[] digests = ExUtils.exceptionAsNull(() -> DigestUtils.getDigests(file));
        mApkChecksumsLiveData.postValue(digests);
        if (digests != null && FeatureController.isVirusTotalEnabled() && mApkFile != null) {
            mVtCoordinator.scan(file, file.getUri().toString(), mApkFile.getName(),
                    "application/vnd.android.package-archive",
                    VtScanRepository.SOURCE_INSTALLED_APK, new VtScanCoordinator.VtScanCallback() {
                        @Override
                        public void onConsentRequired(@NonNull VtScanAttempt attempt, @NonNull VtFile file) {
                            mPendingUploadAttempt = attempt;
                            mVtFileUploadLiveData.postValue(null);
                        }

                        @Override
                        public void onQueued(@NonNull VtScanAttempt attempt) {
                            mVtFileUploadLiveData.postValue("");
                        }

                        @Override
                        public void onCompleted(@NonNull VtFileReport report) {
                            mVtFileReportLiveData.postValue(report);
                        }

                        @Override
                        public void onFailed(@Nullable String message) {
                            mVtFileReportLiveData.postValue(null);
                        }
                    });
        } else mVtFileReportLiveData.postValue(null);
    }

    private void loadApkVerifierResult() {
        waitForFile();
        try {
            ApkVerifier.Builder builder = new ApkVerifier.Builder(mApkFile)
                    .setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT);
            ApkVerifier apkVerifier = builder.build();
            ApkVerifier.Result apkVerifierResult = apkVerifier.verify();
            mApkVerifierResultLiveData.postValue(apkVerifierResult);
        } catch (IOException | ApkFormatException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    @WorkerThread
    private void loadPackageInfo() {
        waitForFile();
        PackageManager pm = getApplication().getPackageManager();
        PackageInfo packageInfo = pm.getPackageArchiveInfo(mApkFile.getAbsolutePath(), 0);
        if (packageInfo != null) {
            mPackageName = packageInfo.packageName;
        }
        mPackageInfoLiveData.postValue(packageInfo);
    }

    @WorkerThread
    private void loadAllClasses() {
        waitForFile();
        try {
            NativeLibraries nativeLibraries = new NativeLibraries(mApkFile);
            mNativeLibraries = nativeLibraries.getUniqueLibs();
        } catch (Throwable e) {
            mNativeLibraries = Collections.emptyList();
        }
        try {
            mDexVfsId = VirtualFileSystem.mount(Uri.fromFile(mApkFile), Paths.getUnprivileged(mApkFile), ContentType2.DEX.getMimeType());
            DexFileSystem dfs = (DexFileSystem) Objects.requireNonNull(VirtualFileSystem.getFileSystem(mDexVfsId));
            mAllClasses = dfs.getDexClasses().getBaseClassNames();
            Collections.sort(mAllClasses);
        } catch (Throwable e) {
            e.printStackTrace();
            mAllClasses = Collections.emptyList();
        }
        mAllClassesLiveData.postValue(mAllClasses);
        // Load tracker and library info
        loadTrackers();
        loadLibraries();
        persistLocalAnalysis();
    }

    @WorkerThread
    private void persistLocalAnalysis() {
        if (mApkFile == null || mTrackerInfoList == null || mLibraryInfoList == null) return;
        try {
            JSONObject local = new JSONObject();
            local.put("package", mPackageName);
            JSONArray trackers = new JSONArray();
            for (SignatureInfo info : mTrackerInfoList) {
                trackers.put(new JSONObject().put("signature", info.signature)
                        .put("label", info.label).put("count", info.getCount()));
            }
            JSONArray libraries = new JSONArray();
            for (SignatureInfo info : mLibraryInfoList) {
                libraries.put(new JSONObject().put("signature", info.signature)
                        .put("label", info.label).put("type", info.type)
                        .put("count", info.getCount()));
            }
            local.put("trackers", trackers).put("libraries", libraries);
            if (mMissingClasses != null) {
                local.put("missing_signatures", new JSONArray(mMissingClasses));
            }
            Pair<String, String>[] digests = DigestUtils.getDigests(Paths.getUnprivileged(mApkFile));
            try (VtScanRepository repository = new VtScanRepository()) {
                repository.setLocalAnalysisJson(digests[2].second, local.toString());
            }
        } catch (Throwable ignored) {
        }
    }

    @WorkerThread
    private void loadTrackers() {
        if (mAllClasses == null) return;
        List<SignatureInfo> trackerInfoList = new ArrayList<>();
        String[] trackerNames = StaticDataset.getTrackerNames();
        String[] trackerSignatures = StaticDataset.getTrackerCodeSignatures();
        AtomicIntegerArray signatureCount = new AtomicIntegerArray(trackerSignatures.length);
        mTrackerClasses = new ArrayList<>();
        AhoCorasick aho = StaticDataset.getSearchableTrackerSignatures();
        {
            // Iterate over all classes
            ConcurrentLinkedQueue<String> matchedClasses = new ConcurrentLinkedQueue<>();
            mAllClasses.parallelStream()
                    .filter(className -> className.length() > 8 && className.contains("."))
                    .forEach(className -> {
                        int[] matches = aho.search(className);
                        if (matches.length > 0) {
                            matchedClasses.add(className);
                            for (int idx : matches) {
                                signatureCount.incrementAndGet(idx);
                            }
                        }
                    });
            mTrackerClasses.addAll(matchedClasses);
        }
        // Iterate over signatures again but this time list only the found ones.
        for (int i = 0; i < trackerSignatures.length; i++) {
            if (signatureCount.get(i) == 0) continue;
            SignatureInfo signatureInfo = new SignatureInfo(trackerSignatures[i], trackerNames[i]);
            signatureInfo.setCount(signatureCount.get(i));
            trackerInfoList.add(signatureInfo);
        }
        mTrackerClassesLiveData.postValue(trackerInfoList);
        mTrackerInfoList = trackerInfoList;
    }

    public void loadLibraries() {
        if (mAllClasses == null) return;
        List<SignatureInfo> libraryInfoList = new ArrayList<>();
        ArrayList<String> missingLibs = new ArrayList<>();
        String[] libNames = getApplication().getResources().getStringArray(R.array.lib_names);
        String[] libSignatures = getApplication().getResources().getStringArray(R.array.lib_signatures);
        String[] libTypes = getApplication().getResources().getStringArray(R.array.lib_types);
        // The following array is directly mapped to the arrays above
        AtomicIntegerArray signatureCount = new AtomicIntegerArray(libSignatures.length);
        try (AhoCorasick aho = new AhoCorasick(libSignatures)) {
            // Iterate over all classes
            ConcurrentLinkedQueue<String> missingClasses = new ConcurrentLinkedQueue<>();
            mAllClasses.parallelStream()
                    .filter(className -> className.length() > 8 && className.contains("."))
                    .forEach(className -> {
                        int[] matches = aho.search(className);
                        if (matches.length > 0) {
                            for (int idx : matches) {
                                signatureCount.incrementAndGet(idx);
                            }
                        } else if ((mPackageName != null
                                && !className.startsWith(mPackageName))
                                && !SIG_TO_IGNORE.matcher(className).matches()) {
                            missingClasses.add(className);
                        }
                    });
            missingLibs.addAll(missingClasses);
        }
        // Iterate over signatures again but this time list only the found ones.
        for (int i = 0; i < libSignatures.length; i++) {
            if (signatureCount.get(i) == 0) continue;
            SignatureInfo signatureInfo = new SignatureInfo(libSignatures[i], libNames[i], libTypes[i]);
            signatureInfo.setCount(signatureCount.get(i));
            libraryInfoList.add(signatureInfo);
        }
        mLibraryClassesLiveData.postValue(libraryInfoList);
        mLibraryInfoList = libraryInfoList;

        if (BuildConfig.DEBUG) {
            mMissingClasses = missingLibs;
            mMissingClassesLiveData.postValue(missingLibs);
        }
    }

    @WorkerThread
    private void waitForFile() {
        try {
            mWaitForFile.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void enableUploading() {
        VtScanAttempt attempt = mPendingUploadAttempt;
        if (attempt != null) {
            mPendingUploadAttempt = null;
            mVtCoordinator.approve(attempt, new VtScanCoordinator.VtScanCallback() {
                @Override
                public void onConsentRequired(@NonNull VtScanAttempt a, @NonNull VtFile f) {
                }

                @Override
                public void onQueued(@NonNull VtScanAttempt a) {
                    mVtFileUploadLiveData.postValue("");
                }

                @Override
                public void onCompleted(@NonNull VtFileReport report) {
                    mVtFileReportLiveData.postValue(report);
                }

                @Override
                public void onFailed(@Nullable String message) {
                    mVtFileReportLiveData.postValue(null);
                }
            });
        }
    }

    public void disableUploading() {
        VtScanAttempt attempt = mPendingUploadAttempt;
        if (attempt != null) {
            mPendingUploadAttempt = null;
            mVtCoordinator.reject(attempt, new VtScanCoordinator.VtScanCallback() {
                @Override
                public void onConsentRequired(@NonNull VtScanAttempt a, @NonNull VtFile f) {
                }

                @Override
                public void onQueued(@NonNull VtScanAttempt a) {
                }

                @Override
                public void onCompleted(@NonNull VtFileReport report) {
                }

                @Override
                public void onFailed(@Nullable String message) {
                    mVtFileReportLiveData.postValue(null);
                }
            });
        }
    }
}
