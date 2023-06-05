// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.LinkedHashMap;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.ListOptions;

public class FmListOptions extends ListOptions {
    public static final String TAG = FmListOptions.class.getSimpleName();

    @IntDef({SORT_BY_NAME, SORT_BY_LAST_MODIFIED, SORT_BY_SIZE, SORT_BY_TYPE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SortOrder {
    }

    public static final int SORT_BY_NAME = 0;
    public static final int SORT_BY_LAST_MODIFIED = 1;
    public static final int SORT_BY_SIZE = 2;
    public static final int SORT_BY_TYPE = 3;

    @IntDef(flag = true, value = {OPTIONS_DISPLAY_DOT_FILES, OPTIONS_FOLDERS_FIRST})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Options {
    }

    public static final int OPTIONS_DISPLAY_DOT_FILES = 1 << 0;
    public static final int OPTIONS_FOLDERS_FIRST = 1 << 1;
    public static final int OPTIONS_ONLY_FOR_THIS_FOLDER = 1 << 2; // TODO: 11/12/22

    private static final LinkedHashMap<Integer, Integer> SORT_ITEMS_MAP = new LinkedHashMap<Integer, Integer>() {{
        put(SORT_BY_NAME, R.string.sort_by_filename);
        put(SORT_BY_LAST_MODIFIED, R.string.sort_by_last_modified);
        put(SORT_BY_SIZE, R.string.sort_by_file_size);
        put(SORT_BY_TYPE, R.string.sort_by_file_type);
    }};

    private static final LinkedHashMap<Integer, Integer> OPTIONS_MAP = new LinkedHashMap<Integer, Integer>() {{
        put(OPTIONS_DISPLAY_DOT_FILES, R.string.option_display_dot_files);
        put(OPTIONS_FOLDERS_FIRST, R.string.option_display_folders_on_top);
    }};

    @Nullable
    @Override
    public LinkedHashMap<Integer, Integer> getSortIdLocaleMap() {
        return SORT_ITEMS_MAP;
    }

    @Nullable
    @Override
    public LinkedHashMap<Integer, Integer> getFilterFlagLocaleMap() {
        return null;
    }

    @Nullable
    @Override
    public LinkedHashMap<Integer, Integer> getOptionIdLocaleMap() {
        return OPTIONS_MAP;
    }
}
