// SPDX-License-Identifier: Apache-2.0

package android.app;

import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;

public class ActivityThread {
    public static IPackageManager getPackageManager() {
        throw new UnsupportedOperationException();
    }

    public static ActivityThread systemMain() {
        throw new UnsupportedOperationException();
    }

    public static ActivityThread currentActivityThread() {
        throw new UnsupportedOperationException();
    }

    public static Application currentApplication() {
        throw new UnsupportedOperationException();
    }

    public static String currentProcessName() {
        throw new UnsupportedOperationException();
    }

    public ContextImpl getSystemContext() {
        throw new UnsupportedOperationException();
    }

    public IApplicationThread getApplicationThread() {
        throw new UnsupportedOperationException();
    }

    public void installSystemApplicationInfo(ApplicationInfo info, ClassLoader classLoader) {
    }
}
