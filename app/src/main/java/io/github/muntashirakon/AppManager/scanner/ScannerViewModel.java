// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import io.github.muntashirakon.AppManager.scanner.reflector.Reflector;
import io.github.muntashirakon.AppManager.scanner.vt.VirusTotal;
import io.github.muntashirakon.AppManager.scanner.vt.VtFileReport;
import io.github.muntashirakon.AppManager.scanner.vt.VtFileScanMeta;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.VirtualFileSystem;

public class ScannerViewModel extends AndroidViewModel {
    private File apkFile;
    private boolean cached;
    private boolean loaded = false;
    private Uri apkUri;
    @RequiresApi(Build.VERSION_CODES.O)
    private int dexVfsId;
    private DexClassesPreOreo dexClassesPreOreo;
    @Nullable
    private final VirusTotal vt;

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
    private final MutableLiveData<List<String>> trackerClassesLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<String>> libraryClassesLiveData = new MutableLiveData<>();
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

    public LiveData<List<String>> trackerClassesLiveData() {
        return trackerClassesLiveData;
    }

    public LiveData<List<String>> libraryClassesLiveData() {
        return libraryClassesLiveData;
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
        Pair<String, String>[] digests = DigestUtils.getDigests(apkFile);
        apkChecksumsLiveData.postValue(digests);
        if (vt == null) return;
        String md5 = digests[0].second;
        try {
            List<VtFileReport> reports = vt.fetchReports(new String[]{md5});
            if (reports.size() == 0) throw new IOException("No report returned.");
            VtFileReport report = reports.get(0);
            int responseCode = report.getResponseCode();
            if (responseCode == VirusTotal.RESPONSE_FOUND) {
                vtFileReportLiveData.postValue(report);
                return;
            }
            // Report not found: request scan or wait
            if (responseCode == VirusTotal.RESPONSE_NOT_FOUND) {
                if (apkFile.length() > 32 * 1024 * 1024) {
                    throw new IOException("APK is larger than 32 MB.");
                }
                vtFileScanMetaLiveData.postValue(null);
                VtFileScanMeta scanMeta = vt.scan(apkFile.getName(), apkFile);
                vtFileScanMetaLiveData.postValue(scanMeta);
                responseCode = VirusTotal.RESPONSE_QUEUED;
            } else {
                // Item is queued
                vtFileReportLiveData.postValue(report);
            }
            int waitDuration = 60_000;
            while (responseCode == VirusTotal.RESPONSE_QUEUED) {
                reports = vt.fetchReports(new String[]{md5});
                if (reports.size() == 0) throw new IOException("No report returned.");
                report = reports.get(0);
                responseCode = report.getResponseCode();
                // Wait for result: First wait for 1 minute, then for 30 seconds
                // We won't do it less than 30 seconds since the API has a limit of 4 request/minute
                SystemClock.sleep(waitDuration);
                waitDuration = 30_000;
            }
            if (responseCode == VirusTotal.RESPONSE_FOUND) {
                vtFileReportLiveData.postValue(report);
            } else throw new IOException("Could not generate scan report");
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
        final PackageManager pm = getApplication().getPackageManager();
        packageInfoLiveData.postValue(pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0));
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
    }

    @WorkerThread
    private void waitForFile() {
        try {
            waitForFile.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
