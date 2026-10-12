// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;

public final class ActionState implements IJsonSerializer, Parcelable {
    private final String stateKey;
    private final ActionValue value;

    public ActionState(@NonNull String stateKey, @NonNull ActionValue value) {
        if (stateKey.isEmpty()) {
            throw new IllegalArgumentException("Empty state key");
        }
        this.stateKey = stateKey;
        this.value = value;
    }

    @NonNull
    public String getStateKey() {
        return stateKey;
    }

    @NonNull
    public ActionValue getValue() {
        return value;
    }

    public boolean isSame(@NonNull ActionState other) {
        return stateKey.equals(other.stateKey) && value.isSameValue(other.value);
    }

    private ActionState(@NonNull JSONObject object) throws JSONException {
        this(object.getString("key"), ActionValue.of(object.get("value")));
    }

    public static final JsonDeserializer.Creator<ActionState> DESERIALIZER = ActionState::new;

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("key", stateKey);
        o.put("value", value.serializeToJson().get("value"));
        return o;
    }

    private ActionState(@NonNull Parcel in) {
        stateKey = in.readString();
        value = in.readParcelable(ActionValue.class.getClassLoader());
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(stateKey);
        dest.writeParcelable(value, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ActionState> CREATOR = new Creator<ActionState>() {
        @NonNull
        @Override
        public ActionState createFromParcel(Parcel in) {
            return new ActionState(in);
        }

        @NonNull
        @Override
        public ActionState[] newArray(int size) {
            return new ActionState[size];
        }
    };
}
