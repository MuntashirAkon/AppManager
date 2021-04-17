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

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchCriteria {
    public static final String PID_KEYWORD = "pid:";
    public static final String TAG_KEYWORD = "tag:";

    private static final Pattern PID_PATTERN = Pattern.compile("pid:(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG_PATTERN = Pattern.compile("tag:(\"[^\"]+\"|\\S+)", Pattern.CASE_INSENSITIVE);

    private int pid = -1;
    private String tag;
    private final String searchText;
    private int searchTextAsInt = -1;

    public SearchCriteria(@Nullable CharSequence inputQuery) {
        // Check for the "pid" keyword
        StringBuilder query = new StringBuilder(inputQuery == null ? "" : inputQuery);
        Matcher pidMatcher = PID_PATTERN.matcher(query);
        if (pidMatcher.find()) {
            try {
                pid = Integer.parseInt(Objects.requireNonNull(pidMatcher.group(1)));
                query.replace(pidMatcher.start(), pidMatcher.end(), ""); // remove
                // from
                // search
                // string
            } catch (NumberFormatException ignore) {
            }
        }
        // Check for the "tag" keyword
        Matcher tagMatcher = TAG_PATTERN.matcher(query);
        if (tagMatcher.find()) {
            tag = Objects.requireNonNull(tagMatcher.group(1));
            if (tag.startsWith("\"") && tag.endsWith("\"")) {
                tag = tag.substring(1, tag.length() - 1); // remove quotes
            }
            query.replace(tagMatcher.start(), tagMatcher.end(), ""); // remove
            // from
            // search
            // string
        }
        // Everything else becomes a search term
        searchText = query.toString().trim();
        try {
            searchTextAsInt = Integer.parseInt(searchText);
        } catch (NumberFormatException ignore) {
        }
    }

    public boolean isEmpty() {
        return pid == -1 && TextUtils.isEmpty(tag) && TextUtils.isEmpty(searchText);
    }

    public boolean matches(LogLine logLine) {
        // Consider the criteria to be ANDed
        if (!checkFoundPid(logLine)) {
            return false;
        }
        if (!checkFoundTag(logLine)) {
            return false;
        }
        return checkFoundText(logLine);
    }

    private boolean checkFoundText(LogLine logLine) {
        return TextUtils.isEmpty(searchText)
                || (searchTextAsInt != -1 && searchTextAsInt == logLine.getProcessId())
                || (logLine.getTag() != null && logLine.getTag().toLowerCase(Locale.ROOT).contains(searchText.toLowerCase(Locale.ROOT)))
                || (logLine.getLogOutput() != null && logLine.getLogOutput().toLowerCase(Locale.ROOT).contains(searchText.toLowerCase(Locale.ROOT)));
    }

    private boolean checkFoundTag(LogLine logLine) {
        return TextUtils.isEmpty(tag)
                || (logLine.getTag() != null && logLine.getTag().toLowerCase(Locale.ROOT).contains(tag.toLowerCase(Locale.ROOT)));
    }

    private boolean checkFoundPid(LogLine logLine) {
        return pid == -1 || logLine.getProcessId() == pid;
    }

}
