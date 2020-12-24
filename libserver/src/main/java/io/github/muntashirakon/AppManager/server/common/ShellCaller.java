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

public class ShellCaller extends Caller {
    private final String command;

    public ShellCaller(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    @Override
    public int getType() {
        return BaseCaller.TYPE_SHELL;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(command);
    }

    protected ShellCaller(@NonNull Parcel in) {
        command = in.readString();
    }

    public static final Parcelable.Creator<ShellCaller> CREATOR = new Parcelable.Creator<ShellCaller>() {
        @NonNull
        @Override
        public ShellCaller createFromParcel(Parcel source) {
            return new ShellCaller(source);
        }

        @NonNull
        @Override
        public ShellCaller[] newArray(int size) {
            return new ShellCaller[size];
        }
    };
}
