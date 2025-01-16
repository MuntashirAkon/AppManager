// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.struct;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.logcat.helper.LogcatHelper;
import io.github.muntashirakon.AppManager.logcat.reader.ScrubberUtils;


// Copyright 2012 Nolan Lawson
// Copyright 2021 Muntashir Al-Islam
public class LogLine {
    public static final String TAG = LogLine.class.getSimpleName();

    public static final int LOG_FATAL = 15;

    /**
     * %s %5d %5d %c %-8s:
     * %s %s%5d %5d %c %-8s: (Android 7+)
     *
     * @see LogcatHelper#getLogcatArgs(int, boolean)
     */
    private static final Pattern LOG_PATTERN = Pattern.compile(
            // Timestamp
            "(\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+" +
                    // PID
                    "(\\d+)\\s+" +
                    // TID
                    "(\\d+)\\s+" +
                    // Log level
                    "([ADEIVWF])\\s+" +
                    // Tag
                    "(.+?)" +
                    // Message
                    ": (.*)");
    /**
     * This is the old pattern used prior to v4.0.0. Format: {timestamp} {level}/{tag}(\s{pid}): message
     */
    private static final Pattern LOG_PATTERN_LEGACY = Pattern.compile(
            // Timestamp
            "(\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+" +
                    // Log level
                    "([ADEIVWF])/" +
                    // Tag
                    "([^(].+)" +
                    // PID with optional * prefixed number seen on ZTE blade (Android 4.4)
                    "\\(\\s*(\\d+)(?:\\*\\s*\\d+)?\\)" +
                    // Message
                    ": (.*)");
    private static final String BEGIN = "--------- beginning of ";

    public static boolean omitSensitiveInfo = false;

    @Nullable
    public static LogLine newLogLine(@NonNull String originalLine, boolean expanded, @Nullable Pattern filterPattern) {
        LogLine logLine = new LogLine(originalLine);
        logLine.setExpanded(expanded);

        if (matchPattern(originalLine, logLine)) {
            if (filterPattern != null && filterPattern.matcher(logLine.getTagName()).matches()) {
                return null;
            }
            return logLine;
        }
        if (matchPatternLegacy(originalLine, logLine)) {
            if (filterPattern != null && filterPattern.matcher(logLine.getTagName()).matches()) {
                return null;
            }
            return logLine;
        }
        if (originalLine.startsWith(BEGIN)) {
            Log.d(TAG, "Started buffer: " + originalLine.substring(BEGIN.length()));
            return null;
        } else {
            Log.w(TAG, "Line doesn't match pattern: " + originalLine);
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

    @Nullable
    private String mTimestamp;
    private int mLogLevel;
    private String mTagName;
    private String mLogOutput;
    private int mPid = -1;
    private int mTid = -1;
    @Nullable
    private String mPackageName;

    @Nullable
    private String mProcessName;
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

    public int getPid() {
        return mPid;
    }

    public void setPid(int pid) {
        mPid = pid;
    }

    public int getTid() {
        return mTid;
    }

    public void setTid(int tid) {
        mTid = tid;
    }

    @Nullable
    public String getPackageName() {
        return mPackageName;
    }

    public void setPackageName(@Nullable String packageName) {
        mPackageName = packageName;
    }

    @Nullable
    public String getProcessName() {
        return mProcessName;
    }

    public void setProcessName(@Nullable String processName) {
        mProcessName = processName;
    }

    @Nullable
    public String getTimestamp() {
        return mTimestamp;
    }

    public void setTimestamp(@Nullable String timestamp) {
        mTimestamp = timestamp;
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

    private static boolean matchPatternLegacy(@NonNull String originalLine, @NonNull LogLine logLine) {
        Matcher matcher = LOG_PATTERN_LEGACY.matcher(originalLine);
        if (!matcher.matches()) {
            return false;
        }
        // Group 1: Timestamp
        logLine.setTimestamp(Objects.requireNonNull(matcher.group(1)));
        // Group 2: Log level
        logLine.setLogLevel(convertCharToLogLevel(Objects.requireNonNull(matcher.group(2)).charAt(0)));
        // Group 3: Tag
        logLine.setTag(Objects.requireNonNull(matcher.group(3)).trim());
        // Group 4: PID
        logLine.setPid(Integer.parseInt(matcher.group(4)));
        // Group 5: Message
        logLine.setLogOutput(Objects.requireNonNull(matcher.group(5)));
        return true;
    }

    private static boolean matchPattern(@NonNull String originalLine, @NonNull LogLine logLine) {
        Matcher matcher = LOG_PATTERN.matcher(originalLine);
        if (!matcher.matches()) {
            return false;
        }
        // Group 1: Timestamp
        logLine.setTimestamp(Objects.requireNonNull(matcher.group(1)));
        // Group 2: PID
        logLine.setPid(Integer.parseInt(matcher.group(2)));
        // Group 3: TID
        logLine.setTid(Integer.parseInt(matcher.group(3)));
        // Group 4: Log level
        logLine.setLogLevel(convertCharToLogLevel(Objects.requireNonNull(matcher.group(4)).charAt(0)));
        // Group 5: Tag
        logLine.setTag(Objects.requireNonNull(matcher.group(5)).trim());
        // Group 6: Message
        logLine.setLogOutput(Objects.requireNonNull(matcher.group(6)));
        return true;
    }
}
