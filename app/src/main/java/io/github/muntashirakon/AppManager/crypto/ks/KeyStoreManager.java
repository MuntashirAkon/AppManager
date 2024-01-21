// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.Button;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

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
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.SecretKey;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.adb.AdbConnectionManager;
import io.github.muntashirakon.AppManager.apk.signing.Signer;
import io.github.muntashirakon.AppManager.crypto.AESCrypto;
import io.github.muntashirakon.AppManager.crypto.RSACrypto;
import io.github.muntashirakon.AppManager.crypto.RandomChar;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public class KeyStoreManager {
    public static final String TAG = "KSManager";

    public static final String AM_KEYSTORE_FILE_NAME = "am_keystore.bks";  // Java KeyStore
    public static final File AM_KEYSTORE_FILE;

    private static final String AM_KEYSTORE = "BKS";  // KeyStore.getDefaultType() == JKS
    private static final String PREF_AM_KEYSTORE_PREFIX = "ks_";
    private static final String PREF_AM_KEYSTORE_PASS = "kspass";
    private static final SharedPreferences sSharedPreferences;

    public static final String ACTION_KS_INTERACTION_BEGIN = BuildConfig.APPLICATION_ID + ".action.KS_INTERACTION_BEGIN";
    public static final String ACTION_KS_INTERACTION_END = BuildConfig.APPLICATION_ID + ".action.KS_INTERACTION_END";

    static {
        Context ctx = ContextUtils.getContext();
        AM_KEYSTORE_FILE = new File(ctx.getFilesDir(), AM_KEYSTORE_FILE_NAME);
        sSharedPreferences = ctx.getSharedPreferences("keystore", Context.MODE_PRIVATE);
    }

    @SuppressLint("StaticFieldLeak")
    private static KeyStoreManager sInstance;

    public static KeyStoreManager getInstance() throws Exception {
        if (sInstance == null) {
            sInstance = new KeyStoreManager();
        }
        return sInstance;
    }

    public static void reloadKeyStore() throws Exception {
        sInstance = new KeyStoreManager();
    }

    @NonNull
    public static AlertDialog generateAndDisplayKeyStorePassword(@NonNull FragmentActivity activity,
                                                                 @Nullable Runnable dismissListener) {
        char[] password = new char[30];
        RandomChar randomChar = new RandomChar();
        randomChar.nextChars(password);
        savePass(activity, PREF_AM_KEYSTORE_PASS, password);
        return displayKeyStorePassword(activity, password, dismissListener);
    }

    @NonNull
    public static AlertDialog displayKeyStorePassword(@NonNull FragmentActivity activity,
                                                      @NonNull char[] password,
                                                      @Nullable Runnable dismissListener) {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_keystore_password, null);
        TextInputEditText editText = view.findViewById(R.id.ks_pass);
        editText.setText(password, 0, password.length);
        return new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.keystore)
                .setView(view)
                .setNegativeButton(R.string.close, null)
                .setCancelable(false)
                .setOnDismissListener(dialog -> {
                    Utils.clearChars(password);
                    if (dismissListener != null) {
                        dismissListener.run();
                    }
                })
                .create();
    }

    @NonNull
    public static AlertDialog inputKeyStorePassword(@NonNull FragmentActivity activity,
                                                    @Nullable Runnable dismissListener) {
        AtomicBoolean dismiss = new AtomicBoolean(true);
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_keystore_password, null);
        TextInputEditText editText = view.findViewById(R.id.ks_pass);
        editText.setCursorVisible(true);
        view.findViewById(android.R.id.text1).setVisibility(View.GONE);
        TextInputLayout tv = view.findViewById(android.R.id.text2);
        tv.setHint(R.string.input_keystore_pass);
        AlertDialog alertDialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.keystore)
                .setView(view)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.delete, null)
                .setCancelable(false)
                .setOnDismissListener(dialog -> {
                    if (dismissListener != null && dismiss.get()) {
                        dismissListener.run();
                    }
                })
                .create();
        alertDialog.setOnShowListener(dialog -> {
            AlertDialog d = (AlertDialog) dialog;
            Button okButton = d.getButton(AlertDialog.BUTTON_POSITIVE);
            Button deleteButton = d.getButton(AlertDialog.BUTTON_NEGATIVE);
            okButton.setOnClickListener(v -> {
                Editable editable = editText.getText();
                if (TextUtils.isEmpty(editable)) {
                    editText.setError(activity.getString(R.string.keystore_pass_cannot_be_empty));
                    return;
                }
                //noinspection ConstantConditions
                char[] password = new char[editable.length()];
                editable.getChars(0, editable.length(), password, 0);
                savePass(activity, PREF_AM_KEYSTORE_PASS, password);
                Utils.clearChars(password);
                try {
                    getInstance();
                } catch (Exception e) {
                    // Couldn't use the password.
                    editText.setError(activity.getString(R.string.invalid_password));
                    return;
                }
                d.dismiss();
            });
            deleteButton.setOnClickListener(v -> {
                AM_KEYSTORE_FILE.delete();
                if (sSharedPreferences.contains(PREF_AM_KEYSTORE_PASS)) {
                    sSharedPreferences.edit().remove(PREF_AM_KEYSTORE_PASS).apply();
                }
                dismiss.set(false);
                generateAndDisplayKeyStorePassword(activity, dismissListener).show();
                d.dismiss();
            });
        });
        return alertDialog;
    }

    public static boolean hasKeyStore() {
        return AM_KEYSTORE_FILE.exists();
    }

    public static boolean hasKeyStorePassword() {
        try {
            reloadKeyStore();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Deprecated // To be removed in v3.0.0
    @WorkerThread
    public static void migrateKeyStore() throws Exception {
        // Reset all alias password
        String[] aliases = new String[]{
                AdbConnectionManager.ADB_KEY_ALIAS,
                Signer.SIGNING_KEY_ALIAS,
                RSACrypto.AES_KEY_ALIAS,
                AESCrypto.AES_KEY_ALIAS,
        };
        KeyStoreManager ksm = KeyStoreManager.getInstance();
        Key key;
        for (String alias : aliases) {
            try {
                if (!ksm.containsKey(alias)) continue;
                key = ksm.getKey(alias, null);
                ksm.removeItem(alias);
                if (key instanceof SecretKey) {
                    ksm.addSecretKey(alias, (SecretKey) key, false);
                    SecretKeyCompat.destroy((SecretKey) key);
                } else if (key instanceof PrivateKey) {
                    KeyPair keyPair = new KeyPair((PrivateKey) key, ksm.getCertificate(alias));
                    ksm.addKeyPair(alias, keyPair, false);
                    keyPair.destroy();
                } else throw new NoSuchAlgorithmException();
            } catch (Exception ignore) {
            }
        }
    }

    private final Context mContext;
    private final KeyStore mAmKeyStore;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
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

    private KeyStoreManager()
            throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        mContext = ContextUtils.getContext();
        mAmKeyStore = getAmKeyStore();
    }

    public void addKeyPair(String alias, @NonNull KeyPair keyPair, boolean isOverride)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        // Check existence of this alias in system preferences, this should be unique
        String prefAlias = getPrefAlias(alias);
        if (sSharedPreferences.contains(prefAlias) && mAmKeyStore.containsAlias(alias)) {
            Log.w(TAG, "Alias %s exists.", alias);
            if (isOverride) removeItemInternal(alias);
            else return;
        }
        char[] password = getAmKeyStorePassword();
        PrivateKey privateKey = keyPair.getPrivateKey();
        Certificate certificate = keyPair.getCertificate();
        mAmKeyStore.setKeyEntry(alias, privateKey, password, new Certificate[]{certificate});
        String encryptedPass = getEncryptedPassword(mContext, password);
        if (encryptedPass == null) {
            mAmKeyStore.deleteEntry(alias);
            throw new KeyStoreException("Password for " + alias + " could not be saved.");
        }
        sSharedPreferences.edit().putString(prefAlias, encryptedPass).apply();
        try (OutputStream is = new FileOutputStream(AM_KEYSTORE_FILE)) {
            mAmKeyStore.store(is, password);
            Utils.clearChars(password);
            Utils.clearChars(password);
        }
    }

    public void addSecretKey(String alias, @NonNull SecretKey secretKey, boolean isOverride)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        // Check existence of this alias in system preferences, this should be unique
        String prefAlias = getPrefAlias(alias);
        if (sSharedPreferences.contains(prefAlias) && mAmKeyStore.containsAlias(alias)) {
            if (!isOverride) throw new KeyStoreException("Alias " + alias + " exists.");
            else Log.w(TAG, "Alias %s exists.", alias);
        }
        char[] password = getAmKeyStorePassword();
        mAmKeyStore.setEntry(alias, new KeyStore.SecretKeyEntry(secretKey), new KeyStore.PasswordProtection(password));
        String encryptedPass = getEncryptedPassword(mContext, password);
        if (encryptedPass == null) {
            mAmKeyStore.deleteEntry(alias);
            throw new KeyStoreException("Password for " + alias + " could not be saved.");
        }
        sSharedPreferences.edit().putString(prefAlias, encryptedPass).apply();
        try (OutputStream is = new FileOutputStream(AM_KEYSTORE_FILE)) {
            mAmKeyStore.store(is, password);
        } finally {
            Utils.clearChars(password);
            Utils.clearChars(password);
        }
    }

    public void removeItem(String alias)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        removeItemInternal(alias);
        char[] realPassword = getAmKeyStorePassword();
        try (OutputStream is = new FileOutputStream(AM_KEYSTORE_FILE)) {
            mAmKeyStore.store(is, realPassword);
        } finally {
            Utils.clearChars(realPassword);
        }
    }

    private void removeItemInternal(String alias) throws KeyStoreException {
        mAmKeyStore.deleteEntry(alias);
        String prefAlias = getPrefAlias(alias);
        if (sSharedPreferences.contains(prefAlias)) {
            sSharedPreferences.edit().remove(prefAlias).apply();
        }
    }

    @Nullable
    private Key getKey(String alias)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
        char[] password = getAmKeyStorePassword();
        Key key = mAmKeyStore.getKey(alias, password);
        Utils.clearChars(password);
        return key;
    }

    /**
     * @deprecated Kept for migratory purposes only, deprecated since v2.6.3. To be removed in v3.0.0.
     */
    @Deprecated
    @Nullable
    private Key getKey(String alias, @Nullable char[] password)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
        if (password == null) {
            password = getAliasPassword(alias);
        }
        Key key = mAmKeyStore.getKey(alias, password);
        Utils.clearChars(password);
        return key;
    }

    @Nullable
    public SecretKey getSecretKey(String alias)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
        Key key = getKey(alias);
        if (key instanceof SecretKey) {
            return (SecretKey) key;
        }
        throw new KeyStoreException("The alias " + alias + " does not have a KeyPair.");
    }

    @Nullable
    public KeyPair getKeyPair(String alias)
            throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
        Key key = getKey(alias);
        if (key instanceof PrivateKey) {
            return new KeyPair((PrivateKey) key, getCertificate(alias));
        }
        throw new KeyStoreException("The alias " + alias + " does not have a KeyPair.");
    }

    @Nullable
    public KeyPair getKeyPairNoThrow(String alias) {
        try {
            return getKeyPair(alias);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean containsKey(String alias) throws KeyStoreException {
        return mAmKeyStore.containsAlias(alias);
    }

    /**
     * Get the certificate associated with the alias
     *
     * @param alias The given KeyStore alias
     * @return Certificate associated with the alias, usually {@link X509Certificate}
     */
    private Certificate getCertificate(String alias) throws KeyStoreException {
        return mAmKeyStore.getCertificate(alias);
    }

    /**
     * Save password in the Shared Preferences in encrypted form.
     *
     * @param prefAlias The alias after running {@link #getPrefAlias(String)}
     * @param password  The password for the alias. {@link Utils#clearChars(char[])} must be called when done.
     */
    static void savePass(@NonNull Context context, String prefAlias, char[] password) {
        sSharedPreferences.edit().putString(prefAlias, getEncryptedPassword(context, password)).apply();
    }

    /**
     * Get the password decrypted by Android KeyStore.
     *
     * @param encryptedPass Encrypted password (IV length + IV + password) in base 64 format
     * @return The password in decrypted form. {@link Utils#clearChars(char[])} must be called when done.
     */
    @CheckResult
    @Nullable
    private static char[] getDecryptedPassword(@NonNull Context context, @NonNull String encryptedPass) {
        try {
            byte[] encryptedBytes = Base64.decode(encryptedPass, Base64.NO_WRAP);
            return Utils.bytesToChars(CompatUtil.decryptData(context, encryptedBytes));
        } catch (Exception e) {
            Log.e("KS", "Could not get decrypted password for %s", e, encryptedPass);
        }
        return null;
    }

    /**
     * Get the password to be encrypted using Android KeyStore.
     *
     * @param realPass The password to be encrypted. {@link Utils#clearChars(char[])} must be called when done.
     * @return Encrypted password (IV length + IV + password) in base 64 format
     */
    @Nullable
    private static String getEncryptedPassword(@NonNull Context context, @NonNull char[] realPass) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            AesEncryptedData encryptedData = CompatUtil.getEncryptedData(Utils.charsToBytes(realPass), context);
            bos.write((byte) encryptedData.getIv().length);
            bos.write(encryptedData.getIv());
            bos.write(encryptedData.getEncryptedData());
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
        Log.w(TAG, "Using keystore %s", AM_KEYSTORE);
        char[] realPassword = getAmKeyStorePassword();
        try {
            if (AM_KEYSTORE_FILE.exists()) {
                try (InputStream is = new FileInputStream(AM_KEYSTORE_FILE)) {
                    keyStore.load(is, realPassword);
                }
            } else {
                keyStore.load(null);
            }
        } finally {
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
    public char[] getAmKeyStorePassword() throws KeyStoreException {
        String encryptedPass = sSharedPreferences.getString(PREF_AM_KEYSTORE_PASS, null);
        if (encryptedPass == null) {
            throw new KeyStoreException("No saved password for KeyStore.");
        }
        char[] realPassword = getDecryptedPassword(mContext, encryptedPass);
        if (realPassword == null) {
            throw new KeyStoreException("Could not decrypt encrypted password.");
        }
        return realPassword;
    }

    /**
     * @return Password for the given alias. {@link Utils#clearChars(char[])} must be called when done.
     * @deprecated Kept for migratory purposes only, deprecated since v2.6.3. To be removed in v3.0.0.
     */
    @Deprecated
    @CheckResult
    @NonNull
    private char[] getAliasPassword(@NonNull String alias) throws KeyStoreException {
        char[] password;
        String prefAlias = getPrefAlias(alias);
        if (sSharedPreferences.contains(prefAlias)) {
            String encryptedPass = sSharedPreferences.getString(prefAlias, null);
            if (encryptedPass == null) {
                throw new KeyStoreException("Stored pass is empty for alias " + alias);
            }
            password = getDecryptedPassword(mContext, encryptedPass);
            if (password == null) {
                throw new KeyStoreException("Decrypted pass is empty for alias " + alias);
            }
            return password;
        } else {
            IntentFilter filter = new IntentFilter(ACTION_KS_INTERACTION_BEGIN);
            filter.addAction(ACTION_KS_INTERACTION_END);
            ContextCompat.registerReceiver(mContext, mReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            Intent broadcastIntent = new Intent(ACTION_KS_INTERACTION_BEGIN);
            mContext.sendBroadcast(broadcastIntent);
            // Intent wrapper
            Intent intent = new Intent(mContext, KeyStoreActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(KeyStoreActivity.EXTRA_ALIAS, alias);
            String ks = "AM KeyStore";
            // We don't need a delete intent since the time will be expired anyway
            NotificationCompat.Builder builder = NotificationUtils.getHighPriorityNotificationBuilder(mContext)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.drawable.ic_default_notification)
                    .setTicker(ks)
                    .setContentTitle(ks)
                    .setSubText(ks)
                    .setContentText(mContext.getString(R.string.input_keystore_alias_pass_msg, alias));
            builder.setContentIntent(PendingIntentCompat.getActivity(mContext, 0, intent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT, false));
            NotificationUtils.displayHighPriorityNotification(mContext, builder.build());
            acquireLock();
            mContext.unregisterReceiver(mReceiver);
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
    static String getPrefAlias(@NonNull String alias) {
        return PREF_AM_KEYSTORE_PREFIX + alias;
    }

    private CountDownLatch mInteractionWatcher;

    private void releaseLock() {
        if (mInteractionWatcher != null) mInteractionWatcher.countDown();
    }

    private void acquireLock() {
        mInteractionWatcher = new CountDownLatch(1);
        try {
            mInteractionWatcher.await(100, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForResult: interrupted", e);
            Thread.currentThread().interrupt();
        }
    }
}
