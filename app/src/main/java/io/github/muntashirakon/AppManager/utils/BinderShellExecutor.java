// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.io.Path;

@RequiresApi(Build.VERSION_CODES.N)
public class BinderShellExecutor {
    public static class ShellResult {
        private int resultCode;
        private String stdout;
        private String stderr;

        private ShellResult() {
        }

        public int getResultCode() {
            return resultCode;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }
    }

    public static ShellResult execute(@NonNull IBinder binder, @NonNull String[] command,
                                      @Nullable File input) throws IOException {
        if (input == null) {
            return execute(binder, command);
        }
        try (FileInputStream out = new FileInputStream(input)) {
            return execute(binder, command, out);
        }
    }

    public static ShellResult execute(@NonNull IBinder binder, @NonNull String[] command,
                                      @Nullable Path input) throws IOException {
        if (input == null) {
            return execute(binder, command);
        }
        try (InputStream os = input.openInputStream()) {
            return execute(binder, command, os);
        }
    }

    public static ShellResult execute(@NonNull IBinder binder, @NonNull String[] command,
                                      @Nullable FileInputStream input) throws IOException {
        if (input == null) {
            return execute(binder, command);
        }
        return execute(binder, command, input.getFD());
    }

    public static ShellResult execute(@NonNull IBinder binder, @NonNull String[] command, @Nullable InputStream input) throws IOException {
        if (input == null) {
            return execute(binder, command);
        }
        ParcelFileDescriptor inputFd = ParcelFileDescriptorUtil.pipeFrom(input);
        return execute(binder, command, inputFd.getFileDescriptor());
    }

    public static ShellResult execute(@NonNull IBinder binder, @NonNull String[] command,
                                      @Nullable FileDescriptor input) throws IOException {
        if (input == null) {
            return execute(binder, command);
        }
        return executeInternal(binder, command, input);
    }

    public static ShellResult execute(@NonNull IBinder binder, @NonNull String[] command)
            throws IOException {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        ParcelFileDescriptor readSide = pipe[0];
        ParcelFileDescriptor writeSide = pipe[1];
        return executeInternal(binder, command, readSide.getFileDescriptor());
    }

    private static ShellResult executeInternal(@NonNull IBinder binder, @NonNull String[] command,
                                               @NonNull FileDescriptor in) throws IOException {
        try (ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
             ByteArrayOutputStream stderrStream = new ByteArrayOutputStream()) {
            ParcelFileDescriptor stdoutFd = ParcelFileDescriptorUtil.pipeTo(stdoutStream);
            ParcelFileDescriptor stderrFd = ParcelFileDescriptorUtil.pipeTo(stderrStream);
            AtomicInteger atomicResultCode = new AtomicInteger(-1);
            CountDownLatch sem = new CountDownLatch(1);
            ProxyBinder.shellCommand(binder, in, stdoutFd.getFileDescriptor(), stderrFd.getFileDescriptor(), command, null, new ResultReceiver(ThreadUtils.getUiThreadHandler()) {
                @Override
                protected void onReceiveResult(int resultCode, Bundle resultData) {
                    atomicResultCode.set(resultCode);
                    sem.countDown();
                }
            });
            try {
                sem.await();
            } catch (InterruptedException ignore) {
            }
            int resultCode = atomicResultCode.get();
            if (resultCode == -1) {
                throw new IOException("Invalid result code " + resultCode);
            }
            ShellResult result = new ShellResult();
            result.resultCode = resultCode;
            result.stdout = stdoutStream.toString();
            result.stderr = stderrStream.toString();
            if (BuildConfig.DEBUG) {
                Log.d("BinderShell_IN", TextUtils.join(" ", command));
                Log.d("BinderShell_OUT", "(exit code: " + result.resultCode + ")");
                Log.d("BinderShell_OUT", result.stdout);
            }
            return result;
        } catch (RemoteException e) {
            return ExUtils.rethrowFromSystemServer(e);
        } catch (Throwable th) {
            return ExUtils.rethrowAsIOException(th);
        }
    }
}
