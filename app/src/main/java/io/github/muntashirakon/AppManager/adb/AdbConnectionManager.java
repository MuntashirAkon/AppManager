/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.adb;


import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreUtils;

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
        // Setup the crypto object required for the AdbConnection
        AdbCrypto crypto = AdbCrypto.loadAdbKeyPair(getAdbKeyPair());
        // Construct the AdbConnection object
        AdbConnection adbConnection = AdbConnection.create(host, port, crypto);
        // Connect to ADB
        adbConnection.connect();
        return adbConnection;
    }

    @WorkerThread
    @NonNull
    public static AdbStream openShell(String host, int port) throws Exception {
        AdbConnection connection = connect(host, port);
        return connection.open("shell:");
    }
}
