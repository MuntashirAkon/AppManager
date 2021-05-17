// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class JSONUtils {
    @Nullable
    public static <T> JSONArray getJSONArray(@Nullable final T[] typicalArray) {
        if (typicalArray == null) return null;
        JSONArray jsonArray = new JSONArray();
        for (T elem : typicalArray) jsonArray.put(elem);
        return jsonArray;
    }

    @Nullable
    public static JSONArray getJSONArray(@Nullable final int[] typicalArray) {
        if (typicalArray == null) return null;
        JSONArray jsonArray = new JSONArray();
        for (int elem : typicalArray) jsonArray.put(elem);
        return jsonArray;
    }

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

    public static String getString(@NonNull final JSONObject jsonObject, @NonNull String key, String defaultValue) {
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
}
