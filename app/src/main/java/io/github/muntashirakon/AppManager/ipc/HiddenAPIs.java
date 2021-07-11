// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.AppManager.ipc;

import android.annotation.SuppressLint;
import android.content.Intent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * All hidden Android framework APIs used here are very stable.
 * <p>
 * These methods should only be accessed in the root process, since under normal circumstances
 * accessing these internal APIs through reflection will be blocked.
 */
// Copyright 2020 John "topjohnwu" Wu
class HiddenAPIs {
    // Set this flag to silence AMS's complaints
    @SuppressWarnings("JavaReflectionMemberAccess")
    static int FLAG_RECEIVER_FROM_SHELL() {
        try {
            Field f = Intent.class.getDeclaredField("FLAG_RECEIVER_FROM_SHELL");
            return (int) f.get(null);
        } catch (Exception e) {
            // Only exist on Android 8.0+
            return 0;
        }
    }

    @SuppressLint("PrivateApi,DiscouragedPrivateApi")
    static void setAppName(String name) {
        try {
            Class<?> ddm = Class.forName("android.ddm.DdmHandleAppName");
            Method m = ddm.getDeclaredMethod("setAppName", String.class, int.class);
            m.invoke(null, name, 0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
