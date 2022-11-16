// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@IntDef({FileType.FILE, FileType.DIRECTORY, FileType.VIRTUAL, FileType.UNKNOWN})
@Retention(RetentionPolicy.SOURCE)
public @interface FileType {
    int FILE = 1;
    int DIRECTORY = 2;
    int VIRTUAL = 3;
    int UNKNOWN = -1;
}
