// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.adb;

import static io.github.muntashirakon.AppManager.backup.adb.Constants.BACKUP_FILE_HEADER_MAGIC;
import static io.github.muntashirakon.AppManager.backup.adb.Constants.BACKUP_FILE_VERSION;
import static io.github.muntashirakon.AppManager.backup.adb.Constants.ENCRYPTION_ALGORITHM_NAME;
import static io.github.muntashirakon.AppManager.backup.adb.Constants.PBKDF2_HASH_ROUNDS;
import static io.github.muntashirakon.AppManager.backup.adb.Constants.PBKDF2_KEY_SIZE;
import static io.github.muntashirakon.AppManager.backup.adb.Constants.PBKDF2_SALT_SIZE;
import static io.github.muntashirakon.AppManager.backup.adb.Constants.PBKDF_CURRENT;
import static io.github.muntashirakon.AppManager.backup.adb.Constants.PBKDF_FALLBACK;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import aosp.libcore.util.HexEncoding;

final class AndroidBackupHeader {
    // NOTE: (CWE-326) Vulnerable to padding oracle attacks, but there's no way to fix it as it's used by Android.
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";

    private final SecureRandom mRng = new SecureRandom();
    private int mBackupFileVersion;
    private boolean mCompress;
    @Nullable
    private final char[] mPassword;

    public AndroidBackupHeader(int backupFileVersion, boolean compress, @Nullable char[] password) {
        mBackupFileVersion = backupFileVersion;
        mCompress = compress;
        mPassword = password;
    }

    public AndroidBackupHeader(@Nullable char[] password) {
        mBackupFileVersion = Constants.getBackupFileVersionFromApi(Build.VERSION.SDK_INT);
        mCompress = true;
        mPassword = password;
    }

    @NonNull
    public InputStream read(@NonNull InputStream backupStream) throws Exception {
        // First, parse out the unencrypted/uncompressed header
        InputStream preCompressStream = backupStream;

        final int headerLen = BACKUP_FILE_HEADER_MAGIC.length();
        byte[] streamHeader = new byte[headerLen];
        readFullyOrThrow(backupStream, streamHeader);
        byte[] magicBytes = BACKUP_FILE_HEADER_MAGIC.getBytes(StandardCharsets.UTF_8);
        if (Arrays.equals(magicBytes, streamHeader)) {
            // okay, header looks good.  now parse out the rest of the fields.
            String s = readHeaderLine(backupStream);
            mBackupFileVersion = Integer.parseInt(s);
            if (mBackupFileVersion <= BACKUP_FILE_VERSION) {
                // okay, it's a version we recognize.  if it's version 1, we may need
                // to try two different PBKDF2 regimes to compare checksums.
                final boolean pbkdf2Fallback = (mBackupFileVersion == 1);

                s = readHeaderLine(backupStream);
                mCompress = (Integer.parseInt(s) != 0);
                s = readHeaderLine(backupStream);
                if (s.equals("none")) {
                    // no more header to parse; we're good to go
                } else if (mPassword != null && mPassword.length > 0) { // AES-256
                    preCompressStream = decodeAesHeaderAndInitialize(mPassword, s, pbkdf2Fallback, backupStream);
                } else {
                    throw new IOException("Archive is encrypted but no password given");
                }
            } else {
                throw new IOException("Wrong header version: " + s);
            }
        } else {
            throw new IOException("Didn't read the right header magic");
        }

        // okay, use the right stream layer based on compression
        return mCompress ? new InflaterInputStream(preCompressStream) : preCompressStream;
    }

