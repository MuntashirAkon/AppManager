// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import io.github.muntashirakon.AppManager.profiles.ProfileApplierActivity.ProfileApplierInfo;
import io.github.muntashirakon.AppManager.profiles.struct.AppsProfile;

public class ProfileQueueItem implements Parcelable {
    @NonNull
    public static ProfileQueueItem fromProfiledApplierInfo(@NonNull ProfileApplierInfo info) {
        return new ProfileQueueItem(info.profile, info.state);
    }

    @NonNull
    private final String mProfileId;
    @NonNull
    private final String mProfileName;
    @Nullable
    private final String mState;

    private ProfileQueueItem(@NonNull AppsProfile profile, @Nullable String state) {
        mProfileId = profile.profileId;
        mProfileName = profile.name;
        mState = state;
    }

    protected ProfileQueueItem(@NonNull Parcel in) {
        mProfileId = Objects.requireNonNull(in.readString());
        mProfileName = Objects.requireNonNull(in.readString());
        mState = in.readString();
    }

    @NonNull
    public String getProfileId() {
        return mProfileId;
    }

    @NonNull
    public String getProfileName() {
        return mProfileName;
    }

    @Nullable
    public String getState() {
        return mState;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mProfileId);
        dest.writeString(mProfileName);
        dest.writeString(mState);
    }

    public static final Creator<ProfileQueueItem> CREATOR = new Creator<ProfileQueueItem>() {
        @NonNull
        @Override
        public ProfileQueueItem createFromParcel(@NonNull Parcel in) {
            return new ProfileQueueItem(in);
        }

        @NonNull
        @Override
        public ProfileQueueItem[] newArray(int size) {
            return new ProfileQueueItem[size];
        }
    };
}
