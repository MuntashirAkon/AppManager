// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreUtils;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;

public class AdbConnectionManager extends AbsAdbConnectionManager {
    public static final String TAG = AdbConnectionManager.class.getSimpleName();

    public static final String ADB_KEY_ALIAS = "adb_rsa";

    private static AdbConnectionManager sInstance;
    private static PairingSession sPairingSession;

    public static synchronized AdbConnectionManager getInstance() throws Exception {
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

    public static synchronized PairingSession beginPairingSession() {
        if (sPairingSession != null) {
            sPairingSession.cancel();
        }
        return sPairingSession = new PairingSession();
    }

    public static synchronized PairingSession getPairingSession() {
        return sPairingSession;
    }

    public static synchronized void endPairingSession(@NonNull PairingSession session) {
        if (sPairingSession == session) {
            sPairingSession = null;
        }
    }

    @WorkerThread
    public void pairAndReport(@NonNull PairingSession session, @NonNull String host, int port,
                              @NonNull String pairingCode) throws Exception {
        try {
            pair(host, port, pairingCode);
            session.reportSuccess();
        } catch (Exception e) {
            Log.w(TAG, "Pairing failed.", e);
            session.reportFailure(e);
            throw e;
        }
    }

    public static final class PairingResult {
        public final boolean success;
        public final Exception error;

        private PairingResult(boolean success, Exception error) {
            this.success = success;
            this.error = error;
        }
    }

    public static final class PairingSession {
        private final LinkedBlockingQueue<PairingResult> mResults = new LinkedBlockingQueue<>();

        public PairingResult await(long timeout, @NonNull TimeUnit unit) throws InterruptedException {
            return mResults.poll(timeout, unit);
        }

        public void reportSuccess() {
            mResults.offer(new PairingResult(true, null));
        }

        public void reportFailure(@NonNull Exception error) {
            mResults.offer(new PairingResult(false, error));
        }

        public void cancel() {
            reportFailure(new InterruptedException("Pairing was cancelled."));
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
