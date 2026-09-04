// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.GET_SIGNING_CERTIFICATES;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.GET_SIGNING_CERTIFICATES_APK;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_DISABLED_COMPONENTS;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.UserHandleHidden;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.IoUtils;

public class PackageInstallerViewModel extends AndroidViewModel {
    private static final String STATE_QUEUE_INITIALIZED = "queue_initialized";
    private static final String STATE_PENDING_ITEMS = "pending_items";
    private static final String STATE_CURRENT_ITEM = "current_item";
    private static final String STATE_CURRENT_ITEM_SUBMITTED = "current_item_submitted";
    private static final String STATE_INSTALL_RESULT = "install_result";
    private static final String STATE_LAST_USER_ID = "last_user_id";
    private static final String STATE_INSTALLER_OPTIONS = "installer_options";

    private final PackageManager mPm;
    private final SavedStateHandle mSavedStateHandle;
    private final ArrayDeque<ApkQueueItem> mPendingItems = new ArrayDeque<>();
    private boolean mQueueInitialized;
    @Nullable
    private ApkQueueItem mCurrentItem;
    private boolean mCurrentItemSubmitted;
    @Nullable
    private PackageInstallResult mInstallResult;
    private int mLastUserId;
    @NonNull
    private InstallerOptions mInstallerOptions;
    @Nullable
    private PackageParseResult mCurrentPackage;
    @Nullable
    private Future<?> mPackageInfoResult;
    private final AtomicInteger mPackageInfoGeneration = new AtomicInteger();
    private final MutableLiveData<PackageParseResult> mPackageParseResultLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mPackageUninstalledLiveData = new MutableLiveData<>();
    private final Set<String> mSelectedSplits = new HashSet<>();

    public PackageInstallerViewModel(@NonNull Application application) {
        this(application, new SavedStateHandle());
    }

    public PackageInstallerViewModel(@NonNull Application application, @NonNull SavedStateHandle savedStateHandle) {
        super(application);
        mPm = application.getPackageManager();
        mSavedStateHandle = savedStateHandle;
        mQueueInitialized = Boolean.TRUE.equals(savedStateHandle.get(STATE_QUEUE_INITIALIZED));
        ArrayList<ApkQueueItem> pendingItems = savedStateHandle.get(STATE_PENDING_ITEMS);
        if (pendingItems != null) {
            mPendingItems.addAll(pendingItems);
        }
        mCurrentItem = savedStateHandle.get(STATE_CURRENT_ITEM);
        mCurrentItemSubmitted = Boolean.TRUE.equals(savedStateHandle.get(STATE_CURRENT_ITEM_SUBMITTED));
        Integer lastUserId = savedStateHandle.get(STATE_LAST_USER_ID);
        mLastUserId = lastUserId != null ? lastUserId : UserHandleHidden.myUserId();
        InstallerOptions installerOptions = savedStateHandle.get(STATE_INSTALLER_OPTIONS);
        mInstallerOptions = installerOptions != null
                ? InstallerOptions.copyOf(installerOptions)
                : InstallerOptions.getDefault();
        mSavedStateHandle.set(STATE_INSTALLER_OPTIONS, mInstallerOptions);
        mInstallResult = savedStateHandle.get(STATE_INSTALL_RESULT);
    }

    @Override
    protected void onCleared() {
        mPackageInfoGeneration.incrementAndGet();
        if (mPackageInfoResult != null) {
            mPackageInfoResult.cancel(true);
        }
        if (mCurrentPackage != null) {
            IoUtils.closeQuietly(mCurrentPackage.apkFile);
            mCurrentPackage = null;
        }
        super.onCleared();
    }

    public LiveData<PackageParseResult> packageParseResultLiveData() {
        return mPackageParseResultLiveData;
    }

    public LiveData<Boolean> packageUninstalledLiveData() {
        return mPackageUninstalledLiveData;
    }

    public synchronized boolean isQueueInitialized() {
        return mQueueInitialized;
    }

    public synchronized void initializeQueue(@NonNull Collection<ApkQueueItem> initialItems) {
        if (mQueueInitialized) {
            return;
        }
        mQueueInitialized = true;
        mPendingItems.addAll(initialItems);
        persistQueueState();
    }

