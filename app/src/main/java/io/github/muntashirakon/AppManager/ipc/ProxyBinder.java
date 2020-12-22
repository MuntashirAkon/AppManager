/*
 * Copyright (C) 2020 Muntashir Al-Islam
 * Copyright (C) 2020 Rikka
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

package io.github.muntashirakon.AppManager.ipc;

import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;

import java.io.FileDescriptor;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.server.common.IRootIPC;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;

public class ProxyBinder implements IBinder {
    @NonNull
    public static IBinder getService(String serviceName) {
        return new ProxyBinder(ServiceManager.getService(serviceName));
    }

    private final IBinder original;

    public ProxyBinder(@NonNull IBinder original) {
        this.original = Objects.requireNonNull(original);
    }

    @Override
    public boolean transact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags) throws RemoteException {
        if (LocalServer.getAmService() != null) {
            Parcel newData = Parcel.obtain();
            try {
                newData.writeInterfaceToken(IRootIPC.class.getName());
                newData.writeStrongBinder(original);
                newData.writeInt(code);
                newData.appendFrom(data, 0, data.dataSize());
                // Transact via AMService instead of AM
                LocalServer.getAmService().asBinder().transact(IBinder.FIRST_CALL_TRANSACTION, newData, reply, flags);
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
