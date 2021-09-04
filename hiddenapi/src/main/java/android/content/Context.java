// SPDX-License-Identifier: Apache-2.0

package android.content;

import android.content.pm.PackageManager;
import android.os.UserHandle;

import misc.utils.HiddenUtil;

public class Context {
    public Context createPackageContextAsUser(String packageName, int flags, UserHandle user)
            throws PackageManager.NameNotFoundException {
        return HiddenUtil.throwUOE();
    }
}