    @NonNull
    public OutputStream write(@NonNull OutputStream backupStream) throws Exception {
        // Write the global file header.  All strings are UTF-8 encoded; lines end
        // with a '\n' byte.  Actual backup data begins immediately following the
        // final '\n'.
        //
        // line 1: "ANDROID BACKUP"
        // line 2: backup file format version, currently "5"
        // line 3: compressed?  "0" if not compressed, "1" if compressed.
        // line 4: name of encryption algorithm [currently only "none" or "AES-256"]
        //
        // When line 4 is not "none", then additional header data follows:
        //
        // line 5: user password salt [hex]
        // line 6: encryption key checksum salt [hex]
        // line 7: number of PBKDF2 rounds to use (same for user & encryption key) [decimal]
        // line 8: IV of the user key [hex]
        // line 9: encryption key blob [hex]
        //     IV of the encryption key, encryption key itself, encryption key checksum hash
        //
        // The encryption key checksum is the encryption key plus its checksum salt, run through
        // 10k rounds of PBKDF2.  This is used to verify that the user has supplied the
        // correct password for decrypting the archive:  the encryption key decrypted from
        // the archive using the user-supplied password is also run through PBKDF2 in
        // this way, and if the result does not match the checksum as stored in the
        // archive, then we know that the user-supplied password does not match the
        // archive's.
        StringBuilder headerbuf = new StringBuilder(1024);

        headerbuf.append(BACKUP_FILE_HEADER_MAGIC);
        headerbuf.append(mBackupFileVersion); // integer, no trailing \n
        headerbuf.append(mCompress ? "\n1\n" : "\n0\n");

        OutputStream finalOutput = backupStream;
        // Set up the encryption stage if appropriate, and emit the correct header
        if (mPassword != null) {
            finalOutput = emitAesBackupHeader(headerbuf, backupStream);
        } else {
            headerbuf.append("none\n");
        }

        byte[] header = headerbuf.toString().getBytes(StandardCharsets.UTF_8);
        backupStream.write(header);

        // Set up the compression stage feeding into the encryption stage (if any)
        if (mCompress) {
            Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
            finalOutput = new DeflaterOutputStream(finalOutput, deflater, true);
        }

        return finalOutput;
    }

    @NonNull
    private OutputStream emitAesBackupHeader(@NonNull StringBuilder headerbuf,
                                             @NonNull OutputStream ofstream) throws Exception {
        // User key will be used to encrypt the encryption key.
        byte[] newUserSalt = randomBytes(PBKDF2_SALT_SIZE);
        SecretKey userKey = buildCharArrayKey(PBKDF_CURRENT, mPassword, newUserSalt, PBKDF2_HASH_ROUNDS);

        // the encryption key is random for each backup
        byte[] encryptionKey = new byte[256 / 8];
        mRng.nextBytes(encryptionKey);
        byte[] checksumSalt = randomBytes(PBKDF2_SALT_SIZE);

        // primary encryption of the datastream with the encryption key
        Cipher c = Cipher.getInstance(TRANSFORMATION);
        SecretKeySpec encryptionKeySpec = new SecretKeySpec(encryptionKey, "AES");
        c.init(Cipher.ENCRYPT_MODE, encryptionKeySpec);
        OutputStream finalOutput = new CipherOutputStream(ofstream, c);

        // line 4: name of encryption algorithm
        headerbuf.append(ENCRYPTION_ALGORITHM_NAME);
        headerbuf.append('\n');
        // line 5: user password salt [hex]
        headerbuf.append(byteArrayToHex(newUserSalt));
        headerbuf.append('\n');
        // line 6: encryption key checksum salt [hex]
        headerbuf.append(byteArrayToHex(checksumSalt));
        headerbuf.append('\n');
        // line 7: number of PBKDF2 rounds used [decimal]
        headerbuf.append(PBKDF2_HASH_ROUNDS);
        headerbuf.append('\n');

        // line 8: IV of the user key [hex]
        Cipher mkC = Cipher.getInstance(TRANSFORMATION);
        mkC.init(Cipher.ENCRYPT_MODE, userKey);

        byte[] IV = mkC.getIV();
        headerbuf.append(byteArrayToHex(IV));
        headerbuf.append('\n');

        // line 9: encryption IV + key blob, encrypted by the user key [hex].  Blob format:
        //    [byte] IV length = Niv
        //    [array of Niv bytes] IV itself
        //    [byte] encryption key length = Nek
        //    [array of Nek bytes] encryption key itself
        //    [byte] encryption key checksum hash length = Nck
        //    [array of Nck bytes] encryption key checksum hash
        //
        // The checksum is the (encryption key + checksum salt), run through the
        // stated number of PBKDF2 rounds
        IV = c.getIV();
        byte[] mk = encryptionKeySpec.getEncoded();
        byte[] checksum = makeKeyChecksum(PBKDF_CURRENT,
                encryptionKeySpec.getEncoded(),
                checksumSalt, PBKDF2_HASH_ROUNDS);

        ByteArrayOutputStream blob = new ByteArrayOutputStream(IV.length + mk.length
                + checksum.length + 3);
        DataOutputStream mkOut = new DataOutputStream(blob);
        mkOut.writeByte(IV.length);
        mkOut.write(IV);
        mkOut.writeByte(mk.length);
        mkOut.write(mk);
        mkOut.writeByte(checksum.length);
        mkOut.write(checksum);
        mkOut.flush();
        byte[] encryptedMk = mkC.doFinal(blob.toByteArray());
        headerbuf.append(byteArrayToHex(encryptedMk));
        headerbuf.append('\n');

        return finalOutput;
    }

