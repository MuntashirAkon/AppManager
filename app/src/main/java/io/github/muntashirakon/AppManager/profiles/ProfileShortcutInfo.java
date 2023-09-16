// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.content.Context;
import android.content.Intent;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.shortcut.ShortcutInfo;

public class ProfileShortcutInfo extends ShortcutInfo {
    public final String profileId;
    @ProfileApplierActivity.ShortcutType
    public final String shortcutType;

    public ProfileShortcutInfo(@NonNull String profileId, @NonNull String profileName,
                               @ProfileApplierActivity.ShortcutType String shortcutType,
                               @Nullable CharSequence readableShortcutType) {
        this.profileId = profileId;
        this.shortcutType = shortcutType;
        setName(profileName + " - " + (readableShortcutType != null ? readableShortcutType : shortcutType));
    }

    protected ProfileShortcutInfo(Parcel in) {
        super(in);
        profileId = in.readString();
        shortcutType = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(profileId);
        dest.writeString(shortcutType);
    }

    @Override
    public Intent toShortcutIntent(@NonNull Context context) {
        Intent intent = ProfileApplierActivity.getShortcutIntent(context, profileId, shortcutType, null);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    public static final Creator<ProfileShortcutInfo> CREATOR = new Creator<ProfileShortcutInfo>() {
        @Override
        public ProfileShortcutInfo createFromParcel(Parcel source) {
            return new ProfileShortcutInfo(source);
        }

        @Override
        public ProfileShortcutInfo[] newArray(int size) {
            return new ProfileShortcutInfo[size];
        }
    };
}