    public synchronized void enqueue(@NonNull Collection<ApkQueueItem> items) {
        mPendingItems.addAll(items);
        persistQueueState();
    }

    @Nullable
    public synchronized ApkQueueItem getCurrentQueueItem() {
        return mCurrentItem;
    }

    @Nullable
    public synchronized ApkQueueItem startNextQueueItem() {
        if (mCurrentItem != null) {
            return mCurrentItem;
        }
        mCurrentItem = mPendingItems.poll();
        mCurrentItemSubmitted = false;
        mInstallResult = null;
        persistQueueState();
        persistInstallResult();
        return mCurrentItem;
    }

    public synchronized void finishCurrentQueueItem() {
        mPackageInfoGeneration.incrementAndGet();
        if (mPackageInfoResult != null) {
            mPackageInfoResult.cancel(true);
            mPackageInfoResult = null;
        }
        if (mCurrentPackage != null) {
            IoUtils.closeQuietly(mCurrentPackage.apkFile);
            mCurrentPackage = null;
        }
        mSelectedSplits.clear();
        mCurrentItem = null;
        mCurrentItemSubmitted = false;
        mInstallResult = null;
        persistQueueState();
        persistInstallResult();
    }

    public synchronized boolean hasPendingItems() {
        return !mPendingItems.isEmpty();
    }

    @NonNull
    synchronized ArrayList<ApkQueueItem> getPendingItems() {
        return new ArrayList<>(mPendingItems);
    }

    public synchronized void markCurrentItemSubmitted(int lastUserId) {
        mCurrentItemSubmitted = true;
        mLastUserId = lastUserId;
        persistQueueState();
        mSavedStateHandle.set(STATE_LAST_USER_ID, lastUserId);
    }

    public synchronized boolean isCurrentItemSubmitted() {
        return mCurrentItemSubmitted;
    }

    public synchronized int getLastUserId() {
        return mLastUserId;
    }

    @NonNull
    public synchronized InstallerOptions getInstallerOptions() {
        return InstallerOptions.copyOf(mInstallerOptions);
    }

    public synchronized void updateInstallerOptions(@NonNull InstallerOptions installerOptions) {
        mInstallerOptions = InstallerOptions.copyOf(installerOptions);
        mSavedStateHandle.set(STATE_INSTALLER_OPTIONS, mInstallerOptions);
    }

    public synchronized void setInstallResult(@NonNull PackageInstallResult installResult) {
        mInstallResult = installResult;
        persistInstallResult();
    }

    @Nullable
    public synchronized PackageInstallResult getInstallResult() {
        return mInstallResult;
    }

    @AnyThread
    public synchronized void getPackageInfo(ApkQueueItem apkQueueItem) {
        int generation = mPackageInfoGeneration.incrementAndGet();
        if (mPackageInfoResult != null) {
            mPackageInfoResult.cancel(true);
        }
        mPackageInfoResult = ThreadUtils.postOnBackgroundThread(() -> {
            try {
                PackageParseResult result = loadPackageInfo(apkQueueItem);
                ThreadUtils.postOnMainThread(() -> publishPackageInfo(generation, apkQueueItem, result));
            } catch (Throwable th) {
                Log.e("PIVM", "Couldn't fetch package info", th);
                ThreadUtils.postOnMainThread(() -> publishPackageInfoFailure(generation));
            }
        });
    }

    public void uninstallPackage() {
        PackageParseResult currentPackage = Objects.requireNonNull(mCurrentPackage);
        ThreadUtils.postOnBackgroundThread(() -> {
            PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
            installer.setAppLabel(currentPackage.appLabel);
            mPackageUninstalledLiveData.postValue(installer.uninstall(currentPackage.packageName,
                    UserHandleHidden.USER_ALL, false));
        });
    }

    @Nullable
    public PackageParseResult getCurrentPackage() {
        return mCurrentPackage;
    }

    public Set<String> getSelectedSplits() {
        return mSelectedSplits;
    }

    @NonNull
    public ArrayList<String> getSelectedSplitsForInstallation() {
        ApkFile apkFile = Objects.requireNonNull(mCurrentPackage).apkFile;
        if (apkFile.isSplit()) {
            if (mSelectedSplits.isEmpty()) {
                throw new IllegalArgumentException("No splits selected.");
            }
            return new ArrayList<>(mSelectedSplits);
        }
        return new ArrayList<>(Collections.singletonList(apkFile.getBaseEntry().id));
    }

