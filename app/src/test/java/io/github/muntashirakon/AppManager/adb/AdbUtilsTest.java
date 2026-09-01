// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowNetworkCapabilities;
import org.robolectric.shadows.ShadowNetwork;
import org.robolectric.shadows.ShadowNetworkInfo;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class AdbUtilsTest {
    @SuppressWarnings("deprecation")
    @Test
    public void localOnlyWifiIsFoundWhenItIsNotTheDefaultNetwork() {
        Context context = RuntimeEnvironment.getApplication();
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        ShadowConnectivityManager shadowCm = shadowOf(cm);

        Network wifi = ShadowNetwork.newInstance(101);
        NetworkInfo wifiInfo = ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI, 0, true, true);
        NetworkCapabilities wifiCapabilities = ShadowNetworkCapabilities.newInstance();
        ShadowNetworkCapabilities shadowWifiCapabilities = shadowOf(wifiCapabilities);
        shadowWifiCapabilities.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        shadowWifiCapabilities.removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        shadowWifiCapabilities.removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        shadowCm.addNetwork(wifi, wifiInfo);
        shadowCm.setNetworkCapabilities(wifi, wifiCapabilities);

        NetworkInfo cellular = ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_MOBILE, 0, true, true);
        shadowCm.setActiveNetworkInfo(cellular);

        assertTrue(AdbUtils.isWifiConnected(context));
    }
}
