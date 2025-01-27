// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import android.os.Parcel;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat.NetPolicy;

public class BatchNetPolicyOptions implements IBatchOpOptions {
    public static final String TAG = BatchNetPolicyOptions.class.getSimpleName();
    @NetPolicy
    private int mPolicies;

    public BatchNetPolicyOptions(@NetPolicy int policies) {
        mPolicies = policies;
    }

    @NetPolicy
    public int getPolicies() {
        return mPolicies;
    }

    protected BatchNetPolicyOptions(@NonNull Parcel in) {
        mPolicies = in.readInt();
    }

    public static final Creator<BatchNetPolicyOptions> CREATOR = new Creator<BatchNetPolicyOptions>() {
        @Override
        @NonNull
        public BatchNetPolicyOptions createFromParcel(@NonNull Parcel in) {
            return new BatchNetPolicyOptions(in);
        }

        @Override
        @NonNull
        public BatchNetPolicyOptions[] newArray(int size) {
            return new BatchNetPolicyOptions[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mPolicies);
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("tag", TAG);
        jsonObject.put("policies", mPolicies);
        return jsonObject;
    }
}
