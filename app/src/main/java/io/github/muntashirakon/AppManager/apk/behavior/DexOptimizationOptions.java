// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class DexOptimizationOptions implements Parcelable {
    public String[] packages;
    public String compilerFiler;
    public boolean compileLayouts;
    public boolean clearProfileData;
    public boolean checkProfiles;
    public boolean forceCompilation;
    public boolean forceDexOpt;

    public DexOptimizationOptions() {
    }

    protected DexOptimizationOptions(@NonNull Parcel in) {
        packages = in.createStringArray();
        compilerFiler = in.readString();
        compileLayouts = in.readByte() != 0;
        clearProfileData = in.readByte() != 0;
        checkProfiles = in.readByte() != 0;
        forceCompilation = in.readByte() != 0;
        forceDexOpt = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeStringArray(packages);
        dest.writeString(compilerFiler);
        dest.writeByte((byte) (compileLayouts ? 1 : 0));
        dest.writeByte((byte) (clearProfileData ? 1 : 0));
        dest.writeByte((byte) (checkProfiles ? 1 : 0));
        dest.writeByte((byte) (forceCompilation ? 1 : 0));
        dest.writeByte((byte) (forceDexOpt ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<DexOptimizationOptions> CREATOR = new Creator<DexOptimizationOptions>() {
        @NonNull
        @Override
        public DexOptimizationOptions createFromParcel(@NonNull Parcel in) {
            return new DexOptimizationOptions(in);
        }

        @NonNull
        @Override
        public DexOptimizationOptions[] newArray(int size) {
            return new DexOptimizationOptions[size];
        }
    };
}
