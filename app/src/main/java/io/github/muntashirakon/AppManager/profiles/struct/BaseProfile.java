// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.struct;

import static io.github.muntashirakon.AppManager.profiles.ProfileManager.PROFILE_EXT;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

import org.jetbrains.annotations.Contract;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.profiles.ProfileLogger;
import io.github.muntashirakon.AppManager.profiles.ProfileManager;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.util.LocalizedString;

public abstract class BaseProfile implements LocalizedString, IJsonSerializer {
    @Contract("null -> fail")
    @NonNull
    public static BaseProfile fromPath(@Nullable Path profilePath) throws IOException, JSONException {
        if (profilePath == null) {
            throw new IOException("Empty profile path");
        }
        String profileStr = profilePath.getContentAsString();
        JSONObject profileObj = new JSONObject(profileStr);
        return BaseProfile.DESERIALIZER.deserialize(profileObj);
    }

    @NonNull
    public static BaseProfile newProfile(@NonNull String newProfileName, int type, @Nullable BaseProfile source) {
        String profileId = ProfileManager.getProfileIdCompat(newProfileName);
        // TODO: 17/9/23 TODO: Remove these once we migrated to UUID based profile ID
        // BEGIN legacy: For legacy profile, the generated ID can be the same as an existing profile
        Path profilesDir = ProfileManager.getProfilesDir();
        Path profilePath = Paths.build(profilesDir, profileId + PROFILE_EXT);
        String profileName = newProfileName;
        int i = 1;
        while (profilePath != null && profilePath.exists()) {
            // Try another name
            profileName = newProfileName + " (" + i + ")";
            profileId = ProfileManager.getProfileIdCompat(profileName);
            profilePath = Paths.build(profilesDir, profileId + PROFILE_EXT);
            ++i;
        }
        // END legacy: For legacy profile, the generated ID can be the same as an existing profile
        switch (type) {
            case PROFILE_TYPE_APPS:
                if (source != null) {
                    assert source instanceof AppsProfile;
                    return new AppsProfile(profileId, profileName, (AppsProfile) source);
                } else return new AppsProfile(profileId, profileName);
            case PROFILE_TYPE_APPS_FILTER:
                if (source != null) {
                    assert source instanceof AppsFilterProfile;
                    return new AppsFilterProfile(profileId, profileName, (AppsFilterProfile) source);
                } else return new AppsFilterProfile(profileId, profileName);
            default:
                throw new IllegalArgumentException("Invalid type: " + type);
        }
    }

    public static final int PROFILE_TYPE_APPS = 0;
    public static final int PROFILE_TYPE_APPS_FILTER = 1;

    @IntDef({PROFILE_TYPE_APPS, PROFILE_TYPE_APPS_FILTER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ProfileType {
    }

    public static final String STATE_ON = "on";
    public static final String STATE_OFF = "off";

    @StringDef({STATE_ON, STATE_OFF})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ProfileState {
    }

    @NonNull
    public final String profileId; // id
    @NonNull
    public final String name;  // name (name of the profile)
    @ProfileType
    public final int type;  // type
    @ProfileState
    public String state; // state

    protected BaseProfile(@NonNull String profileId, @NonNull String profileName, int profileType) {
        this.profileId = profileId;
        this.name = profileName;
        this.type = profileType;
    }

    public abstract ProfileApplierResult apply(@NonNull String state, @Nullable ProfileLogger logger, @Nullable ProgressHandler progressHandler);

    public void write(@NonNull OutputStream out) throws IOException {
        try {
            out.write(serializeToJson().toString().getBytes());
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    @NonNull
    @CallSuper
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject profileObj = new JSONObject();
        profileObj.put("id", profileId);
        profileObj.put("name", name);
        profileObj.put("type", type);
        profileObj.put("state", state);
        return profileObj;
    }

    protected BaseProfile(@NonNull JSONObject profileObj) throws JSONException {
        name = profileObj.getString("name");
        profileId = JSONUtils.getString(profileObj, "id", ProfileManager.getProfileIdCompat(name));
        type = profileObj.getInt("type");
        state = JSONUtils.getString(profileObj, "state", STATE_ON);
    }

    public static final JsonDeserializer.Creator<BaseProfile> DESERIALIZER = jsonObject -> {
        int type = jsonObject.getInt("type");
        if (type == PROFILE_TYPE_APPS) {
            return AppsProfile.DESERIALIZER.deserialize(jsonObject);
        } else if (type == PROFILE_TYPE_APPS_FILTER) {
            return AppsFilterProfile.DESERIALIZER.deserialize(jsonObject);
        } else throw new JSONException("Invalid type: " + type);
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseProfile)) return false;
        BaseProfile that = (BaseProfile) o;
        return Objects.equals(profileId, that.profileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profileId);
    }
}
