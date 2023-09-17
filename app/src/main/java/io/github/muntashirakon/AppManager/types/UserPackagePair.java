// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types;

import android.annotation.UserIdInt;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

public final class UserPackagePair extends Pair<String, Integer> implements Parcelable {
    public UserPackagePair(String packageName, @UserIdInt int userId) {
        super(packageName, userId);
    }

    public String getPackageName() {
        return super.first;
    }

    @UserIdInt
    public int getUserId() {
        return super.second;
    }

    @NonNull
    @Override
    public String toString() {
        return "(" + first + ", " + second + ")";
    }

    private UserPackagePair(@NonNull Parcel in) {
        super(in.readString(), in.readInt());
    }

    public static final Creator<UserPackagePair> CREATOR = new Creator<UserPackagePair>() {
        @Override
        @NonNull
        public UserPackagePair createFromParcel(Parcel in) {
            return new UserPackagePair(in);
        }

        @Override
        @NonNull
        public UserPackagePair[] newArray(int size) {
            return new UserPackagePair[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(getPackageName());
        dest.writeInt(getUserId());
    }
}