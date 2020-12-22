/*
 * Copyright 2020 John "topjohnwu" Wu
 * Copyright 2020 Muntashir Al-Islam
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
