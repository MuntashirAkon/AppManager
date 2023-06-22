// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.ipc;

import android.os.IBinder;
import android.os.RemoteException;

import com.topjohnwu.superuser.internal.UiThreadHandler;

// Copyright 2022 John "topjohnwu" Wu
abstract class BinderHolder implements IBinder.DeathRecipient {
    private final IBinder mBinder;

    BinderHolder(IBinder b) throws RemoteException {
        mBinder = b;
        mBinder.linkToDeath(this, 0);
    }

    @Override
    public final void binderDied() {
        mBinder.unlinkToDeath(this, 0);
        UiThreadHandler.run(this::onBinderDied);
    }

    protected abstract void onBinderDied();
}