// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.Manifest;
import android.content.Context;
import android.net.INetworkStatsService;
import android.net.NetworkCapabilities;
import android.net.NetworkTemplate;
import android.os.Build;
import android.os.RemoteException;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import io.github.muntashirakon.AppManager.ipc.ProxyBinder;

public class NetworkStatsManagerCompat {
    @NonNull
    @RequiresApi(Build.VERSION_CODES.M)
    @RequiresPermission(Manifest.permission.PACKAGE_USAGE_STATS)
    public static NetworkStatsCompat querySummary(int networkType, @Nullable String subscriberId, long startTime, long endTime)
            throws RemoteException, SecurityException {
        INetworkStatsService statsService = INetworkStatsService.Stub.asInterface(ProxyBinder.getService(Context.NETWORK_STATS_SERVICE));
        NetworkTemplate template = createTemplate(networkType, subscriberId);
        NetworkStatsCompat networkStats = new NetworkStatsCompat(template, 0, startTime, endTime, statsService);
        networkStats.startSummaryEnumeration();
        return networkStats;
    }

    @NonNull
    private static NetworkTemplate createTemplate(int networkType, @Nullable String subscriberId) {
        final NetworkTemplate template;
        switch (networkType) {
            case NetworkCapabilities.TRANSPORT_CELLULAR:
                template = subscriberId == null
                        ? NetworkTemplate.buildTemplateMobileWildcard()
                        : NetworkTemplate.buildTemplateMobileAll(subscriberId);
                break;
            case NetworkCapabilities.TRANSPORT_WIFI:
                template = TextUtils.isEmpty(subscriberId)
                        ? NetworkTemplate.buildTemplateWifiWildcard()
                        : new NetworkTemplate(NetworkTemplate.MATCH_WIFI, subscriberId, null);
                break;
            default:
                throw new IllegalArgumentException("Cannot create template for network type " + networkType + "'.");
        }
        return template;
    }
}
