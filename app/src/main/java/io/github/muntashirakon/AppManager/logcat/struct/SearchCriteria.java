// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.struct;

import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.utils.ArrayUtils;

// Copyright 2012 Nolan Lawson
public class SearchCriteria {

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({TYPE_MSG, TYPE_PID, TYPE_PKG, TYPE_TAG, TYPE_UID})
    private @interface FilterType {
    }

    private static final String TYPE_MSG = "msg";
    private static final String TYPE_PID = "pid";
    private static final String TYPE_PKG = "pkg";
    private static final String TYPE_TAG = "tag";
    private static final String TYPE_UID = "uid";

    private static final String[] TYPES = new String[]{
            TYPE_MSG, TYPE_PID, TYPE_PKG, TYPE_TAG, TYPE_UID,
    };

    public static final String PID_KEYWORD = TYPE_PID + ":";
    public static final String PKG_KEYWORD = TYPE_PKG + "=:";
    public static final String TAG_KEYWORD = TYPE_TAG + "=:";
    public static final String UID_KEYWORD = TYPE_UID + "=:";

    @Nullable
    public final String query;
    private final List<Filter> mFilters = new ArrayList<>();

    public SearchCriteria(@Nullable String query) {
        this.query = query;
        if (query == null) {
            return;
        }
        String[] parts = query.split(" ");
        // Check for keywords, we support the following keywords:
        // 1. pid:<int>
        // 2. pkg:<string>|"<string>"
        // 3. proc:<string>|"<string>"
        // 4. tag:<string>|"<string>"
        // Each can take regex if it has ~: separator instead of :
        // For exact match, it has =: instead of :
        // Each can be inverse if it begins with -
        StringBuilder lastString = null;
        StringBuilder queryString = new StringBuilder();
        for (String part : parts) {
            if (lastString != null) {
                // This part belongs to the last filter
                if (part.endsWith("\"")) {
                    Filter filter = mFilters.get(mFilters.size() - 1);
                    filter.setValue(lastString + " " + part.substring(0, parts.length - 1));
                    lastString = null;
                } else lastString.append(" ").append(part);
                continue;
            }
            int colon = part.indexOf(":");
            if (colon > 0) {
                String type = part.substring(0, colon);
                boolean inv = type.startsWith("-");
                boolean regex = type.endsWith("~");
                boolean exact = type.endsWith("=");
                // Check for inverse
                if (inv && type.length() > 1) type = type.substring(1);
                if (regex && type.length() > 1) type = type.substring(0, type.length() - 1);
                if (exact && type.length() > 1) type = type.substring(0, type.length() - 1);
                if (ArrayUtils.contains(TYPES, type)) {
                    // Valid type
                    Filter filter = new Filter(type, regex, inv, exact);
                    mFilters.add(filter);
                    if (colon + 1 < part.length()) {
                        String value = part.substring(colon + 1);
                        // Check if value begins with quote
                        if (value.startsWith("\"")) {
                            if (value.length() > 1) {
                                if (value.endsWith("\"")) {
                                    filter.setValue(value.substring(1, value.length() - 1));
                                } else {
                                    // Value didn't end here
                                    lastString = new StringBuilder(value.substring(1));
                                }
                            } else lastString = new StringBuilder();
                        } else filter.setValue(value);
                    }
                    continue;
                }
            }
            // Query string
            queryString.append(" ").append(part);
        }
        String text = queryString.toString().trim();
        if (!text.isEmpty()) {
            mFilters.add(new Filter(TYPE_MSG, queryString.toString().trim()));
        }
    }

