// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.utils.AppPref;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, shadows = SecurityAndOpsViewModelTest.ShadowOps.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class SecurityAndOpsViewModelTest {
    private SecurityAndOpsViewModel mViewModel;

    @Before
    public void setUp() {
        ShadowOps.reset();
        AppPref.set(AppPref.PrefKey.PREF_LAST_VERSION_CODE_LONG, (long) BuildConfig.VERSION_CODE);
        mViewModel = new SecurityAndOpsViewModel(RuntimeEnvironment.getApplication());
    }

    @After
    public void tearDown() {
        mViewModel.onCleared();
    }

    @Test
    public void duplicateModeInitialisationIsIgnored() throws InterruptedException {
        ShadowOps.blockInit = true;

        mViewModel.setModeOfOps();
        assertTrue(ShadowOps.operationStarted.await(5, TimeUnit.SECONDS));
        mViewModel.setModeOfOps();
        ShadowOps.continueOperation.countDown();
        assertTrue(ShadowOps.operationFinished.await(5, TimeUnit.SECONDS));

        assertEquals(1, ShadowOps.initCalls.get());
        assertEquals(Integer.valueOf(Ops.STATUS_SUCCESS), awaitStatus());
    }

    @Test
    public void unexpectedFailureFallsBackAndPublishesFailure() throws InterruptedException {
        ShadowOps.throwFromInit = true;

        mViewModel.setModeOfOps();
        assertTrue(ShadowOps.fallbackCalled.await(5, TimeUnit.SECONDS));

        assertEquals(1, ShadowOps.fallbackCalls.get());
        assertEquals(Integer.valueOf(Ops.STATUS_FAILURE), awaitStatus());
    }

    @Test
    public void clearingViewModelInterruptsPairingWait() throws InterruptedException {
        mViewModel.pairAdb();
        assertTrue(ShadowOps.operationStarted.await(5, TimeUnit.SECONDS));

        mViewModel.onCleared();

        assertTrue(ShadowOps.operationInterrupted.await(5, TimeUnit.SECONDS));
    }

    private Integer awaitStatus() throws InterruptedException {
        for (int i = 0; i < 100; ++i) {
            ShadowLooper.idleMainLooper();
            Integer status = mViewModel.authenticationStatus().getValue();
            if (status != null) {
                return status;
            }
            Thread.sleep(10);
        }
        return null;
    }

    @Implements(Ops.class)
    public static class ShadowOps {
        static final AtomicInteger initCalls = new AtomicInteger();
        static final AtomicInteger fallbackCalls = new AtomicInteger();
        static CountDownLatch operationStarted;
        static CountDownLatch continueOperation;
        static CountDownLatch operationFinished;
        static CountDownLatch operationInterrupted;
        static CountDownLatch fallbackCalled;
        static boolean blockInit;
        static boolean throwFromInit;

        static void reset() {
            initCalls.set(0);
            fallbackCalls.set(0);
            operationStarted = new CountDownLatch(1);
            continueOperation = new CountDownLatch(1);
            operationFinished = new CountDownLatch(1);
            operationInterrupted = new CountDownLatch(1);
            fallbackCalled = new CountDownLatch(1);
            blockInit = false;
            throwFromInit = false;
        }

        @Implementation
        public static int init(Context context, boolean force) {
            initCalls.incrementAndGet();
            operationStarted.countDown();
            if (throwFromInit) {
                throw new IllegalStateException("Simulated initialisation failure");
            }
            if (blockInit) {
                try {
                    continueOperation.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    operationInterrupted.countDown();
                    return Ops.STATUS_FAILURE;
                }
            }
            operationFinished.countDown();
            return Ops.STATUS_SUCCESS;
        }

        @Implementation
        public static int pairAdb(Context context) {
            operationStarted.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                operationInterrupted.countDown();
            }
            return Ops.STATUS_FAILURE;
        }

        @Implementation
        public static void fallbackToNoRoot(Context context) {
            fallbackCalls.incrementAndGet();
            fallbackCalled.countDown();
        }
    }
}
