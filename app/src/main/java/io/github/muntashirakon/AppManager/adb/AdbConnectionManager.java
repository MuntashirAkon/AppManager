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

import android.content.Context;
import android.util.Base64;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import androidx.annotation.NonNull;

public class AdbConnectionManager {
    public static final String TAG = AdbConnectionManager.class.getSimpleName();

    @NonNull
    private static AdbBase64 getAdbBase64() {
        return data -> Base64.encodeToString(data, Base64.DEFAULT);
    }

    @NonNull
    private static AdbCrypto setupCrypto(File publicKey, File privateKey)
            throws NoSuchAlgorithmException, IOException {
        AdbCrypto adbCrypto = null;
        // Try to load a key pair from the files
        if (publicKey.exists() && privateKey.exists()) {
            try {
                adbCrypto = AdbCrypto.loadAdbKeyPair(getAdbBase64(), privateKey, publicKey);
            } catch (IOException e) {
                // Failed to read from file
            } catch (InvalidKeySpecException e) {
                // Key spec was invalid
            } catch (NoSuchAlgorithmException e) {
                // RSA algorithm was unsupported with the crypo packages available
            }
        }

        if (adbCrypto == null) {
            // We couldn't load a key, so let's generate a new one
            adbCrypto = AdbCrypto.generateAdbKeyPair(getAdbBase64());
            // Save it
            adbCrypto.saveAdbKeyPair(privateKey, publicKey);
        }
        return adbCrypto;
    }

    @NonNull
    public static AdbConnection buildConnect(@NonNull Context context, String host, int port)
            throws IOException, NoSuchAlgorithmException {
        // Setup the crypto object required for the AdbConnection
        String path = context.getCacheDir().getAbsolutePath();
        File publicKey = new File(path, "pub.key");
        File privateKey = new File(path, "priv.key");
        AdbCrypto crypto = setupCrypto(publicKey, privateKey);
        // Connect the socket to the remote host
        Socket sock = new Socket(host, port);
        // Construct the AdbConnection object
        return AdbConnection.create(sock, crypto);
    }

    @NonNull
    public static AdbConnection connect(@NonNull Context context, String host, int port)
            throws IOException, NoSuchAlgorithmException, InterruptedException {
        AdbConnection adbConnection = buildConnect(context, host, port);
        adbConnection.connect();
        return adbConnection;
    }

    public static AdbStream openShell(Context context, String host, int port) throws Exception {
        AdbConnection connection = connect(context, host, port);
        return connection.open("shell:");
    }
}
