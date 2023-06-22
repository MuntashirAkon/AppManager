// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.content.pm.PackageInfo;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;

public class DebloatObject {
    @IntDef({REMOVAL_SAFE, REMOVAL_REPLACE, REMOVAL_CAUTION, REMOVAL_UNSAFE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Removal {
    }

    public static final int REMOVAL_SAFE = 0;
    public static final int REMOVAL_REPLACE = 1;
    public static final int REMOVAL_CAUTION = 2;
    public static final int REMOVAL_UNSAFE = 3;

    @SerializedName("id")
    public String packageName;
    // Possible values: Google, Misc, Oem, Aosp, Pending, Carrier
    @SerializedName("list")
    public String type;
    @SerializedName("description")
    public String description;

    @SerializedName("dependencies")
    private String[] mDependencies;
    @SerializedName("neededBy")
    private String[] mNeededBy;
    @SerializedName("labels")
    private String[] mLabels;
    @SerializedName("removal")
    private String mRemoval;

    @Nullable
    private CharSequence mLabel;
    @Nullable
    private App mApp;
    @Nullable
    private PackageInfo mPackageInfo;
    private int[] mUsers;
    private boolean mInstalled;
    @Nullable
    private Boolean mSystemApp = null;

    public String[] getDependencies() {
        return mDependencies;
    }

    public String[] getNeededBy() {
        return mNeededBy;
    }

    public String[] getLabels() {
        return mLabels;
    }

    @Removal
    public int getmRemoval() {
        switch (mRemoval) {
            default:
            case "Recommended":
                return REMOVAL_SAFE;
            case "Advanced":
                return REMOVAL_REPLACE;
            case "Expert":
                return REMOVAL_CAUTION;
            case "Unsafe":
                return REMOVAL_UNSAFE;
        }
    }

    @Nullable
    public CharSequence getLabel() {
        return mLabel;
    }

    public void setLabel(@Nullable CharSequence label) {
        mLabel = label;
    }

    @Nullable
    public App getApp() {
        return mApp;
    }

    public void setApp(@Nullable App app) {
        mApp = app;
    }

    @Nullable
    public PackageInfo getPackageInfo() {
        return mPackageInfo;
    }

    public void setPackageInfo(@Nullable PackageInfo packageInfo) {
        mPackageInfo = packageInfo;
    }

    public int[] getUsers() {
        return mUsers;
    }

    public void addUser(int userId) {
        if (mUsers == null) {
            mUsers = new int[]{userId};
        } else {
            mUsers = ArrayUtils.appendInt(mUsers, userId);
        }
    }

    public boolean isInstalled() {
        return mInstalled;
    }

    public void setInstalled(boolean installed) {
        mInstalled = installed;
    }

    public boolean isSystemApp() {
        return Boolean.TRUE.equals(mSystemApp);
    }

    public boolean isUserApp() {
        return Boolean.FALSE.equals(mSystemApp);
    }

    public void setSystemApp(@Nullable Boolean systemApp) {
        mSystemApp = systemApp;
    }
}
