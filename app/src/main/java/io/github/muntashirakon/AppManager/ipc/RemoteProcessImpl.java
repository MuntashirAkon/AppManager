// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.IRemoteProcess;
import io.github.muntashirakon.io.IoUtils;

// Copyright 2020 Rikka
// Copyright 2023 Muntashir Al-Islam
public class RemoteProcessImpl extends IRemoteProcess.Stub {
    private final Process process;
    private ParcelFileDescriptor in;
    private OutputTransferThread outputTransferThread;

    public RemoteProcessImpl(Process process) {
        this.process = process;
    }

    @Override
    public ParcelFileDescriptor getOutputStream() {
        if (outputTransferThread == null) {
            outputTransferThread = new OutputTransferThread(process);
            outputTransferThread.start();
        }
        try {
            return outputTransferThread.getWriteSide();
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void closeOutputStream() {
        if (outputTransferThread != null) {
            outputTransferThread.interrupt();
        }
    }

    @Override
    public ParcelFileDescriptor getInputStream() {
        if (in == null) {
            try {
                InputTransferThread thread = new InputTransferThread(process, false);
                thread.start();
                in = thread.getReadSide();
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
        return in;
    }

    @Override
    public ParcelFileDescriptor getErrorStream() {
        try {
            InputTransferThread thread = new InputTransferThread(process, true);
            thread.start();
            return thread.getReadSide();
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int waitFor() {
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int exitValue() {
        return process.exitValue();
    }

    @Override
    public void destroy() {
        process.destroy();
    }

    @Override
    public boolean alive() {
        try {
            this.exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
        }
    }

    @Override
    public boolean waitForTimeout(long timeout, String unitName) {
        TimeUnit unit = TimeUnit.valueOf(unitName);
        long startTime = System.nanoTime();
        long rem = unit.toNanos(timeout);

        do {
            try {
                exitValue();
                return true;
            } catch (IllegalThreadStateException ex) {
                if (rem > 0) {
                    SystemClock.sleep(Math.min(TimeUnit.NANOSECONDS.toMillis(rem) + 1, 100));
                }
            }
            rem = unit.toNanos(timeout) - (System.nanoTime() - startTime);
        } while (rem > 0);
        return false;
    }

    private static class OutputTransferThread extends Thread {
        private final Process mProcess;
        private OutputStream processOutputStream;
        @Nullable
        private volatile ParcelFileDescriptor mWriteSide;
        @NonNull
        private CountDownLatch mWaitForWriteSide;

        private OutputTransferThread(Process process) {
            super();
            mProcess = process;
            mWaitForWriteSide = new CountDownLatch(1);
            setDaemon(true);
        }

        @NonNull
        public ParcelFileDescriptor getWriteSide() throws IOException, InterruptedException {
            mWaitForWriteSide.await();
            ParcelFileDescriptor writeSide = mWriteSide;
            if (writeSide == null) {
                throw new IOException("Could not get the write side");
            }
            return writeSide;
        }

        @Override
        public void run() {
            if (processOutputStream == null) {
                processOutputStream = mProcess.getOutputStream();
            }
            try {
                do {
                    if (mWaitForWriteSide.getCount() == 0) {
                        mWaitForWriteSide = new CountDownLatch(1);
                    }
                    ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                    ParcelFileDescriptor readSide = pipe[0];
                    mWriteSide = pipe[1];
                    mWaitForWriteSide.countDown();
                    try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(readSide)) {
                        byte[] buf = new byte[IoUtils.DEFAULT_BUFFER_SIZE];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            processOutputStream.write(buf, 0, len);
                        }
                    }
                    processOutputStream.flush();
                } while (!isInterrupted());
                processOutputStream.close();
            } catch (IOException e) {
                Log.e("FD", "IOException when writing to out", e);
                mWaitForWriteSide.countDown();
            }
        }
    }

    private static class InputTransferThread extends Thread {
        private final Process mProcess;
        private final boolean mErrorStream;
        @NonNull
        private final CountDownLatch mWaitForReadSide;
        @Nullable
        private volatile ParcelFileDescriptor mReadSide;

        InputTransferThread(Process process, boolean errorStream) {
            super();
            mProcess = process;
            mErrorStream = errorStream;
            mWaitForReadSide = new CountDownLatch(1);
            setDaemon(true);
        }

        @NonNull
        public ParcelFileDescriptor getReadSide() throws IOException, InterruptedException {
            mWaitForReadSide.await();
            ParcelFileDescriptor writeSide = mReadSide;
            if (writeSide == null) {
                throw new IOException("Could not get the write side");
            }
            return writeSide;
        }

        @Override
        public void run() {
            try {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                mReadSide = pipe[0];
                ParcelFileDescriptor writeSide = pipe[1];
                mWaitForReadSide.countDown();
                try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(writeSide);
                     InputStream in = mErrorStream ? mProcess.getErrorStream() : mProcess.getInputStream()) {
                    byte[] buf = new byte[IoUtils.DEFAULT_BUFFER_SIZE];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }
            } catch (IOException e) {
                Log.e("FD", "IOException when writing to out", e);
                mWaitForReadSide.countDown();
            }
        }
    }
}
