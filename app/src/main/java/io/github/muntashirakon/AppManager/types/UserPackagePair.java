// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types;

import android.annotation.UserIdInt;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import java.util.Objects;

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

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Pair)) return false;
        Pair<?, ?> other = (Pair<?, ?>) object;
        return Objects.equals(first, other.first) && Objects.equals(second, other.second);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPackageName(), getUserId());
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
