// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Parcel;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONException;
import org.json.JSONObject;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowPackageManager;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerViewModel.PackageParseResult;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 27, shadows = PackageInstallerViewModelTest.ShadowComponentUtils.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class PackageInstallerViewModelTest {
    private PackageManager mPackageManager;
    private ShadowPackageManager mShadowPackageManager;
    private PackageInstallerViewModel mViewModel;

    @Before
    public void setUp() {
        Application application = RuntimeEnvironment.getApplication();
        mPackageManager = application.getPackageManager();
        mShadowPackageManager = shadowOf(mPackageManager);
        mViewModel = new PackageInstallerViewModel(application);
    }

    @After
    public void tearDown() {
        mViewModel.onCleared();
    }

    @Test
    public void installedPackageStateDoesNotLeakIntoNextQueueItem() throws Exception {
        File installedApk = getResourceFile("oandbackups/org.billthefarmer.editor/base.apk");
        PackageInfo installedPackage = Objects.requireNonNull(
                mPackageManager.getPackageArchiveInfo(installedApk.getAbsolutePath(), 0));
        installedPackage.applicationInfo.sourceDir = installedApk.getAbsolutePath();
        installedPackage.applicationInfo.publicSourceDir = installedApk.getAbsolutePath();
        mShadowPackageManager.installPackage(installedPackage);

        ApkQueueItem installedItem = queueItem(installedApk);
        mViewModel.getPackageInfo(installedItem);
        PackageParseResult firstResult = awaitPackage(installedPackage.packageName);

        assertEquals(installedPackage.packageName, firstResult.getPackageName());
        assertEquals(installedPackage.packageName,
                Objects.requireNonNull(firstResult.getInstalledPackageInfo()).packageName);

        File newApk = getResourceFile("oandbackups/ademar.textlauncher/base.apk");
        ApkQueueItem newItem = queueItem(newApk);
        mViewModel.getSelectedSplits().add("stale-split");
        mViewModel.getPackageInfo(newItem);
        PackageParseResult secondResult = awaitPackageDifferentFrom(firstResult.getPackageName());

        assertSame(secondResult, mViewModel.getCurrentPackage());
        assertNull(secondResult.getInstalledPackageInfo());
        assertFalse(secondResult.isSignatureDifferent());
        assertTrue(mViewModel.getSelectedSplits().isEmpty());
        assertEquals(secondResult.getPackageName(), newItem.getPackageName());
    }

    @Test
    public void supersededParseCannotOverwriteLatestPackage() throws Exception {
        File firstApk = getResourceFile("oandbackups/org.billthefarmer.editor/base.apk");
        BlockingApkSource blockingSource = new BlockingApkSource(
                ApkSource.getApkSource(Uri.fromFile(firstApk),
                        "application/vnd.android.package-archive"));
        mViewModel.getPackageInfo(ApkQueueItem.fromApkSource(blockingSource));
        assertTrue(blockingSource.resolveStarted.await(5, TimeUnit.SECONDS));

        File latestApk = getResourceFile("oandbackups/ademar.textlauncher/base.apk");
        ApkQueueItem latestItem = queueItem(latestApk);
        mViewModel.getPackageInfo(latestItem);
        PackageParseResult latestResult = awaitPackageDifferentFrom("org.billthefarmer.editor");

        blockingSource.allowResolve.countDown();
        for (int i = 0; i < 500 && (blockingSource.resolvedApk == null
                || !blockingSource.resolvedApk.isClosed()); ++i) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(10);
        }

        assertSame(latestResult, mViewModel.packageParseResultLiveData().getValue());
        assertSame(latestResult, mViewModel.getCurrentPackage());
        assertNotNull(blockingSource.resolvedApk);
        assertTrue(blockingSource.resolvedApk.isClosed());
    }

    @NonNull
    private PackageParseResult awaitPackage(@NonNull String packageName) throws InterruptedException {
        for (int i = 0; i < 500; ++i) {
            ShadowLooper.idleMainLooper();
            PackageParseResult value = mViewModel.packageParseResultLiveData().getValue();
            if (value != null && packageName.equals(value.getPackageName())) {
                return value;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for " + packageName);
    }

    @NonNull
    private PackageParseResult awaitPackageDifferentFrom(@NonNull String packageName) throws InterruptedException {
        for (int i = 0; i < 500; ++i) {
            ShadowLooper.idleMainLooper();
            PackageParseResult value = mViewModel.packageParseResultLiveData().getValue();
            if (value != null && !packageName.equals(value.getPackageName())) {
                return value;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for a package other than " + packageName);
    }

    @NonNull
    private static ApkQueueItem queueItem(@NonNull File apk) {
        return ApkQueueItem.fromApkSource(ApkSource.getApkSource(Uri.fromFile(apk),
                "application/vnd.android.package-archive"));
    }

    @NonNull
    private File getResourceFile(@NonNull String name) {
        ClassLoader classLoader = Objects.requireNonNull(getClass().getClassLoader());
        return new File(Objects.requireNonNull(classLoader.getResource(name)).getFile());
    }

    @Implements(ComponentUtils.class)
    public static class ShadowComponentUtils {
        @Implementation
        protected static int getTrackerComponentsCountForPackage(PackageInfo packageInfo) {
            return 0;
        }
    }

    private static final class BlockingApkSource extends ApkSource {
        @NonNull
        private final ApkSource delegate;
        private final CountDownLatch resolveStarted = new CountDownLatch(1);
        private final CountDownLatch allowResolve = new CountDownLatch(1);
        private volatile ApkFile resolvedApk;

        private BlockingApkSource(@NonNull ApkSource delegate) {
            this.delegate = delegate;
        }

        @NonNull
        @Override
        public ApkFile resolve() throws ApkFile.ApkFileException {
            resolveStarted.countDown();
            boolean waiting = true;
            while (waiting) {
                try {
                    allowResolve.await();
                    waiting = false;
                } catch (InterruptedException ignore) {
                    // Simulate an underlying parse operation that cannot be cancelled.
                }
            }
            return resolvedApk = delegate.resolve();
        }

        @NonNull
        @Override
        public ApkSource toCachedSource() {
            return this;
        }

        @NonNull
        @Override
        public JSONObject serializeToJson() throws JSONException {
            return delegate.serializeToJson();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeParcelable(delegate, flags);
        }
    }
}
