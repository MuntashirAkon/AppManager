// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Objects;

public class RoboUtils {
    @NonNull
    public static File getTestBaseDir() {
        return Objects.requireNonNull(ContextUtils.getContext().getDataDir().getParentFile());
    }
}
