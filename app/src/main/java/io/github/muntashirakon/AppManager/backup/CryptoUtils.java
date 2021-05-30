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

package io.github.muntashirakon.AppManager.backup;


import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.SecureRandom;

import io.github.muntashirakon.AppManager.crypto.AESCrypto;
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.crypto.DummyCrypto;
import io.github.muntashirakon.AppManager.crypto.OpenPGPCrypto;
import io.github.muntashirakon.AppManager.crypto.RSACrypto;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.utils.AppPref;

public class CryptoUtils {
    @StringDef(value = {
            MODE_NO_ENCRYPTION,
            MODE_AES,
            MODE_RSA,
            MODE_ECC,
            MODE_OPEN_PGP,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mode {
    }

    public static final String MODE_NO_ENCRYPTION = "none";
    public static final String MODE_AES = "aes";
    public static final String MODE_RSA = "rsa";
    public static final String MODE_ECC = "ecc";
    public static final String MODE_OPEN_PGP = "pgp";

    @Mode
    public static String getMode() {
        String currentMode = (String) AppPref.get(AppPref.PrefKey.PREF_ENCRYPTION_STR);
        if (isAvailable(currentMode)) return currentMode;
        // Fallback to no encryption if none of the modes are available.
        return MODE_NO_ENCRYPTION;
    }

    public static String getExtension(@NonNull @Mode String mode) {
        switch (mode) {
            case MODE_OPEN_PGP:
                return OpenPGPCrypto.GPG_EXT;
            case MODE_AES:
                return AESCrypto.AES_EXT;
            case MODE_RSA:
                return RSACrypto.RSA_EXT;
            case MODE_NO_ENCRYPTION:
            default:
                return "";
        }
    }

    /**
     * Get file name with appropriate extension
     */
    @NonNull
    public static String getAppropriateFilename(String filename, @NonNull @Mode String mode) {
        return filename + getExtension(mode);
    }

    @WorkerThread
    @NonNull
    public static Crypto getCrypto(@NonNull MetadataManager.Metadata metadata) throws CryptoException {
        switch (metadata.crypto) {
            case MODE_OPEN_PGP:
                return new OpenPGPCrypto(metadata.keyIds);
            case MODE_AES:
                return new AESCrypto(metadata.iv);
            case MODE_RSA:
                RSACrypto rsaCrypto = new RSACrypto(metadata.iv, metadata.aes);
                if (metadata.aes == null) {
                    metadata.aes = rsaCrypto.getEncryptedAesKey();
                }
                return rsaCrypto;
            case MODE_NO_ENCRYPTION:
            default:
                // Dummy crypto to generalise and return nonNull
                return new DummyCrypto();
        }
    }

    @WorkerThread
    public static void setupCrypto(@NonNull MetadataManager.Metadata metadata) {
        switch (metadata.crypto) {
            case MODE_OPEN_PGP:
                metadata.keyIds = (String) AppPref.get(AppPref.PrefKey.PREF_OPEN_PGP_USER_ID_STR);
                break;
            case MODE_AES:
            case MODE_RSA:
                SecureRandom random = new SecureRandom();
                metadata.iv = new byte[AESCrypto.GCM_IV_LENGTH];
                random.nextBytes(metadata.iv);
                break;
            case MODE_NO_ENCRYPTION:
            default:
        }
    }

    @WorkerThread
    public static boolean isAvailable(@NonNull @Mode String mode) {
        switch (mode) {
            case MODE_OPEN_PGP:
                String keyIds = (String) AppPref.get(AppPref.PrefKey.PREF_OPEN_PGP_USER_ID_STR);
                // FIXME(1/10/20): Check for the availability of the provider
                return !TextUtils.isEmpty(keyIds);
            case MODE_AES:
                try {
                    return KeyStoreManager.getInstance().containsKey(AESCrypto.AES_KEY_ALIAS);
                } catch (Exception e) {
                    return false;
                }
            case MODE_RSA:
                try {
                    return KeyStoreManager.getInstance().containsKey(RSACrypto.RSA_KEY_ALIAS);
                } catch (Exception e) {
                    return false;
                }
            case MODE_NO_ENCRYPTION:
                return true;
            default:
                return false;
        }
    }
}
