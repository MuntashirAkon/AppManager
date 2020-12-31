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

package io.github.muntashirakon.AppManager.utils;

import android.annotation.SuppressLint;
import android.app.ActivityThread;
import android.content.Context;
import android.os.Build;

import com.topjohnwu.superuser.internal.UiThreadHandler;

import io.github.muntashirakon.AppManager.logs.Log;

public final class ContextUtils {

    @SuppressLint("StaticFieldLeak")
    public static Context context;
    private static final String TAG = "IPC";

    @SuppressLint("PrivateApi")
    public static synchronized Context getContext() {
        if (context == null) {
            UiThreadHandler.runAndWait(() -> {
                try {
                    context = ActivityThread.currentApplication();
                } catch (Exception e) {
                    // Shall never happen
                    Log.e(TAG, e);
                }
            });
        }
        return context;
    }

    public static Context getDeContext(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ?
                context.createDeviceProtectedStorageContext() : context;
    }
}
