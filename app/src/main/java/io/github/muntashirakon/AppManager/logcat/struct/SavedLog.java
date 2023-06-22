// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.struct;

import java.util.List;

// Copyright 2012 Nolan Lawson
public class SavedLog {
    private final List<String> mLogLines;
    private final boolean mTruncated;

    public SavedLog(List<String> logLines, boolean truncated) {
        mLogLines = logLines;
        mTruncated = truncated;
    }

    public List<String> getLogLines() {
        return mLogLines;
    }

    public boolean isTruncated() {
        return mTruncated;
    }
}
