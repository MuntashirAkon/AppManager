/*
 * Copyright (C) 2020 Muntashir Al-Islam
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

    public void installSystemApplicationInfo(ApplicationInfo info, ClassLoader classLoader) {}
}

