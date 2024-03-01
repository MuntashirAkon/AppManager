// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;

public class DebloatObject {
    @IntDef({REMOVAL_SAFE, REMOVAL_REPLACE, REMOVAL_CAUTION})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Removal {
    }

    public static final int REMOVAL_SAFE = 1;
    public static final int REMOVAL_REPLACE = 1 << 1;
    public static final int REMOVAL_CAUTION = 1 << 2;

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
    @Nullable
    private int[] mUsers;
    private boolean mInstalled;
    @Nullable
    private Boolean mSystemApp = null;
    @Nullable
    private List<SuggestionObject> mSuggestions;

    @NonNull
    public String[] getDependencies() {
        return ArrayUtils.defeatNullable(mDependencies);
    }

    @NonNull
    public String[] getRequiredBy() {
        return ArrayUtils.defeatNullable(mRequiredBy);
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
    public String getSuggestionId() {
        return mSuggestionId;
    }

    @Nullable
    public List<SuggestionObject> getSuggestions() {
        return mSuggestions;
    }

    public void setSuggestions(@Nullable List<SuggestionObject> suggestions) {
        mSuggestions = suggestions;
    }

    @Nullable
    public CharSequence getLabel() {
        return mLabel != null ? mLabel : mInternalLabel;
    }

    @Nullable
    public Drawable getIcon() {
        return mIcon;
    }

    @Nullable
    public int[] getUsers() {
        return mUsers;
    }

    private void addUser(int userId) {
        if (mUsers == null) {
            mUsers = new int[]{userId};
        } else {
            mUsers = ArrayUtils.appendInt(mUsers, userId);
        }
    }

    public boolean isInstalled() {
        return mInstalled;
    }

    public boolean isSystemApp() {
        return Boolean.TRUE.equals(mSystemApp);
    }

    public boolean isUserApp() {
        return Boolean.FALSE.equals(mSystemApp);
    }

    public void fillInstallInfo(@NonNull Context context, @NonNull AppDb appDb) {
        PackageManager pm = context.getPackageManager();
        List<SuggestionObject> suggestionObjects = getSuggestions();
        if (suggestionObjects != null) {
            for (SuggestionObject suggestionObject : suggestionObjects) {
                List<App> apps = appDb.getAllApplications(suggestionObject.packageName);
                for (App app : apps) {
                    if (app.isInstalled) {
                        suggestionObject.addUser(app.userId);
                    }
                }
            }
        }
        // Update application data
        mInstalled = false;
        mUsers = null;
        List<App> apps = appDb.getAllApplications(packageName);
        for (App app : apps) {
            if (!app.isInstalled) {
                continue;
            }
            mInstalled = true;
            addUser(app.userId);
            mSystemApp = app.isSystemApp();
            mLabel = app.packageLabel;
            if (getIcon() == null) {
                try {
                    ApplicationInfo ai = PackageManagerCompat.getApplicationInfo(packageName,
                            MATCH_UNINSTALLED_PACKAGES | MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, app.userId);
                    mInstalled = (ai.flags & ApplicationInfo.FLAG_INSTALLED) != 0;
                    mSystemApp = ApplicationInfoCompat.isSystemApp(ai);
                    mLabel = ai.loadLabel(pm);
                    mIcon = ai.loadIcon(pm);
                } catch (RemoteException | PackageManager.NameNotFoundException ignore) {
                }
            }
        }
    }
}
