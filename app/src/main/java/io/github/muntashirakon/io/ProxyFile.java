/*
 * Copyright (C) 2021 Muntashir Al-Islam
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

package io.github.muntashirakon.io;

import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.IAMService;
import io.github.muntashirakon.AppManager.IRemoteFile;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.utils.AppPref;

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
        boolean isDeleted = false;
        try {
            isDeleted = super.delete();
        } catch (SecurityException ignore) {
        }
        if (!isDeleted && AppPref.isRootOrAdbEnabled()) {
            RunnerUtils.deleteFile(getAbsolutePath(), true);
            return !RunnerUtils.fileExists(getAbsolutePath());
        }
        return isDeleted;
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

    public InputStream getInputStream() throws RemoteException, FileNotFoundException {
        if (isRemoteAlive()) {
            //noinspection ConstantConditions
            return new ParcelFileDescriptor.AutoCloseInputStream(file.getInputStream());
        } else return new FileInputStream(this);
    }

    public OutputStream getOutputStream() throws RemoteException, FileNotFoundException {
        if (isRemoteAlive()) {
            //noinspection ConstantConditions
            return new ParcelFileDescriptor.AutoCloseOutputStream(file.getOutputStream());
        } else return new FileOutputStream(this);
    }

    private void getRemoteFile() {
        if (LocalServer.isAMServiceAlive()) {
            IAMService amService = LocalServer.getAmService();
            if (amService != null) {
                try {
                    file = amService.getFile(getAbsolutePath());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private boolean isRemoteAlive() {
        return file != null && file.asBinder().pingBinder();
    }
}
