// SPDX-License-Identifier: Apache-2.0

package android.miui;

import misc.utils.HiddenUtil;

// This is a MIUI specific API
public class AppOpsUtils {
    public static boolean isXOptMode() {
        return HiddenUtil.throwUOE();
    }
}
