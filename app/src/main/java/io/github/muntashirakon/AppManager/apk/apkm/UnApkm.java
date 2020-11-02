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

package io.github.muntashirakon.AppManager.apk.apkm;

import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.interfaces.PwHash;
import com.goterl.lazycode.lazysodium.interfaces.SecretStream;
import com.sun.jna.NativeLong;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import androidx.annotation.NonNull;

public class UnApkm {
    public static final long MEM_LIMIT = 0x20000000;
    public static final byte[] PASSWORD = "#$%@#dfas4d00fFSDF9GSD56$^53$%7WRGF3dzzqasD!@".getBytes();

    private UnApkm() {
    }

    @NonNull
    private static byte[] getBytes(@NonNull InputStream i, int num) throws IOException {
        byte[] data = new byte[num];
        i.read(data, 0, data.length);
        return data;
    }

    private static int byteToInt(@NonNull byte[] b) {
        int i = 0, result = 0, shift = 0;

        while (i < b.length) {
            byte be = b[i];
            result |= (be & 0xff) << shift;
            shift += 8;
            i += 1;
        }

        return result;
    }

    static class Header {
        byte[] pwHashBytes, outputHash;
        long chunkSize;

        Header(byte[] pwHashBytes, byte[] outputHash, long chunkSize) {
            this.pwHashBytes = pwHashBytes;
            this.outputHash = outputHash;
            this.chunkSize = chunkSize;
        }
    }

    @NonNull
    public static Header processHeader(InputStream inputStream,
                                       LazySodiumAndroid lazySodium)
            throws IOException {
        return processHeader(inputStream, lazySodium, true);
    }

    @NonNull
    public static Header processHeader(InputStream inputStream,
                                       LazySodiumAndroid lazySodium,
                                       boolean expensiveOps)
            throws IOException {
        return processHeader(inputStream, lazySodium, expensiveOps, MEM_LIMIT);
    }

    @NonNull
    public static Header processHeader(InputStream inputStream,
                                       LazySodiumAndroid lazySodium,
                                       boolean expensiveOps,
                                       long upperMemLimit)
            throws IOException {
        getBytes(inputStream, 1); // skip

        byte alg = getBytes(inputStream, 1)[0];
        if (alg > 2 || alg < 1) {
            throw new IOException("incorrect algo");
        }

        PwHash.Alg algo = PwHash.Alg.valueOf(alg);

        long opsLimit = byteToInt(getBytes(inputStream, 8));
        int memLimit = byteToInt(getBytes(inputStream, 8));

        if (memLimit < 0 || memLimit > upperMemLimit) {
            throw new IOException("too much memory aaah");
        }

        byte[] en = getBytes(inputStream, 8);
        long chunkSize = byteToInt(en);

        byte[] salt = getBytes(inputStream, 16);
        byte[] pwHashBytes = getBytes(inputStream, 24);


        byte[] outputHash = new byte[32];
        if (expensiveOps) {
            lazySodium.cryptoPwHash(outputHash, 32, PASSWORD, PASSWORD.length, salt,
                    opsLimit, new NativeLong(memLimit), algo);
        }

        return new Header(pwHashBytes, outputHash, chunkSize);
    }

    @NonNull
    public static InputStream decryptStream(InputStream inputStream) throws IOException {
        LazySodiumAndroid lazySodium = new LazySodiumAndroid(new SodiumAndroid());
        Header h = processHeader(inputStream, lazySodium);
        return decryptStream(inputStream, h, lazySodium);
    }

    @NonNull
    public static InputStream decryptStream(final InputStream inputStream,
                                            final Header header,
                                            final LazySodiumAndroid lazySodium)
            throws IOException {
        final PipedInputStream pipedInputStream = new PipedInputStream();
        final PipedOutputStream pipedOutputStream = new PipedOutputStream();

        pipedInputStream.connect(pipedOutputStream);

        Thread pipeWriter = new Thread() {
            public void run() {
                try {
                    SecretStream.State state = new SecretStream.State();
                    lazySodium.cryptoSecretStreamInitPull(state, header.pwHashBytes, header.outputHash);

                    long chunkSizePlusPadding = header.chunkSize + 0x11;
                    byte[] cipherChunk = new byte[(int) chunkSizePlusPadding];

                    int bytesRead;

                    while ((bytesRead = inputStream.read(cipherChunk)) != -1) {
                        int tagSize = 1;

                        byte[] decryptedChunk = new byte[(int) header.chunkSize];
                        byte[] tag = new byte[tagSize];

                        boolean success = lazySodium.cryptoSecretStreamPull(state, decryptedChunk, tag, cipherChunk, bytesRead);

                        if (!success) {
                            throw new IOException("decrypto error");
                        }
                        pipedOutputStream.write(decryptedChunk);
                        Arrays.fill(cipherChunk, (byte) 0);
                    }
                } catch (IOException e) {
                    if (!"Pipe closed".equals(e.getMessage())) {
                        e.printStackTrace();
                    }
                } finally {
                    try {
                        pipedOutputStream.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        };

        pipeWriter.start();
        return pipedInputStream;
    }

    public static void decryptFile(@NonNull InputStream is, @NonNull OutputStream os) {
        try {
            InputStream toOut = decryptStream(is);

            // fix zip format if missing end signature
            ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os));
            ZipInputStream zipIn = new ZipInputStream(toOut);

            ZipEntry entry = zipIn.getNextEntry();

            while (entry != null) {
                zos.putNextEntry(new ZipEntry(entry.getName()));

                byte[] bytesIn = new byte[4096];
                int read;
                while ((read = zipIn.read(bytesIn)) != -1) {
                    zos.write(bytesIn, 0, read);
                }
                zos.closeEntry();
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
            zipIn.close();
            zos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
