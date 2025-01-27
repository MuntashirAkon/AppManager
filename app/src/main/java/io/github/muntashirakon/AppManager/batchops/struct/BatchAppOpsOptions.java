// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import android.os.Parcel;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import io.github.muntashirakon.AppManager.utils.JSONUtils;

public class BatchAppOpsOptions implements IBatchOpOptions {
    public static final String TAG = BatchAppOpsOptions.class.getSimpleName();

    @NonNull
    private int[] mAppOps;
    private int mMode;

    public BatchAppOpsOptions(@NonNull int[] appOps, int mode) {
        mAppOps = appOps;
        mMode = mode;
    }

    @NonNull
    public int[] getAppOps() {
        return mAppOps;
    }

    public int getMode() {
        return mMode;
    }

    protected BatchAppOpsOptions(@NonNull Parcel in) {
        mAppOps = Objects.requireNonNull(in.createIntArray());
        mMode = in.readInt();
    }

    public static final Creator<BatchAppOpsOptions> CREATOR = new Creator<BatchAppOpsOptions>() {
        @Override
        @NonNull
        public BatchAppOpsOptions createFromParcel(@NonNull Parcel in) {
            return new BatchAppOpsOptions(in);
        }

        @Override
        @NonNull
        public BatchAppOpsOptions[] newArray(int size) {
            return new BatchAppOpsOptions[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeIntArray(mAppOps);
        dest.writeInt(mMode);
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("tag", TAG);
        jsonObject.put("app_ops", JSONUtils.getJSONArray(mAppOps));
        jsonObject.put("mode", mMode);
        return jsonObject;
    }
}
