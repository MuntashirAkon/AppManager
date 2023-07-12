// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import io.github.muntashirakon.AppManager.utils.ArrayUtils;

public class SuggestionObject {
    @SerializedName("_id")
    public String suggestionId;
    @SerializedName("id")
    public String packageName;

    @SerializedName("label")
    private String mLabel;
    @SerializedName("reason")
    @Nullable
    private String mReason;
    @SerializedName("source")
    private String mSource;
    @SerializedName("repo")
    private String mRepo;

    private int[] mUsers;

    public String getLabel() {
        return mLabel;
    }

    public String getRepo() {
        return mRepo;
    }

    @Nullable
    public String getReason() {
        return mReason;
    }

    public boolean isInFDroidMarket() {
        return mSource != null && mSource.contains("f");
    }

    public Intent getMarketLink() {
        // Not supported by most app stores
        // return new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pname:" + packageName + " " + mLabel));
        Uri uri;
        if (isInFDroidMarket()) {
            uri = Uri.parse("https://f-droid.org/packages/" + packageName);
        } else uri = Uri.parse("https://play.google.com/store/apps/details?id=" + packageName);
        return new Intent(Intent.ACTION_VIEW, uri);
    }

    public int[] getUsers() {
        return mUsers;
    }

    public void addUser(int userId) {
        if (mUsers == null) {
            mUsers = new int[]{userId};
        } else if (!ArrayUtils.contains(mUsers, userId)) {
            mUsers = ArrayUtils.appendInt(mUsers, userId);
        }
    }
}
