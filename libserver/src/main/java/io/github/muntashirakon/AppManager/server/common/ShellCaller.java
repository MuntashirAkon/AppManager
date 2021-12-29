// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class ShellCaller extends Caller {
    private final String mCommand;

    public ShellCaller(String command) {
        mCommand = command;
    }

    public String getCommand() {
        return mCommand;
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
        dest.writeString(mCommand);
    }

    protected ShellCaller(@NonNull Parcel in) {
        mCommand = in.readString();
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
