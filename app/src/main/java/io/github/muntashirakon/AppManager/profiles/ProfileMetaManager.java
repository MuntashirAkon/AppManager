// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.content.Context;
import android.os.RemoteException;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;

import org.jetbrains.annotations.Contract;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import aosp.libcore.util.EmptyArray;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.util.LocalizedString;

public class ProfileMetaManager implements LocalizedString {
    public static final String PROFILE_EXT = ".am.json";

    @StringDef({STATE_ON, STATE_OFF})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ProfileState {
    }

    public static final String STATE_ON = "on";
    public static final String STATE_OFF = "off";

    public static class Profile {
        @NonNull
        public final String name;  // name (name of the profile)

        public int type = 0;  // type
        public int version = 1;  // version
        public boolean allowRoutine = true;  // allow_routine
        @ProfileState
        public String state;  // state
        @Nullable
        public int[] users;  // users
        @NonNull
        public String[] packages;  // packages (a list of packages)
        @Nullable
        public String comment;  // comment
        @Nullable
        public String[] components;  // components
        @Nullable
        public int[] appOps;  // app_ops
        @Nullable
        public String[] permissions;  // permissions
        @Nullable
        public BackupInfo backupData;  // backup_data
        @Nullable
        public Integer exportRules;  // export_rules
        /**
         * Whether to freeze or unfreeze the selected packages. This only functions when the value is
         * set to {@code true} and {@link #state} {@code on} means freeze and
         * {@code off} means unfreeze. If it is set to {@code false}, it will be removed from
         * the profile.
         */
        public boolean freeze = false;  // misc.disable or misc.freeze (false = remove)
        public boolean forceStop = false;  // misc.force_stop (false = remove)
        public boolean clearCache = false;  // misc.clear_cache (false = remove)
        public boolean clearData = false;  // misc.clear_data (false = remove)
        public boolean blockTrackers = false;  // misc.block_trackers (false = remove)
        public boolean saveApk = false;  // misc.save_apk (false = remove)

        private Profile(@NonNull String profileName, @NonNull String[] packageNames) {
            name = profileName;
            packages = packageNames;
        }

        private Profile(@NonNull String profileName, @NonNull Profile profile) {
            name = profileName;
            type = profile.type;
            version = profile.version;
            allowRoutine = profile.allowRoutine;
            state = profile.state;
            users =  profile.users != null ? profile.users.clone() : null;
            packages = profile.packages.clone();
            comment = profile.comment;
            components = profile.components != null ? profile.components.clone() : null;
            appOps = profile.appOps != null ? profile.appOps.clone() : null;
            permissions = profile.permissions != null ? profile.permissions.clone() : null;
            backupData = profile.backupData != null ? new BackupInfo(profile.backupData) : null;
            exportRules = profile.exportRules != null ? profile.exportRules : null;
            freeze = profile.freeze;
            forceStop = profile.forceStop;
            clearCache = profile.clearCache;
            clearData = profile.clearData;
            blockTrackers = profile.blockTrackers;
            saveApk = profile.saveApk;
        }

        public static class BackupInfo {
            @Nullable
            public String name;
            public int flags = (int) AppPref.get(AppPref.PrefKey.PREF_BACKUP_FLAGS_INT);

            public BackupInfo() {}

            public BackupInfo(@NonNull BackupInfo backupInfo) {
                name = backupInfo.name;
                flags = backupInfo.flags;
            }
        }
    }

    @NonNull
    public static Path getProfilesDir() {
        Context context = AppManager.getContext();
        return Objects.requireNonNull(Paths.build(context.getFilesDir(), "profiles"));
    }

    @NonNull
    public static ProfileMetaManager fromPreset(@NonNull String profileName,
                                                @NonNull String presetName)
            throws JSONException {
        String fileContents = FileUtils.getContentFromAssets(AppManager.getContext(), "profiles/" + presetName + ".am.json");
        return fromJSONString(profileName, fileContents);
    }

    @NonNull
    public static ProfileMetaManager fromJSONString(@NonNull String profileName,
                                                    @NonNull String profileContents)
            throws JSONException {
        return new ProfileMetaManager(profileName, Objects.requireNonNull(readProfile(profileContents)));
    }

