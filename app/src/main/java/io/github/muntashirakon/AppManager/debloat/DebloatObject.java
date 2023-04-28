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
    private String[] dependencies;
    @SerializedName("neededBy")
    private String[] neededBy;
    @SerializedName("labels")
    private String[] labels;
    @SerializedName("removal")
    private String removal;

    @Nullable
    private CharSequence label;
    @Nullable
    private App app;
    @Nullable
    private PackageInfo packageInfo;
    private int[] users;
    private boolean installed;
    @Nullable
    private Boolean systemApp = null;

    public String[] getDependencies() {
        return dependencies;
    }

    public String[] getNeededBy() {
        return neededBy;
    }

    public String[] getLabels() {
        return labels;
    }

    @Removal
    public int getRemoval() {
        switch (removal) {
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
        return label;
    }

    public void setLabel(@Nullable CharSequence label) {
        this.label = label;
    }

    @Nullable
    public App getApp() {
        return app;
    }

    public void setApp(@Nullable App app) {
        this.app = app;
    }

    @Nullable
    public PackageInfo getPackageInfo() {
        return packageInfo;
    }

    public void setPackageInfo(@Nullable PackageInfo packageInfo) {
        this.packageInfo = packageInfo;
    }

    public int[] getUsers() {
        return users;
    }

    public void addUser(int userId) {
        if (users == null) {
            users = new int[]{userId};
        } else {
            users = ArrayUtils.appendInt(users, userId);
        }
    }

    public boolean isInstalled() {
        return installed;
    }

    public void setInstalled(boolean installed) {
        this.installed = installed;
    }

    public boolean isSystemApp() {
        return Boolean.TRUE.equals(systemApp);
    }

    public boolean isUserApp() {
        return Boolean.FALSE.equals(systemApp);
    }

    public void setSystemApp(@Nullable Boolean systemApp) {
        this.systemApp = systemApp;
    }
}