    @WorkerThread
    @NonNull
    private PackageParseResult loadPackageInfo(@NonNull ApkQueueItem apkQueueItem) throws Throwable {
        ApkSource apkSource;
        ApkFile apkFile = null;
        try {
            PackageInfo installedPackageInfo = null;
            if (apkQueueItem.isInstallExisting()) {
                String packageName = apkQueueItem.getPackageName();
                if (packageName == null) {
                    throw new IllegalArgumentException("Package name not set for install-existing.");
                }
                installedPackageInfo = loadInstalledPackageInfo(packageName);
                apkSource = ApkSource.getApkSource(installedPackageInfo.applicationInfo);
            } else {
                apkSource = apkQueueItem.getApkSource();
                if (apkSource == null) {
                    throw new IllegalArgumentException("Invalid queue item.");
                }
            }
            apkFile = apkSource.resolve();
            PackageInfo newPackageInfo = loadNewPackageInfo(apkFile);
            String packageName = newPackageInfo.packageName;
            throwIfInterrupted();
            if (installedPackageInfo == null) {
                try {
                    installedPackageInfo = loadInstalledPackageInfo(packageName);
                } catch (PackageManager.NameNotFoundException ignore) {
                }
            }
            throwIfInterrupted();
            String appLabel = mPm.getApplicationLabel(newPackageInfo.applicationInfo).toString();
            Drawable appIcon = mPm.getApplicationIcon(newPackageInfo.applicationInfo);
            int trackerCount = ComponentUtils.getTrackerComponentsCountForPackage(newPackageInfo);
            throwIfInterrupted();
            boolean isSignatureDifferent = installedPackageInfo != null
                    && PackageUtils.isSignatureDifferent(newPackageInfo, installedPackageInfo);
            return new PackageParseResult(apkQueueItem.getOperationId(), newPackageInfo, installedPackageInfo,
                    apkSource, apkFile,
                    packageName, appLabel, appIcon, isSignatureDifferent, trackerCount);
        } catch (Throwable th) {
            IoUtils.closeQuietly(apkFile);
            throw th;
        }
    }

    private static void throwIfInterrupted() throws InterruptedException {
        if (ThreadUtils.isInterrupted()) {
            throw new InterruptedException();
        }
    }

    private void publishPackageInfo(int generation, @NonNull ApkQueueItem apkQueueItem,
                                    @NonNull PackageParseResult result) {
        if (generation != mPackageInfoGeneration.get()) {
            IoUtils.closeQuietly(result.apkFile);
            return;
        }
        PackageParseResult oldPackage = mCurrentPackage;
        mCurrentPackage = result;
        mSelectedSplits.clear();
        apkQueueItem.setApkSource(result.apkSource);
        apkQueueItem.setPackageName(result.packageName);
        apkQueueItem.setAppLabel(result.appLabel);
        apkQueueItem.setTestOnly(ApplicationInfoCompat.isTestOnly(result.newPackageInfo.applicationInfo));
        persistQueueState();
        if (oldPackage != null && oldPackage.apkFile != result.apkFile) {
            IoUtils.closeQuietly(oldPackage.apkFile);
        }
        mPackageParseResultLiveData.setValue(result);
    }

    private void publishPackageInfoFailure(int generation) {
        if (generation != mPackageInfoGeneration.get()) {
            return;
        }
        if (mCurrentPackage != null) {
            IoUtils.closeQuietly(mCurrentPackage.apkFile);
            mCurrentPackage = null;
        }
        mSelectedSplits.clear();
        mPackageParseResultLiveData.setValue(null);
    }

