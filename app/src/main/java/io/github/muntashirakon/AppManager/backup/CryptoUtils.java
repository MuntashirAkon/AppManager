// SPDX-License-Identifier: GPL-3.0-or-later

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
import io.github.muntashirakon.AppManager.crypto.ECCCrypto;
import io.github.muntashirakon.AppManager.crypto.OpenPGPCrypto;
import io.github.muntashirakon.AppManager.crypto.RSACrypto;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

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
        String currentMode = Prefs.Encryption.getEncryptionMode();
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
            case MODE_ECC:
                return ECCCrypto.ECC_EXT;
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
                return new OpenPGPCrypto(ContextUtils.getContext(), metadata.keyIds);
            case MODE_AES: {
                AESCrypto aesCrypto = new AESCrypto(metadata.iv);
                if (metadata.version < 4) {
                    // Old backups use 32 bit MAC
                    aesCrypto.setMacSizeBits(AESCrypto.MAC_SIZE_BITS_OLD);
                }
                return aesCrypto;
            }
            case MODE_RSA: {
                RSACrypto rsaCrypto = new RSACrypto(metadata.iv, metadata.aes);
                if (metadata.version < 4) {
                    // Old backups use 32 bit MAC
                    rsaCrypto.setMacSizeBits(AESCrypto.MAC_SIZE_BITS_OLD);
                }
                if (metadata.aes == null) {
                    metadata.aes = rsaCrypto.getEncryptedAesKey();
                }
                return rsaCrypto;
            }
            case MODE_ECC: {
                ECCCrypto eccCrypto = new ECCCrypto(metadata.iv, metadata.aes);
                if (metadata.version < 4) {
                    // Old backups use 32 bit MAC
                    eccCrypto.setMacSizeBits(AESCrypto.MAC_SIZE_BITS_OLD);
                }
                if (metadata.aes == null) {
                    metadata.aes = eccCrypto.getEncryptedAesKey();
                }
                return eccCrypto;
            }
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
                metadata.keyIds = Prefs.Encryption.getOpenPgpKeyIds();
                break;
            case MODE_AES:
            case MODE_RSA:
            case MODE_ECC:
                SecureRandom random = new SecureRandom();
                metadata.iv = new byte[AESCrypto.GCM_IV_SIZE_BYTES];
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
                String keyIds = Prefs.Encryption.getOpenPgpKeyIds();
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
            case MODE_ECC:
                try {
                    return KeyStoreManager.getInstance().containsKey(ECCCrypto.ECC_KEY_ALIAS);
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
