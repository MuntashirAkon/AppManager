/*
 * Copyright (c) 2021 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.utils;

import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Process;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;
import aosp.libcore.util.HexEncoding;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.io.ProxyFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import static io.github.muntashirakon.AppManager.utils.SettingsState.SETTINGS_TYPE_SSAID;

@RequiresApi(Build.VERSION_CODES.O)
public class SsaidSettings {
    public static final String SSAID_USER_KEY = "userkey";

    @SuppressWarnings("FieldCanBeLocal")
    private final Object lock = new Object();
    private final int uid;
    private final String packageName;
    private final SettingsState settingsState;

    @WorkerThread
    public SsaidSettings(String packageName, int uid) {
        this.uid = uid;
        this.packageName = packageName;
        HandlerThread thread = new HandlerThread("SSAID", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        int ssaidKey = SettingsState.makeKey(SETTINGS_TYPE_SSAID, 0);
        settingsState = new SettingsState(AppManager.getContext(), lock,
                new ProxyFile(OsEnvironment.getUserSystemDirectory(Users.getUserHandle(uid)),
                        "settings_ssaid.xml"), ssaidKey, SettingsState.MAX_BYTES_PER_APP_PACKAGE_UNLIMITED,
                thread.getLooper());
    }

    public String getSsaid() {
        return settingsState.getSettingLocked(getName()).getValue();
    }

    public boolean setSsaid(String ssaid) {
        return settingsState.insertSettingLocked(getName(), ssaid, null, true, packageName);
    }

    private String getName() {
        return packageName.equals(SettingsState.SYSTEM_PACKAGE_NAME) ? SSAID_USER_KEY : String.valueOf(uid);
    }

    @NonNull
    private static String generateUserKey() {
        // Generate a random key for each user used for creating a new ssaid.
        final byte[] keyBytes = new byte[32];
        final SecureRandom rand = new SecureRandom();
        rand.nextBytes(keyBytes);
        // Convert to string for storage in settings table.
        return HexEncoding.encodeToString(keyBytes, true /* upperCase */);
    }

    @NonNull
    public static String generateSsaid(@NonNull String packageName) {
        boolean isUserKey = packageName.equals(SettingsState.SYSTEM_PACKAGE_NAME);
        // Generate a random key for each user used for creating a new ssaid.
        final byte[] keyBytes = new byte[isUserKey ? 32 : 8];
        final SecureRandom rand = new SecureRandom();
        rand.nextBytes(keyBytes);
        // Convert to string for storage in settings table.
        return HexEncoding.encodeToString(keyBytes, isUserKey /* upperCase */);
    }

    @NonNull
    public static String generateSsaid(@NonNull PackageInfo callingPkg) {
        // Read the user's key from the ssaid table.
        SsaidSettings ssaidSettings = new SsaidSettings(callingPkg.packageName, callingPkg.applicationInfo.uid);
        SettingsState settingsState = ssaidSettings.settingsState;
        SettingsState.Setting userKeySetting = settingsState.getSettingLocked(SSAID_USER_KEY);
        if (userKeySetting == null || userKeySetting.isNull()
                || userKeySetting.getValue() == null) {
            // Lazy initialize and store the user key.
            String userKey = generateUserKey();
            settingsState.insertSettingLocked(SSAID_USER_KEY, userKey, null, true,
                    SettingsState.SYSTEM_PACKAGE_NAME);
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
