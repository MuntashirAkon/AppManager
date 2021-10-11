// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;

import java.nio.charset.StandardCharsets;

import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreUtils;

// Copyright 2017 Zheng Li
public class AdbConnectionManager {
    public static final String TAG = AdbConnectionManager.class.getSimpleName();

    public static final String ADB_KEY_ALIAS = "adb_rsa";

    @WorkerThread
    @NonNull
    private static KeyPair getAdbKeyPair() throws Exception {
        KeyStoreManager keyStoreManager = KeyStoreManager.getInstance();
        KeyPair keyPair = keyStoreManager.getKeyPairNoThrow(ADB_KEY_ALIAS);
        if (keyPair == null) {
            String subject = "CN=App Manager";
            keyPair = KeyStoreUtils.generateRSAKeyPair(subject, AdbCrypto.KEY_LENGTH_BITS,
                    System.currentTimeMillis() + 86400000);
            keyStoreManager.addKeyPair(ADB_KEY_ALIAS, keyPair, true);
        }
        return keyPair;
    }

    @WorkerThread
    @NonNull
    public static AdbConnection connect(@NonNull String host, int port) throws Exception {
        // Construct the AdbConnection object
        AdbConnection adbConnection = AdbConnection.create(host, port, getAdbKeyPair());
        // Connect to ADB
        adbConnection.connect();
        return adbConnection;
    }

    @RequiresApi(Build.VERSION_CODES.R)
    public static void pair(@NonNull String host, int port, @NonNull String pairingCode) throws Exception {
        KeyPair keyPair = getAdbKeyPair();
        try (PairingConnectionCtx pairingClient = new PairingConnectionCtx(host, port,
                pairingCode.getBytes(StandardCharsets.UTF_8), keyPair)) {
            pairingClient.start();
        }
    }
}
