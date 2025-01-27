// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import android.os.Parcel;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

public class BatchPermissionOptions implements IBatchOpOptions {
    public static final String TAG = BatchPermissionOptions.class.getSimpleName();
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

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("tag", TAG);
        jsonObject.put("permissions", mPermissions);
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