    public boolean isEmpty() {
        for (Filter filter : mFilters) {
            if (!filter.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean matches(LogLine logLine) {
        // Consider the criteria to be ANDed
        for (Filter filter : mFilters) {
            if (!filter.matches(logLine)) {
                return false;
            }
        }
        return true;
    }

    private static class Filter {
        @FilterType
        private final String mType;
        private final boolean mRegex;
        private final boolean mExact;
        private final boolean mInverse;
        @Nullable
        private Object mValue;

        public Filter(@FilterType String type, boolean regex, boolean inverse, boolean exact) {
            mType = type;
            mRegex = regex;
            mInverse = inverse;
            mExact = exact;
        }

        public Filter(@FilterType String type, @Nullable String value) {
            mType = type;
            mRegex = false;
            mInverse = false;
            mExact = false;
            mValue = getRealValue(value);
        }

        public void setValue(@Nullable String value) {
            mValue = getRealValue(value);
        }

        public boolean isEmpty() {
            if (mValue instanceof CharSequence) {
                return TextUtils.isEmpty((CharSequence) mValue);
            }
            return mValue == null;
        }

        /**
         * @noinspection DataFlowIssue
         */
        public boolean matches(@NonNull LogLine logLine) {
            if (isEmpty()) {
                // Empty always matches
                return true;
            }
            boolean matches;
            switch (mType) {
                case TYPE_MSG: {
                    String tag = logLine.getTagName();
                    String out = logLine.getLogOutput();
                    if (tag == null && out == null) {
                        return false;
                    }
                    if (mRegex) {
                        Pattern p = (Pattern) mValue;
                        matches = matchPattern(p, tag) || matchPattern(p, out);
                    } else {
                        String query = (String) mValue;
                        matches = matchQuery(query, tag, mExact) || matchQuery(query, tag, mExact);
                    }
                    break;
                }
                case TYPE_PID: {
                    if (mRegex) {
                        matches = matchPattern((Pattern) mValue, String.valueOf(logLine.getPid()));
                    } else {
                        matches = logLine.getPid() == ((int) mValue);
                    }
                    break;
                }
                case TYPE_PKG: {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                        // Package name is not available for these versions
                        // Use match-all
                        return true;
                    }
                    String packageName = logLine.getPackageName();
                    if (mRegex) {
                        matches = matchPattern((Pattern) mValue, packageName);
                    } else {
                        matches = matchQuery((String) mValue, packageName, mExact);
                    }
                    break;
                }
                case TYPE_UID: {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                        // UID is not available for these versions
                        // Use match-all
                        return true;
                    }
                    String owner = logLine.getUidOwner();
                    int uid = logLine.getUid();
                    if (mRegex) {
                        Pattern p = (Pattern) mValue;
                        matches = matchPattern(p, owner) || matchPattern(p, String.valueOf(uid));
                    } else {
                        if (mValue instanceof Integer) {
                            matches = uid == ((int) mValue);
                        } else matches = matchQuery((String) mValue, owner, mExact);
                    }
                    break;
                }
                case TYPE_TAG: {
                    String tag = logLine.getTagName();
                    if (mRegex) {
                        matches = matchPattern((Pattern) mValue, tag);
                    } else {
                        matches = matchQuery((String) mValue, tag, mExact);
                    }
                    break;
                }
                default:
                    throw new IllegalArgumentException("Invalid filter: " + mType);
            }
            return mInverse != matches;
        }

        @Nullable
        private Object getRealValue(@Nullable String value) {
            if (value == null) {
                return null;
            }
            if (mRegex) {
                return Pattern.compile(Pattern.quote(value));
            }
            switch (mType) {
                case TYPE_UID: {
                    if (TextUtils.isDigitsOnly(value)) {
                        return Integer.parseInt(value);
                    } // else fallthrough
                }
                case TYPE_MSG:
                case TYPE_PKG:
                case TYPE_TAG: {
                    return mExact ? value : value.toLowerCase(Locale.ROOT);
                }
                case TYPE_PID: {
                    return TextUtils.isDigitsOnly(value) ? Integer.parseInt(value) : null;
                }
                default:
                    throw new IllegalArgumentException("Invalid filter: " + mType);
            }
        }

        @NonNull
        @Override
        public String toString() {
            return "Filter{" +
                    "mType='" + mType + '\'' +
                    ", mRegex=" + mRegex +
                    ", mExact=" + mExact +
                    ", mInverse=" + mInverse +
                    ", mValue=" + mValue +
                    '}';
        }

        private static boolean matchPattern(@NonNull Pattern pattern, @Nullable String value) {
            if (value == null) {
                return false;
            }
            return pattern.matcher(value).matches();
        }

        private static boolean matchQuery(@NonNull String query, @Nullable String value, boolean exact) {
            if (value == null) {
                return false;
            }
            return exact ? value.equals(query) : value.toLowerCase(Locale.ROOT).contains(query);
        }
    }
}
