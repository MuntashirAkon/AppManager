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

import androidx.annotation.NonNull;

public final class WaitRunnable implements Runnable {

    private Runnable r;

    public WaitRunnable(@NonNull Runnable run) {
        r = run;
    }

    public synchronized void waitUntilDone() {
        while (r != null) {
            try {
                wait();
            } catch (InterruptedException ignored) {
            }
        }
    }

    @Override
    public synchronized void run() {
        r.run();
        r = null;
        notifyAll();
    }
}
