// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.utils;

import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.github.muntashirakon.io.IoUtils;

// Copyright 2013 Florian Schmaus
public class ParcelFileDescriptorUtil {

    @NonNull
    public static ParcelFileDescriptor pipeFrom(@NonNull InputStream inputStream)
            throws IOException {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        ParcelFileDescriptor readSide = pipe[0];
        ParcelFileDescriptor writeSide = pipe[1];

        new TransferThread(inputStream, new ParcelFileDescriptor.AutoCloseOutputStream(writeSide))
                .start();

        return readSide;
    }


    @NonNull
    public static TransferThread pipeTo(@NonNull OutputStream outputStream,
                                        @NonNull ParcelFileDescriptor output) {
        TransferThread t = new TransferThread(new ParcelFileDescriptor.AutoCloseInputStream(output), outputStream);
        t.start();
        return t;
    }

    @NonNull
    public static ParcelFileDescriptor pipeTo(@NonNull OutputStream outputStream)
            throws IOException {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        ParcelFileDescriptor readSide = pipe[0];
        ParcelFileDescriptor writeSide = pipe[1];

        new TransferThread(new ParcelFileDescriptor.AutoCloseInputStream(readSide), outputStream)
                .start();

        return writeSide;
    }


    static class TransferThread extends Thread {
        private final InputStream mIn;
        private final OutputStream mOut;

        TransferThread(InputStream in, OutputStream out) {
            super("IPC Transfer Thread");
            mIn = in;
            mOut = out;
            setDaemon(true);
        }

        @Override
        public void run() {
            byte[] buf = new byte[IoUtils.DEFAULT_BUFFER_SIZE];
            int len;

            try {
                while ((len = mIn.read(buf)) > 0) {
                    mOut.write(buf, 0, len);
                }
            } catch (IOException e) {
                Log.e("FD", "IOException when writing to out", e);
            } finally {
                try {
                    mIn.close();
                } catch (IOException ignored) {
                }
                try {
                    mOut.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

}