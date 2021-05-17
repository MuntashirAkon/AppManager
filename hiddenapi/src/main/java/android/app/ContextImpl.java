// SPDX-License-Identifier: Apache-2.0

package android.app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;

public abstract class ContextImpl extends Context {
    public Context createPackageContextAsUser(String packageName, int flags, UserHandle user)
            throws PackageManager.NameNotFoundException {
        throw new UnsupportedOperationException();
    }
}
