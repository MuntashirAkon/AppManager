// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.SettingsHidden;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;
import androidx.core.util.Pair;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.adb.android.AdbMdns;

public class AdbUtils {
    /**
     * Returns whether the device is connected to any Wi-Fi network. The Wi-Fi network does not
     * need to provide Internet access: local-only and captive-portal networks can still expose
     * wireless debugging.
     */
    public static boolean isWifiConnected(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        for (Network network : cm.getAllNetworks()) {
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return true;
            }
        }
        return false;
    }

    @WorkerThread
    @NonNull
    public static Pair<String, Integer> getLatestAdbDaemon(@NonNull Context context, long timeout, @NonNull TimeUnit unit)
            throws InterruptedException, IOException {
        if (!isAdbdRunning()) {
            throw new IOException("ADB daemon not running.");
        }
        AtomicInteger atomicPort = new AtomicInteger(-1);
        AtomicReference<String> atomicHostAddress = new AtomicReference<>(null);
        CountDownLatch resolveHostAndPort = new CountDownLatch(1);

        AdbMdns adbMdnsTcp = new AdbMdns(context, AdbMdns.SERVICE_TYPE_ADB, (hostAddress, port) -> {
            if (hostAddress != null) {
                atomicHostAddress.set(hostAddress.getHostAddress());
                atomicPort.set(port);
            }
            resolveHostAndPort.countDown();
        });
        AdbMdns adbMdnsTls = null;
        try {
            adbMdnsTcp.start();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                adbMdnsTls = new AdbMdns(context, AdbMdns.SERVICE_TYPE_TLS_CONNECT, (hostAddress, port) -> {
                    if (hostAddress != null) {
                        atomicHostAddress.set(hostAddress.getHostAddress());
                        atomicPort.set(port);
                    }
                    resolveHostAndPort.countDown();
                });
                adbMdnsTls.start();
            }
            if (!resolveHostAndPort.await(timeout, unit)) {
                throw new InterruptedException("Timed out while trying to find a valid host address and port");
            }
        } catch (RuntimeException e) {
            throw new IOException("Could not start ADB service discovery", e);
        } finally {
            stopDiscovery(adbMdnsTcp);
            stopDiscovery(adbMdnsTls);
        }

        String host = atomicHostAddress.get();
        int port = atomicPort.get();
        if (host == null || port == -1) {
            throw new IOException("Could not find any valid host address or port");
        }
        return new Pair<>(host, port);
    }

    private static void stopDiscovery(AdbMdns adbMdns) {
        if (adbMdns == null) {
            return;
        }
        try {
            adbMdns.stop();
        } catch (RuntimeException ignored) {
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    public static boolean enableWirelessDebugging(@NonNull Context context) {
        ContentResolver resolver = context.getContentResolver();
        boolean wirelessDebuggingEnabled = Settings.Global.getInt(resolver, SettingsHidden.Global.ADB_WIFI_ENABLED, 0) != 0;
        if (wirelessDebuggingEnabled && isAdbdRunning()) {
            return true;
        }
        if (!SelfPermissions.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)) {
            // No permission
            return false;
        }
        try {
            if (Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 0) {
                ContentValues contentValues = new ContentValues(2);
                contentValues.put("name", Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);
                contentValues.put("value", 1);
                resolver.insert(Uri.parse("content://settings/global"), contentValues);
            }
            if (!wirelessDebuggingEnabled) {
                ContentValues contentValues = new ContentValues(2);
                contentValues.put("name", SettingsHidden.Global.ADB_WIFI_ENABLED);
                contentValues.put("value", 1);
                resolver.insert(Uri.parse("content://settings/global"), contentValues);
            }
            // Try at most 3 times to figure out if something has altered
            for (int i = 0; i < 5; ++i) {
                if (isAdbdRunning()) {
                    return true;
                }
                SystemClock.sleep(500);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return false;
    }

    public static boolean isAdbdRunning() {
        // Default is set to “running” to avoid other issues
        return "running".equals(SystemProperties.get("init.svc.adbd", "running"));
    }

    public static int getAdbPortOrDefault() {
        return SystemProperties.getInt("service.adb.tcp.port", ServerConfig.DEFAULT_ADB_PORT);
    }

    public static boolean startAdb(int port) {
        return Runner.runCommand(new String[]{"setprop", "service.adb.tcp.port", String.valueOf(port)}).isSuccessful()
                && Runner.runCommand(new String[]{"setprop", "ctl.restart", "adbd"}).isSuccessful();
    }

    public static boolean stopAdb() {
        return Runner.runCommand(new String[]{"setprop", "service.adb.tcp.port", "-1"}).isSuccessful()
                && Runner.runCommand(new String[]{"setprop", "ctl.restart", "adbd"}).isSuccessful();
    }
}
