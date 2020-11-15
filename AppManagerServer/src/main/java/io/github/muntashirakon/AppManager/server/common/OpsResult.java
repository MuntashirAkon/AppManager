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
import java.util.List;

import androidx.annotation.NonNull;

public class OpsResult implements Parcelable {
    private final Throwable exception;
    private List<PackageOps> list;
    private int mode;

    public OpsResult(List<PackageOps> list, Throwable exception) {
        this.list = list;
        this.exception = exception;
    }

    public OpsResult(int mode, Throwable exception) {
        this.mode = mode;
        this.exception = exception;
    }

    public Throwable getException() {
        return exception;
    }

    public List<PackageOps> getList() {
        return list;
    }

    public int getMode() {
        return mode;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeSerializable(this.exception);
        dest.writeTypedList(this.list);
        dest.writeInt(this.mode);
    }

    protected OpsResult(@NonNull Parcel in) {
        this.exception = (Exception) in.readSerializable();
        this.list = in.createTypedArrayList(PackageOps.CREATOR);
        this.mode = in.readInt();
    }

    public static final Parcelable.Creator<OpsResult> CREATOR = new Parcelable.Creator<OpsResult>() {
        @NonNull
        @Override
        public OpsResult createFromParcel(Parcel source) {
            return new OpsResult(source);
        }

        @NonNull
        @Override
        public OpsResult[] newArray(int size) {
            return new OpsResult[size];
        }
    };

    @NonNull
    @Override
    public String toString() {
        return "OpsResult{" +
                "exception=" + exception +
                ", list=" + list +
                ", mode=" + mode +
                '}';
    }
}
