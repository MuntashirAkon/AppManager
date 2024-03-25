// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ssaid;

import static io.github.muntashirakon.AppManager.ssaid.SettingsState.SYSTEM_PACKAGE_NAME;

import android.annotation.UserIdInt;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Process;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import aosp.libcore.util.HexEncoding;
import io.github.muntashirakon.AppManager.apk.signing.SignerInfo;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.io.Path;

@RequiresApi(Build.VERSION_CODES.O)
public class SsaidSettings {
    public static final String SSAID_USER_KEY = "userkey";

    @SuppressWarnings("FieldCanBeLocal")
    private final Object mLock = new Object();
    private final SettingsState mSettingsState;

    @WorkerThread
    public SsaidSettings(@UserIdInt int userId) throws IOException {
        HandlerThread thread = new HandlerThread("SSAID", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        int ssaidKey = SettingsStateV26.makeKey(SettingsState.SETTINGS_TYPE_SSAID, userId);
        Path ssaidLocation = OsEnvironment.getUserSystemDirectory(userId)
                .findFile("settings_ssaid.xml");
        if (!ssaidLocation.canRead()) {
            throw new IOException("settings_ssaid.xml is inaccessible.");
        }
        try {
            mSettingsState = new SettingsStateV26(mLock, ssaidLocation, ssaidKey,
                    SettingsState.MAX_BYTES_PER_APP_PACKAGE_UNLIMITED, thread.getLooper());
        } catch (IllegalStateException e) {
            throw new IOException(e);
        }
    }

    @Nullable
    public String getSsaid(@NonNull String packageName, int uid) {
        return mSettingsState.getSettingLocked(getName(packageName, uid)).getValue();
    }

    public boolean setSsaid(@NonNull String packageName, int uid, String ssaid) {
        try {
            PackageManagerCompat.forceStopPackage(packageName, UserHandleHidden.getUserId(uid));
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return mSettingsState.insertSettingLocked(getName(packageName, uid), ssaid, null, true, packageName);
    }

    private static String getName(@Nullable String packageName, int uid) {
        return Objects.equals(packageName, SYSTEM_PACKAGE_NAME) ? SSAID_USER_KEY : String.valueOf(uid);
    }

    @NonNull
    public static String generateSsaid(@NonNull String packageName) {
        boolean isUserKey = packageName.equals(SYSTEM_PACKAGE_NAME);
        // Generate a random key for each user used for creating a new ssaid.
        final byte[] keyBytes = new byte[isUserKey ? 32 : 8];
        final SecureRandom rand = new SecureRandom();
        rand.nextBytes(keyBytes);
        // Convert to string for storage in settings table.
        return HexEncoding.encodeToString(keyBytes, isUserKey /* upperCase */);
    }

    @NonNull
    public static String generateSsaid(@NonNull PackageInfo callingPkg) throws IOException {
        // Read the user's key from the ssaid table.
        SsaidSettings ssaidSettings = new SsaidSettings(UserHandleHidden.getUserId(callingPkg.applicationInfo.uid));
        SettingsState settingsState = ssaidSettings.mSettingsState;
        SettingsState.Setting userKeySetting = settingsState.getSettingLocked(SSAID_USER_KEY);
        if (userKeySetting == null || userKeySetting.isNull()
                || userKeySetting.getValue() == null) {
            // Lazy initialize and store the user key.
            String userKey = generateSsaid(SYSTEM_PACKAGE_NAME);
            settingsState.insertSettingLocked(SSAID_USER_KEY, userKey, null, true, SYSTEM_PACKAGE_NAME);
            userKeySetting = settingsState.getSettingLocked(SSAID_USER_KEY);
            if (userKeySetting == null || userKeySetting.isNull()
                    || userKeySetting.getValue() == null) {
                throw new IllegalStateException("User key not accessible");
            }
        }
        final String userKey = userKeySetting.getValue();
        if (userKey == null || userKey.length() % 2 != 0) {
            throw new IllegalStateException("User key invalid");
        }

        // Convert the user's key back to a byte array.
        final byte[] keyBytes = HexEncoding.decode(userKey);

        // Validate that the key is of expected length.
        // Keys are currently 32 bytes, but were once 16 bytes during Android O development.
        if (keyBytes.length != 16 && keyBytes.length != 32) {
            throw new IllegalStateException("User key invalid");
        }

        final Mac m;
        try {
            m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(keyBytes, m.getAlgorithm()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("Key is corrupted", e);
        }

        // Mac each of the developer signatures.
        SignerInfo signerInfo = PackageUtils.getSignerInfo(callingPkg, false);
        if (signerInfo != null) {
            X509Certificate[] signerCerts = signerInfo.getCurrentSignerCerts();
            if (signerCerts != null) {
                for (X509Certificate cert : signerCerts) {
                    try {
                        byte[] sig = cert.getEncoded();
                        m.update(getLengthPrefix(sig), 0, 4);
                        m.update(sig);
                    } catch (Exception ignore) {
                    }
                }
            }
        }

        // Convert result to a string for storage in settings table. Only want first 64 bits.
        return HexEncoding.encodeToString(m.doFinal(), false /* upperCase */).substring(0, 16);
    }

    @NonNull
    private static byte[] getLengthPrefix(@NonNull byte[] data) {
        return ByteBuffer.allocate(4).putInt(data.length).array();
    }
}
