// SPDX-License-Identifier: Apache-2.0

package android.app;

import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;

import misc.utils.HiddenUtil;

public class ActivityThread {
    public static IPackageManager getPackageManager() {
        return HiddenUtil.throwUOE();
    }

    public static ActivityThread systemMain() {
        return HiddenUtil.throwUOE();
    }

    public static ActivityThread currentActivityThread() {
        return HiddenUtil.throwUOE();
    }

    public static Application currentApplication() {
        return HiddenUtil.throwUOE();
    }

    public static String currentProcessName() {
        return HiddenUtil.throwUOE();
    }

    public ContextImpl getSystemContext() {
        return HiddenUtil.throwUOE();
    }

    public IApplicationThread getApplicationThread() {
        return HiddenUtil.throwUOE();
    }

    public void installSystemApplicationInfo(ApplicationInfo info, ClassLoader classLoader) {
        HiddenUtil.throwUOE(info, classLoader);
    }
}
