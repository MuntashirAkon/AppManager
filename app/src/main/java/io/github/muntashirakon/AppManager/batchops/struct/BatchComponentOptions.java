// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import android.os.Parcel;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import io.github.muntashirakon.AppManager.utils.JSONUtils;

public class BatchComponentOptions implements IBatchOpOptions {
    public static final String TAG = BatchComponentOptions.class.getSimpleName();

    private String[] mSignatures;

    public BatchComponentOptions(@NonNull String[] signatures) {
        mSignatures = signatures;
    }

    @NonNull
    public String[] getSignatures() {
        return mSignatures;
    }

    protected BatchComponentOptions(@NonNull Parcel in) {
        mSignatures = Objects.requireNonNull(in.createStringArray());
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeStringArray(mSignatures);
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("tag", TAG);
        jsonObject.put("signatures", JSONUtils.getJSONArray(mSignatures));
        return jsonObject;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<BatchComponentOptions> CREATOR = new Creator<BatchComponentOptions>() {
        @Override
        @NonNull
        public BatchComponentOptions createFromParcel(@NonNull Parcel in) {
            return new BatchComponentOptions(in);
        }

        @Override
        @NonNull
        public BatchComponentOptions[] newArray(int size) {
            return new BatchComponentOptions[size];
        }
    };
}
