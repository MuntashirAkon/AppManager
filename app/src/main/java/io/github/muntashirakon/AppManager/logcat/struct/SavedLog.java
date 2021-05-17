// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.struct;

import java.util.List;

// Copyright 2012 Nolan Lawson
public class SavedLog {
    private final List<String> logLines;
    private final boolean truncated;

    public SavedLog(List<String> logLines, boolean truncated) {
        this.logLines = logLines;
        this.truncated = truncated;
    }

    public List<String> getLogLines() {
        return logLines;
    }

    public boolean isTruncated() {
        return truncated;
    }
}
