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

package io.github.muntashirakon.AppManager.misc;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.runner.Runner;

public final class SystemProperties {
    @NonNull
    public static String get(@NonNull String key, @NonNull String defaultVal) {
        Runner.Result result = Runner.runCommand(new String[]{"getprop", key, defaultVal});
        if (result.isSuccessful()) return result.getOutput().trim();
        else return defaultVal;
    }

    public static boolean getBoolean(@NonNull String key, boolean defaultVal) {
        String val = get(key, String.valueOf(defaultVal));
        if ("1".equals(val)) return true;
        return Boolean.parseBoolean(val);
    }
}
