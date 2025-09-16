// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.struct;

import android.annotation.UserIdInt;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.utils.JSONUtils;

public class DeleteOpOptions implements Parcelable, IJsonSerializer {
    @NonNull
    public final String packageName;
    @UserIdInt
    public final int userId;
    @Nullable
    public final String[] relativeDirs;

    public DeleteOpOptions(@NonNull String packageName, @UserIdInt int userId, @Nullable String[] relativeDirs) {
        this.packageName = packageName;
        this.userId = userId;
        this.relativeDirs = relativeDirs;
    }

    protected DeleteOpOptions(@NonNull Parcel in) {
        packageName = Objects.requireNonNull(in.readString());
        userId = in.readInt();
        relativeDirs = Objects.requireNonNull(in.createStringArray());
    }

    public static final Creator<DeleteOpOptions> CREATOR = new Creator<DeleteOpOptions>() {
        @Override
        @NonNull
        public DeleteOpOptions createFromParcel(@NonNull Parcel in) {
            return new DeleteOpOptions(in);
        }

        @Override
        @NonNull
        public DeleteOpOptions[] newArray(int size) {
            return new DeleteOpOptions[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(packageName);
        dest.writeInt(userId);
        dest.writeStringArray(relativeDirs);
    }

    public DeleteOpOptions(@NonNull JSONObject jsonObject) throws JSONException {
        packageName = jsonObject.getString("package_name");
        userId = jsonObject.getInt("user_id");
        relativeDirs = JSONUtils.getArray(String.class, jsonObject.optJSONArray("relative_dirs"));
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("package_name", packageName);
        jsonObject.put("user_id", userId);
        jsonObject.put("relative_dirs", JSONUtils.getJSONArray(relativeDirs));
        return jsonObject;
    }
}
