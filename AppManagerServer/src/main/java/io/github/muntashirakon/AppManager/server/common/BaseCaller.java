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

package io.github.muntashirakon.AppManager.server.common;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class BaseCaller implements Parcelable {

    public static final int TYPE_CLOSE = -10;
    public static final int TYPE_SYSTEM_SERVICE = 1;
    public static final int TYPE_STATIC_METHOD = 2;
    public static final int TYPE_CLASS = 3;

    private final int type;
    private byte[] rawBytes;

    public BaseCaller(@NonNull Caller method) {
        this.type = method.getType();
        this.rawBytes = ParcelableUtil.marshall(method);
    }

    public BaseCaller(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(this.type);
        dest.writeByteArray(this.rawBytes);
    }


    protected BaseCaller(@NonNull Parcel in) {
        this.type = in.readInt();
        this.rawBytes = in.createByteArray();
    }

    public static final Parcelable.Creator<BaseCaller> CREATOR = new Parcelable.Creator<BaseCaller>() {
        @NonNull
        @Override
        public BaseCaller createFromParcel(Parcel source) {
            return new BaseCaller(source);
        }

        @NonNull
        @Override
        public BaseCaller[] newArray(int size) {
            return new BaseCaller[size];
        }
    };
}
