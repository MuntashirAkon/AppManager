// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ParcelCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.profiles.ProfileApplierActivity.ProfileApplierInfo;
import io.github.muntashirakon.AppManager.profiles.struct.AppsProfile;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class ProfileQueueItem implements Parcelable, IJsonSerializer {
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
    @Nullable
    private final Path mTempProfilePath;

    private ProfileQueueItem(@NonNull AppsProfile profile, @Nullable String state) {
        mProfileId = profile.profileId;
        mProfileName = profile.name;
        mState = state;
        mTempProfilePath = null;
    }

    protected ProfileQueueItem(@NonNull Parcel in) {
        mProfileId = Objects.requireNonNull(in.readString());
        mProfileName = Objects.requireNonNull(in.readString());
        mState = in.readString();
        Uri uri = ParcelCompat.readParcelable(in, Uri.class.getClassLoader(), Uri.class);
        mTempProfilePath = uri != null ? Paths.get(uri) : null;
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

    @Nullable
    public Path getTempProfilePath() {
        return mTempProfilePath;
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
        dest.writeParcelable(mTempProfilePath != null ? mTempProfilePath.getUri() : null, flags);
    }

    protected ProfileQueueItem(@NonNull JSONObject jsonObject) throws JSONException {
        mProfileId = jsonObject.getString("profile_id");
        mProfileName = jsonObject.getString("profile_name");
        mState = JSONUtils.getString(jsonObject, "state");
        JSONObject profile = jsonObject.optJSONObject("profile");
        File profilePath = null;
        if (profile != null) {
            try (InputStream is = new ByteArrayInputStream(profile.toString().getBytes(StandardCharsets.UTF_8))) {
                profilePath = FileCache.getGlobalFileCache().getCachedFile(is, ProfileManager.PROFILE_EXT);
            } catch (IOException e) {
                //noinspection UnnecessaryInitCause
                throw (JSONException) new JSONException(e.getMessage()).initCause(e);
            }
        }
        mTempProfilePath = profilePath != null ? Paths.get(profilePath) : null;
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("profile_id", mProfileId);
        jsonObject.put("profile_name", mProfileName);
        jsonObject.put("state", mState);
        // A profile can be altered any time. So, we need to store a snapshot of the profile
        try {
            AppsProfile profile = AppsProfile.fromPath(ProfileManager.findProfilePathById(mProfileId));
            jsonObject.put("profile", profile.serializeToJson());
        } catch (IOException e) {
            //noinspection UnnecessaryInitCause
            throw (JSONException) new JSONException(e.getMessage()).initCause(e);
        }
        return jsonObject;
    }

    public static final JsonDeserializer.Creator<ProfileQueueItem> DESERIALIZER = ProfileQueueItem::new;

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
