// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.reader;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.logcat.helper.LogcatHelper;
import io.github.muntashirakon.AppManager.logs.Log;

// Copyright 2012 Nolan Lawson
public class SingleLogcatReader extends AbsLogcatReader {
    private final Process mLogcatProcess;
    private final BufferedReader mBufferedReader;
    @Nullable
    private String mLastLine;

    public SingleLogcatReader(boolean recordingMode, @LogcatHelper.LogBufferId int buffers, @Nullable String lastLine)
            throws IOException {
        super(recordingMode);
        mLastLine = lastLine;

        // Use the "time" log so we can see what time the logs were logged at
        mLogcatProcess = LogcatHelper.getLogcatProcess(buffers);
        mBufferedReader = new BufferedReader(new InputStreamReader(mLogcatProcess.getInputStream()), 8192);
    }

    @Override
    public void killQuietly() {
        if (mLogcatProcess != null) {
            mLogcatProcess.destroy();
            Log.d("SLR", "killed 1 logcat process");
        }
    }

    @Override
    public String readLine() throws IOException {
        String line = mBufferedReader.readLine();
        if (recordingMode && mLastLine != null) { // Still skipping past the 'last line'
            if (mLastLine.equals(line) /*|| isAfterLastTime(line)*/) {
                mLastLine = null; // Indicates we've passed the last line
            }
        }
        return line;
    }

    private boolean isAfterLastTime(String line) {
        if (mLastLine == null) {
            return false;
        }
        // Doing a string comparison is sufficient to determine whether this line is chronologically
        // after the last line, because the format they use is exactly the same and
        // lists larger time period before smaller ones
        return isDatedLogLine(mLastLine) && isDatedLogLine(line) && line.compareTo(mLastLine) > 0;
    }

    private boolean isDatedLogLine(String line) {
        // 18 is the size of the logcat timestamp
        return (!TextUtils.isEmpty(line) && line.length() >= 18 && Character.isDigit(line.charAt(0)));
    }

    @Override
    public boolean readyToRecord() {
        return recordingMode && mLastLine == null;
    }

    @Override
    public List<Process> getProcesses() {
        return Collections.singletonList(mLogcatProcess);
    }
}
