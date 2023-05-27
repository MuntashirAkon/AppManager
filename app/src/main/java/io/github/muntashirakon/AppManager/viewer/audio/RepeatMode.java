// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.audio;

import androidx.annotation.IntDef;

@IntDef({RepeatMode.NO_REPEAT, RepeatMode.REPEAT_INDEFINITELY, RepeatMode.REPEAT_SINGLE_INDEFINITELY})
public @interface RepeatMode {
    int NO_REPEAT = 0;
    int REPEAT_INDEFINITELY = 1;
    int REPEAT_SINGLE_INDEFINITELY = 3;
}
