// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;


import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

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
        KeyPair keyPair = keyStoreManager.getKeyPairNoThrow(ADB_KEY_ALIAS, null);
        if (keyPair == null) {
            String subject = "CN=App Manager";
            keyPair = KeyStoreUtils.generateRSAKeyPair(subject, AdbCrypto.KEY_LENGTH_BITS,
                    System.currentTimeMillis() + 86400000);
            keyStoreManager.addKeyPair(ADB_KEY_ALIAS, keyPair, null, true);
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
}
