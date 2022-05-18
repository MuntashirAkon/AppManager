// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Pair;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.adb.android.AdbMdns;

public class AdbUtils {
    @WorkerThread
    @NonNull
    public static Pair<String, Integer> getLatestAdbDaemon(@NonNull Context context, long timeout, @NonNull TimeUnit unit)
            throws InterruptedException, IOException {
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
        adbMdnsTcp.start();

        AdbMdns adbMdnsTls;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            adbMdnsTls = new AdbMdns(context, AdbMdns.SERVICE_TYPE_TLS_CONNECT, (hostAddress, port) -> {
                if (hostAddress != null) {
                    atomicHostAddress.set(hostAddress.getHostAddress());
                    atomicPort.set(port);
                }
                resolveHostAndPort.countDown();
            });
            adbMdnsTls.start();
        } else adbMdnsTls = null;

        try {
            if (!resolveHostAndPort.await(timeout, unit)) {
                throw new InterruptedException("Timed out while trying to find a valid host address and port");
            }
        } finally {
            adbMdnsTcp.stop();
            if (adbMdnsTls != null) {
                adbMdnsTls.stop();
            }
        }

        String host = atomicHostAddress.get();
        int port = atomicPort.get();
        if (host == null || port == -1) {
            throw new IOException("Could not find any valid host address or port");
        }
        return new Pair<>(host, port);
    }
}
