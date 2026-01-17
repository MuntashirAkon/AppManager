// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;
import io.github.muntashirakon.AppManager.profiles.struct.ProfileApplierResult;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class ProfileManager {
    public static final String TAG = "ProfileManager";

    public static final String PROFILE_EXT = ".am.json";

    @NonNull
    public static Intent getProfileIntent(@NonNull Context context, @BaseProfile.ProfileType int type, @NonNull String profileId) {
        if (type == BaseProfile.PROFILE_TYPE_APPS) {
            return AppsProfileActivity.getProfileIntent(context, profileId);
        } else if (type == BaseProfile.PROFILE_TYPE_APPS_FILTER) {
            return AppsFilterProfileActivity.getProfileIntent(context, profileId);
        } else throw new UnsupportedOperationException("Invalid type: " + type);
    }

    @NonNull
    public static Intent getNewProfileIntent(@NonNull Context context, @BaseProfile.ProfileType int type, @NonNull String profileName) {
        if (type == BaseProfile.PROFILE_TYPE_APPS) {
            return AppsProfileActivity.getNewProfileIntent(context, profileName);
        } else if (type == BaseProfile.PROFILE_TYPE_APPS_FILTER) {
            return AppsFilterProfileActivity.getNewProfileIntent(context, profileName);
        } else throw new UnsupportedOperationException("Invalid type: " + type);
    }

    @NonNull
    public static Intent getCloneProfileIntent(@NonNull Context context,
                                               @BaseProfile.ProfileType int type,
                                               @NonNull String oldProfileId,
                                               @NonNull String newProfileName) {
        if (type == BaseProfile.PROFILE_TYPE_APPS) {
            return AppsProfileActivity.getCloneProfileIntent(context,
                    oldProfileId, newProfileName);
        } else if (type == BaseProfile.PROFILE_TYPE_APPS_FILTER) {
            return AppsFilterProfileActivity.getCloneProfileIntent(context,
                    oldProfileId, newProfileName);
        } else throw new UnsupportedOperationException("Invalid type: " + type);
    }

    @NonNull
    public static Path getProfilesDir() {
        Context context = ContextUtils.getContext();
        return Objects.requireNonNull(Paths.build(context.getFilesDir(), "profiles"));
    }

    @Nullable
    public static Path findProfilePathById(@NonNull String profileId) {
        return Paths.build(getProfilesDir(), profileId + PROFILE_EXT);
    }

    @NonNull
    public static Path requireProfilePathById(@NonNull String profileId) throws IOException {
        Path profilesDir = getProfilesDir();
        if (!profilesDir.exists()) {
            profilesDir.mkdirs();
        }
        return getProfilesDir().findOrCreateFile(profileId + PROFILE_EXT, null);
    }

    public static boolean deleteProfile(@NonNull String profileId) {
        Path profilePath = findProfilePathById(profileId);
        return profilePath == null || !profilePath.exists() || profilePath.delete();
    }

    @NonNull
    public static String getProfileName(@NonNull String filename) {
        int index = filename.indexOf(PROFILE_EXT);
        if (index == -1) {
            // Maybe only ends with .json
            index = filename.indexOf(".json");
        }
        return index != -1 ? filename.substring(0, index) : filename;
    }

    @NonNull
    public static ArrayList<String> getProfileNames() {
        Path profilesPath = getProfilesDir();
        String[] profilesFiles = profilesPath.listFileNames((dir, name) -> name.endsWith(PROFILE_EXT));
        ArrayList<String> profileNames = new ArrayList<>(profilesFiles.length);
        for (String profile : profilesFiles) {
            profileNames.add(getProfileName(profile));
        }
        return profileNames;
    }

    @NonNull
    public static HashMap<BaseProfile, CharSequence> getProfileSummaries(@NonNull Context context) throws IOException, JSONException {
        Path profilesPath = getProfilesDir();
        Path[] profilePaths = profilesPath.listFiles((dir, name) -> name.endsWith(PROFILE_EXT));
        HashMap<BaseProfile, CharSequence> profiles = new HashMap<>(profilePaths.length);
        for (Path profilePath : profilePaths) {
            if (ThreadUtils.isInterrupted()) {
                // Thread interrupted, return as is
                return profiles;
            }
            BaseProfile profile = BaseProfile.fromPath(profilePath);
            profiles.put(profile, profile.toLocalizedString(context));
        }
        return profiles;
    }

    @NonNull
    public static <T> List<T> getProfiles(int type) throws IOException, JSONException {
        Path profilesPath = getProfilesDir();
        Path[] profilePaths = profilesPath.listFiles((dir, name) -> name.endsWith(PROFILE_EXT));
        List<T> profiles = new ArrayList<>(profilePaths.length);
        for (Path profilePath : profilePaths) {
            BaseProfile profile = BaseProfile.fromPath(profilePath);
            if (profile.type == type) {
                profiles.add((T) profile);
            }
        }
        return profiles;
    }

    @NonNull
    public static List<BaseProfile> getProfiles() throws IOException, JSONException {
        Path profilesPath = getProfilesDir();
        Path[] profilePaths = profilesPath.listFiles((dir, name) -> name.endsWith(PROFILE_EXT));
        List<BaseProfile> profiles = new ArrayList<>(profilePaths.length);
        for (Path profilePath : profilePaths) {
            profiles.add(BaseProfile.fromPath(profilePath));
        }
        return profiles;
    }

    @NonNull
    public static String getProfileIdCompat(@NonNull String profileName) {
        String profileId = Paths.sanitizeFilename(profileName, "_", Paths.SANITIZE_FLAG_SPACE
                | Paths.SANITIZE_FLAG_UNIX_ILLEGAL_CHARS | Paths.SANITIZE_FLAG_UNIX_RESERVED
                | Paths.SANITIZE_FLAG_FAT_ILLEGAL_CHARS);
        return profileId != null ? profileId : UUID.randomUUID().toString();
    }

    @NonNull
    private final BaseProfile mProfile;
    @Nullable
    private ProfileLogger mLogger;
    private boolean mRequiresRestart;

    public ProfileManager(@NonNull String profileId, @Nullable Path profilePath) throws IOException {
        try {
            mLogger = new ProfileLogger(profileId);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            Path realProfilePath = profilePath != null ? profilePath : findProfilePathById(profileId);
            mProfile = BaseProfile.fromPath(realProfilePath);
        } catch (IOException e) {
            if (mLogger != null) {
                mLogger.println(null, e);
            }
            throw e;
        } catch (JSONException e) {
            if (mLogger != null) {
                mLogger.println(null, e);
            }
            throw new IOException(e);
        }
    }

    public boolean requiresRestart() {
        return mRequiresRestart;
    }

    @SuppressLint("SwitchIntDef")
    public void applyProfile(@Nullable String state, @Nullable ProgressHandler progressHandler) {
        // Set state
        if (state == null) state = mProfile.state;
        log("====> Started execution with state " + state);
        ProfileApplierResult result = mProfile.apply(state, mLogger, progressHandler);
        mRequiresRestart = result.requiresRestart();
        log("====> Execution completed.");
    }

    public void conclude() {
        if (mLogger != null) {
            mLogger.close();
        }
    }

    private void log(@Nullable String message) {
        if (mLogger != null) {
            mLogger.println(message);
        }
    }
}
