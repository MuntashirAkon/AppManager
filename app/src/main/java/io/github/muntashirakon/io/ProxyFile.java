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
import io.github.muntashirakon.AppManager.IRemoteFileWriter;
import io.github.muntashirakon.AppManager.ipc.IPCUtils;

public class ProxyFile extends File {
    @Nullable
    IRemoteFile file;

    public ProxyFile(@NonNull String pathname) {
        super(pathname);
        getRemoteFile();
    }

    public ProxyFile(@NonNull File file) {
        super(file.getAbsolutePath());
        getRemoteFile();
    }

    public ProxyFile(@Nullable String parent, @NonNull String child) {
        super(parent, child);
        getRemoteFile();
    }

    public ProxyFile(@Nullable File parent, @NonNull String child) {
        super(parent, child);
        getRemoteFile();
    }

    @Override
    public long length() {
        if (isRemoteAlive()) {
            try {
                //noinspection ConstantConditions
                return file.length();
            } catch (RemoteException ignore) {
            }
        }
        return super.length();
    }

    @Override
    public boolean createNewFile() throws IOException {
        if (isRemoteAlive()) {
            try {
                //noinspection ConstantConditions
                return file.createNewFile();
            } catch (RemoteException ignore) {
            }
        }
        return super.createNewFile();
    }

    @Override
    public boolean delete() {
        if (isRemoteAlive()) {
            try {
                //noinspection ConstantConditions
                return file.delete();
            } catch (RemoteException ignore) {
            }
        }
        return super.delete();
    }

    public boolean forceDelete() {
        if (isFile()) return super.delete();
        else return deleteDir(this);
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
        if (isRemoteAlive()) {
            try {
                //noinspection ConstantConditions
                return file.exists();
            } catch (RemoteException ignore) {
            }
        }
        return super.exists();
    }

    @Override
    public boolean isDirectory() {
        if (isRemoteAlive()) {
            try {
                //noinspection ConstantConditions
                return file.isDirectory();
            } catch (RemoteException ignore) {
            }
        }
        return super.isDirectory();
    }

    @Override
    public boolean isFile() {
        if (isRemoteAlive()) {
            try {
                //noinspection ConstantConditions
                return file.isFile();
            } catch (RemoteException ignore) {
            }
        }
        return super.isFile();
    }

    @Override
    public long lastModified() {
        if (isRemoteAlive()) {
            try {
                //noinspection ConstantConditions
                return file.lastModified();
            } catch (RemoteException ignore) {
            }
        }
        return super.lastModified();
    }

    @Override
    public boolean mkdir() {
        if (isRemoteAlive()) {
            try {
                //noinspection ConstantConditions
                return file.mkdir();
            } catch (RemoteException ignore) {
            }
        }
        return super.mkdir();
    }

    @Override
    public boolean mkdirs() {
        if (isRemoteAlive()) {
            try {
                //noinspection ConstantConditions
                return file.mkdirs();
            } catch (RemoteException ignore) {
            }
        }
        return super.mkdirs();
    }

    @Override
    public boolean renameTo(@NonNull File dest) {
        if (isRemoteAlive()) {
            try {
                //noinspection ConstantConditions
                return file.renameTo(dest.getAbsolutePath());
            } catch (RemoteException ignore) {
            }
        }
        return super.renameTo(dest);
    }

    @Nullable
    @Override
    public String[] list() {
        if (isRemoteAlive()) {
            try {
                //noinspection ConstantConditions
                return file.list();
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
        if (isRemoteAlive()) {
            try {
                //noinspection ConstantConditions
                return file.getCanonicalPath();
            } catch (RemoteException ignore) {
            }
        }
        return super.getCanonicalPath();
    }

    @NonNull
    @Override
    public File getCanonicalFile() throws IOException {
        if (isRemoteAlive()) {
            return new File(getCanonicalPath());
        }
        return super.getCanonicalFile();
    }

    @NonNull
    public IRemoteFileWriter getFileWriter() throws RemoteException {
        if (isRemoteAlive()) {
            //noinspection ConstantConditions
            IRemoteFileWriter writer = file.getFileWriter();
            if (writer == null) throw new RemoteException(getAbsolutePath() + ": Couldn't get remote file writer.");
            return writer;
        } else throw new RemoteException("Remote service isn't alive.");
    }

    private void getRemoteFile() {
        IAMService amService = IPCUtils.getService();
        if (amService != null) {
            try {
                file = amService.getFile(getAbsolutePath());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isRemoteAlive() {
        return file != null && file.asBinder().pingBinder();
    }

    private static boolean deleteDir(ProxyFile dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children == null) return false;
            for (String child : children) {
                boolean success = deleteDir(new ProxyFile(dir, child));
                if (!success) return false;
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        } else return false;
    }
}
