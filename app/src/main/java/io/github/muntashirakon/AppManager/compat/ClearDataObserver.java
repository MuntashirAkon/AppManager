// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.content.pm.IPackageDataObserver;

public class ClearDataObserver extends IPackageDataObserver.Stub {
    private boolean mCompleted;
    private boolean mSuccessful;

    @Override
    public void onRemoveCompleted(String packageName, boolean succeeded) {
        synchronized (this) {
            mCompleted = true;
            mSuccessful = succeeded;
            notifyAll();
        }
    }

    public boolean isCompleted() {
        return mCompleted;
    }

    public boolean isSuccessful() {
        return mSuccessful;
    }
}
