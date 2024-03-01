// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.filters.options.FilterOption;

public class FilterItem {
    @NonNull
    private String mName;
    private final List<FilterOption> mFilterOptions;

    public FilterItem() {
        this("Untitled");
    }

    private FilterItem(@NonNull String name) {
        mName = name;
        mFilterOptions = Collections.synchronizedList(new ArrayList<>());
    }

    @NonNull
    public String getName() {
        return mName;
    }

    public void setName(@NonNull String name) {
        mName = name;
    }

    public List<FilterOption> getOptions() {
        return mFilterOptions;
    }

    public List<FilteredItemInfo> getFilteredList(@NonNull List<FilterableAppInfo> allFilterableAppInfo) {
        List<FilteredItemInfo> filteredFilterableAppInfo = new ArrayList<>();
        for (FilterableAppInfo info : allFilterableAppInfo) {
            FilterOption.TestResult result = new FilterOption.TestResult();
            for (FilterOption option : mFilterOptions) {
                if (!option.test(info, result).isMatched()) {
                    break;
                }
            }
            if (result.isMatched()) {
                filteredFilterableAppInfo.add(new FilteredItemInfo(info, result));
            }
        }
        return filteredFilterableAppInfo;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        JSONArray array = new JSONArray();
        for (FilterOption filterOption : mFilterOptions) {
            array.put(filterOption.toJson());
        }
        object.put("name", mName);
        object.put("options", array); // FIXME: 7/2/24 Store them as key-value pairs to allow more than one use of a single option
        return object;
    }

    @NonNull
    public static FilterItem fromJson(@NonNull JSONObject object) throws JSONException {
        FilterItem item = new FilterItem(object.getString("name"));
        JSONArray array = object.getJSONArray("options");
        for (int i = 0; i < array.length(); ++i) {
            FilterOption option = FilterOption.fromJson(array.getJSONObject(i));
            item.mFilterOptions.add(option);
        }
        return item;
    }

    public static class FilteredItemInfo {
        public final FilterableAppInfo info;
        public final FilterOption.TestResult result;

        FilteredItemInfo(FilterableAppInfo info, FilterOption.TestResult result) {
            this.info = info;
            this.result = result;
        }
    }
}
