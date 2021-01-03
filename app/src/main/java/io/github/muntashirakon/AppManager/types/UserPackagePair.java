/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.types;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import io.github.muntashirakon.AppManager.misc.UserIdInt;

public final class UserPackagePair extends Pair<String, Integer> implements Parcelable {
    public UserPackagePair(String packageName, @UserIdInt int userHandle) {
        super(packageName, userHandle);
    }

    public String getPackageName() {
        return super.first;
    }

    @UserIdInt
    public int getUserHandle() {
        return super.second;
    }

    protected UserPackagePair(@NonNull Parcel in) {
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
        dest.writeInt(getUserHandle());
    }
}