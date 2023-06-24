// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.io;

import static android.system.OsConstants.ENOSYS;
import static android.system.OsConstants.O_APPEND;
import static android.system.OsConstants.O_CREAT;
import static android.system.OsConstants.O_RDONLY;
import static android.system.OsConstants.O_RDWR;
import static android.system.OsConstants.O_TRUNC;
import static android.system.OsConstants.O_WRONLY;
import static io.github.muntashirakon.io.FileSystemManager.MODE_APPEND;
import static io.github.muntashirakon.io.FileSystemManager.MODE_CREATE;
import static io.github.muntashirakon.io.FileSystemManager.MODE_READ_ONLY;
import static io.github.muntashirakon.io.FileSystemManager.MODE_READ_WRITE;
import static io.github.muntashirakon.io.FileSystemManager.MODE_TRUNCATE;
import static io.github.muntashirakon.io.FileSystemManager.MODE_WRITE_ONLY;

import android.annotation.SuppressLint;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Int64Ref;
import android.system.Os;
import android.util.ArraySet;
import android.util.MutableLong;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.Set;

// Copyright 2022 John "topjohnwu" Wu
@SuppressWarnings({"ConstantConditions", "JavaReflectionMemberAccess"})
@SuppressLint("DiscouragedPrivateApi")
class FileUtils {
    private static Object os;
    private static Method splice;
    private static Method sendfile;
    private static AccessibleObject setFd;

    static class Flag {
        boolean read;
        boolean write;
        boolean create;
        boolean truncate;
        boolean append;
    }

    static int modeToPosix(int mode) {
        int res;
        if ((mode & MODE_READ_WRITE) == MODE_READ_WRITE) {
            res = O_RDWR;
        } else if ((mode & MODE_WRITE_ONLY) == MODE_WRITE_ONLY) {
            res = O_WRONLY;
        } else if ((mode & MODE_READ_ONLY) == MODE_READ_ONLY) {
            res = O_RDONLY;
        } else {
            throw new IllegalArgumentException("Bad mode: " + mode);
        }
        if ((mode & MODE_CREATE) == MODE_CREATE) {
            res |= O_CREAT;
        }
        if ((mode & MODE_TRUNCATE) == MODE_TRUNCATE) {
            res |= O_TRUNC;
        }
        if ((mode & MODE_APPEND) == MODE_APPEND) {
            res |= O_APPEND;
        }
        return res;
    }

    @RequiresApi(api = 26)
    static Set<OpenOption> modeToOptions(int mode) {
        Set<OpenOption> set = new ArraySet<>();
        if ((mode & MODE_READ_WRITE) == MODE_READ_WRITE) {
            set.add(StandardOpenOption.READ);
            set.add(StandardOpenOption.WRITE);
        } else if ((mode & MODE_WRITE_ONLY) == MODE_WRITE_ONLY) {
            set.add(StandardOpenOption.WRITE);
        } else if ((mode & MODE_READ_ONLY) == MODE_READ_ONLY) {
            set.add(StandardOpenOption.READ);
        } else {
            throw new IllegalArgumentException("Bad mode: " + mode);
        }
        if ((mode & MODE_CREATE) == MODE_CREATE) {
            set.add(StandardOpenOption.CREATE);
        }
        if ((mode & MODE_TRUNCATE) == MODE_TRUNCATE) {
            set.add(StandardOpenOption.TRUNCATE_EXISTING);
        }
        if ((mode & MODE_APPEND) == MODE_APPEND) {
            set.add(StandardOpenOption.APPEND);
        }
        return set;
    }

    static Flag modeToFlag(int mode) {
        Flag f = new Flag();
        if ((mode & MODE_READ_WRITE) == MODE_READ_WRITE) {
            f.read = true;
            f.write = true;
        } else if ((mode & MODE_WRITE_ONLY) == MODE_WRITE_ONLY) {
            f.write = true;
        } else if ((mode & MODE_READ_ONLY) == MODE_READ_ONLY) {
            f.read = true;
        } else {
            throw new IllegalArgumentException("Bad mode: " + mode);
        }
        if ((mode & MODE_CREATE) == MODE_CREATE) {
            f.create = true;
        }
        if ((mode & MODE_TRUNCATE) == MODE_TRUNCATE) {
            f.truncate = true;
        }
        if ((mode & MODE_APPEND) == MODE_APPEND) {
            f.append = true;
        }

        // Validate flags
        if (f.append && f.read) {
            throw new IllegalArgumentException("READ + APPEND not allowed");
        }
        if (f.append && f.truncate) {
            throw new IllegalArgumentException("APPEND + TRUNCATE not allowed");
        }

        return f;
    }

    @RequiresApi(api = 28)
    static long splice(
            FileDescriptor fdIn, Int64Ref offIn,
            FileDescriptor fdOut, Int64Ref offOut,
            long len, int flags) throws ErrnoException {
        try {
            if (splice == null) {
                splice = Os.class.getMethod("splice",
                        FileDescriptor.class, Int64Ref.class,
                        FileDescriptor.class, Int64Ref.class,
                        long.class, int.class);
            }
            return (long) splice.invoke(null, fdIn, offIn, fdOut, offOut, len, flags);
        } catch (InvocationTargetException e) {
            throw (ErrnoException) e.getTargetException();
        } catch (ReflectiveOperationException e) {
            throw new ErrnoException("splice", ENOSYS);
        }
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("NewApi")
    static long sendfile(
            FileDescriptor outFd, FileDescriptor inFd,
            MutableLong inOffset, long byteCount) throws ErrnoException {
        if (Build.VERSION.SDK_INT >= 28) {
            Int64Ref off = inOffset == null ? null : new Int64Ref(inOffset.value);
            long result = Os.sendfile(outFd, inFd, off, byteCount);
            if (off != null)
                inOffset.value = off.value;
            return result;
        } else {
            try {
                if (os == null) {
                    os = Class.forName("libcore.io.Libcore").getField("os").get(null);
                }
                if (sendfile == null) {
                    sendfile = os.getClass().getMethod("sendfile",
                            FileDescriptor.class, FileDescriptor.class,
                            MutableLong.class, long.class);
                }
                return (long) sendfile.invoke(os, outFd, inFd, inOffset, byteCount);
            } catch (InvocationTargetException e) {
                throw (ErrnoException) e.getTargetException();
            } catch (ReflectiveOperationException e) {
                throw new ErrnoException("sendfile", ENOSYS);
            }
        }
    }

    @SuppressWarnings("OctalInteger")
    static File createTempFIFO() throws ErrnoException, IOException {
        File fifo = File.createTempFile("libsu-fifo-", null);
        fifo.delete();
        Os.mkfifo(fifo.getPath(), 0644);
        return fifo;
    }

    static FileDescriptor createFileDescriptor(int fd) {
        if (setFd == null) {
            try {
                // Available API 24+
                setFd = FileDescriptor.class.getDeclaredConstructor(int.class);
            } catch (NoSuchMethodException e) {
                // This is actually how the Android framework sets the fd internally
                try {
                    setFd = FileDescriptor.class.getDeclaredMethod("setInt$", int.class);
                } catch (NoSuchMethodException ignored) {}
            }
            setFd.setAccessible(true);
        }
        try {
            if (setFd instanceof Constructor) {
                return (FileDescriptor) ((Constructor<?>) setFd).newInstance(fd);
            } else {
                FileDescriptor f = new FileDescriptor();
                ((Method) setFd).invoke(f, fd);
                return f;
            }
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}