    @WorkerThread
    @NonNull
    private PackageInfo loadNewPackageInfo(@NonNull ApkFile apkFile) throws PackageManager.NameNotFoundException, IOException {
        String apkPath = apkFile.getBaseEntry().getFile(false).getAbsolutePath();
        int flags = PackageManager.GET_PERMISSIONS
                | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                | PackageManager.GET_SERVICES | MATCH_DISABLED_COMPONENTS | GET_SIGNING_CERTIFICATES_APK
                | PackageManager.GET_CONFIGURATIONS | PackageManager.GET_SHARED_LIBRARY_FILES;
        PackageInfo packageInfo = mPm.getPackageArchiveInfo(apkPath, flags);
        if (packageInfo == null) {
            // Previous method could return null if the APK isn't signed. So, try without it.
            packageInfo = mPm.getPackageArchiveInfo(apkPath, flags & ~GET_SIGNING_CERTIFICATES_APK);
        }
        if (packageInfo == null) {
            throw new PackageManager.NameNotFoundException("Package cannot be parsed.");
        }
        packageInfo.applicationInfo.sourceDir = apkPath;
        packageInfo.applicationInfo.publicSourceDir = apkPath;
        return packageInfo;
    }

    public static final class PackageParseResult {
        @NonNull
        private final String operationId;
        @NonNull
        private final PackageInfo newPackageInfo;
        @Nullable
        private final PackageInfo installedPackageInfo;
        @NonNull
        private final ApkSource apkSource;
        @NonNull
        private final ApkFile apkFile;
        @NonNull
        private final String packageName;
        @NonNull
        private final String appLabel;
        @NonNull
        private final Drawable appIcon;
        private final boolean isSignatureDifferent;
        private final int trackerCount;

        private PackageParseResult(@NonNull String operationId, @NonNull PackageInfo newPackageInfo,
                                   @Nullable PackageInfo installedPackageInfo,
                                   @NonNull ApkSource apkSource, @NonNull ApkFile apkFile,
                                   @NonNull String packageName, @NonNull String appLabel,
                                   @NonNull Drawable appIcon, boolean isSignatureDifferent,
                                   int trackerCount) {
            this.operationId = operationId;
            this.newPackageInfo = newPackageInfo;
            this.installedPackageInfo = installedPackageInfo;
            this.apkSource = apkSource;
            this.apkFile = apkFile;
            this.packageName = packageName;
            this.appLabel = appLabel;
            this.appIcon = appIcon;
            this.isSignatureDifferent = isSignatureDifferent;
            this.trackerCount = trackerCount;
        }

        @NonNull
        public String getOperationId() {
            return operationId;
        }

        @NonNull
        public PackageInfo getNewPackageInfo() {
            return newPackageInfo;
        }

        @Nullable
        public PackageInfo getInstalledPackageInfo() {
            return installedPackageInfo;
        }

        @NonNull
        public ApkSource getApkSource() {
            return apkSource;
        }

        @NonNull
        public ApkFile getApkFile() {
            return apkFile;
        }

        @NonNull
        public String getPackageName() {
            return packageName;
        }

        @NonNull
        public String getAppLabel() {
            return appLabel;
        }

        @NonNull
        public Drawable getAppIcon() {
            return appIcon;
        }

        public boolean isSignatureDifferent() {
            return isSignatureDifferent;
        }

        public int getTrackerCount() {
            return trackerCount;
        }
    }

    private synchronized void persistQueueState() {
        mSavedStateHandle.set(STATE_QUEUE_INITIALIZED, mQueueInitialized);
        mSavedStateHandle.set(STATE_PENDING_ITEMS, new ArrayList<>(mPendingItems));
        mSavedStateHandle.set(STATE_CURRENT_ITEM, mCurrentItem);
        mSavedStateHandle.set(STATE_CURRENT_ITEM_SUBMITTED, mCurrentItemSubmitted);
    }

    private synchronized void persistInstallResult() {
        if (mInstallResult != null) {
            mSavedStateHandle.set(STATE_INSTALL_RESULT, mInstallResult);
        } else {
            mSavedStateHandle.remove(STATE_INSTALL_RESULT);
        }
    }

    @WorkerThread
    @NonNull
    private PackageInfo loadInstalledPackageInfo(String packageName) throws PackageManager.NameNotFoundException {
        @SuppressLint("WrongConstant")
        PackageInfo packageInfo = mPm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS
                | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                | PackageManager.GET_SERVICES | MATCH_DISABLED_COMPONENTS | GET_SIGNING_CERTIFICATES | MATCH_UNINSTALLED_PACKAGES
                | PackageManager.GET_CONFIGURATIONS | PackageManager.GET_SHARED_LIBRARY_FILES);
        if (packageInfo == null) {
            throw new PackageManager.NameNotFoundException("Package not found.");
        }
        return packageInfo;
    }
}
