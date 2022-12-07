// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.annotation.SuppressLint;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Objects;

// Copyright 2022 John "topjohnwu" Wu
@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class NIOFactory {

    private NIOFactory() {}

    public static FileSystemManager createLocal() {
        return new FileSystemManager() {
            @NonNull
            @Override
            public ExtendedFile getFile(@NonNull String pathname) {
                return new LocalFile(pathname);
            }

            @NonNull
            @Override
            public ExtendedFile getFile(@Nullable String parent, @NonNull String child) {
                return new LocalFile(parent, child);
            }

            @SuppressLint("NewApi")
            @NonNull
            @Override
            public FileChannel openChannel(@NonNull File file, int mode) throws IOException {
                if (Build.VERSION.SDK_INT >= 26) {
                    return FileChannel.open(file.toPath(), FileUtils.modeToOptions(mode));
                } else {
                    FileUtils.Flag f = FileUtils.modeToFlag(mode);
                    if (f.write) {
                        if (!f.create) {
                            if (!file.exists()) {
                                ErrnoException e = new ErrnoException("open", OsConstants.ENOENT);
                                throw new FileNotFoundException(file + ": " + e.getMessage());
                            }
                        }
                        if (f.append) {
                            return new FileOutputStream(file, true).getChannel();
                        }
                        if (!f.read && f.truncate) {
                            return new FileOutputStream(file, false).getChannel();
                        }

                        // Unfortunately, there is no way to create a write-only channel
                        // without truncating. Forced to open rw RAF in all cases.
                        FileChannel ch = new RandomAccessFile(file, "rw").getChannel();
                        if (f.truncate) {
                            ch.truncate(0);
                        }
                        return ch;
                    } else {
                        return new FileInputStream(file).getChannel();
                    }
                }
            }
        };
    }

    public static FileSystemManager createRemote(@NonNull IBinder b) throws RemoteException {
        Objects.requireNonNull(b);
        IFileSystemService fs = IFileSystemService.Stub.asInterface(b);
        if (fs == null) {
            throw new IllegalArgumentException("The IBinder provided is invalid");
        }

        fs.register(new Binder());
        return new FileSystemManager() {
            @NonNull
            @Override
            public ExtendedFile getFile(@NonNull String pathname) {
                return new RemoteFile(fs, pathname);
            }

            @NonNull
            @Override
            public ExtendedFile getFile(@Nullable String parent, @NonNull String child) {
                return new RemoteFile(fs, parent, child);
            }

            @NonNull
            @Override
            public FileChannel openChannel(@NonNull File file, int mode) throws IOException {
                return new RemoteFileChannel(fs, file, mode);
            }
        };
    }

    public static FileSystemService createFsService() {
        return new FileSystemService();
    }
}