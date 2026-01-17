// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import android.os.Parcel;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.utils.JSONUtils;

public class BatchPermissionOptions implements IBatchOpOptions {
    public static final String TAG = BatchPermissionOptions.class.getSimpleName();
    @NonNull
    private String[] mPermissions;

    public BatchPermissionOptions(@NonNull String[] permissions) {
        mPermissions = permissions;
    }

    @NonNull
    public String[] getPermissions() {
        return mPermissions;
    }

    protected BatchPermissionOptions(@NonNull Parcel in) {
        mPermissions = Objects.requireNonNull(in.createStringArray());
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeStringArray(mPermissions);
    }

    protected BatchPermissionOptions(@NonNull JSONObject jsonObject) throws JSONException {
        assert jsonObject.getString("tag").equals(TAG);
        mPermissions = JSONUtils.getArray(String.class, jsonObject.getJSONArray("permissions"));
    }

    public static final JsonDeserializer.Creator<BatchPermissionOptions> DESERIALIZER
            = BatchPermissionOptions::new;

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("tag", TAG);
        jsonObject.put("permissions", JSONUtils.getJSONArray(mPermissions));
        return jsonObject;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<BatchPermissionOptions> CREATOR = new Creator<BatchPermissionOptions>() {
        @Override
        @NonNull
        public BatchPermissionOptions createFromParcel(@NonNull Parcel in) {
            return new BatchPermissionOptions(in);
        }

        @Override
        @NonNull
        public BatchPermissionOptions[] newArray(int size) {
            return new BatchPermissionOptions[size];
        }
    };
}
