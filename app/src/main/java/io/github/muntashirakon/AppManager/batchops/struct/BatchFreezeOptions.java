// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import android.os.Parcel;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;

public class BatchFreezeOptions implements IBatchOpOptions {
    public static final String TAG = BatchFreezeOptions.class.getSimpleName();

    @FreezeUtils.FreezeType
    private int mType;
    private boolean mPreferCustom;

    public BatchFreezeOptions(@FreezeUtils.FreezeType int type, boolean preferCustom) {
        mType = type;
        mPreferCustom = preferCustom;
    }

    public int getType() {
        return mType;
    }

    public boolean isPreferCustom() {
        return mPreferCustom;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    protected BatchFreezeOptions(@NonNull JSONObject jsonObject) throws JSONException {
        assert jsonObject.getString("tag").equals(TAG);
        mType = jsonObject.getInt("type");
        mPreferCustom = jsonObject.getBoolean("prefer_custom");
    }


    public static final JsonDeserializer.Creator<BatchFreezeOptions> DESERIALIZER
            = BatchFreezeOptions::new;

    protected BatchFreezeOptions(@NonNull Parcel in) {
        mType = in.readInt();
        mPreferCustom = in.readByte() != 0;
    }

    public static final Creator<BatchFreezeOptions> CREATOR = new Creator<BatchFreezeOptions>() {
        @NonNull
        @Override
        public BatchFreezeOptions createFromParcel(@NonNull Parcel in) {
            return new BatchFreezeOptions(in);
        }

        @NonNull
        @Override
        public BatchFreezeOptions[] newArray(int size) {
            return new BatchFreezeOptions[size];
        }
    };

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeByte((byte) (mPreferCustom ? 1 : 0));
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("tag", TAG);
        jsonObject.put("type", mType);
        jsonObject.put("prefer_custom", mPreferCustom);
        return jsonObject;
    }
}
