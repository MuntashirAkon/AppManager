// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.security.PrivateKey;
import java.security.cert.Certificate;

import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreUtils;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
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
    private final MutableLiveData<Exception> mPairingObserver = new MutableLiveData<>();

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

    public LiveData<Exception> getPairingObserver() {
        return mPairingObserver;
    }

    @WorkerThread
    public void pairLiveData(@NonNull String host, int port, @NonNull String pairingCode) throws Exception {
        try {
            ThreadUtils.ensureWorkerThread();
            pair(host, port, pairingCode);
            mPairingObserver.postValue(null);
        } catch (Exception e) {
            Log.w(TAG, "Pairing failed.", e);
            mPairingObserver.postValue(e);
            throw e;
        }
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
