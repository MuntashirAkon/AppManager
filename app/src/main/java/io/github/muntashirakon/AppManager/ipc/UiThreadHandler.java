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

import android.os.Handler;
import android.os.Looper;

import com.topjohnwu.superuser.ShellUtils;

import java.util.concurrent.Executor;

public class UiThreadHandler {
    public static final Handler handler = new Handler(Looper.getMainLooper());
    public static final Executor executor = UiThreadHandler::run;

    public static void run(Runnable r) {
        if (ShellUtils.onMainThread()) {
            r.run();
        } else {
            handler.post(r);
        }
    }

    public static void runAndWait(Runnable r) {
        if (ShellUtils.onMainThread()) {
            r.run();
        } else {
            WaitRunnable wr = new WaitRunnable(r);
            handler.post(wr);
            wr.waitUntilDone();
        }
    }
}
