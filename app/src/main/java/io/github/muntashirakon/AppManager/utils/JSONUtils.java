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
    @Contract("null -> null")
    @Nullable
    public static <T> JSONArray getJSONArray(@Nullable final T[] typicalArray) {
        if (typicalArray == null) return null;
        JSONArray jsonArray = new JSONArray();
        for (T elem : typicalArray) jsonArray.put(elem);
        return jsonArray;
    }

    @Contract("null -> null")
    @Nullable
    public static JSONArray getJSONArray(@Nullable final int[] typicalArray) {
        if (typicalArray == null) return null;
        JSONArray jsonArray = new JSONArray();
        for (int elem : typicalArray) jsonArray.put(elem);
        return jsonArray;
    }

    @Contract("null -> null")
    @Nullable
    public static <T> JSONArray getJSONArray(@Nullable final Collection<T> collection) {
        if (collection == null || collection.size() == 0) return null;
        JSONArray jsonArray = new JSONArray();
        for (T elem : collection) jsonArray.put(elem);
        return jsonArray;
    }

    @NonNull
    public static <T> T[] getArray(Class<T> clazz, @NonNull final JSONArray jsonArray)
            throws JSONException {
        //noinspection unchecked
        T[] typicalArray = (T[]) Array.newInstance(clazz, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); ++i) typicalArray[i] = clazz.cast(jsonArray.get(i));
        return typicalArray;
    }

    @NonNull
    public static int[] getIntArray(@NonNull final JSONArray jsonArray)
            throws JSONException {
        int[] typicalArray = new int[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); ++i) typicalArray[i] = jsonArray.getInt(i);
        return typicalArray;
    }

    @NonNull
    public static <T> ArrayList<T> getArray(@NonNull final JSONArray jsonArray)
            throws JSONException {
        ArrayList<T> arrayList = new ArrayList<>(jsonArray.length());
        for (int i = 0; i < jsonArray.length(); ++i) {
            //noinspection unchecked
            arrayList.add((T) jsonArray.get(i));
        }
        return arrayList;
    }

    @Nullable
    public static JSONArray getJSONArray(@NonNull final JSONObject jsonObject, @NonNull String key) {
        try {
            return jsonObject.getJSONArray(key);
        } catch (JSONException e) {
            return null;
        }
    }

    @Nullable
    public static JSONObject getJSONObject(@NonNull final JSONObject jsonObject, @NonNull String key) {
        try {
            return jsonObject.getJSONObject(key);
        } catch (JSONException e) {
            return null;
        }
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

    public static boolean getBoolean(@NonNull final JSONObject jsonObject, @NonNull String key, boolean defaultValue) {
        try {
            return jsonObject.getBoolean(key);
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

    public static long getLong(@NonNull final JSONObject jsonObject, @NonNull String key, long defaultValue) {
        try {
            return jsonObject.getLong(key);
        } catch (JSONException ignore) {
        }
        return defaultValue;
    }
}
