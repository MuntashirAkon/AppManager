// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.os.ShellCallback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.collection.ArrayMap;

import org.jetbrains.annotations.NotNull;

import java.io.FileDescriptor;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.compat.BinderCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.server.common.IRootServiceManager;
import io.github.muntashirakon.compat.os.ParcelCompat2;

// Copyright 2020 Rikka
public class ProxyBinder implements IBinder {
    private static final String TAG = ProxyBinder.class.getSimpleName();
    public static final int PROXY_BINDER_TRANSACTION = 2;
    /**
     * IBinder protocol transaction code: execute a shell command.
     */
    public static final int SHELL_COMMAND_TRANSACTION = ('_' << 24) | ('C' << 16) | ('M' << 8) | 'D';

    private static final Map<String, IBinder> sServiceCache
            = Collections.synchronizedMap(new ArrayMap<>());

    @NonNull
    public static IBinder getService(String serviceName) {
        IBinder binder = sServiceCache.get(serviceName);
        if (binder == null) {
            binder = getServiceInternal(serviceName);
            sServiceCache.put(serviceName, binder);
        }
        return new ProxyBinder(binder);
    }

    /**
     * Some services can't be called without certain permissions
     * so we redirect to AMService who can make that call no mater which mode it's in.
     * as 0, 1000, and 2000 all have access to the overlay service.
     *
     * @param serviceName service to be loaded
     * @return binder to that service
     */
    @NotNull
    private static IBinder getServiceInternal(String serviceName) {
        IBinder binder = ServiceManager.getService(serviceName);
        if (LocalServices.alive() && binder == null) {
            try {
                binder = LocalServices.getAmService().getService(serviceName);
            } catch (RemoteException e) {
                Log.e(TAG, e);
                throw new RuntimeException("Service couldn't be loaded: " + serviceName, e);
            }
        }
        if (binder == null) {
            throw new RuntimeException("Service couldn't be found");
        }
        return binder;
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

    /**
     * @see BinderCompat#shellCommand(IBinder, FileDescriptor, FileDescriptor, FileDescriptor, String[], ShellCallback, ResultReceiver)
     */
    @RequiresApi(Build.VERSION_CODES.N)
    public static void shellCommand(@NonNull IBinder binder,
                                    @NonNull FileDescriptor in, @NonNull FileDescriptor out,
                                    @NonNull FileDescriptor err,
                                    @NonNull String[] args, @Nullable ShellCallback callback,
                                    @NonNull ResultReceiver resultReceiver) throws RemoteException {
        if (!(binder instanceof ProxyBinder)) {
            BinderCompat.shellCommand(binder, in, out, err, args, callback, resultReceiver);
            return;
        }
        ProxyBinder proxyBinder = (ProxyBinder) binder;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeFileDescriptor(in);
        data.writeFileDescriptor(out);
        data.writeFileDescriptor(err);
        data.writeStringArray(args);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ShellCallback.writeToParcel(callback, data);
        }
        resultReceiver.writeToParcel(data, 0);
        try {
            proxyBinder.transact(SHELL_COMMAND_TRANSACTION, data, reply, 0);
            reply.readException();
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private final IBinder mOriginal;

    public ProxyBinder(@NonNull IBinder original) {
        mOriginal = Objects.requireNonNull(original);
    }

    @Override
    public boolean transact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags) throws RemoteException {
        if (LocalServices.alive()) {
            IBinder targetBinder = LocalServices.getAmService().asBinder();
            Parcel newData = ParcelCompat2.obtain(targetBinder);
            try {
                newData.writeInterfaceToken(IRootServiceManager.class.getName());
                newData.writeStrongBinder(mOriginal);
                newData.writeInt(code);
                newData.writeInt(flags);
                newData.appendFrom(data, 0, data.dataSize());
                // Transact via AMService
                targetBinder.transact(PROXY_BINDER_TRANSACTION, newData, reply, 0);
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
