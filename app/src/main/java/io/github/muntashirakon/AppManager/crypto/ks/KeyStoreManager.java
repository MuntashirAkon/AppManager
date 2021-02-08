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

package io.github.muntashirakon.AppManager.crypto.ks;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.RemoteException;
import android.security.KeyPairGeneratorSpec;

import java.io.*;
import java.math.BigInteger;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Calendar;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.security.auth.x500.X500Principal;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public class KeyStoreManager {
    public static final String TAG = "KSManager";

    private static final String AM_KEYSTORE = "JKS";  // Java KeyStore
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String ANDROID_KEYSTORE_ALIAS = "am_secret";
    private static final String KEY_TYPE_RSA = "RSA";
    private static final String CIPHER_ALGO_RSA = "RSA/ECB/PKCS1Padding";
    private static final String CIPHER_PROVIDER = "RSA/ECB/PKCS1Padding";
    private static final String AM_KEYSTORE_FILE_NAME = "am_keystore.jks";  // Java KeyStore
    private static final String PREF_AM_KEYSTORE_PREFIX = "ks_";
    private static final String PREF_AM_KEYSTORE_PASS = "kspass";
    private static final File AM_KEYSTORE_FILE;
    @Nullable
    private static final KeyStore androidKeyStore;
    private static final SharedPreferences sharedPreferences;

    public static final String ACTION_KS_INTERACTION_BEGIN = BuildConfig.APPLICATION_ID + ".action.KS_INTERACTION_BEGIN";
    public static final String ACTION_KS_INTERACTION_END = BuildConfig.APPLICATION_ID + ".action.KS_INTERACTION_END";

    static {
        Context ctx = AppManager.getContext();
        AM_KEYSTORE_FILE = new File(ctx.getFilesDir(), AM_KEYSTORE_FILE_NAME);
        androidKeyStore = getAndroidKeyStore();
        sharedPreferences = ctx.getSharedPreferences("keystore", Context.MODE_PRIVATE);
    }

    @SuppressLint("StaticFieldLeak")
    private static KeyStoreManager INSTANCE;

    public static KeyStoreManager getInstance() throws Exception {
        if (INSTANCE == null) {
            INSTANCE = new KeyStoreManager(AppManager.getContext());
        }
        return INSTANCE;
    }

    private final Context context;
    private final KeyStore amKeyStore;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            if (intent.getAction() == null) return;
            switch (intent.getAction()) {
                case ACTION_KS_INTERACTION_BEGIN:
                    break;
                case ACTION_KS_INTERACTION_END:
                    releaseLock();
                    break;
            }
        }
    };

    private KeyStoreManager(@NonNull Context context)
            throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, RemoteException {
        this.context = context;
        amKeyStore = getAmKeyStore();
    }

    public void addItem(String alias, PrivateKey privateKey, X509Certificate certificate, @Nullable char[] password)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, RemoteException {
        // Check existence of this alias in system preferences, this should be unique
        String prefAlias = getPrefAlias(alias);
        if (sharedPreferences.contains(prefAlias) && amKeyStore.containsAlias(alias)) {
            throw new KeyStoreException("Alias " + alias + " exists.");
        }
        char[] realPassword = getAmKeyStorePassword();
        if (password == null) password = realPassword;
        amKeyStore.setKeyEntry(alias, privateKey, password, new X509Certificate[]{certificate});
        String encryptedPass = getEncryptedPassword(password);
        if (encryptedPass == null) {
            amKeyStore.deleteEntry(alias);
            throw new KeyStoreException("Password for " + alias + " could not be saved.");
        }
        sharedPreferences.edit().putString(prefAlias, encryptedPass).apply();
        try (OutputStream is = new FileOutputStream(AM_KEYSTORE_FILE)) {
            amKeyStore.store(is, realPassword);
            Utils.clearChars(realPassword);
            Utils.clearChars(password);
        }
    }

    public void removeItem(String alias) throws KeyStoreException {
        amKeyStore.deleteEntry(alias);
        String prefAlias = getPrefAlias(alias);
        if (sharedPreferences.contains(prefAlias)) {
            sharedPreferences.edit().remove(prefAlias).apply();
        }
    }

    public Key getKey(String alias, @Nullable char[] password)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
        if (password == null) {
            password = getAliasPassword(alias);
        }
        Key key = amKeyStore.getKey(alias, password);
        Utils.clearChars(password);
        return key;
    }

    /**
     * Get the certificate associated with the alias
     *
     * @param alias The given KeyStore alias
     * @return Certificate associated with the alias, usually {@link X509Certificate}
     */
    public Certificate getCertificate(String alias) throws KeyStoreException {
        return amKeyStore.getCertificate(alias);
    }

    public static void savePass(String prefAlias, char[] password) {
        sharedPreferences.edit().putString(prefAlias, getEncryptedPassword(password)).apply();
        Utils.clearChars(password);
    }

    @CheckResult
    @Nullable
    private static char[] getDecryptedPassword(@NonNull String encryptedPass) {
        try {
            if (androidKeyStore == null) throw new Exception("AndroidKeyStore wasn't initialized.");
            if (androidKeyStore.containsAlias(ANDROID_KEYSTORE_ALIAS)) {
                KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) androidKeyStore.getEntry(ANDROID_KEYSTORE_ALIAS, null);
                RSAPrivateKey privateKey = (RSAPrivateKey) privateKeyEntry.getPrivateKey();
                Cipher output = Cipher.getInstance(CIPHER_ALGO_RSA, CIPHER_PROVIDER);
                output.init(Cipher.DECRYPT_MODE, privateKey);
                try (ByteArrayInputStream bis = new ByteArrayInputStream(encryptedPass.getBytes());
                     CipherInputStream cipherInputStream = new CipherInputStream(bis, output)) {
                    // Use of String as an intermediary has security issues
                    return Utils.bytesToChars(IOUtils.readFully(cipherInputStream, -1, true));
                }
            }
        } catch (Exception e) {
            Log.e("KS", "Could not get decrypted password for " + encryptedPass, e);
        }
        return null;
    }

    @Nullable
    private static String getEncryptedPassword(@NonNull char[] realPass) {
        try {
            if (androidKeyStore == null) throw new Exception("AndroidKeyStore wasn't initialized.");
            if (androidKeyStore.containsAlias(ANDROID_KEYSTORE_ALIAS)) {
                KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) androidKeyStore.getEntry(ANDROID_KEYSTORE_ALIAS, null);
                RSAPublicKey publicKey = (RSAPublicKey) privateKeyEntry.getCertificate().getPublicKey();
                Cipher input = Cipher.getInstance(CIPHER_ALGO_RSA, CIPHER_PROVIDER);
                input.init(Cipher.ENCRYPT_MODE, publicKey);
                try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                     CipherOutputStream cipherOutputStream = new CipherOutputStream(bos, input)) {
                    cipherOutputStream.write(Utils.charsToBytes(realPass));
                    Utils.clearChars(realPass);
                    return new String(bos.toByteArray());
                }
            }
        } catch (Exception e) {
            Log.e("KS", "Could not get encrypted password", e);
        }
        return null;
    }

    /**
     * Get AndroidKeyStore. A new am_secret entry will be created if not already exists and all
     * stored passwords will be removed along with this.
     *
     * @return AndroidKeyStore
     */
    @Nullable
    private static KeyStore getAndroidKeyStore() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            // Create am_secret if not exists
            if (!keyStore.containsAlias(ANDROID_KEYSTORE_ALIAS)) {
                // Delete all items from shared pref
                sharedPreferences.edit().clear().apply();
                // Create new am_secret in AndroidKeyStore
                Calendar start = Calendar.getInstance();
                Calendar end = Calendar.getInstance();
                end.add(Calendar.YEAR, 20);
                KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(AppManager.getContext())
                        .setAlias(ANDROID_KEYSTORE_ALIAS)
                        .setSubject(new X500Principal("CN=App Manager"))
                        .setSerialNumber(BigInteger.ONE)
                        .setStartDate(start.getTime())
                        .setEndDate(end.getTime())
                        .build();
                KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_TYPE_RSA, ANDROID_KEYSTORE);
                generator.initialize(spec);
                generator.generateKeyPair();
            }
            return keyStore;
        } catch (Exception e) {
            Log.e(TAG, "Could not initialize AndroidKeyStore", e);
        }
        return null;
    }

    /**
     * Get App Manager's KeyStore. The user will be asked for a password if the KeyStore password
     * does not exist. If the KeyStore itself doesn't exist, it will initialize an empty KeyStore.
     *
     * @return App Manager's KeyStore
     */
    private KeyStore getAmKeyStore() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, RemoteException {
        KeyStore keyStore = KeyStore.getInstance(AM_KEYSTORE);
        if (AM_KEYSTORE_FILE.exists()) {
            try (InputStream is = new FileInputStream(AM_KEYSTORE_FILE)) {
                char[] realPassword = getAmKeyStorePassword();
                keyStore.load(is, realPassword);
                Utils.clearChars(realPassword);
            }
        } else {
            keyStore.load(null);
        }
        return keyStore;
    }

    /**
     * Get App Manager's KeyStore password. The password is stored in the shared preferences in an
     * encrypted format (the encryption/decryption is performed via AndroidKeyStore). In case the
     * user restores from the cache or accidentally deletes all entries from the shared pref, App
     * Manager will ask for KeyStore password again.
     *
     * @return KeyStore password in decrypted format. {@link Utils#clearChars(char[])} must be called when done.
     */
    @CheckResult
    @NonNull
    public char[] getAmKeyStorePassword() {
        String encryptedPass = sharedPreferences.getString(PREF_AM_KEYSTORE_PASS, null);
        if (encryptedPass == null) {
            IntentFilter filter = new IntentFilter(ACTION_KS_INTERACTION_BEGIN);
            filter.addAction(ACTION_KS_INTERACTION_END);
            context.registerReceiver(receiver, filter);
            Intent broadcastIntent = new Intent(ACTION_KS_INTERACTION_BEGIN);
            context.sendBroadcast(broadcastIntent);
            // Intent wrapper
            Intent intent = new Intent(context, KeyStoreActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(KeyStoreActivity.EXTRA_TYPE, KeyStoreActivity.TYPE_KS);
            intent.putExtra(KeyStoreActivity.EXTRA_ALIAS, PREF_AM_KEYSTORE_PASS);
            String ks = "AM KeyStore";
            // We don't need a delete intent since the time will be expired anyway
            NotificationCompat.Builder builder = NotificationUtils.getHighPriorityNotificationBuilder(context)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setTicker(ks)
                    .setContentTitle(ks)
                    .setSubText(ks)
                    .setContentText(context.getString(R.string.input_keystore_pass_msg));
            builder.setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT));
            NotificationUtils.displayHighPriorityNotification(context, builder.build());
            acquireLock();
            context.unregisterReceiver(receiver);
            // Check if the password is given
            return getAmKeyStorePassword();
        }
        char[] realPassword = getDecryptedPassword(encryptedPass);
        if (realPassword == null) {
            throw new RuntimeException("Could not decrypt encrypted password.");
        }
        return realPassword;
    }

    @CheckResult
    @NonNull
    private char[] getAliasPassword(@NonNull String alias) throws KeyStoreException {
        char[] password;
        String prefAlias = getPrefAlias(alias);
        if (sharedPreferences.contains(prefAlias)) {
            String encryptedPass = sharedPreferences.getString(prefAlias, null);
            if (encryptedPass == null) {
                throw new KeyStoreException("Stored pass is empty for alias " + alias);
            }
            password = getDecryptedPassword(encryptedPass);
            if (password == null) {
                throw new KeyStoreException("Decrypted pass is empty for alias " + alias);
            }
            return password;
        } else {
            IntentFilter filter = new IntentFilter(ACTION_KS_INTERACTION_BEGIN);
            filter.addAction(ACTION_KS_INTERACTION_END);
            context.registerReceiver(receiver, filter);
            Intent broadcastIntent = new Intent(ACTION_KS_INTERACTION_BEGIN);
            context.sendBroadcast(broadcastIntent);
            // Intent wrapper
            Intent intent = new Intent(context, KeyStoreActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(KeyStoreActivity.EXTRA_TYPE, KeyStoreActivity.TYPE_KS);
            intent.putExtra(KeyStoreActivity.EXTRA_ALIAS, PREF_AM_KEYSTORE_PASS);
            String ks = "AM KeyStore";
            // We don't need a delete intent since the time will be expired anyway
            NotificationCompat.Builder builder = NotificationUtils.getHighPriorityNotificationBuilder(context)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setTicker(ks)
                    .setContentTitle(ks)
                    .setSubText(ks)
                    .setContentText(context.getString(R.string.input_keystore_pass_msg));
            builder.setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT));
            NotificationUtils.displayHighPriorityNotification(context, builder.build());
            acquireLock();
            context.unregisterReceiver(receiver);
            return getAliasPassword(alias);
        }
    }

    /**
     * Get the formatted alias stored in the shared pref. Normally, a prefix {@link #PREF_AM_KEYSTORE_PREFIX}
     * is added to the alias.
     *
     * @param alias The given alias
     * @return Alias with {@link #PREF_AM_KEYSTORE_PREFIX}
     */
    @NonNull
    public static String getPrefAlias(@NonNull String alias) {
        return PREF_AM_KEYSTORE_PREFIX + alias;
    }

    private boolean lock;

    private void releaseLock() {
        lock = false;
    }

    private void acquireLock() {
        lock = true;
        try {
            int i = 0;
            while (lock) {
                if (i % 200 == 0) Log.i(TAG, "Waiting for user interaction");
                Thread.sleep(100);
                if (i > 1000)
                    break;
                i++;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForResult: interrupted", e);
            lock = false;
            Thread.currentThread().interrupt();
        }
    }
}
