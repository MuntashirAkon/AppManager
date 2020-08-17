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

import com.tananaev.adblib.AdbBase64;
import com.tananaev.adblib.AdbConnection;
import com.tananaev.adblib.AdbCrypto;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import androidx.annotation.NonNull;

public class AdbConnectionManager {
    public static final String TAG = "AdbConnMan";

    @NonNull
    private static AdbBase64 getAdbBase64() {
        return data -> Base64.encodeToString(data, Base64.DEFAULT);
    }

    @NonNull
    private static AdbCrypto setupCrypto(String publicKey, String privateKey)
            throws NoSuchAlgorithmException, IOException {
        File publicKeyFile = new File(publicKey);
        File privateKeyFile = new File(privateKey);
        AdbCrypto adbCrypto = null;

        // Try to load a key pair from the files
        if (publicKeyFile.exists() && privateKeyFile.exists()) {
            try {
                adbCrypto = AdbCrypto.loadAdbKeyPair(getAdbBase64(), privateKeyFile, publicKeyFile);
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
            adbCrypto.saveAdbKeyPair(privateKeyFile, publicKeyFile);
        }
        return adbCrypto;
    }

    @NonNull
    public static AdbConnection connect(@NonNull Context context, String host, int port)
            throws IOException, NoSuchAlgorithmException, InterruptedException {
        // Setup the crypto object required for the AdbConnection
        String path = context.getCacheDir().getAbsolutePath();
        String publicKey = path + File.separatorChar + "pub.key";
        String privateKey = path + File.separatorChar + "priv.key";
        AdbCrypto crypto = setupCrypto(publicKey, privateKey);

        // Connect the socket to the remote host
        Socket sock = new Socket(host, port);
        // Construct the AdbConnection object
        AdbConnection adbConnection = AdbConnection.create(sock, crypto);
        adbConnection.connect();
        return adbConnection;
    }
}
