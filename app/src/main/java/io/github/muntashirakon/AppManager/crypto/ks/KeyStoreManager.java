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
import android.util.Base64;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public class KeyStoreManager {
    public static final String TAG = "KSManager";

    private static final String AM_KEYSTORE = "BKS";  // KeyStore.getDefaultType() == JKS
    private static final String AM_KEYSTORE_FILE_NAME = "am_keystore.bks";  // Java KeyStore
    private static final String PREF_AM_KEYSTORE_PREFIX = "ks_";
    private static final String PREF_AM_KEYSTORE_PASS = "kspass";
    private static final File AM_KEYSTORE_FILE;
    private static final SharedPreferences sharedPreferences;

    public static final String ACTION_KS_INTERACTION_BEGIN = BuildConfig.APPLICATION_ID + ".action.KS_INTERACTION_BEGIN";
    public static final String ACTION_KS_INTERACTION_END = BuildConfig.APPLICATION_ID + ".action.KS_INTERACTION_END";

    static {
        Context ctx = AppManager.getContext();
        AM_KEYSTORE_FILE = new File(ctx.getFilesDir(), AM_KEYSTORE_FILE_NAME);
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
            throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        this.context = context;
        amKeyStore = getAmKeyStore();
    }

    public void addKeyPair(String alias, @NonNull KeyPair keyPair, @Nullable char[] password, boolean isOverride)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        // Check existence of this alias in system preferences, this should be unique
        String prefAlias = getPrefAlias(alias);
        if (sharedPreferences.contains(prefAlias) && amKeyStore.containsAlias(alias)) {
            Log.w(TAG, "Alias " + alias + " exists.");
            if (isOverride) removeItemInternal(alias);
            else return;
        }
        char[] realPassword = getAmKeyStorePassword();
        if (password == null) password = realPassword;
        PrivateKey privateKey = keyPair.getPrivateKey();
        Certificate certificate = keyPair.getCertificate();
        amKeyStore.setKeyEntry(alias, privateKey, password, new Certificate[]{certificate});
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

    public void addSecretKey(String alias, @NonNull SecretKey secretKey, @Nullable char[] password, boolean isOverride)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        // Check existence of this alias in system preferences, this should be unique
        String prefAlias = getPrefAlias(alias);
        if (sharedPreferences.contains(prefAlias) && amKeyStore.containsAlias(alias)) {
            if (!isOverride) throw new KeyStoreException("Alias " + alias + " exists.");
            else Log.w(TAG, "Alias " + alias + " exists.");
        }
        char[] realPassword = getAmKeyStorePassword();
        if (password == null) password = realPassword;
        amKeyStore.setEntry(alias, new KeyStore.SecretKeyEntry(secretKey), new KeyStore.PasswordProtection(password));
        String encryptedPass = getEncryptedPassword(password);
        if (encryptedPass == null) {
            amKeyStore.deleteEntry(alias);
            throw new KeyStoreException("Password for " + alias + " could not be saved.");
        }
        sharedPreferences.edit().putString(prefAlias, encryptedPass).apply();
        try (OutputStream is = new FileOutputStream(AM_KEYSTORE_FILE)) {
            amKeyStore.store(is, realPassword);
        } finally {
            Utils.clearChars(realPassword);
            Utils.clearChars(password);
        }
    }

    public void removeItem(String alias)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        removeItemInternal(alias);
        char[] realPassword = getAmKeyStorePassword();
        try (OutputStream is = new FileOutputStream(AM_KEYSTORE_FILE)) {
            amKeyStore.store(is, realPassword);
        } finally {
            Utils.clearChars(realPassword);
        }
    }

    private void removeItemInternal(String alias) throws KeyStoreException {
        amKeyStore.deleteEntry(alias);
        String prefAlias = getPrefAlias(alias);
        if (sharedPreferences.contains(prefAlias)) {
            sharedPreferences.edit().remove(prefAlias).apply();
        }
    }

    @Nullable
    private Key getKey(String alias, @Nullable char[] password)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
        if (password == null) {
            password = getAliasPassword(alias);
        }
        Key key = amKeyStore.getKey(alias, password);
        Utils.clearChars(password);
        return key;
    }

    @Nullable
    public SecretKey getSecretKey(String alias, @Nullable char[] password)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
        Key key = getKey(alias, password);
        if (key instanceof SecretKey) {
            return (SecretKey) key;
        }
        throw new KeyStoreException("The alias " + alias + " does not have a KeyPair.");
    }

    @Nullable
    public KeyPair getKeyPair(String alias, @Nullable char[] password)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
        Key key = getKey(alias, password);
        if (key instanceof PrivateKey) {
            return new KeyPair((PrivateKey) key, getCertificate(alias));
        }
        throw new KeyStoreException("The alias " + alias + " does not have a KeyPair.");
    }

    public boolean containsKey(String alias) throws KeyStoreException {
        return amKeyStore.containsAlias(alias);
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
        byte[] encryptedBytes = Base64.decode(encryptedPass, Base64.NO_WRAP);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(encryptedBytes);
             CipherInputStream cipherInputStream = CompatUtil.createCipherInputStream(bis, AppManager.getContext())) {
            return Utils.bytesToChars(IOUtils.readFully(cipherInputStream, -1, true));
        } catch (Exception e) {
            Log.e("KS", "Could not get decrypted password for " + encryptedPass, e);
        }
        return null;
    }

    @Nullable
    private static String getEncryptedPassword(@NonNull char[] realPass) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             CipherOutputStream cipherOutputStream = CompatUtil.createCipherOutputStream(bos, AppManager.getContext())) {
            cipherOutputStream.write(Utils.charsToBytes(realPass));
            cipherOutputStream.close();
            return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e("KS", "Could not get encrypted password", e);
        }
        return null;
    }

    /**
     * Get App Manager's KeyStore. The user will be asked for a password if the KeyStore password
     * does not exist. If the KeyStore itself doesn't exist, it will initialize an empty KeyStore.
     *
     * @return App Manager's KeyStore
     */
    private KeyStore getAmKeyStore() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        KeyStore keyStore = KeyStore.getInstance(AM_KEYSTORE);
        Log.w(TAG, "Using keystore " + AM_KEYSTORE);
        if (AM_KEYSTORE_FILE.exists()) {
            try (InputStream is = new FileInputStream(AM_KEYSTORE_FILE)) {
                char[] realPassword = getAmKeyStorePassword();
                keyStore.load(is, realPassword);
                Utils.clearChars(realPassword);
            }
        } else {
            keyStore.load(null);
            char[] realPassword = getAmKeyStorePassword();
            Utils.clearChars(realPassword);
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
            context.sendBroadcast(new Intent(ACTION_KS_INTERACTION_BEGIN));
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
            intent.putExtra(KeyStoreActivity.EXTRA_TYPE, KeyStoreActivity.TYPE_ALIAS);
            intent.putExtra(KeyStoreActivity.EXTRA_ALIAS, alias);
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
                    .setContentText(context.getString(R.string.input_keystore_alias_pass_msg, alias));
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

    private CountDownLatch interactionWatcher;

    private void releaseLock() {
        if (interactionWatcher != null) interactionWatcher.countDown();
    }

    private void acquireLock() {
        interactionWatcher = new CountDownLatch(1);
        try {
            interactionWatcher.await(100, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForResult: interrupted", e);
            Thread.currentThread().interrupt();
        }
    }
}
