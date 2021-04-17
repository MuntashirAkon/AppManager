/*
 * Copyright (c) 2021 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.logcat.struct;

import android.text.TextUtils;
import android.util.Log;

import io.github.muntashirakon.AppManager.logcat.reader.ScrubberUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class LogLine {
    public static final int LOG_FATAL = 15;

    private static final int TIMESTAMP_LENGTH = 19;

    private static final Pattern logPattern = Pattern.compile(
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

    private int logLevel;
    private String tag;
    private String logOutput;
    private int processId = -1;
    private String timestamp;
    private boolean expanded = false;
    private boolean highlighted = false;

    public static boolean omitSensitiveInfo = false;

    public static LogLine newLogLine(String originalLine, boolean expanded, String filterPattern) {

        LogLine logLine = new LogLine();
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

        Matcher matcher = logPattern.matcher(originalLine);

        if (matcher.find(startIdx)) {
            char logLevelChar = matcher.group(1).charAt(0);

            String logText = originalLine.substring(matcher.end());
            if (logText.matches("^maxLineHeight.*|Failed to read.*")) {
                logLine.setLogLevel(convertCharToLogLevel('V'));
            } else {
                logLine.setLogLevel(convertCharToLogLevel(logLevelChar));
            }

            String tagText = matcher.group(2);
            if (tagText.matches(filterPattern)) {
                logLine.setLogLevel(convertCharToLogLevel('V'));
            }

            logLine.setTag(tagText);
            logLine.setProcessId(Integer.parseInt(matcher.group(3)));

            logLine.setLogOutput(logText);

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

    public String getOriginalLine() {

        if (logLevel == -1) { // starter line like "begin of log etc. etc."
            return logOutput;
        }

        StringBuilder stringBuilder = new StringBuilder();

        if (timestamp != null) {
            stringBuilder.append(timestamp).append(' ');
        }

        stringBuilder.append(convertLogLevelToChar(logLevel))
                .append('/')
                .append(tag)
                .append('(')
                .append(processId)
                .append("): ")
                .append(logOutput);

        return stringBuilder.toString();
    }

    public String getProcessIdText() {
        return Character.toString(convertLogLevelToChar(logLevel));
    }

    public int getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(int logLevel) {
        this.logLevel = logLevel;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getLogOutput() {
        return logOutput;
    }

    public void setLogOutput(String logOutput) {
        if (omitSensitiveInfo) {
            this.logOutput = ScrubberUtils.scrubLine(logOutput);
        } else {
            this.logOutput = logOutput;
        }
    }

    public int getProcessId() {
        return processId;
    }

    public void setProcessId(int processId) {
        this.processId = processId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public boolean isHighlighted() {
        return highlighted;
    }

    public void setHighlighted(boolean highlighted) {
        this.highlighted = highlighted;
    }
}
