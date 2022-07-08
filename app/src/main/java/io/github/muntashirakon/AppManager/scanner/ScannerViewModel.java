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
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.apksig.ApkVerifier;
import com.android.apksig.apk.ApkFormatException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.scanner.reflector.Reflector;
import io.github.muntashirakon.AppManager.scanner.vt.VirusTotal;
import io.github.muntashirakon.AppManager.scanner.vt.VtFileReport;
import io.github.muntashirakon.AppManager.scanner.vt.VtFileScanMeta;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.io.VirtualFileSystem;

public class ScannerViewModel extends AndroidViewModel implements VirusTotal.FullScanResponseInterface {
    private static final Pattern SIG_TO_IGNORE = Pattern.compile("^(android(|x)|com\\.android|com\\.google\\.android|java(|x)|j\\$\\.(util|time)|\\w\\d?(\\.\\w\\d?)+)\\..*$");

    private File mApkFile;
    private boolean mIsApkCached;
    private boolean mIsSummaryLoaded = false;
    private Uri mApkUri;
    @RequiresApi(Build.VERSION_CODES.O)
    private int mDexVfsId;
    private DexClassesPreOreo mDexClassesPreOreo;
    @Nullable
    private final VirusTotal mVt;
    @Nullable
    private String mPackageName;

    private List<String> mAllClasses;
    private List<String> mTrackerClasses;
    private List<String> mLibraryClasses;
    private Collection<String> mNativeLibraries;

    private CountDownLatch mWaitForFile;
    private final MultithreadedExecutor mExecutor = MultithreadedExecutor.getNewInstance();
    private final MutableLiveData<Pair<String, String>[]> mApkChecksumsLiveData = new MutableLiveData<>();
    private final MutableLiveData<ApkVerifier.Result> mApkVerifierResultLiveData = new MutableLiveData<>();
    private final MutableLiveData<PackageInfo> mPackageInfoLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<String>> mAllClassesLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<SignatureInfo>> mTrackerClassesLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<SignatureInfo>> mLibraryClassesLiveData = new MutableLiveData<>();
    private final MutableLiveData<ArrayList<String>> mMissingClassesLiveData = new MutableLiveData<>();
    // Null = Uploading, NonNull = Queued
    private final MutableLiveData<VtFileScanMeta> mVtFileScanMetaLiveData = new MutableLiveData<>();
    // Null = Failed, NonNull = Result generated
    private final MutableLiveData<VtFileReport> mVtFileReportLiveData = new MutableLiveData<>();

