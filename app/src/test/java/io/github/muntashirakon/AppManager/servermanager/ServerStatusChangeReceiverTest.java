// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class ServerStatusChangeReceiverTest {
    @Test
    public void serverWaitIsBounded() {
        long started = 1_000;

        assertFalse(ServerStatusChangeReceiver.hasServerStartTimedOut(started,
                started + TimeUnit.SECONDS.toMillis(29)));
        assertTrue(ServerStatusChangeReceiver.hasServerStartTimedOut(started,
                started + TimeUnit.SECONDS.toMillis(30)));
    }
}