    @NonNull
    private static InputStream decodeAesHeaderAndInitialize(char[] decryptPassword,
                                                            @NonNull String encryptionName,
                                                            boolean pbkdf2Fallback,
                                                            @NonNull InputStream rawInStream) throws Exception {
        if (!encryptionName.equals(ENCRYPTION_ALGORITHM_NAME)) {
            throw new IOException("Unsupported encryption method: " + encryptionName);
        }

        String userSaltHex = readHeaderLine(rawInStream); // 5
        byte[] userSalt = hexToByteArray(userSaltHex);

        String ckSaltHex = readHeaderLine(rawInStream); // 6
        byte[] ckSalt = hexToByteArray(ckSaltHex);

        int rounds = Integer.parseInt(readHeaderLine(rawInStream)); // 7
        String userIvHex = readHeaderLine(rawInStream); // 8

        String encryptionKeyBlobHex = readHeaderLine(rawInStream); // 9

        // decrypt the encryption key blob
        try {
            return attemptEncryptionKeyDecryption(decryptPassword, PBKDF_CURRENT, userSalt,
                    ckSalt, rounds, userIvHex, encryptionKeyBlobHex, rawInStream);
        } catch (Exception e) {
            if (pbkdf2Fallback) {
                return attemptEncryptionKeyDecryption(decryptPassword, PBKDF_FALLBACK, userSalt,
                        ckSalt, rounds, userIvHex, encryptionKeyBlobHex, rawInStream);
            }
            throw e;
        }
    }

    @NonNull
    private static InputStream attemptEncryptionKeyDecryption(char[] decryptPassword, String algorithm, byte[] userSalt,
                                                              byte[] ckSalt, int rounds, String userIvHex,
                                                              String encryptionKeyBlobHex, InputStream rawInStream)
            throws Exception {
        InputStream result;
        Cipher c = Cipher.getInstance(TRANSFORMATION);
        SecretKey userKey = buildCharArrayKey(algorithm, decryptPassword, userSalt,
                rounds);
        byte[] IV = hexToByteArray(userIvHex);
        IvParameterSpec ivSpec = new IvParameterSpec(IV);
        c.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(userKey.getEncoded(), "AES"),
                ivSpec);
        byte[] mkCipher = hexToByteArray(encryptionKeyBlobHex);
        byte[] mkBlob = c.doFinal(mkCipher);

