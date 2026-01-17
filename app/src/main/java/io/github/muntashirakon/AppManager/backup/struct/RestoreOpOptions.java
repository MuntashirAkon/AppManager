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

import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.utils.JSONUtils;

public class RestoreOpOptions implements Parcelable, IJsonSerializer {
    @NonNull
    public final String packageName;
    @UserIdInt
    public final int userId;
    @Nullable
    public final String relativeDir;
    public final BackupFlags flags;

    public RestoreOpOptions(@NonNull String packageName, int userId, @Nullable String relativeDir, int flags) {
        this.packageName = packageName;
        this.userId = userId;
        this.relativeDir = relativeDir;
        this.flags = new BackupFlags(flags);
    }

    protected RestoreOpOptions(@NonNull Parcel in) {
        packageName = Objects.requireNonNull(in.readString());
        userId = in.readInt();
        relativeDir = in.readString();
        flags = new BackupFlags(in.readInt());
    }

    public static final Creator<RestoreOpOptions> CREATOR = new Creator<RestoreOpOptions>() {
        @Override
        @NonNull
        public RestoreOpOptions createFromParcel(@NonNull Parcel in) {
            return new RestoreOpOptions(in);
        }

        @Override
        @NonNull
        public RestoreOpOptions[] newArray(int size) {
            return new RestoreOpOptions[size];
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
        dest.writeString(relativeDir);
        dest.writeInt(this.flags.getFlags());
    }

    public RestoreOpOptions(@NonNull JSONObject jsonObject) throws JSONException {
        packageName = jsonObject.getString("package_name");
        userId = jsonObject.getInt("user_id");
        relativeDir = JSONUtils.optString(jsonObject, "relative_dir");
        flags = new BackupFlags(jsonObject.getInt("flags"));
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("package_name", packageName);
        jsonObject.put("user_id", userId);
        jsonObject.put("relative_dir", relativeDir);
        jsonObject.put("flags", flags.getFlags());
        return jsonObject;
    }
}
