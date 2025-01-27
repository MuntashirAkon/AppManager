// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import android.os.Parcel;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import io.github.muntashirakon.AppManager.apk.dexopt.DexOptOptions;

public class BatchDexOptOptions implements IBatchOpOptions {
    public static final String TAG = BatchDexOptOptions.class.getSimpleName();
    private DexOptOptions mDexOptOptions;

    public BatchDexOptOptions(@NonNull DexOptOptions dexOptOptions) {
        mDexOptOptions = dexOptOptions;
    }

    public DexOptOptions getDexOptOptions() {
        return mDexOptOptions;
    }

    protected BatchDexOptOptions(@NonNull Parcel in) {
        mDexOptOptions = Objects.requireNonNull(in.readParcelable(DexOptOptions.class.getClassLoader()));
    }

    public static final Creator<BatchDexOptOptions> CREATOR = new Creator<BatchDexOptOptions>() {
        @Override
        @NonNull
        public BatchDexOptOptions createFromParcel(@NonNull Parcel in) {
            return new BatchDexOptOptions(in);
        }

        @Override
        @NonNull
        public BatchDexOptOptions[] newArray(int size) {
            return new BatchDexOptOptions[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(mDexOptOptions, flags);
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("tag", TAG);
        jsonObject.put("dex_opt_options", mDexOptOptions.serializeToJson());
        return jsonObject;
    }
}
