// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;

import java.io.FileDescriptor;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.server.common.IRootServiceManager;

// Copyright 2020 Rikka
public class ProxyBinder implements IBinder {
    public static final int PROXY_BINDER_TRANSACT_CODE = 2;

    private static final Map<String, IBinder> sServiceCache = new ArrayMap<>();

    @NonNull
    public static IBinder getService(String serviceName) {
        IBinder binder = sServiceCache.get(serviceName);
        if (binder == null) {
            binder = ServiceManager.getService(serviceName);
            sServiceCache.put(serviceName, binder);
        }
        return new ProxyBinder(binder);
    }

    @NonNull
    public static IBinder getUnprivilegedService(String serviceName) {
        IBinder binder = sServiceCache.get(serviceName);
        if (binder == null) {
            binder = ServiceManager.getService(serviceName);
            sServiceCache.put(serviceName, binder);
        }
        return binder;
    }

    private final IBinder mOriginal;

    public ProxyBinder(@NonNull IBinder original) {
        mOriginal = Objects.requireNonNull(original);
    }

    @Override
    public boolean transact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags) throws RemoteException {
        if (LocalServices.alive()) {
            Parcel newData = Parcel.obtain();
            try {
                newData.writeInterfaceToken(IRootServiceManager.class.getName());
                newData.writeStrongBinder(mOriginal);
                newData.writeInt(code);
                newData.appendFrom(data, 0, data.dataSize());
                // Transact via AMService
                LocalServices.getAmService().asBinder().transact(PROXY_BINDER_TRANSACT_CODE, newData, reply, flags);
            } finally {
                newData.recycle();
            }
            return true;
        }
        // Run unprivileged code as a fallback method
        return mOriginal.transact(code, data, reply, flags);
    }

    @Nullable
    @Override
    public String getInterfaceDescriptor() {
        try {
            return mOriginal.getInterfaceDescriptor();
        } catch (RemoteException e) {
            throw new IllegalStateException(e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public boolean pingBinder() {
        return mOriginal.pingBinder();
    }

    @Override
    public boolean isBinderAlive() {
        return mOriginal.isBinderAlive();
    }

    @Nullable
    @Override
    public IInterface queryLocalInterface(@NonNull String descriptor) {
        return null;
    }

    @Override
    public void dump(@NonNull FileDescriptor fd, @Nullable String[] args) {
        try {
            mOriginal.dump(fd, args);
        } catch (RemoteException e) {
            throw new IllegalStateException(e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public void dumpAsync(@NonNull FileDescriptor fd, @Nullable String[] args) {
        try {
            mOriginal.dumpAsync(fd, args);
        } catch (RemoteException e) {
            throw new IllegalStateException(e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public void linkToDeath(@NonNull DeathRecipient recipient, int flags) {
        try {
            mOriginal.linkToDeath(recipient, flags);
        } catch (RemoteException e) {
            throw new IllegalStateException(e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public boolean unlinkToDeath(@NonNull DeathRecipient recipient, int flags) {
        return mOriginal.unlinkToDeath(recipient, flags);
    }
}
