// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;

public final class JSONUtils {
    @Contract("!null -> !null")
    @Nullable
    public static <T> JSONArray getJSONArray(@Nullable final T[] typicalArray) {
        if (typicalArray == null) return null;
        JSONArray jsonArray = new JSONArray();
        for (T elem : typicalArray) jsonArray.put(elem);
        return jsonArray;
    }

    @Contract("!null -> !null")
    @Nullable
    public static JSONArray getJSONArray(@Nullable final int[] typicalArray) {
        if (typicalArray == null) return null;
        JSONArray jsonArray = new JSONArray();
        for (int elem : typicalArray) jsonArray.put(elem);
        return jsonArray;
    }

    @Contract("!null -> !null")
    @Nullable
    public static <T> JSONArray getJSONArray(@Nullable final Collection<T> collection) {
        if (collection == null) return null;
        JSONArray jsonArray = new JSONArray();
        for (T elem : collection) jsonArray.put(elem);
        return jsonArray;
    }

    @Contract("_,!null -> !null")
    @Nullable
    public static <T> T[] getArray(Class<T> clazz, @Nullable final JSONArray jsonArray)
            throws JSONException {
        if (jsonArray == null) return null;
        //noinspection unchecked
        T[] typicalArray = (T[]) Array.newInstance(clazz, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); ++i) typicalArray[i] = clazz.cast(jsonArray.get(i));
        return typicalArray;
    }

    @Contract("!null -> !null")
    @Nullable
    public static int[] getIntArray(@Nullable final JSONArray jsonArray)
            throws JSONException {
        if (jsonArray == null) return null;
        int[] typicalArray = new int[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); ++i) typicalArray[i] = jsonArray.getInt(i);
        return typicalArray;
    }

    @Contract("!null -> !null")
    @Nullable
    public static <T> ArrayList<T> getArray(@Nullable final JSONArray jsonArray)
            throws JSONException {
        if (jsonArray == null) return null;
        ArrayList<T> arrayList = new ArrayList<>(jsonArray.length());
        for (int i = 0; i < jsonArray.length(); ++i) {
            //noinspection unchecked
            arrayList.add((T) jsonArray.get(i));
        }
        return arrayList;
    }

    @Contract("_,_,!null -> !null")
    @Nullable
    public static String getString(@NonNull final JSONObject jsonObject, @NonNull String key,
                                   @Nullable String defaultValue) {
        try {
            return jsonObject.getString(key);
        } catch (JSONException e) {
            return defaultValue;
        }
    }

    @Nullable
    public static Integer getIntOrNull(@NonNull final JSONObject jsonObject, @NonNull String key) {
        try {
            return jsonObject.getInt(key);
        } catch (JSONException ignore) {
        }
        return null;
    }

    @Nullable
    public static String getString(@NonNull final JSONObject jsonObject, @NonNull String key)
            throws JSONException {
        Object obj = jsonObject.get(key);
        if (obj == JSONObject.NULL) {
            return null;
        }
        return jsonObject.getString(key);
    }

    @Nullable
    public static String optString(@NonNull final JSONObject jsonObject, @NonNull String key) {
        Object obj = jsonObject.opt(key);
        if (obj == null || obj == JSONObject.NULL) {
            return null;
        }
        return jsonObject.optString(key);
    }


    @Nullable
    public static String optString(@NonNull final JSONObject jsonObject, @NonNull String key,
                                   @Nullable String fallback) {
        Object obj = jsonObject.opt(key);
        if (obj == null || obj == JSONObject.NULL) {
            return null;
        }
        return jsonObject.optString(key, fallback);
    }
}
