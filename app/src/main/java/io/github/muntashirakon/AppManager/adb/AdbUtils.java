// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;

import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;

public class AdbUtils {
    public static boolean isAdbAvailable(String host, int port) {
        try (AdbConnection ignored = AdbConnectionManager.connect(host, port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @NonNull
    public static SSLContext getSslContext(KeyPair keyPair) throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(new KeyManager[]{getKeyManager(keyPair)}, new TrustManager[]{getTrustManager()}, new SecureRandom());
        return sslContext;
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @NonNull
    private static KeyManager getKeyManager(KeyPair keyPair) {
        return new X509ExtendedKeyManager() {
            private final String alias = "key";

            @Override
            public String[] getClientAliases(String keyType, Principal[] issuers) {
                return null;
            }

            @Override
            public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
                for (String keyType : keyTypes) {
                    if (keyType.equals("RSA")) return alias;
                }
                return null;
            }

            @Override
            public String[] getServerAliases(String keyType, Principal[] issuers) {
                return null;
            }

            @Override
            public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                return null;
            }

            @Override
            public X509Certificate[] getCertificateChain(String alias) {
                if (this.alias.equals(alias)) {
                    return new X509Certificate[]{(X509Certificate) keyPair.getCertificate()};
                }
                return null;
            }

            @Override
            public PrivateKey getPrivateKey(String alias) {
                if (this.alias.equals(alias)) {
                    return keyPair.getPrivateKey();
                }
                return null;
            }
        };
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @NonNull
    private static TrustManager getTrustManager() {
        return new X509ExtendedTrustManager() {
            @SuppressLint("TrustAllX509TrustManager")
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
            }

            @SuppressLint("TrustAllX509TrustManager")
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
            }

            @SuppressLint("TrustAllX509TrustManager")
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            }

            @SuppressLint("TrustAllX509TrustManager")
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            }

            @SuppressLint("TrustAllX509TrustManager")
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @SuppressLint("TrustAllX509TrustManager")
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
}
