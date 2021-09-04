// SPDX-License-Identifier: Apache-2.0

package android.os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileDescriptor;

import misc.utils.HiddenUtil;

public class Binder implements IBinder {

    @Override
    public boolean transact(int code, @NonNull Parcel data, Parcel reply, int flags) {
        return HiddenUtil.throwUOE();
    }

    @Override
    public String getInterfaceDescriptor() {
        return HiddenUtil.throwUOE();
    }

    public boolean pingBinder() {
        return HiddenUtil.throwUOE();
    }

    @Override
    public boolean isBinderAlive() {
        return HiddenUtil.throwUOE();
    }

    @Override
    public IInterface queryLocalInterface(@NonNull String descriptor) {
        return HiddenUtil.throwUOE();
    }

    @Override
    public void dump(@NonNull FileDescriptor fd, String[] args) {
        HiddenUtil.throwUOE();
    }

    @Override
    public void dumpAsync(@NonNull FileDescriptor fd, String[] args) {
        HiddenUtil.throwUOE();
    }

    @Override
    public void linkToDeath(@NonNull DeathRecipient recipient, int flags) {
        HiddenUtil.throwUOE();
    }

    @Override
    public boolean unlinkToDeath(@NonNull DeathRecipient recipient, int flags) {
        return HiddenUtil.throwUOE();
    }

    protected boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel reply,
                                 int flags) throws RemoteException {
        return HiddenUtil.throwUOE();
    }
}
