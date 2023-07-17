// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemProperties;
import android.text.TextUtils;

import androidx.annotation.NonNull;

public class DexOptOptions implements Parcelable {
    @NonNull
    public static DexOptOptions getDefault() {
        DexOptOptions options = new DexOptOptions();
        options.compilerFiler = getDefaultCompilerFilterForInstallation();
        options.checkProfiles = SystemProperties.getBoolean("dalvik.vm.usejitprofiles", false);
        options.bootComplete = true;
        return options;
    }

    public String[] packages;
    public String compilerFiler;
    public boolean compileLayouts;
    public boolean clearProfileData;
    public boolean checkProfiles;
    public boolean bootComplete;
    public boolean forceCompilation;
    public boolean forceDexOpt;

    private DexOptOptions() {
    }

    protected DexOptOptions(@NonNull Parcel in) {
        packages = in.createStringArray();
        compilerFiler = in.readString();
        compileLayouts = in.readByte() != 0;
        clearProfileData = in.readByte() != 0;
        checkProfiles = in.readByte() != 0;
        bootComplete = in.readByte() != 0;
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
        dest.writeByte((byte) (bootComplete ? 1 : 0));
        dest.writeByte((byte) (forceCompilation ? 1 : 0));
        dest.writeByte((byte) (forceDexOpt ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<DexOptOptions> CREATOR = new Creator<DexOptOptions>() {
        @NonNull
        @Override
        public DexOptOptions createFromParcel(@NonNull Parcel in) {
            return new DexOptOptions(in);
        }

        @NonNull
        @Override
        public DexOptOptions[] newArray(int size) {
            return new DexOptOptions[size];
        }
    };

    @NonNull
    static String getDefaultCompilerFilterForInstallation() {
        String profile = SystemProperties.get("pm.dexopt.install");
        if (TextUtils.isEmpty(profile)) {
            return "speed";
        }
        return profile;
    }

    @NonNull
    static String getDefaultCompilerFilter() {
        String profile = SystemProperties.get("dalvik.vm.dex2oat-filter");
        if (TextUtils.isEmpty(profile)) {
            return "speed";
        }
        return profile;
    }
}
