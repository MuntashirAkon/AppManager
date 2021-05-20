// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.ipc;

import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.RemoteException;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.IRemoteProcess;

// Copyright 2020 Rikka
public class RemoteProcess extends Process implements Parcelable {
    private final IRemoteProcess mRemote;
    private OutputStream os;
    private InputStream is;

    public RemoteProcess(IRemoteProcess remote) {
        mRemote = remote;
    }

    @Override
    public OutputStream getOutputStream() {
        if (os == null) {
            try {
                os = new ParcelFileDescriptor.AutoCloseOutputStream(mRemote.getOutputStream());
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
        return os;
    }

    @Override
    public InputStream getInputStream() {
        if (is == null) {
            try {
                is = new ParcelFileDescriptor.AutoCloseInputStream(mRemote.getInputStream());
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
        return is;
    }

    @Override
    public InputStream getErrorStream() {
        try {
            return new ParcelFileDescriptor.AutoCloseInputStream(mRemote.getErrorStream());
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int waitFor() throws InterruptedException {
        try {
            return mRemote.waitFor();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int exitValue() {
        try {
            return mRemote.exitValue();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void destroy() {
        try {
            mRemote.destroy();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean alive() {
        try {
            return mRemote.alive();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean waitForTimeout(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            return mRemote.waitForTimeout(timeout, unit.toString());
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public IBinder asBinder() {
        return mRemote.asBinder();
    }

    private RemoteProcess(Parcel in) {
        mRemote = IRemoteProcess.Stub.asInterface(in.readStrongBinder());
    }

    public static final Creator<RemoteProcess> CREATOR = new Creator<RemoteProcess>() {
        @Override
        public RemoteProcess createFromParcel(Parcel in) {
            return new RemoteProcess(in);
        }

        @Override
        public RemoteProcess[] newArray(int size) {
            return new RemoteProcess[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStrongBinder(mRemote.asBinder());
    }
}
