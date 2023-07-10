// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.graphics.drawable.Drawable;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.AppManager.utils.ArrayUtils;

public class DebloatObject {
    @IntDef({REMOVAL_SAFE, REMOVAL_REPLACE, REMOVAL_CAUTION})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Removal {
    }

    public static final int REMOVAL_SAFE = 0;
    public static final int REMOVAL_REPLACE = 1;
    public static final int REMOVAL_CAUTION = 2;

    @SerializedName("id")
    public String packageName;
    @SerializedName("label")
    @Nullable
    private String mInternalLabel;
    @SerializedName("tags")
    @Nullable
    private String[] mTags;
    @SerializedName("dependencies")
    @Nullable
    private String[] mDependencies;
    @SerializedName("required_by")
    @Nullable
    private String[] mRequiredBy;
    // Possible values: aosp, carrier, google, misc, oem, pending
    @SerializedName("type")
    public String type;
    @SerializedName("description")
    private String mDescription;
    @SerializedName("web")
    @Nullable
    private String[] mWebRefs;
    @SerializedName("removal")
    private String mRemoval;
    @SerializedName("warning")
    @Nullable
    private String mWarning;
    @SerializedName("suggestions")
    @Nullable
    private String mSuggestionId;

    @Nullable
    private Drawable mIcon;
    @Nullable
    private CharSequence mLabel;
    private int[] mUsers;
    private boolean mInstalled;
    @Nullable
    private Boolean mSystemApp = null;

    @Nullable
    public String[] getDependencies() {
        return mDependencies;
    }

    @Nullable
    public String[] getRequiredBy() {
        return mRequiredBy;
    }

    @Removal
    public int getRemoval() {
        switch (mRemoval) {
            default:
            case "safe":
                return REMOVAL_SAFE;
            case "replace":
                return REMOVAL_REPLACE;
            case "caution":
                return REMOVAL_CAUTION;
        }
    }

    @Nullable
    public String getWarning() {
        return mWarning;
    }

    public String getDescription() {
        return mDescription;
    }

    @NonNull
    public String[] getWebRefs() {
        return ArrayUtils.defeatNullable(mWebRefs);
    }

    @Nullable
    public CharSequence getLabel() {
        return mLabel != null ? mLabel : mInternalLabel;
    }

    public void setLabel(@Nullable CharSequence label) {
        mLabel = label;
    }

    @Nullable
    public Drawable getIcon() {
        return mIcon;
    }

    public void setIcon(@Nullable Drawable icon) {
        mIcon = icon;
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
