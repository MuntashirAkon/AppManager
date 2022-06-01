// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.struct;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.logcat.reader.ScrubberUtils;


// Copyright 2012 Nolan Lawson
// Copyright 2021 Muntashir Al-Islam
public class LogLine {
    public static final int LOG_FATAL = 15;

    private static final int TIMESTAMP_LENGTH = 19;

    private static final Pattern LOG_PATTERN = Pattern.compile(
            // log level
            "(\\w)" + "/" +
                    // tag
                    "([^(].+)" +
                    "\\(\\s*" +
                    // pid
                    "(\\d+)" +
                    // optional weird number that only occurs on ZTE blade
                    "(?:\\*\\s*\\d+)?" +
                    "\\): ");
    private static final String BEGIN = "--------- beginning of ";

    public static boolean omitSensitiveInfo = false;

    @Nullable
    public static LogLine newLogLine(@NonNull String originalLine, boolean expanded, @Nullable Pattern filterPattern) {
        LogLine logLine = new LogLine(originalLine);
        logLine.setExpanded(expanded);

        int startIdx = 0;

        // if the first char is a digit, then this starts out with a timestamp
        // otherwise, it's a legacy log or the beginning of the log output or something
        if (!TextUtils.isEmpty(originalLine)
                && Character.isDigit(originalLine.charAt(0))
                && originalLine.length() >= TIMESTAMP_LENGTH) {
            String timestamp = originalLine.substring(0, TIMESTAMP_LENGTH - 1);
            logLine.setTimestamp(timestamp);
            startIdx = TIMESTAMP_LENGTH; // cut off timestamp
        }

        Matcher matcher = LOG_PATTERN.matcher(originalLine);

        if (matcher.find(startIdx)) {
            char logLevelChar = matcher.group(1).charAt(0);

            String logText = originalLine.substring(matcher.end());
            if (logText.matches("^maxLineHeight.*|Failed to read.*")) {
                logLine.setLogLevel(convertCharToLogLevel('V'));
            } else {
                logLine.setLogLevel(convertCharToLogLevel(logLevelChar));
            }

            String tagText = matcher.group(2);
            if (filterPattern != null && filterPattern.matcher(tagText).matches()) {
                return null;
            }

            logLine.setTag(tagText);
            logLine.setProcessId(Integer.parseInt(matcher.group(3)));

            logLine.setLogOutput(logText);
        } else if (originalLine.startsWith(BEGIN)) {
            Log.d("LogLine", "Started buffer: " + originalLine.substring(BEGIN.length()));
        } else {
            Log.d("LogLine", "Line doesn't match pattern: " + originalLine);
            logLine.setLogOutput(originalLine);
            logLine.setLogLevel(-1);
        }
        return logLine;
    }

    public static int convertCharToLogLevel(char logLevelChar) {
        switch (logLevelChar) {
            case 'A':
                return Log.ASSERT;
            case 'D':
                return Log.DEBUG;
            case 'E':
                return Log.ERROR;
            case 'I':
                return Log.INFO;
            case 'V':
                return Log.VERBOSE;
            case 'W':
                return Log.WARN;
            case 'F':
                return LOG_FATAL;
        }
        return -1;
    }

    public static char convertLogLevelToChar(int logLevel) {
        switch (logLevel) {
            case Log.ASSERT:
                return 'A';
            case Log.DEBUG:
                return 'D';
            case Log.ERROR:
                return 'E';
            case Log.INFO:
                return 'I';
            case Log.VERBOSE:
                return 'V';
            case Log.WARN:
                return 'W';
            case LOG_FATAL:
                return 'F';
        }
        return ' ';
    }

    @NonNull
    private final String mOriginalLine;

    private int mLogLevel;
    private String mTagName;
    private String mLogOutput;
    private int mPid = -1;
    private String mTimestamp;
    private boolean mExpanded = false;

    public LogLine(@NonNull String originalLine) {
        mOriginalLine = originalLine;
    }

    public String getOriginalLine() {
        return mOriginalLine;
    }

    public String getProcessIdText() {
        return Character.toString(convertLogLevelToChar(mLogLevel));
    }

    public int getLogLevel() {
        return mLogLevel;
    }

    public void setLogLevel(int logLevel) {
        mLogLevel = logLevel;
    }

    public String getTagName() {
        return mTagName;
    }

    public void setTag(String tag) {
        mTagName = tag;
    }

    public String getLogOutput() {
        return mLogOutput;
    }

    public void setLogOutput(String logOutput) {
        if (omitSensitiveInfo) {
            mLogOutput = ScrubberUtils.scrubLine(logOutput);
        } else {
            mLogOutput = logOutput;
        }
    }

    public int getProcessId() {
        return mPid;
    }

    public void setProcessId(int processId) {
        this.mPid = processId;
    }

    public String getTimestamp() {
        return mTimestamp;
    }

    public void setTimestamp(String timestamp) {
        this.mTimestamp = timestamp;
    }

    public boolean isExpanded() {
        return mExpanded;
    }

    public void setExpanded(boolean expanded) {
        this.mExpanded = expanded;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LogLine)) return false;
        LogLine logLine = (LogLine) o;
        return mOriginalLine.equals(logLine.mOriginalLine);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mOriginalLine);
    }

    @NonNull
    @Override
    public String toString() {
        return mOriginalLine;
    }
}