    public ScannerViewModel(@NonNull Application application) {
        super(application);
        mVt = VirusTotal.getInstance();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mExecutor.shutdownNow();
        if (mIsApkCached && mApkFile != null) {
            // Only attempt to delete the apk file if it's cached
            FileUtils.deleteSilently(mApkFile);
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VirtualFileSystem.unmount(mDexVfsId);
            } else mDexClassesPreOreo.close();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @AnyThread
    public void loadSummary() {
        if (mIsSummaryLoaded) return;
        mIsSummaryLoaded = true;
        mIsApkCached = false;
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
        mExecutor.submit(this::generateApkChecksumsAndScanInVirusTotal);
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

    public LiveData<VtFileScanMeta> vtFileScanMetaLiveData() {
        return mVtFileScanMetaLiveData;
    }

    public List<String> getTrackerClasses() {
        return mTrackerClasses;
    }

    public void setTrackerClasses(List<String> trackerClasses) {
        this.mTrackerClasses = trackerClasses;
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
        this.mApkFile = apkFile;
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

    @RequiresApi(Build.VERSION_CODES.O)
    public Uri getUriFromClassName(String className) throws FileNotFoundException {
        Path fsRoot = VirtualFileSystem.getFsRoot(mDexVfsId);
        if (fsRoot == null) {
            throw new FileNotFoundException("FS Root not found.");
        }
        return fsRoot.findFile(className.replace('.', '/') + ".smali").getUri();
    }

    @Deprecated
    public String getClassContent(@NonNull String className) throws ClassNotFoundException {
        Reflector reflector = mDexClassesPreOreo.getReflector(className);
        reflector.generateClassData();
        return reflector.toString();
    }

    @WorkerThread
    private void cacheFileIfRequired() {
        // Test if this path is readable
        if (this.mApkFile == null || !mApkFile.canRead()) {
            // Not readable, cache the file
            try (InputStream uriStream = getApplication().getContentResolver().openInputStream(mApkUri)) {
                mApkFile = FileUtils.getCachedFile(uriStream);
                mIsApkCached = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @WorkerThread
    private void generateApkChecksumsAndScanInVirusTotal() {
        waitForFile();
        Pair<String, String>[] digests = DigestUtils.getDigests(Paths.get(mApkFile));
        mApkChecksumsLiveData.postValue(digests);
        if (mVt == null) return;
        String md5 = digests[0].second;
        try (InputStream is = new FileInputStream(mApkFile)) {
            mVt.fetchReportsOrScan(mApkFile.getName(), mApkFile.length(), is, md5, this);
        } catch (IOException e) {
            e.printStackTrace();
            mVtFileReportLiveData.postValue(null);
        }
    }

    private void loadApkVerifierResult() {
        waitForFile();
        try {
            // TODO: 26/5/21 Add v4 verification
            ApkVerifier.Builder builder = new ApkVerifier.Builder(mApkFile)
                    .setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT);
            ApkVerifier apkVerifier = builder.build();
            ApkVerifier.Result apkVerifierResult = apkVerifier.verify();
            this.mApkVerifierResultLiveData.postValue(apkVerifierResult);
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
            this.mNativeLibraries = nativeLibraries.getUniqueLibs();
        } catch (Throwable e) {
            mNativeLibraries = Collections.emptyList();
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                VirtualFileSystem.DexFileSystem dfs = new VirtualFileSystem.DexFileSystem(Uri.fromFile(mApkFile), mApkFile);
                mDexVfsId = VirtualFileSystem.mount(dfs);
                mAllClasses = dfs.getDexClasses().getClassNames();
            } catch (Throwable e) {
                mAllClasses = Collections.emptyList();
            }
        } else {
            mDexClassesPreOreo = new DexClassesPreOreo(getApplication(), mApkFile);
            mAllClasses = mDexClassesPreOreo.getClassNames();
        }
        mAllClassesLiveData.postValue(mAllClasses);
        // Load tracker and library info
        mExecutor.submit(this::loadTrackers);
        mExecutor.submit(this::loadLibraries);
    }

    @WorkerThread
    private void loadTrackers() {
        if (mAllClasses == null) return;
        List<SignatureInfo> trackerInfoList = new ArrayList<>();
        String[] trackerNames = StaticDataset.getTrackerNames();
        String[] trackerSignatures = StaticDataset.getTrackerCodeSignatures();
        int[] signatureCount = new int[trackerSignatures.length];
        // Iterate over all classes
        mTrackerClasses = new ArrayList<>();
        for (String className : mAllClasses) {
            if (className.length() > 8 && className.contains(".")) {
                // Iterate over all signatures to match the class name
                // This is a greedy algorithm, only matches the first item
                for (int i = 0; i < trackerSignatures.length; i++) {
                    if (className.contains(trackerSignatures[i])) {
                        mTrackerClasses.add(className);
                        signatureCount[i]++;
                        break;
                    }
                }
            }
        }
        // Iterate over signatures again but this time list only the found ones.
        for (int i = 0; i < trackerSignatures.length; i++) {
            if (signatureCount[i] == 0) continue;
            SignatureInfo signatureInfo = new SignatureInfo(trackerSignatures[i], trackerNames[i]);
            signatureInfo.setCount(signatureCount[i]);
            trackerInfoList.add(signatureInfo);
        }
        mTrackerClassesLiveData.postValue(trackerInfoList);
    }

    public void loadLibraries() {
        if (mAllClasses == null) return;
        List<SignatureInfo> libraryInfoList = new ArrayList<>();
        ArrayList<String> missingLibs = new ArrayList<>();
        String[] libNames = getApplication().getResources().getStringArray(R.array.lib_names);
        String[] libSignatures = getApplication().getResources().getStringArray(R.array.lib_signatures);
        String[] libTypes = getApplication().getResources().getStringArray(R.array.lib_types);
        // The following array is directly mapped to the arrays above
        int[] signatureCount = new int[libSignatures.length];
        // Iterate over all classes
        mLibraryClasses = new ArrayList<>();
        for (String className : mAllClasses) {
            if (className.length() > 8 && className.contains(".")) {
                boolean matched = false;
                // Iterate over all signatures to match the class name
                // This is a greedy algorithm, only matches the first item
                for (int i = 0; i < libSignatures.length; i++) {
                    if (className.contains(libSignatures[i])) {
                        matched = true;
                        // Add to found classes
                        mLibraryClasses.add(className);
                        // Increment this signature match count
                        signatureCount[i]++;
                        break;
                    }
                }
                // Add the class to the missing libs list if it doesn't match the filters
                if (!matched
                        && (mPackageName != null && !className.startsWith(mPackageName))
                        && !SIG_TO_IGNORE.matcher(className).matches()) {
                    missingLibs.add(className);
                }
            }
        }
        // Iterate over signatures again but this time list only the found ones.
        for (int i = 0; i < libSignatures.length; i++) {
            if (signatureCount[i] == 0) continue;
            SignatureInfo signatureInfo = new SignatureInfo(libSignatures[i], libNames[i], libTypes[i]);
            signatureInfo.setCount(signatureCount[i]);
            libraryInfoList.add(signatureInfo);
        }
        mLibraryClassesLiveData.postValue(libraryInfoList);
        mMissingClassesLiveData.postValue(missingLibs);
    }

    @WorkerThread
    private void waitForFile() {
        try {
            mWaitForFile.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean mUploadingEnabled;
    private CountDownLatch mUploadingEnabledWatcher;

    public void enableUploading() {
        mUploadingEnabled = true;
        if (mUploadingEnabledWatcher != null) {
            mUploadingEnabledWatcher.countDown();
        }
    }

    public void disableUploading() {
        mUploadingEnabled = false;
        if (mUploadingEnabledWatcher != null) {
            mUploadingEnabledWatcher.countDown();
        }
    }

    @Override
    public boolean scanFile() {
        mUploadingEnabled = false;
        mUploadingEnabledWatcher = new CountDownLatch(1);
        mVtFileScanMetaLiveData.postValue(null);
        try {
            mUploadingEnabledWatcher.await(2, TimeUnit.MINUTES);
        } catch (InterruptedException ignore) {
        }
        return mUploadingEnabled;
    }

    @Override
    public void onScanningInitiated() {
    }

    @Override
    public void onScanCompleted(@NonNull VtFileScanMeta meta) {
        mVtFileScanMetaLiveData.postValue(meta);
    }

    @Override
    public void onReportReceived(@NonNull VtFileReport report) {
        mVtFileReportLiveData.postValue(report);
    }
}
