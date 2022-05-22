// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.reader;


// Copyright 2012 Nolan Lawson
abstract class AbsLogcatReader implements LogcatReader {
    protected boolean recordingMode;

    public AbsLogcatReader(boolean recordingMode) {
        this.recordingMode = recordingMode;
    }

    public boolean isRecordingMode() {
        return recordingMode;
    }
}
