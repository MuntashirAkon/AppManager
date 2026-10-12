// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;

/**
 * Payload for an action state.
 */
public final class ActionValue implements IJsonSerializer, Parcelable {
    private final Object mValue;

    private ActionValue(Object value) {
        if (value == null || value == JSONObject.NULL || value instanceof String
                || value instanceof Boolean || value instanceof Number
                || value instanceof JSONObject || value instanceof JSONArray) {
            mValue = value == null ? JSONObject.NULL : value;
        } else {
            throw new IllegalArgumentException("Action values must be JSON-compatible");
        }
    }

    private ActionValue(@NonNull JSONObject object) throws JSONException {
        this(object.get("value"));
    }

    @NonNull
    public static ActionValue of(@NonNull Object value) {
        return new ActionValue(value);
    }

    public Object getValue() {
        return mValue;
    }

    public boolean isSameValue(@NonNull ActionValue other) {
        if (mValue instanceof JSONObject && other.mValue instanceof JSONObject) {
            return mValue.toString().equals(other.mValue.toString());
        }
        if (mValue instanceof JSONArray && other.mValue instanceof JSONArray) {
            return mValue.toString().equals(other.mValue.toString());
        }
        return Objects.equals(mValue, other.mValue);
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("value", mValue);
        return object;
    }

    public static final JsonDeserializer.Creator<ActionValue> DESERIALIZER = ActionValue::new;

    private ActionValue(@NonNull Parcel in) {
        try {
            switch (in.readInt()) {
                case 0:
                    mValue = JSONObject.NULL;
                    break;
                case 1:
                    mValue = in.readString();
                    break;
                case 2:
                    mValue = in.readInt() != 0;
                    break;
                case 3:
                    mValue = in.readInt();
                    break;
                case 4:
                    mValue = in.readLong();
                    break;
                case 5:
                    mValue = in.readDouble();
                    break;
                case 6:
                    mValue = new JSONObject(Objects.requireNonNull(in.readString()));
                    break;
                case 7:
                    mValue = new JSONArray(Objects.requireNonNull(in.readString()));
                    break;
                default:
                    throw new IllegalArgumentException("Invalid action value type");
            }
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid action value", e);
        }
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        if (mValue == JSONObject.NULL) {
            dest.writeInt(0);
        } else if (mValue instanceof String) {
            dest.writeInt(1);
            dest.writeString((String) mValue);
        } else if (mValue instanceof Boolean) {
            dest.writeInt(2);
            dest.writeInt((Boolean) mValue ? 1 : 0);
        } else if (mValue instanceof Integer) {
            dest.writeInt(3);
            dest.writeInt((Integer) mValue);
        } else if (mValue instanceof Long) {
            dest.writeInt(4);
            dest.writeLong((Long) mValue);
        } else if (mValue instanceof Number) {
            dest.writeInt(5);
            dest.writeDouble(((Number) mValue).doubleValue());
        } else if (mValue instanceof JSONObject || mValue instanceof JSONArray) {
            dest.writeInt(mValue instanceof JSONObject ? 6 : 7);
            dest.writeString(mValue.toString());
        } else
            throw new IllegalStateException("Unsupported action value type: " + mValue.getClass());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ActionValue> CREATOR = new Creator<ActionValue>() {
        @NonNull
        @Override
        public ActionValue createFromParcel(Parcel in) {
            return new ActionValue(in);
        }

        @NonNull
        @Override
        public ActionValue[] newArray(int size) {
            return new ActionValue[size];
        }
    };
}
