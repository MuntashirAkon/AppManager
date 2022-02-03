// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.content.Context;
import android.os.Build;
import android.text.Editable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;
import androidx.fragment.app.FragmentActivity;

import com.android.internal.util.TextUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.adb.android.AdbMdns;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDropdownDialogBuilder;

public class AdbUtils {
    @WorkerThread
    @NonNull
    public static Pair<String, Integer> getLatestAdbDaemon(@NonNull Context context, long timeout, @NonNull TimeUnit unit)
            throws InterruptedException, IOException {
        AtomicInteger atomicPort = new AtomicInteger(-1);
        AtomicReference<String> atomicHostAddress = new AtomicReference<>(null);
        CountDownLatch resolveHostAndPort = new CountDownLatch(1);

        AdbMdns adbMdnsTcp = new AdbMdns(context, AdbMdns.SERVICE_TYPE_ADB, (hostAddress, port) -> {
            atomicHostAddress.set(hostAddress.getHostAddress());
            atomicPort.set(port);
            resolveHostAndPort.countDown();
        });
        adbMdnsTcp.start();

        AdbMdns adbMdnsTls;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            adbMdnsTls = new AdbMdns(context, AdbMdns.SERVICE_TYPE_TLS_CONNECT, (hostAddress, port) -> {
                atomicHostAddress.set(hostAddress.getHostAddress());
                atomicPort.set(port);
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

    @WorkerThread
    @NonNull
    public static Pair<String, Integer> getLatestAdbPairingDaemon(@NonNull Context context, long timeout, @NonNull TimeUnit unit)
            throws InterruptedException, IOException {
        AtomicInteger atomicPort = new AtomicInteger(-1);
        AtomicReference<String> atomicHostAddress = new AtomicReference<>(null);
        CountDownLatch resolveHostAndPort = new CountDownLatch(1);

        AdbMdns adbMdnsPairing = new AdbMdns(context, AdbMdns.SERVICE_TYPE_TLS_PAIRING, (hostAddress, port) -> {
            atomicHostAddress.set(hostAddress.getHostAddress());
            atomicPort.set(port);
            resolveHostAndPort.countDown();
        });
        adbMdnsPairing.start();

        try {
            if (!resolveHostAndPort.await(timeout, unit)) {
                throw new InterruptedException("Timed out while trying to find a valid host address and port");
            }
        } finally {
            adbMdnsPairing.stop();
        }

        String host = atomicHostAddress.get();
        int port = atomicPort.get();
        if (host == null || port == -1) {
            throw new IOException("Could not find any valid host address or port");
        }
        return new Pair<>(host, port);
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @UiThread
    public static void configureWirelessDebugging(FragmentActivity activity, AdbConnectionCallback callback) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.wireless_debugging)
                .setMessage(R.string.choose_what_to_do)
                .setCancelable(false)
                .setPositiveButton(R.string.adb_connect, (dialog1, which1) -> displayAdbConnect(activity, callback))
                .setNeutralButton(R.string.adb_pair, (dialog1, which1) -> {
                    TextInputDropdownDialogBuilder builder = new TextInputDropdownDialogBuilder(activity,
                            R.string.adb_wifi_paring_code);
                    builder.setTitle(R.string.wireless_debugging)
                            .setAuxiliaryInput(R.string.port_number, null, null, null, true)
                            .setPositiveButton(R.string.ok, (dialog, which, pairingCode, isChecked) -> {
                                Editable portString = builder.getAuxiliaryInput();
                                if (TextUtils.isEmpty(pairingCode)) {
                                    UIUtils.displayShortToast(R.string.port_number_pairing_code_empty);
                                    callback.pair(null, -1);
                                    return;
                                }
                                Objects.requireNonNull(pairingCode);
                                if (TextUtils.isEmpty(portString)) {
                                    UIUtils.displayShortToast(R.string.port_number_pairing_code_empty);
                                    callback.pair(pairingCode.toString(), -1);
                                    return;
                                }
                                int port;
                                try {
                                    port = Integer.decode(portString.toString().trim());
                                    callback.pair(pairingCode.toString(), port);
                                } catch (NumberFormatException e) {
                                    UIUtils.displayShortToast(R.string.port_number_invalid);
                                    callback.pair(pairingCode.toString(), -1);
                                }
                            })
                            .setNegativeButton(R.string.cancel, (dialog, which, inputText, isChecked) -> callback.pair(null, -1));
                    AlertDialog dialog = builder.create();
                    dialog.setCancelable(false);
                    dialog.show();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> callback.connect(-1))
                .show();
    }

    @UiThread
    public static void displayAdbConnect(FragmentActivity activity, AdbConnectionCallback callback) {
        AlertDialog alertDialog = new TextInputDialogBuilder(activity, R.string.port_number)
                .setTitle(R.string.wireless_debugging)
                .setInputText(String.valueOf(ServerConfig.getAdbPort()))
                .setHelperText(R.string.adb_connect_port_number_description)
                .setPositiveButton(R.string.ok, (dialog2, which2, inputText, isChecked) -> {
                    if (TextUtils.isEmpty(inputText)) {
                        UIUtils.displayShortToast(R.string.port_number_empty);
                        callback.connect(-1);
                        return;
                    }
                    try {
                        callback.connect(Integer.decode(inputText.toString().trim()));
                    } catch (NumberFormatException e) {
                        UIUtils.displayShortToast(R.string.port_number_invalid);
                        callback.connect(-1);
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which, inputText, isChecked) -> callback.connect(-1))
                .create();
        alertDialog.setCancelable(false);
        alertDialog.show();
    }

    public interface AdbConnectionCallback {
        @UiThread
        void connect(@IntRange(from = -1, to = 65535) int port);

        @UiThread
        void pair(@Nullable String pairingCode, @IntRange(from = -1, to = 65535) int port);
    }
}
