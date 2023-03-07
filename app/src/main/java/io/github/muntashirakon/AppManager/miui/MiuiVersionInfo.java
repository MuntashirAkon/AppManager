// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.miui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// https://www.xiaomist.com/2020/08/what-do-letters-and-numbers-that.html
public class MiuiVersionInfo {
    @NonNull
    public final String version;
    @Nullable
    public final String letters;
    public final boolean isBeta;

    public MiuiVersionInfo(@NonNull String version, @Nullable String letters, boolean isBeta) {
        this.version = version;
        this.letters = letters;
        this.isBeta = isBeta || letters == null;
    }

    @NonNull
    public String getVersion() {
        return version;
    }

    @Nullable
    public String getMiuiVersion() {
        if (isBeta) {
            return null;
        }
        String[] splits = version.split("\\.");
        return splits[0] + '.' + splits[1];
    }

    @Nullable
    public String getRomVersion() {
        if (isBeta) {
            return null;
        }
        String[] splits  = version.split("\\.");
        return splits[2] + '.' + splits[3];
    }

    @Nullable
    public String getAndroidVersionCodeName() {
        if (letters == null) {
            return null;
        }
        String[] splits = letters.split("\\.");
        return splits[0];
    }

    @Nullable
    public String getTargetDevice() {
        if (letters == null) {
            return null;
        }
        String[] splits = letters.split("\\.");
        return splits[1] + splits[2];
    }

    @Nullable
    public String getRegion() {
        if (letters == null) {
            return null;
        }
        String[] splits = letters.split("\\.");
        return splits[3] + splits[4];
    }

    @Nullable
    public String getOrigin() {
        if (letters == null) {
            return null;
        }
        String[] splits = letters.split("\\.");
        return splits[5] + splits[6];
    }
}
