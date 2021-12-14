// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ssaid;

import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Process;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import aosp.libcore.util.HexEncoding;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.io.ProxyFile;

import static io.github.muntashirakon.AppManager.ssaid.SettingsState.SYSTEM_PACKAGE_NAME;

@RequiresApi(Build.VERSION_CODES.O)
public class SsaidSettings {
    public static final String SSAID_USER_KEY = "userkey";

    @SuppressWarnings("FieldCanBeLocal")
    private final Object lock = new Object();
    private final int uid;
    private final String packageName;
    private final SettingsState settingsState;

    @WorkerThread
    public SsaidSettings(String packageName, int uid) throws IOException {
        this.uid = uid;
        this.packageName = packageName;
        HandlerThread thread = new HandlerThread("SSAID", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        int ssaidKey = SettingsStateV26.makeKey(SettingsState.SETTINGS_TYPE_SSAID, 0);
        File ssaidLocation = new ProxyFile(OsEnvironment.getUserSystemDirectory(UserHandleHidden.getUserId(uid)),
                "settings_ssaid.xml");
        try {
            if (!ssaidLocation.canRead()) {
                throw new IOException("settings_ssaid.xml is inaccessible.");
            }
        } catch (SecurityException e) {
            throw new IOException(e);
        }
        try {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S) {
                settingsState = new SettingsStateV31(lock, ssaidLocation, ssaidKey,
                        SettingsState.MAX_BYTES_PER_APP_PACKAGE_UNLIMITED, thread.getLooper());
            } else {
                settingsState = new SettingsStateV26(lock, ssaidLocation, ssaidKey,
                        SettingsState.MAX_BYTES_PER_APP_PACKAGE_UNLIMITED, thread.getLooper());
            }
        } catch (IllegalStateException e) {
            throw new IOException(e);
        }
    }

    @Nullable
    public String getSsaid() {
        return settingsState.getSettingLocked(getName()).getValue();
    }

    public boolean setSsaid(String ssaid) {
        try {
            PackageManagerCompat.forceStopPackage(packageName, UserHandleHidden.getUserId(uid));
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return settingsState.insertSettingLocked(getName(), ssaid, null, true, packageName);
    }

    private String getName() {
        return packageName.equals(SYSTEM_PACKAGE_NAME) ? SSAID_USER_KEY : String.valueOf(uid);
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
        SsaidSettings ssaidSettings = new SsaidSettings(callingPkg.packageName, callingPkg.applicationInfo.uid);
        SettingsState settingsState = ssaidSettings.settingsState;
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
        Signature[] signatures = PackageUtils.getSigningInfo(callingPkg, false);
        if (signatures != null) {
            for (Signature signature : signatures) {
                byte[] sig = signature.toByteArray();
                m.update(getLengthPrefix(sig), 0, 4);
                m.update(sig);
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
