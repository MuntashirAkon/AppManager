// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.struct;

import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.logcat.helper.LogcatHelper;
import io.github.muntashirakon.AppManager.logcat.reader.ScrubberUtils;
import io.github.muntashirakon.AppManager.users.Owners;


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
                    // UID PID
                    "(.+\\d+)\\s+" +
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
    private int mUid = -1;
    @Nullable
    private String mUidOwner;
    @Nullable
    private String mPackageName;

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

    public int getUid() {
        return mUid;
    }

    public void setUid(int uid) {
        mUid = uid;
    }

    @Nullable
    public String getUidOwner() {
        return mUidOwner;
    }

    public void setUidOwner(@Nullable String owner) {
        mUidOwner = owner;
    }

    @Nullable
    public String getPackageName() {
        return mPackageName;
    }

    public void setPackageName(@Nullable String packageName) {
        mPackageName = packageName;
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
        // Group 2: UID PID
        String[] uidPid = Objects.requireNonNull(matcher.group(2)).split("\\s+", 2);
        if (uidPid.length == 2) {
            String owner = uidPid[0];
            int uid = Owners.parseUid(owner);
            logLine.setUidOwner(owner);
            logLine.setUid(uid);
            // Set package name
            logLine.setPackageName(retrievePackageName(uid));
        }
        logLine.setPid(Integer.parseInt(uidPid[uidPid.length == 2 ? 1 : 0]));
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

    private static final LruCache<Integer, String> sUidPackageNameCache = new LruCache<>(300);

    @Nullable
    private static String retrievePackageName(int uid) {
        if (uid < 0) {
            return null;
        }
        String packageName = sUidPackageNameCache.get(uid);
        if (packageName != null) {
            return TextUtils.isEmpty(packageName) ? null : packageName;
        }
        // TODO: 1/18/25
        // Assumptions for multiple UIDs:
        // 1. Process name likely matches/starts with the package name
        // 2. Shortest package name is preferred (the primary package in a shared UID is likely to have the shortest package name)
        // Ignored assumption:
        // 3. Primary package is likely to be installed first
        try {
            String[] packages = PackageManagerCompat.getPackageManager().getPackagesForUid(uid);
            String selectedPackage = null;
            if (packages == null || packages.length == 0) {
                selectedPackage = null;
            } else {
                if (packages.length == 1) {
                    selectedPackage = packages[0];
                } else {
                    int shortestIndex = 0;
                    for (int i = 0; i < packages.length; ++i) {
                        if (packages[shortestIndex].length() > packages[i].length()) {
                            shortestIndex = i;
                        }
                    }
                    if (selectedPackage == null) {
                        selectedPackage = packages[shortestIndex];
                    }
                }
            }
            if (selectedPackage != null) {
                sUidPackageNameCache.put(uid, selectedPackage);
            } else {
                // Still cache this data
                sUidPackageNameCache.put(uid, "");
            }
            return selectedPackage;
        } catch (RemoteException e) {
            return null;
        }
    }
}
