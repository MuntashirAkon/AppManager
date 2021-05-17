// SPDX-License-Identifier: Apache-2.0

package android.os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileDescriptor;

public class Binder implements IBinder {

    @Override
    public boolean transact(int code, @NonNull Parcel data, Parcel reply, int flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getInterfaceDescriptor() {
        throw new UnsupportedOperationException();
    }

    public boolean pingBinder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isBinderAlive() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IInterface queryLocalInterface(@NonNull String descriptor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void dump(@NonNull FileDescriptor fd, String[] args) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void dumpAsync(@NonNull FileDescriptor fd, String[] args) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void linkToDeath(@NonNull DeathRecipient recipient, int flags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean unlinkToDeath(@NonNull DeathRecipient recipient, int flags) {
        throw new UnsupportedOperationException();
    }

    protected boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel reply,
                                 int flags) throws RemoteException {
        throw new UnsupportedOperationException();
    }
}
