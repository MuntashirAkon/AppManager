// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.filters.options.FilterOption;

public class FilterItem {
    private static class ExprEvaluator extends AbsExpressionEvaluator {
        private final ArrayMap<Integer, FilterOption> mFilterOptions;
        @Nullable
        private FilterableAppInfo mInfo;
        @Nullable
        private FilterOption.TestResult mResult;

        public ExprEvaluator(ArrayMap<Integer, FilterOption> filterOptions) {
            mFilterOptions = filterOptions;
        }

        public void setInfo(@Nullable FilterableAppInfo info) {
            mInfo = info;
            mResult = new FilterOption.TestResult();
        }

        @Nullable
        public FilterOption.TestResult getResult() {
            return mResult;
        }

        @Override
        protected boolean evalId(@NonNull String id) {
            if (mResult == null) {
                mResult = new FilterOption.TestResult();
            }
            // Extract ID
            int idx = id.lastIndexOf('_');
            int intId;
            if (idx >= 0 && id.length() > (idx + 1)) {
                intId = Integer.parseInt(id.substring(idx + 1));
            } else intId = 0;
            FilterOption option = mFilterOptions.get(intId);
            if (option == null || mInfo == null) {
                return false;
            }
            return option.test(mInfo, mResult).isMatched();
        }
    }

    @NonNull
    private String mName;
    private final ArrayMap<Integer, FilterOption> mFilterOptions;
    private String mExpr = "";
    private boolean customExpr = false;
    // Assign this id to the next filter option (starts with 1)
    private int nextId = 1;

    public FilterItem() {
        this("Untitled");
    }

    private FilterItem(@NonNull String name) {
        mName = name;
        mFilterOptions = new ArrayMap<>();
    }

    @NonNull
    public String getName() {
        return mName;
    }

    public void setName(@NonNull String name) {
        mName = name;
    }

    @NonNull
    public String getExpr() {
        return mExpr;
    }

    public void setExpr(@NonNull String expr) {
        mExpr = expr;
        customExpr = true;
    }

    public boolean addFilterOption(@NonNull FilterOption filterOption) {
        filterOption.id = getNextId();
        String id = filterOption.type + "_" + filterOption.id;
        if (!customExpr) {
            // Add this to expr
            if (TextUtils.isEmpty(mExpr)) {
                mExpr = id;
            } else mExpr += " & " + id;
        }
        return mFilterOptions.put(filterOption.id, filterOption) == null;
    }

    public void updateFilterOptionAt(int i, @NonNull FilterOption filterOption) {
        FilterOption oldFilterOption = mFilterOptions.valueAt(i);
        if (oldFilterOption == null) {
            throw new IllegalArgumentException("Invalid index " + i);
        }
        filterOption.id = oldFilterOption.id;
        mFilterOptions.setValueAt(i, filterOption);
    }

    public boolean removeFilterOptionAt(int i) {
        FilterOption filterOption = mFilterOptions.removeAt(i);
        if (filterOption == null) {
            return false;
        }
        nextId = filterOption.id;
        String idStr = filterOption.type + "_" + filterOption.id;
        if (!customExpr) {
            // Default expression is just all the filters &'ed together
            String[] ops = mExpr.split(" & ");
            StringBuilder sb = new StringBuilder();
            for (String op : ops) {
                if (!idStr.equals(op)) {
                    if (sb.length() > 0) {
                        sb.append(" & ");
                    }
                    sb.append(op);
                }
            }
            mExpr = sb.toString();
        }
        return true;
    }

    public int getSize() {
        return mFilterOptions.size();
    }

    public FilterOption getFilterOptionAt(int i) {
        return mFilterOptions.valueAt(i);
    }

    public List<FilteredItemInfo> getFilteredList(@NonNull List<FilterableAppInfo> allFilterableAppInfo) {
        List<FilteredItemInfo> filteredFilterableAppInfo = new ArrayList<>();
        ExprEvaluator evaluator = new ExprEvaluator(mFilterOptions);
        String expr = TextUtils.isEmpty(mExpr) ? "true" : mExpr;
        for (FilterableAppInfo info : allFilterableAppInfo) {
            evaluator.setInfo(info);
            boolean eval = evaluator.evaluate(expr);
            FilterOption.TestResult result = Objects.requireNonNull(evaluator.getResult());
            if (eval) {
                filteredFilterableAppInfo.add(new FilteredItemInfo(info, result));
            }
        }
        return filteredFilterableAppInfo;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        JSONArray array = new JSONArray();
        for (FilterOption filterOption : mFilterOptions.values()) {
            array.put(filterOption.toJson());
        }
        object.put("name", mName);
        object.put("expr", mExpr);
        object.put("options", array);
        return object;
    }

    @NonNull
    public static FilterItem fromJson(@NonNull JSONObject object) throws JSONException {
        FilterItem item = new FilterItem(object.getString("name"));
        item.mExpr = object.getString("expr");
        JSONArray array = object.getJSONArray("options");
        for (int i = 0; i < array.length(); ++i) {
            FilterOption option = FilterOption.fromJson(array.getJSONObject(i));
            item.mFilterOptions.put(option.id, option);
        }
        return item;
    }

    private int getNextId() {
        // Find next ID
        while (mFilterOptions.containsKey(nextId)) {
            ++nextId;
        }
        return nextId;
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
