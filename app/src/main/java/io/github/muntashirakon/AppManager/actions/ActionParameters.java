// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;

/**
 * Named parameters for an Action.
 */
public final class ActionParameters implements IJsonSerializer, Parcelable {
    private final Bundle mParameters;

    public ActionParameters() {
        mParameters = new Bundle();
    }

    public ActionParameters(@NonNull Bundle parameters) {
        mParameters = new Bundle(parameters);
    }

    private ActionParameters(@NonNull JSONObject object) throws JSONException {
        mParameters = fromJsonObject(object);
    }

    private ActionParameters(@NonNull Parcel in) {
        Bundle parameters = in.readBundle(ActionParameters.class.getClassLoader());
        mParameters = parameters == null ? new Bundle() : parameters;
    }

    @NonNull
    public ActionParameters put(@NonNull String key, @Nullable Object value) {
        if (value == null) {
            mParameters.putString(key, null);
        } else if (value instanceof String) {
            mParameters.putString(key, (String) value);
        } else if (value instanceof Boolean) {
            mParameters.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            mParameters.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            mParameters.putLong(key, (Long) value);
        } else if (value instanceof Double) {
            mParameters.putDouble(key, (Double) value);
        } else if (value instanceof Float) {
            mParameters.putFloat(key, (Float) value);
        } else if (value instanceof String[]) {
            mParameters.putStringArray(key, (String[]) value);
        } else if (value instanceof int[]) {
            mParameters.putIntArray(key, (int[]) value);
        } else if (value instanceof Bundle) {
            mParameters.putBundle(key, (Bundle) value);
        } else if (value instanceof Parcelable) {
            mParameters.putParcelable(key, (Parcelable) value);
        } else {
            throw new IllegalArgumentException("Unsupported action parameter: " + value.getClass());
        }
        return this;
    }

    public boolean containsKey(@NonNull String key) {
        return mParameters.containsKey(key);
    }

    @Nullable
    public Object get(@NonNull String key) {
        return mParameters.get(key);
    }

    @NonNull
    public Bundle asBundle() {
        return new Bundle(mParameters);
    }

    @NonNull
    public Set<String> keySet() {
        return Collections.unmodifiableSet(mParameters.keySet());
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        return toJsonObject(mParameters);
    }

    public static final JsonDeserializer.Creator<ActionParameters> DESERIALIZER = ActionParameters::new;

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeBundle(mParameters);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ActionParameters> CREATOR = new Creator<ActionParameters>() {
        @NonNull
        @Override
        public ActionParameters createFromParcel(Parcel in) {
            return new ActionParameters(in);
        }

        @NonNull
        @Override
        public ActionParameters[] newArray(int size) {
            return new ActionParameters[size];
        }
    };

    @NonNull
    private static JSONObject toJsonObject(@NonNull Bundle bundle) throws JSONException {
        JSONObject object = new JSONObject();
        for (String key : bundle.keySet()) {
            object.put(key, toJsonValue(bundle.get(key)));
        }
        return object;
    }

    @NonNull
    private static Object toJsonValue(Object value) throws JSONException {
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Number) {
            return value == null ? JSONObject.NULL : value;
        }
        if (value instanceof Bundle) {
            return toJsonObject((Bundle) value);
        }
        if (value instanceof String[]) {
            return new JSONArray(value);
        }
        if (value instanceof int[]) {
            return new JSONArray(value);
        }
        throw new IllegalArgumentException("Unsupported JSON action parameter: " + value.getClass());
    }

    @NonNull
    private static Bundle fromJsonObject(@NonNull JSONObject object) throws JSONException {
        Bundle bundle = new Bundle();
        JSONArray names = object.names();
        if (names == null) {
            return bundle;
        }
        for (int i = 0; i < names.length(); ++i) {
            String name = names.getString(i);
            Object value = object.get(name);
            putJsonValue(bundle, name, value);
        }
        return bundle;
    }

    private static void putJsonValue(@NonNull Bundle bundle, @NonNull String key,
                                     @NonNull Object value) throws JSONException {
        if (value == JSONObject.NULL) {
            bundle.putString(key, null);
        } else if (value instanceof String) {
            bundle.putString(key, (String) value);
        } else if (value instanceof Boolean) {
            bundle.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            bundle.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            bundle.putLong(key, (Long) value);
        } else if (value instanceof Double) {
            bundle.putDouble(key, (Double) value);
        } else if (value instanceof JSONObject) {
            bundle.putBundle(key, fromJsonObject((JSONObject) value));
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            if (array.length() == 0 || array.get(0) instanceof String) {
                List<String> values = new ArrayList<>();
                for (int i = 0; i < array.length(); ++i) values.add(array.getString(i));
                bundle.putStringArray(key, values.toArray(new String[0]));
            } else {
                int[] values = new int[array.length()];
                for (int i = 0; i < array.length(); ++i) values[i] = array.getInt(i);
                bundle.putIntArray(key, values);
            }
        } else {
            throw new IllegalArgumentException("Unsupported JSON action parameter: " + value.getClass());
        }
    }
}