        // first, the encryption key IV
        int offset = 0;
        int len = mkBlob[offset++];
        IV = Arrays.copyOfRange(mkBlob, offset, offset + len);
        offset += len;
        // then the encryption key itself
        len = mkBlob[offset++];
        byte[] encryptionKey = Arrays.copyOfRange(mkBlob,
                offset, offset + len);
        offset += len;
        // and finally the encryption key checksum hash
        len = mkBlob[offset++];
        byte[] mkChecksum = Arrays.copyOfRange(mkBlob,
                offset, offset + len);

        // now validate the decrypted encryption key against the checksum
        byte[] calculatedCk = makeKeyChecksum(algorithm, encryptionKey, ckSalt,
                rounds);
        if (MessageDigest.isEqual(calculatedCk, mkChecksum)) {
            ivSpec = new IvParameterSpec(IV);
            c.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(encryptionKey, "AES"),
                    ivSpec);
            // Only if all of the above worked properly will 'result' be assigned
            result = new CipherInputStream(rawInStream, c);
        } else {
            throw new IOException("Incorrect password");
        }

        return result;
    }

    /**
     * Used for generating random salts or passwords.
     */
    public byte[] randomBytes(int bits) {
        byte[] array = new byte[bits / 8];
        mRng.nextBytes(array);
        return array;
    }

    @NonNull
    private static String readHeaderLine(@NonNull InputStream in) throws IOException {
        int c;
        StringBuilder buffer = new StringBuilder(80);
        while ((c = in.read()) >= 0) {
            if (c == '\n') {
                break;   // consume and discard the newlines
            }
            buffer.append((char) c);
        }
        return buffer.toString();
    }

    private static void readFullyOrThrow(InputStream in, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int bytesRead = in.read(buffer, offset, buffer.length - offset);
            if (bytesRead <= 0) {
                throw new IOException("Couldn't fully read data");
            }
            offset += bytesRead;
        }
    }

    /**
     * Generates {@link SecretKey} instance from given parameters and returns it's checksum.
     * <p>
     * Current implementation returns the key in its primary encoding format.
     *
     * @param algorithm - key generation algorithm.
     * @param pwBytes   - password.
     * @param salt      - salt.
     * @param rounds    - number of rounds to run in key generation.
     * @return Hex representation of the generated key, or null if generation failed.
     */
    @NonNull
    public static byte[] makeKeyChecksum(String algorithm, byte[] pwBytes, byte[] salt, int rounds)
            throws Exception {
        char[] mkAsChar = new char[pwBytes.length];
        for (int i = 0; i < pwBytes.length; i++) {
            mkAsChar[i] = (char) pwBytes[i];
        }

        Key checksum = buildCharArrayKey(algorithm, mkAsChar, salt, rounds);
        return checksum.getEncoded();
    }

    /**
     * Creates {@link SecretKey} instance from given parameters.
     *
     * @param algorithm key generation algorithm.
     * @param pwArray   password.
     * @param salt      salt.
     * @param rounds    number of rounds to run in key generation.
     * @return {@link SecretKey} instance or null in case of an error.
     */
    @NonNull
    private static SecretKey buildCharArrayKey(String algorithm, char[] pwArray, byte[] salt, int rounds)
            throws Exception {
        // FIXME: 18/2/23 May not work for backup file version 1
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(algorithm);
        KeySpec ks = new PBEKeySpec(pwArray, salt, rounds, PBKDF2_KEY_SIZE);
        return keyFactory.generateSecret(ks);
    }

    /**
     * Creates hex string representation of the byte array.
     */
    public static String byteArrayToHex(byte[] data) {
        return HexEncoding.encodeToString(data, true);
    }

    /**
     * Creates byte array from it's hex string representation.
     */
    public static byte[] hexToByteArray(String digits) {
        final int bytes = digits.length() / 2;
        if (2 * bytes != digits.length()) {
            throw new IllegalArgumentException("Hex string must have an even number of digits");
        }

        byte[] result = new byte[bytes];
        for (int i = 0; i < digits.length(); i += 2) {
            result[i / 2] = (byte) Integer.parseInt(digits.substring(i, i + 2), 16);
        }
        return result;
    }
}
