// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.IAMService;
import io.github.muntashirakon.AppManager.IRemoteFile;
import io.github.muntashirakon.AppManager.ipc.IPCUtils;

public class ProxyFile extends File {
    @Nullable
    private final IRemoteFile mFile;

    public ProxyFile(@NonNull String pathname) {
        super(pathname);
        mFile = getRemoteFile();
    }

    public ProxyFile(@NonNull File file) {
        super(file.getAbsolutePath());
        if (file instanceof ProxyFile) {
            // Reuse old remote file
            mFile = ((ProxyFile) file).mFile;
        } else mFile = getRemoteFile();
    }

    public ProxyFile(@Nullable String parent, @NonNull String child) {
        super(parent, child);
        mFile = getRemoteFile();
    }

    public ProxyFile(@Nullable File parent, @NonNull String child) {
        super(parent, child);
        if (parent instanceof ProxyFile) {
            mFile = getRemoteFile();
        } else mFile = null;
    }

    @Override
    public long length() {
        if (isRemote()) {
            try {
                //noinspection ConstantConditions
                return mFile.length();
            } catch (RemoteException ignore) {
            }
        }
        return super.length();
    }

    @Override
    public boolean createNewFile() throws IOException {
        if (isRemote()) {
            try {
                //noinspection ConstantConditions
                return mFile.createNewFile();
            } catch (RemoteException ignore) {
            }
        }
        return super.createNewFile();
    }

    @Override
    public boolean delete() {
        if (isRemote()) {
            try {
                //noinspection ConstantConditions
                return mFile.delete();
            } catch (RemoteException ignore) {
            }
        }
        return super.delete();
    }

    @NonNull
    @Override
    public ProxyFile getAbsoluteFile() {
        return new ProxyFile(super.getAbsoluteFile());
    }

    @Nullable
    @Override
    public ProxyFile getParentFile() {
        File parentFile = super.getParentFile();
        if (parentFile != null) return new ProxyFile(parentFile);
        else return null;
    }

    @Override
    public boolean exists() {
        if (isRemote()) {
            try {
                //noinspection ConstantConditions
                return mFile.exists();
            } catch (RemoteException ignore) {
            }
        }
        return super.exists();
    }

    @Override
    public boolean isDirectory() {
        if (isRemote()) {
            try {
                //noinspection ConstantConditions
                return mFile.isDirectory();
            } catch (RemoteException ignore) {
            }
        }
        return super.isDirectory();
    }

    @Override
    public boolean isFile() {
        if (isRemote()) {
            try {
                //noinspection ConstantConditions
                return mFile.isFile();
            } catch (RemoteException ignore) {
            }
        }
        return super.isFile();
    }

    @Override
    public long lastModified() {
        if (isRemote()) {
            try {
                //noinspection ConstantConditions
                return mFile.lastModified();
            } catch (RemoteException ignore) {
            }
        }
        return super.lastModified();
    }

    @Override
    public boolean mkdir() {
        if (isRemote()) {
            try {
                //noinspection ConstantConditions
                return mFile.mkdir();
            } catch (RemoteException ignore) {
            }
        }
        return super.mkdir();
    }

    @Override
    public boolean mkdirs() {
        if (isRemote()) {
            try {
                //noinspection ConstantConditions
                return mFile.mkdirs();
            } catch (RemoteException ignore) {
            }
        }
        return super.mkdirs();
    }

    @Override
    public boolean renameTo(@NonNull File dest) {
        if (isRemote()) {
            try {
                //noinspection ConstantConditions
                return mFile.renameTo(dest.getAbsolutePath());
            } catch (RemoteException ignore) {
            }
        }
        return super.renameTo(dest);
    }

    @Nullable
    @Override
    public String[] list() {
        if (isRemote()) {
            try {
                //noinspection ConstantConditions
                return mFile.list();
            } catch (RemoteException ignore) {
            }
        }
        return super.list();
    }

    @Nullable
    @Override
    public String[] list(@Nullable FilenameFilter filter) {
        String[] names = list();
        if ((names == null) || (filter == null)) {
            return names;
        }
        List<String> v = new ArrayList<>();
        for (String name : names) {
            if (filter.accept(this, name)) {
                v.add(name);
            }
        }
        return v.toArray(new String[0]);
    }

    @Nullable
    @Override
    public ProxyFile[] listFiles() {
        String[] ss = list();
        if (ss == null) return null;
        int n = ss.length;
        ProxyFile[] fs = new ProxyFile[n];
        for (int i = 0; i < n; i++) {
            fs[i] = new ProxyFile(this, ss[i]);
        }
        return fs;
    }

    @Nullable
    @Override
    public ProxyFile[] listFiles(@Nullable FileFilter filter) {
        String[] ss = list();
        if (ss == null) return null;
        ArrayList<ProxyFile> files = new ArrayList<>();
        for (String s : ss) {
            ProxyFile f = new ProxyFile(this, s);
            if ((filter == null) || filter.accept(f))
                files.add(f);
        }
        return files.toArray(new ProxyFile[0]);
    }

    @Nullable
    @Override
    public ProxyFile[] listFiles(@Nullable FilenameFilter filter) {
        String[] ss = list();
        if (ss == null) return null;
        ArrayList<ProxyFile> files = new ArrayList<>();
        for (String s : ss)
            if ((filter == null) || filter.accept(this, s))
                files.add(new ProxyFile(this, s));
        return files.toArray(new ProxyFile[0]);
    }

    @NonNull
    @Override
    public String getCanonicalPath() throws IOException {
        if (isRemote()) {
            try {
                //noinspection ConstantConditions
                return mFile.getCanonicalPath();
            } catch (RemoteException ignore) {
            }
        }
        return super.getCanonicalPath();
    }

    @NonNull
    @Override
    public ProxyFile getCanonicalFile() throws IOException {
        if (isRemote()) {
            return new ProxyFile(getCanonicalPath());
        }
        return new ProxyFile(super.getCanonicalFile());
    }

    @Override
    public boolean canRead() {
        if (isRemote()) {
            try {
                //noinspection ConstantConditions
                return mFile.canRead();
            } catch (RemoteException ignore) {
            }
        }
        return super.canRead();
    }

    @Override
    public boolean canWrite() {
        if (isRemote()) {
            try {
                //noinspection ConstantConditions
                return mFile.canWrite();
            } catch (RemoteException ignore) {
            }
        }
        return super.canWrite();
    }

    @Override
    public boolean canExecute() {
        if (isRemote()) {
            try {
                //noinspection ConstantConditions
                return mFile.canExecute();
            } catch (RemoteException ignore) {
            }
        }
        return super.canExecute();
    }

    @Nullable
    private IRemoteFile getRemoteFile() {
        if (canRead() && canWrite()) {
            // No need to use remote service
            return null;
        }
        IAMService amService = IPCUtils.getService();
        if (amService != null) {
            try {
                return amService.getFile(getAbsolutePath());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public boolean isRemote() {
        return mFile != null && mFile.asBinder().pingBinder();
    }
}
