// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import javax.security.auth.Destroyable;

import io.github.muntashirakon.AppManager.crypto.AESCrypto;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.crypto.spake2.Spake2Context;
import io.github.muntashirakon.crypto.spake2.Spake2Role;

@RequiresApi(Build.VERSION_CODES.R)
public class PairingAuthCtx implements Destroyable {
    // The following values are taken from the following source and are subjected to change
    // https://github.com/aosp-mirror/platform_system_core/blob/android-11.0.0_r1/adb/pairing_auth/pairing_auth.cpp
    private static final byte[] CLIENT_NAME = "adb pair client\u0000".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SERVER_NAME = "adb pair server\u0000".getBytes(StandardCharsets.UTF_8);

    // The following values are taken from the following source and are subjected to change
    // https://github.com/aosp-mirror/platform_system_core/blob/android-11.0.0_r1/adb/pairing_auth/aes_128_gcm.cpp
    private static final byte[] INFO = "adb pairing_auth aes-128-gcm key".getBytes(StandardCharsets.UTF_8);
    private static final int HKDF_KEY_LENGTH = 128 / 8;

    private final byte[] msg;
    private final Spake2Context spake2;
    private final byte[] secretKey = new byte[HKDF_KEY_LENGTH];
    private long dec_iv = 0;
    private long enc_iv = 0;
    private boolean isDestroyed = false;

    @Nullable
    public static PairingAuthCtx createAlice(byte[] password) {
        Spake2Context spake25519 = new Spake2Context(Spake2Role.Alice, CLIENT_NAME, SERVER_NAME);
        try {
            return new PairingAuthCtx(spake25519, password);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return null;
        }
    }

    @VisibleForTesting
    @Nullable
    public static PairingAuthCtx createBob(byte[] password) {
        Spake2Context spake25519 = new Spake2Context(Spake2Role.Bob, SERVER_NAME, CLIENT_NAME);
        try {
            return new PairingAuthCtx(spake25519, password);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return null;
        }
    }

    private PairingAuthCtx(Spake2Context spake25519, byte[] password)
            throws IllegalArgumentException, IllegalStateException {
        spake2 = spake25519;
        msg = spake2.generateMessage(password);
    }

    public byte[] getMsg() {
        return msg;
    }

    public boolean initCipher(byte[] theirMsg) throws IllegalArgumentException, IllegalStateException {
        if (isDestroyed) return false;
        byte[] keyMaterial = spake2.processMessage(theirMsg);
        if (keyMaterial == null) return false;
        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(keyMaterial, null, INFO));
        hkdf.generateBytes(secretKey, 0, secretKey.length);
        return true;
    }

    @Nullable
    public byte[] encrypt(@NonNull byte[] in) {
        return encryptDecrypt(true, in, ByteBuffer.allocate(AESCrypto.GCM_IV_LENGTH)
                .order(ByteOrder.LITTLE_ENDIAN).putLong(enc_iv++).array());
    }

    @Nullable
    public byte[] decrypt(@NonNull byte[] in) {
        return encryptDecrypt(false, in, ByteBuffer.allocate(AESCrypto.GCM_IV_LENGTH)
                .order(ByteOrder.LITTLE_ENDIAN).putLong(dec_iv++).array());
    }

    @Override
    public boolean isDestroyed() {
        return isDestroyed;
    }

    @Override
    public void destroy() {
        isDestroyed = true;
        Utils.clearBytes(secretKey);
        spake2.destroy();
    }

    @Nullable
    private byte[] encryptDecrypt(boolean forEncryption, @NonNull byte[] in, @NonNull byte[] iv) {
        if (isDestroyed) return null;
        AEADParameters spec = new AEADParameters(new KeyParameter(secretKey), secretKey.length * 8, iv);
        GCMBlockCipher cipher = new GCMBlockCipher(new AESEngine());
        cipher.init(forEncryption, spec);
        byte[] out = new byte[cipher.getOutputSize(in.length)];
        int newOffset = cipher.processBytes(in, 0, in.length, out, 0);
        try {
            cipher.doFinal(out, newOffset);
        } catch (InvalidCipherTextException e) {
            return null;
        }
        return out;
    }
}
