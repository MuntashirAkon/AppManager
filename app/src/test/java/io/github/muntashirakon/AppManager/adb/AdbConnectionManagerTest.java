// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.net.SocketTimeoutException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public class AdbConnectionManagerTest {
    @After
    public void tearDown() {
        AdbConnectionManager.PairingSession session = AdbConnectionManager.getPairingSession();
        if (session != null) {
            AdbConnectionManager.endPairingSession(session);
        }
    }

    @Test
    public void pairingResultsDoNotReplayIntoNextSession() throws Exception {
        AdbConnectionManager.PairingSession first = AdbConnectionManager.beginPairingSession();
        first.reportSuccess();
        assertTrue(first.await(1, TimeUnit.SECONDS).success);
        AdbConnectionManager.endPairingSession(first);

        AdbConnectionManager.PairingSession second = AdbConnectionManager.beginPairingSession();
        assertNull(second.await(1, TimeUnit.MILLISECONDS));
        assertSame(second, AdbConnectionManager.getPairingSession());
    }

    @Test
    public void replacingSessionCancelsOnlyPreviousSession() throws Exception {
        AdbConnectionManager.PairingSession first = AdbConnectionManager.beginPairingSession();
        AdbConnectionManager.PairingSession second = AdbConnectionManager.beginPairingSession();

        assertFalse(first.await(1, TimeUnit.SECONDS).success);
        assertNull(second.await(1, TimeUnit.MILLISECONDS));
    }

    @Test
    public void timedOutPairingAttemptIsCancelled() throws Exception {
        FutureTask<Boolean> pairingTask = new FutureTask<>(() -> true);

        try {
            AdbConnectionManager.awaitPairingAttempt(pairingTask, 1, TimeUnit.MILLISECONDS);
        } catch (SocketTimeoutException expected) {
            assertTrue(pairingTask.isCancelled());
            return;
        }
        throw new AssertionError("Expected the pairing attempt to time out");
    }
}
