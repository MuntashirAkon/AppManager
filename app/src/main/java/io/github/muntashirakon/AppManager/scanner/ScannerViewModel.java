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

    private File apkFile;
    private boolean cached;
    private boolean loaded = false;
    private Uri apkUri;
    @RequiresApi(Build.VERSION_CODES.O)
    private int dexVfsId;
    private DexClassesPreOreo dexClassesPreOreo;
    @Nullable
    private final VirusTotal vt;
    @Nullable
    private String mPackageName;

    private List<String> allClasses;
    private List<String> trackerClasses;
    private List<String> libraryClasses;
    private Collection<String> nativeLibraries;

    private CountDownLatch waitForFile;
    private final MultithreadedExecutor executor = MultithreadedExecutor.getNewInstance();
    private final MutableLiveData<Pair<String, String>[]> apkChecksumsLiveData = new MutableLiveData<>();
    private final MutableLiveData<ApkVerifier.Result> apkVerifierResultLiveData = new MutableLiveData<>();
    private final MutableLiveData<PackageInfo> packageInfoLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<String>> allClassesLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<SignatureInfo>> trackerClassesLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<SignatureInfo>> libraryClassesLiveData = new MutableLiveData<>();
    private final MutableLiveData<ArrayList<String>> missingClassesLiveData = new MutableLiveData<>();
    // Null = Uploading, NonNull = Queued
    private final MutableLiveData<VtFileScanMeta> vtFileScanMetaLiveData = new MutableLiveData<>();
    // Null = Failed, NonNull = Result generated
    private final MutableLiveData<VtFileReport> vtFileReportLiveData = new MutableLiveData<>();

    public ScannerViewModel(@NonNull Application application) {
        super(application);
        vt = VirusTotal.getInstance();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
        if (cached && apkFile != null) {
            // Only attempt to delete the apk file if it's cached
            FileUtils.deleteSilently(apkFile);
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VirtualFileSystem.unmount(dexVfsId);
            } else dexClassesPreOreo.close();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @AnyThread
    public void loadSummary() {
        if (loaded) return;
        loaded = true;
        cached = false;
        waitForFile = new CountDownLatch(1);
        // Cache files
        executor.submit(() -> {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            try {
                cacheFileIfRequired();
            } finally {
                waitForFile.countDown();
            }
        });
        // Generate APK checksums
        executor.submit(this::generateApkChecksumsAndScanInVirusTotal);
        // Verify APK
        executor.submit(this::loadApkVerifierResult);
        // Load package info
        executor.submit(this::loadPackageInfo);
        // Load all classes
        executor.submit(() -> {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            loadAllClasses();
        });
    }

    @NonNull
    public LiveData<Pair<String, String>[]> apkChecksumsLiveData() {
        return apkChecksumsLiveData;
    }

    @NonNull
    public LiveData<ApkVerifier.Result> apkVerifierResultLiveData() {
        return apkVerifierResultLiveData;
    }

    @NonNull
    public LiveData<PackageInfo> packageInfoLiveData() {
        return packageInfoLiveData;
    }

    @NonNull
    public LiveData<List<String>> allClassesLiveData() {
        return allClassesLiveData;
    }

    public LiveData<List<SignatureInfo>> trackerClassesLiveData() {
        return trackerClassesLiveData;
    }

    public LiveData<List<SignatureInfo>> libraryClassesLiveData() {
        return libraryClassesLiveData;
    }

    public LiveData<ArrayList<String>> missingClassesLiveData() {
        return missingClassesLiveData;
    }

    public LiveData<VtFileReport> vtFileReportLiveData() {
        return vtFileReportLiveData;
    }

    public LiveData<VtFileScanMeta> vtFileScanMetaLiveData() {
        return vtFileScanMetaLiveData;
    }

    public List<String> getTrackerClasses() {
        return trackerClasses;
    }

    public void setTrackerClasses(List<String> trackerClasses) {
        this.trackerClasses = trackerClasses;
    }

    @Nullable
    public File getApkFile() {
        return apkFile;
    }

    public void setApkFile(@Nullable File apkFile) {
        this.apkFile = apkFile;
    }

    public Uri getApkUri() {
        return apkUri;
    }

    public void setApkUri(@NonNull Uri apkUri) {
        this.apkUri = apkUri;
    }

    public List<String> getAllClasses() {
        return allClasses;
    }

    public Collection<String> getNativeLibraries() {
        return nativeLibraries;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public Uri getUriFromClassName(String className) throws FileNotFoundException {
        Path fsRoot = VirtualFileSystem.getFsRoot(dexVfsId);
        if (fsRoot == null) {
            throw new FileNotFoundException("FS Root not found.");
        }
        return fsRoot.findFile(className.replace('.', '/') + ".smali").getUri();
    }

    @Deprecated
    public String getClassContent(@NonNull String className) throws ClassNotFoundException {
        Reflector reflector = dexClassesPreOreo.getReflector(className);
        reflector.generateClassData();
        return reflector.toString();
    }

    @WorkerThread
    private void cacheFileIfRequired() {
        // Test if this path is readable
        if (this.apkFile == null || !apkFile.canRead()) {
            // Not readable, cache the file
            try (InputStream uriStream = getApplication().getContentResolver().openInputStream(apkUri)) {
                apkFile = FileUtils.getCachedFile(uriStream);
                cached = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @WorkerThread
    private void generateApkChecksumsAndScanInVirusTotal() {
        waitForFile();
        Pair<String, String>[] digests = DigestUtils.getDigests(Paths.get(apkFile));
        apkChecksumsLiveData.postValue(digests);
        if (vt == null) return;
        String md5 = digests[0].second;
        try (InputStream is = new FileInputStream(apkFile)) {
            vt.fetchReportsOrScan(apkFile.getName(), apkFile.length(), is, md5, this);
        } catch (IOException e) {
            e.printStackTrace();
            vtFileReportLiveData.postValue(null);
        }
    }

    private void loadApkVerifierResult() {
        waitForFile();
        try {
            // TODO: 26/5/21 Add v4 verification
            ApkVerifier.Builder builder = new ApkVerifier.Builder(apkFile)
                    .setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT);
            ApkVerifier apkVerifier = builder.build();
            ApkVerifier.Result apkVerifierResult = apkVerifier.verify();
            this.apkVerifierResultLiveData.postValue(apkVerifierResult);
        } catch (IOException | ApkFormatException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    @WorkerThread
    private void loadPackageInfo() {
        waitForFile();
        PackageManager pm = getApplication().getPackageManager();
        PackageInfo packageInfo = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
        if (packageInfo != null) {
            mPackageName = packageInfo.packageName;
        }
        packageInfoLiveData.postValue(packageInfo);
    }

    @WorkerThread
    private void loadAllClasses() {
        waitForFile();
        try {
            NativeLibraries nativeLibraries = new NativeLibraries(apkFile);
            this.nativeLibraries = nativeLibraries.getUniqueLibs();
        } catch (Throwable e) {
            nativeLibraries = Collections.emptyList();
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                VirtualFileSystem.DexFileSystem dfs = new VirtualFileSystem.DexFileSystem(Uri.fromFile(apkFile), apkFile);
                dexVfsId = VirtualFileSystem.mount(dfs);
                allClasses = dfs.getDexClasses().getClassNames();
            } catch (Throwable e) {
                allClasses = Collections.emptyList();
            }
        } else {
            dexClassesPreOreo = new DexClassesPreOreo(getApplication(), apkFile);
            allClasses = dexClassesPreOreo.getClassNames();
        }
        allClassesLiveData.postValue(allClasses);
        // Load tracker and library info
        executor.submit(this::loadTrackers);
        executor.submit(this::loadLibraries);
    }

    @WorkerThread
    private void loadTrackers() {
        if (allClasses == null) return;
        List<SignatureInfo> trackerInfoList = new ArrayList<>();
        String[] trackerNames = StaticDataset.getTrackerNames();
        String[] trackerSignatures = StaticDataset.getTrackerCodeSignatures();
        int[] signatureCount = new int[trackerSignatures.length];
        // Iterate over all classes
        trackerClasses = new ArrayList<>();
        for (String className : allClasses) {
            if (className.length() > 8 && className.contains(".")) {
                // Iterate over all signatures to match the class name
                // This is a greedy algorithm, only matches the first item
                for (int i = 0; i < trackerSignatures.length; i++) {
                    if (className.contains(trackerSignatures[i])) {
                        trackerClasses.add(className);
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
        trackerClassesLiveData.postValue(trackerInfoList);
    }

    public void loadLibraries() {
        if (allClasses == null) return;
        List<SignatureInfo> libraryInfoList = new ArrayList<>();
        ArrayList<String> missingLibs = new ArrayList<>();
        String[] libNames = getApplication().getResources().getStringArray(R.array.lib_names);
        String[] libSignatures = getApplication().getResources().getStringArray(R.array.lib_signatures);
        String[] libTypes = getApplication().getResources().getStringArray(R.array.lib_types);
        // The following array is directly mapped to the arrays above
        int[] signatureCount = new int[libSignatures.length];
        // Iterate over all classes
        libraryClasses = new ArrayList<>();
        for (String className : allClasses) {
            if (className.length() > 8 && className.contains(".")) {
                boolean matched = false;
                // Iterate over all signatures to match the class name
                // This is a greedy algorithm, only matches the first item
                for (int i = 0; i < libSignatures.length; i++) {
                    if (className.contains(libSignatures[i])) {
                        matched = true;
                        // Add to found classes
                        libraryClasses.add(className);
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
        libraryClassesLiveData.postValue(libraryInfoList);
        missingClassesLiveData.postValue(missingLibs);
    }

    @WorkerThread
    private void waitForFile() {
        try {
            waitForFile.await();
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
        vtFileScanMetaLiveData.postValue(null);
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
        vtFileScanMetaLiveData.postValue(meta);
    }

    @Override
    public void onReportReceived(@NonNull VtFileReport report) {
        vtFileReportLiveData.postValue(report);
    }
}
