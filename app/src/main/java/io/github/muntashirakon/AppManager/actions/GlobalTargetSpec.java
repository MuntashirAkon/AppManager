// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;

public final class GlobalTargetSpec implements IJsonSerializer, Parcelable {
    private final JSONObject mParameters;

    public GlobalTargetSpec(@NonNull JSONObject parameters) {
        mParameters = parameters;
    }

    public static final JsonDeserializer.Creator<GlobalTargetSpec> DESERIALIZER = object ->
            new GlobalTargetSpec(new JSONObject(object.toString()));

    @NonNull
    public JSONObject getParameters() {
        return mParameters;
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        return new JSONObject(mParameters.toString());
    }

    private GlobalTargetSpec(@NonNull Parcel in) {
        try {
            mParameters = new JSONObject(in.readString());
        } catch (JSONException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid global target", e);
        }
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mParameters.toString());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<GlobalTargetSpec> CREATOR = new Creator<GlobalTargetSpec>() {
        @NonNull
        @Override
        public GlobalTargetSpec createFromParcel(Parcel in) {
            return new GlobalTargetSpec(in);
        }

        @NonNull
        @Override
        public GlobalTargetSpec[] newArray(int size) {
            return new GlobalTargetSpec[size];
        }
    };
}
