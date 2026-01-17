// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.crypto.AESCrypto;
import io.github.muntashirakon.AppManager.crypto.Crypto;
import io.github.muntashirakon.AppManager.crypto.CryptoException;
import io.github.muntashirakon.AppManager.crypto.DummyCrypto;
import io.github.muntashirakon.AppManager.crypto.ECCCrypto;
import io.github.muntashirakon.AppManager.crypto.OpenPGPCrypto;
import io.github.muntashirakon.AppManager.crypto.RSACrypto;
import io.github.muntashirakon.AppManager.crypto.ks.CompatUtil;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

public class BackupCryptSetupHelper {
    @NonNull
    @CryptoUtils.Mode
    public final String mode;
    public final int version;
    @NonNull
    public final Crypto crypto;
    private String keyIds;
    private byte[] aes;
    private byte[] iv;

    public BackupCryptSetupHelper(@NonNull String mode, int version) throws CryptoException {
        this.mode = mode;
        this.version = version;
        this.crypto = setup();
    }

    @Nullable
    public String getKeyIds() {
        return keyIds;
    }

    @Nullable
    public byte[] getAes() {
        return aes;
    }

    @Nullable
    public byte[] getIv() {
        return iv;
    }

    @NonNull
    private Crypto setup() throws CryptoException {
        switch (mode) {
            case CryptoUtils.MODE_OPEN_PGP:
                keyIds = Prefs.Encryption.getOpenPgpKeyIds();
                return new OpenPGPCrypto(ContextUtils.getContext(), keyIds);
            case CryptoUtils.MODE_AES: {
                iv = generateIv();
                AESCrypto aesCrypto = new AESCrypto(iv);
                if (version < 4) {
                    // Old backups use 32 bit MAC
                    aesCrypto.setMacSizeBits(AESCrypto.MAC_SIZE_BITS_OLD);
                }
                return aesCrypto;
            }
            case CryptoUtils.MODE_RSA: {
                iv = generateIv();
                RSACrypto rsaCrypto = new RSACrypto(iv, null);
                if (version < 4) {
                    // Old backups use 32 bit MAC
                    rsaCrypto.setMacSizeBits(AESCrypto.MAC_SIZE_BITS_OLD);
                }
                aes = rsaCrypto.getEncryptedAesKey();
                return rsaCrypto;
            }
            case CryptoUtils.MODE_ECC: {
                iv = generateIv();
                ECCCrypto eccCrypto = new ECCCrypto(iv, null);
                if (version < 4) {
                    // Old backups use 32 bit MAC
                    eccCrypto.setMacSizeBits(AESCrypto.MAC_SIZE_BITS_OLD);
                }
                aes = eccCrypto.getEncryptedAesKey();
                return eccCrypto;
            }
            case CryptoUtils.MODE_NO_ENCRYPTION:
            default:
                return new DummyCrypto();
        }
    }

    @NonNull
    private static byte[] generateIv() {
        byte[] iv = new byte[AESCrypto.GCM_IV_SIZE_BYTES];
        CompatUtil.getPrng().nextBytes(iv);
        return iv;
    }
}
