/*
 * Copyright (c) 2021 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.utils;

import android.os.Handler;
import android.os.Looper;

import com.topjohnwu.superuser.ShellUtils;

import java.util.concurrent.Executor;

public final class UiThreadHandler {
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