    @NonNull
    private final Path mProfilePath;
    @NonNull
    private final Profile mProfile;

    public ProfileMetaManager(@NonNull String profileName) {
        Path profilesDir = getProfilesDir();
        if (!profilesDir.exists()) {
            profilesDir.mkdirs();
        }
        mProfilePath = Objects.requireNonNull(Paths.build(profilesDir, getCleanedProfileName(profileName) + PROFILE_EXT));
        Profile profile = null;
        if (mProfilePath.exists()) {
            try {
                profile = readProfile(FileUtils.getFileContent(mProfilePath));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (profile == null) {
            profile = new Profile(profileName, EmptyArray.STRING);
        }
        if (!profile.name.equals(profileName)) {
            // Adjust profile name
            profile = new Profile(profileName, profile);
        }
        mProfile = profile;
    }

    public ProfileMetaManager(@NonNull String profileName, @NonNull Profile profile) {
        Path profilesDir = getProfilesDir();
        if (!profilesDir.exists()) {
            profilesDir.mkdirs();
        }
        mProfilePath = Objects.requireNonNull(Paths.build(profilesDir, getCleanedProfileName(profileName) + PROFILE_EXT));
        mProfile = new Profile(profileName, profile);
    }

    @NonNull
    public String getProfileName() {
        return mProfile.name;
    }

    @NonNull
    public Profile getProfile() {
        return mProfile;
    }

    public void appendPackages(@NonNull Collection<String> packageList) {
        List<String> uniquePackages = new ArrayList<>();
        for (String newPackage : packageList) {
            if (!ArrayUtils.contains(mProfile.packages, newPackage)) {
                uniquePackages.add(newPackage);
            }
        }
        mProfile.packages = ArrayUtils.concatElements(String.class, mProfile.packages, uniquePackages.toArray(new String[0]));
    }

    @Contract("null -> null")
    @Nullable
    public static Profile readProfile(@Nullable String profileStr) throws JSONException {
        if (TextUtilsCompat.isEmpty(profileStr)) {
            return null;
        }
        JSONObject profileObj = new JSONObject(profileStr);
        String profileName = profileObj.getString("name");
        String[] packageNames = JSONUtils.getArray(String.class, profileObj.getJSONArray("packages"));
        Profile profile = new Profile(profileName, packageNames);
        profile.comment = JSONUtils.getString(profileObj, "comment", null);
        profile.type = profileObj.getInt("type");
        profile.version = profileObj.getInt("version");
        profile.allowRoutine = JSONUtils.getBoolean(profileObj, "allow_routine", true);
        profile.state = JSONUtils.getString(profileObj, "state", STATE_ON);
        try {
            profile.users = JSONUtils.getIntArray(profileObj.getJSONArray("users"));
        } catch (JSONException ignore) {
        }
        try {
            profile.components = JSONUtils.getArray(String.class, profileObj.getJSONArray("components"));
        } catch (JSONException ignore) {
        }
        try {
            profile.appOps = JSONUtils.getIntArray(profileObj.getJSONArray("app_ops"));
        } catch (JSONException ignore) {
        }
        try {
            profile.permissions = JSONUtils.getArray(String.class, profileObj.getJSONArray("permissions"));
        } catch (JSONException ignore) {
        }
        // Backup info
        try {
            JSONObject backupInfo = profileObj.getJSONObject("backup_data");
            profile.backupData = new Profile.BackupInfo();
            profile.backupData.name = JSONUtils.getString(backupInfo, "name", null);
            profile.backupData.flags = backupInfo.getInt("flags");
        } catch (JSONException ignore) {
        }
        profile.exportRules = JSONUtils.getIntOrNull(profileObj, "export_rules");
        // Misc
        try {
            List<String> miscConfig = JSONUtils.getArray(profileObj.getJSONArray("misc"));
            profile.freeze = miscConfig.contains("disable") || miscConfig.contains("freeze");
            profile.forceStop = miscConfig.contains("force_stop");
            profile.clearCache = miscConfig.contains("clear_cache");
            profile.clearData = miscConfig.contains("clear_data");
            profile.blockTrackers = miscConfig.contains("block_trackers");
            profile.saveApk = miscConfig.contains("save_apk");
        } catch (Exception ignore) {
        }
        return profile;
    }

    @WorkerThread
    public void writeProfile() throws IOException, JSONException, RemoteException {
        try (OutputStream outputStream = mProfilePath.openOutputStream()) {
            writeProfile(outputStream);
        }
    }

    public void writeProfile(OutputStream outputStream) throws IOException, JSONException {
        JSONObject profileObj = new JSONObject();
        profileObj.put("type", mProfile.type);
        profileObj.put("version", mProfile.version);
        if (!mProfile.allowRoutine) {
            // Only save allow_routine if it's set to false
            profileObj.put("allow_routine", false);
        }
        profileObj.put("name", mProfile.name);
        profileObj.put("comment", mProfile.comment);
        profileObj.put("state", mProfile.state);
        profileObj.put("users", JSONUtils.getJSONArray(mProfile.users));
        profileObj.put("packages", JSONUtils.getJSONArray(mProfile.packages));
        profileObj.put("components", JSONUtils.getJSONArray(mProfile.components));
        profileObj.put("app_ops", JSONUtils.getJSONArray(mProfile.appOps));
        profileObj.put("permissions", JSONUtils.getJSONArray(mProfile.permissions));
        // Backup info
        if (mProfile.backupData != null) {
            JSONObject backupInfo = new JSONObject();
            backupInfo.put("name", mProfile.backupData.name);
            backupInfo.put("flags", mProfile.backupData.flags);
            profileObj.put("backup_data", backupInfo);
        }
        profileObj.put("export_rules", mProfile.exportRules);
        // Misc
        JSONArray jsonArray = new JSONArray();
        if (mProfile.freeze) jsonArray.put("freeze");
        if (mProfile.forceStop) jsonArray.put("force_stop");
        if (mProfile.clearCache) jsonArray.put("clear_cache");
        if (mProfile.clearData) jsonArray.put("clear_data");
        if (mProfile.blockTrackers) jsonArray.put("block_trackers");
        if (mProfile.saveApk) jsonArray.put("save_apk");
        if (jsonArray.length() > 0) profileObj.put("misc", jsonArray);
        outputStream.write(profileObj.toString().getBytes());
    }

    public boolean deleteProfile() {
        if (mProfilePath.exists()) {
            return mProfilePath.delete();
        }
        // Profile doesn't exist
        return false;
    }

    @NonNull
    public List<String> getLocalisedSummaryOrComment(Context context) {
        if (mProfile.comment != null) {
            return Collections.singletonList(mProfile.comment);
        }

        List<String> arrayList = new ArrayList<>();
        if (mProfile.components != null) arrayList.add(context.getString(R.string.components));
        if (mProfile.appOps != null) arrayList.add(context.getString(R.string.app_ops));
        if (mProfile.permissions != null) arrayList.add(context.getString(R.string.permissions));
        if (mProfile.backupData != null) arrayList.add(context.getString(R.string.backup_restore));
        if (mProfile.exportRules != null) arrayList.add(context.getString(R.string.blocking_rules));
        if (mProfile.freeze) arrayList.add(context.getString(R.string.freeze));
        if (mProfile.forceStop) arrayList.add(context.getString(R.string.force_stop));
        if (mProfile.clearCache) arrayList.add(context.getString(R.string.clear_cache));
        if (mProfile.clearData) arrayList.add(context.getString(R.string.clear_data));
        if (mProfile.blockTrackers) arrayList.add(context.getString(R.string.trackers));
        if (mProfile.saveApk) arrayList.add(context.getString(R.string.save_apk));
        return arrayList;
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        List<String> summaries = getLocalisedSummaryOrComment(context);
        if (summaries.isEmpty()) {
            return context.getString(R.string.no_configurations);
        }
        return TextUtils.join(", ", summaries);
    }

    @NonNull
    static String getCleanedProfileName(@NonNull String dirtyProfileName) {
        return dirtyProfileName.trim().replaceAll("[\\\\/:?\"<>|\\s]+", "_");  // [\\/:?"<>|\s]
    }
}
