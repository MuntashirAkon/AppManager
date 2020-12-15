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

/**
 * Created by zl on 2016/11/5.
 */

public class ActivityThread {

    public static IPackageManager getPackageManager() {
        return null;
    }

    public static ActivityThread systemMain() {
        return null;
    }

    public static ActivityThread currentActivityThread() {
        return null;
    }


    public static Application currentApplication() {
        return null;
    }

    public static String currentProcessName() {
        return null;
    }

    public ContextImpl getSystemContext() {
        return null;
    }

    public void installSystemApplicationInfo(ApplicationInfo info, ClassLoader classLoader) {}
}

