// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.Network;
import android.net.NetworkCapabilities;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowNetwork;
import org.robolectric.shadows.ShadowNetworkCapabilities;

import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.adb.AdbPairingService;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class WifiWaitServiceTest {
    private Application mApplication;
    private ShadowApplication mShadowApplication;

    @Before
    public void setUp() {
        mApplication = RuntimeEnvironment.getApplication();
        mShadowApplication = shadowOf(mApplication);
        while (mShadowApplication.getNextStoppedService() != null) {
            // Clear stop requests made by shared test setup.
        }
    }

    @Test
    public void localOnlyWifiIsAccepted() {
        NetworkCapabilities capabilities = ShadowNetworkCapabilities.newInstance();
        ShadowNetworkCapabilities shadowCapabilities = shadowOf(capabilities);
        shadowCapabilities.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        shadowCapabilities.removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        shadowCapabilities.removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

        assertTrue(WifiWaitService.isWifiNetwork(capabilities));
    }

    @Test
    public void cellularNetworkIsRejectedEvenWithInternet() {
        NetworkCapabilities capabilities = ShadowNetworkCapabilities.newInstance();
        ShadowNetworkCapabilities shadowCapabilities = shadowOf(capabilities);
        shadowCapabilities.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        shadowCapabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        assertFalse(WifiWaitService.isWifiNetwork(capabilities));
    }

    @Test
    public void discoveryFailuresAreRetryableButUserActionFailuresAreNot() {
        assertTrue(WifiWaitService.isRetryableStatus(Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED));
        assertTrue(WifiWaitService.isRetryableStatus(Ops.STATUS_FAILURE));
        assertFalse(WifiWaitService.isRetryableStatus(Ops.STATUS_ADB_PAIRING_REQUIRED));
        assertFalse(WifiWaitService.isRetryableStatus(Ops.STATUS_FAILURE_ADB_NEED_MORE_PERMS));
    }

    @Test
    public void retryableResultUsesWifiThatReplacedAttemptedNetwork() {
        Network attempted = ShadowNetwork.newInstance(101);
        Network replacement = ShadowNetwork.newInstance(102);

        assertTrue(WifiWaitService.shouldTryReplacementNetwork(attempted, replacement, true));
        assertFalse(WifiWaitService.shouldTryReplacementNetwork(attempted, attempted, true));
        assertFalse(WifiWaitService.shouldTryReplacementNetwork(attempted, replacement, false));
        assertFalse(WifiWaitService.shouldTryReplacementNetwork(attempted, null, true));
    }

    @Test
    public void changingAwayFromWirelessAdbStopsWaitService() {
        Ops.setMode(Ops.MODE_ADB_WIFI);
        assertEquals(null, mShadowApplication.getNextStoppedService());

        Ops.setMode(Ops.MODE_NO_ROOT);

        Intent stoppedService = mShadowApplication.getNextStoppedService();
        assertNotNull(stoppedService);
        assertEquals(new ComponentName(mApplication, WifiWaitService.class), stoppedService.getComponent());
    }

    @Test
    public void manifestUsesOnlySpecialUseForegroundServiceType() throws Exception {
        ComponentName component = new ComponentName(mApplication, WifiWaitService.class);
        ServiceInfo serviceInfo = mApplication.getPackageManager().getServiceInfo(
                component, PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA));

        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, serviceInfo.getForegroundServiceType());

        ComponentName pairingComponent = new ComponentName(mApplication, AdbPairingService.class);
        ServiceInfo pairingServiceInfo = mApplication.getPackageManager().getServiceInfo(
                pairingComponent, PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA));
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                pairingServiceInfo.getForegroundServiceType());
    }
}
