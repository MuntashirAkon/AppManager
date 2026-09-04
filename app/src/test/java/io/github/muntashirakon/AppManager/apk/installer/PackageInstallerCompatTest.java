// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 27)
public class PackageInstallerCompatTest {
    @Test
    public void callbacksRequireMatchingOperationAndSession() {
        PackageInstallerCompat first = PackageInstallerCompat.getNewInstance();
        PackageInstallerCompat second = PackageInstallerCompat.getNewInstance();
        Intent callback = new Intent(PackageInstallerCompat.ACTION_INSTALL_COMPLETED)
                .putExtra(PackageInstallerCompat.EXTRA_OPERATION_ID, first.getOperationId())
                .putExtra(PackageInstaller.EXTRA_SESSION_ID, -1);

        assertTrue(first.isExpectedCallback(callback, -1));
        assertFalse(second.isExpectedCallback(callback, -1));
        assertFalse(first.isExpectedCallback(callback, 42));
    }

    @Test
    public void pendingIntentIdentityIsUniquePerOperationAndPurpose() {
        Intent firstCallback = new Intent();
        Intent secondCallback = new Intent();
        Intent firstCancellation = new Intent();
        PackageInstallerCompat.setOperationIdentity(firstCallback, "first", "callback");
        PackageInstallerCompat.setOperationIdentity(secondCallback, "second", "callback");
        PackageInstallerCompat.setOperationIdentity(firstCancellation, "first", "cancel");

        assertNotEquals(firstCallback.getData(), secondCallback.getData());
        assertNotEquals(firstCallback.getData(), firstCancellation.getData());
        assertEquals("first", firstCallback.getStringExtra(PackageInstallerCompat.EXTRA_OPERATION_ID));
        assertEquals("first", PackageInstallerCompat.getOperationId(firstCallback));

        // PackageInstaller may replace the fill-in extras when delivering a mutable PendingIntent.
        // The PendingIntent's data URI remains its stable operation identity.
        firstCallback.removeExtra(PackageInstallerCompat.EXTRA_OPERATION_ID);
        assertEquals("first", PackageInstallerCompat.getOperationId(firstCallback));

        IntentFilter callbackFilter = PackageInstallerCompat.getPackageInstallerCallbackFilter();
        assertTrue(callbackFilter.matchAction(PackageInstallerBroadcastReceiver.ACTION_PI_RECEIVER));
        assertTrue(callbackFilter.hasDataScheme(firstCallback.getScheme()));
        assertTrue(callbackFilter.hasDataAuthority(firstCallback.getData()));
        assertTrue(callbackFilter.match(PackageInstallerBroadcastReceiver.ACTION_PI_RECEIVER,
                null, firstCallback.getScheme(), firstCallback.getData(), null, "test") >= 0);
        IntentFilter actionOnlyFilter =
                new IntentFilter(PackageInstallerBroadcastReceiver.ACTION_PI_RECEIVER);
        assertTrue(actionOnlyFilter.match(PackageInstallerBroadcastReceiver.ACTION_PI_RECEIVER,
                null, firstCallback.getScheme(), firstCallback.getData(), null, "test") < 0);
    }

    @Test
    public void attemptCompletionCanOnlyBeClaimedOnce() {
        PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();

        assertTrue(installer.claimAttemptCompletion(-1));
        assertFalse(installer.claimAttemptCompletion(-1));
        assertFalse(installer.claimAttemptCompletion(42));
    }

    @Test
    public void localResultWaitIsBoundedAndReturnsARealCallback() {
        PackageInstallerCompat.LocalIntentReceiver receiver =
                new PackageInstallerCompat.LocalIntentReceiver();

        assertNull(receiver.getResult(10, TimeUnit.MILLISECONDS));
        Intent pendingResult = new Intent();
        Intent terminalResult = new Intent();
        receiver.offerResult(pendingResult);
        receiver.offerResult(terminalResult);

        assertSame(pendingResult, receiver.getResult(1, TimeUnit.SECONDS));
        assertSame(terminalResult, receiver.getResult(1, TimeUnit.SECONDS));
    }

    @Test
    public void verifierCoordinatorAllowsNormalInstallsToOverlap() throws Exception {
        AtomicInteger activeCallers = new AtomicInteger();
        AtomicInteger maximumActiveCallers = new AtomicInteger();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);

        Thread first = new Thread(() -> runCoordinated(activeCallers, maximumActiveCallers,
                firstEntered, releaseFirst, false));
        Thread second = new Thread(() -> runCoordinated(activeCallers, maximumActiveCallers,
                secondEntered, new CountDownLatch(0), false));
        first.start();
        assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
        second.start();
        assertTrue(secondEntered.await(5, TimeUnit.SECONDS));
        releaseFirst.countDown();
        first.join(5000);
        second.join(5000);

        assertFalse(first.isAlive());
        assertFalse(second.isAlive());
        assertEquals(2, maximumActiveCallers.get());
    }

    @Test
    public void verifierOverrideWaitsForNormalInstall() throws Exception {
        AtomicInteger activeCallers = new AtomicInteger();
        AtomicInteger maximumActiveCallers = new AtomicInteger();
        CountDownLatch normalEntered = new CountDownLatch(1);
        CountDownLatch releaseNormal = new CountDownLatch(1);
        CountDownLatch overrideEntered = new CountDownLatch(1);

        Thread normal = new Thread(() -> runCoordinated(activeCallers, maximumActiveCallers,
                normalEntered, releaseNormal, false));
        Thread override = new Thread(() -> runCoordinated(activeCallers, maximumActiveCallers,
                overrideEntered, new CountDownLatch(0), true));
        normal.start();
        assertTrue(normalEntered.await(5, TimeUnit.SECONDS));
        override.start();
        assertFalse(overrideEntered.await(100, TimeUnit.MILLISECONDS));
        releaseNormal.countDown();
        normal.join(5000);
        override.join(5000);

        assertFalse(normal.isAlive());
        assertFalse(override.isAlive());
        assertEquals(1, maximumActiveCallers.get());
    }

    private static void runCoordinated(AtomicInteger activeCallers, AtomicInteger maximumActiveCallers,
                                       CountDownLatch entered, CountDownLatch release,
                                       boolean disableVerification) {
        PackageVerifierSettingsCoordinator.acquire(disableVerification);
        try {
            int active = activeCallers.incrementAndGet();
            maximumActiveCallers.accumulateAndGet(active, Math::max);
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                activeCallers.decrementAndGet();
            }
        } finally {
            PackageVerifierSettingsCoordinator.release(disableVerification);
        }
    }
}
