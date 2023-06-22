// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.os.Build;

import androidx.annotation.NonNull;

import java.security.PrivateKey;
import java.security.cert.Certificate;

import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreUtils;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;

public class AdbConnectionManager extends AbsAdbConnectionManager {
    public static final String TAG = AdbConnectionManager.class.getSimpleName();

    public static final String ADB_KEY_ALIAS = "adb_rsa";

    private static AdbConnectionManager sInstance;

    public static AdbConnectionManager getInstance() throws Exception {
        if (sInstance == null) {
            sInstance = new AdbConnectionManager();
        }
        return sInstance;
    }

    @NonNull
    private final KeyPair mKeyPair;

    public AdbConnectionManager() throws Exception {
        setApi(Build.VERSION.SDK_INT);
        KeyStoreManager keyStoreManager = KeyStoreManager.getInstance();
        KeyPair keyPair = keyStoreManager.getKeyPairNoThrow(ADB_KEY_ALIAS);
        if (keyPair == null) {
            String subject = "CN=App Manager";
            keyPair = KeyStoreUtils.generateRSAKeyPair(subject, 2048, System.currentTimeMillis() + 86400000);
            keyStoreManager.addKeyPair(ADB_KEY_ALIAS, keyPair, true);
        }
        mKeyPair = keyPair;
    }

    @NonNull
    @Override
    protected PrivateKey getPrivateKey() {
        return mKeyPair.getPrivateKey();
    }

    @NonNull
    @Override
    protected Certificate getCertificate() {
        return mKeyPair.getCertificate();
    }

    @NonNull
    @Override
    protected String getDeviceName() {
        return "AppManager";
    }
}
