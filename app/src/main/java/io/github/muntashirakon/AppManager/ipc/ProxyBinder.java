// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.ArrayMap;

import java.io.FileDescriptor;
import java.util.Map;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.server.common.IRootIPC;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.utils.AppPref;

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

    private final IBinder original;

    public ProxyBinder(@NonNull IBinder original) {
        this.original = Objects.requireNonNull(original);
    }

    @Override
    public boolean transact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags) throws RemoteException {
        if (AppPref.isRootOrAdbEnabled()) {
            if (!LocalServer.isAMServiceAlive()) {
                throw new RemoteException("Root/ADB enabled but privileged service isn't alive.");
            }
            Parcel newData = Parcel.obtain();
            try {
                newData.writeInterfaceToken(IRootIPC.class.getName());
                newData.writeStrongBinder(original);
                newData.writeInt(code);
                newData.appendFrom(data, 0, data.dataSize());
                // Transact via AMService instead of AM
                IPCUtils.getServiceSafe().asBinder().transact(PROXY_BINDER_TRANSACT_CODE, newData, reply, flags);
            } finally {
                newData.recycle();
            }
            return true;
        }
        // Run unprivileged code as a fallback method
        return original.transact(code, data, reply, flags);
    }

    @Nullable
    @Override
    public String getInterfaceDescriptor() {
        try {
            return original.getInterfaceDescriptor();
        } catch (RemoteException e) {
            throw new IllegalStateException(e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public boolean pingBinder() {
        return original.pingBinder();
    }

    @Override
    public boolean isBinderAlive() {
        return original.isBinderAlive();
    }

    @Nullable
    @Override
    public IInterface queryLocalInterface(@NonNull String descriptor) {
        return null;
    }

    @Override
    public void dump(@NonNull FileDescriptor fd, @Nullable String[] args) {
        try {
            original.dump(fd, args);
        } catch (RemoteException e) {
            throw new IllegalStateException(e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public void dumpAsync(@NonNull FileDescriptor fd, @Nullable String[] args) {
        try {
            original.dumpAsync(fd, args);
        } catch (RemoteException e) {
            throw new IllegalStateException(e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public void linkToDeath(@NonNull DeathRecipient recipient, int flags) {
        try {
            original.linkToDeath(recipient, flags);
        } catch (RemoteException e) {
            throw new IllegalStateException(e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public boolean unlinkToDeath(@NonNull DeathRecipient recipient, int flags) {
        return original.unlinkToDeath(recipient, flags);
    }
}
