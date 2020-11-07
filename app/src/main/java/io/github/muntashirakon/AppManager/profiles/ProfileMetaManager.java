/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.profiles;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupDialogFragment;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;

public class ProfileMetaManager {
    public static final String PROFILE_EXT = ".am.json";

    public static class Profile {
        public int type = 0;  // type
        public int version = 1;  // version
        public boolean allowRoutine = true;  // allow_routine
        @Nullable
        private String state;  // state
        @NonNull
        private String name;  // name (name of the profile)
        @NonNull
        public String[] packages;  // packages (a list of packages)
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
         * Whether to enable or disable the selected packages. This only functions when the value is
         * set to {@code true} and {@link #state} {@code on} means disable and
         * {@code off} means enable. If it is set to {@code false}, it will be removed from
         * the profile.
         */
        public boolean disable = false;  // misc.disable (false = remove)
        public boolean forceStop = false;  // misc.force_stop (false = remove)
        public boolean clearCache = false;  // misc.clear_cache (false = remove)
        public boolean clearData = false;  // misc.clear_data (false = remove)
        public boolean blockTrackers = false;  // misc.block_trackers (false = remove)
        public boolean backupApk = false;  // misc.backup_apk (false = remove)

        Profile(@NonNull String profileName, @NonNull String[] packageNames) {
            name = profileName;
            packages = packageNames;
        }

        public static class BackupInfo {
            @BackupDialogFragment.ActionMode
            public int mode = BackupDialogFragment.MODE_BACKUP;
            @Nullable
            public String name;
            public int flags = (int) AppPref.get(AppPref.PrefKey.PREF_BACKUP_FLAGS_INT);
        }
    }

    @NonNull
    public static File getProfilesDir() {
        return new File(AppManager.getContext().getFilesDir(), "profiles");
    }

    @NonNull
    private String profileName;
    private File profilePath;
    public Profile profile;

    public ProfileMetaManager(@NonNull String profileName) {
        this(profileName, null);
    }

    public ProfileMetaManager(@NonNull String profileName, @Nullable String[] packages) {
        this.profileName = getCleanedProfileName(profileName);
        this.profilePath = getProfilesDir();
        if (!profilePath.exists()) {
            //noinspection ResultOfMethodCallIgnored
            profilePath.mkdirs();
        }
        if (packages != null) this.profile = new Profile(profileName, packages);
        if (getProfilePath().exists()) {
            try {
                readProfile(IOUtils.getFileContent(getProfilePath()));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public Profile newProfile(String[] packages) {
        return profile = new Profile(profileName, packages);
    }

    @NonNull
    public String getProfileName() {
        return profileName;
    }

    @NonNull
    public static ProfileMetaManager readProfile(@NonNull String profileName,
                                                 @NonNull String profileContents)
            throws JSONException {
        ProfileMetaManager manager = new ProfileMetaManager(profileName);
        manager.readProfile(profileContents);
        return manager;
    }

    public void readProfile(@Nullable String profileStr) throws JSONException {
        if (TextUtils.isEmpty(profileStr)) throw new JSONException("Empty JSON string");
        @SuppressWarnings("ConstantConditions")
        JSONObject profileObj = new JSONObject(profileStr);
        String profileName = profileObj.getString("name");
        String[] packageNames = JSONUtils.getArray(String.class, profileObj.getJSONArray("packages"));
        profile = new Profile(profileName, packageNames);
        profile.type = profileObj.getInt("type");
        profile.version = profileObj.getInt("version");
        try {
            profile.allowRoutine = profileObj.getBoolean("allow_routine");
        } catch (JSONException ignore) {}
        profile.state = JSONUtils.getStringOrNull(profileObj, "state");
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
        JSONObject backupInfo = null;
        try {
            backupInfo = profileObj.getJSONObject("backup_data");
        } catch (JSONException ignore) {
        }
        if (backupInfo != null) {
            profile.backupData = new Profile.BackupInfo();
            profile.backupData.name = JSONUtils.getStringOrNull(backupInfo, "name");
            profile.backupData.flags = backupInfo.getInt("flags");
            profile.backupData.mode = backupInfo.getInt("mode");
        }
        profile.exportRules = JSONUtils.getIntOrNull(profileObj, "export_rules");
        // Misc
        try {
            List<String> miscConfig = JSONUtils.getArray(profileObj.getJSONArray("misc"));
            profile.disable = miscConfig.contains("disable");
            profile.forceStop = miscConfig.contains("force_stop");
            profile.clearCache = miscConfig.contains("clear_cache");
            profile.clearData = miscConfig.contains("clear_data");
            profile.blockTrackers = miscConfig.contains("block_trackers");
            profile.backupApk = miscConfig.contains("backup_apk");
        } catch (Exception ignore) {
        }
    }

    public void writeProfile() throws IOException, JSONException {
        if (profile == null) throw new IOException("Profile is not set");
        try (FileOutputStream outputStream = new FileOutputStream(getProfilePath())) {
            JSONObject profileObj = new JSONObject();
            profileObj.put("type", profile.type);
            profileObj.put("version", profile.version);
            if (!profile.allowRoutine) {
                // Only save allow_routine if it's set to false
                profileObj.put("allow_routine", false);
            }
            profileObj.put("name", profile.name);
            profileObj.put("state", profile.state);
            profileObj.put("packages", JSONUtils.getJSONArray(profile.packages));
            profileObj.put("components", JSONUtils.getJSONArray(profile.components));
            profileObj.put("app_ops", JSONUtils.getJSONArray(profile.appOps));
            profileObj.put("permissions", JSONUtils.getJSONArray(profile.permissions));
            // Backup info
            if (profile.backupData != null) {
                JSONObject backupInfo = new JSONObject();
                backupInfo.put("name", profile.backupData.name);
                backupInfo.put("flags", profile.backupData.flags);
                backupInfo.put("mode", profile.backupData.mode);
                profileObj.put("backup_data", backupInfo);
            }
            profileObj.put("export_rules", profile.exportRules);
            // Misc
            JSONArray jsonArray = new JSONArray();
            if (profile.disable) jsonArray.put("disable");
            if (profile.forceStop) jsonArray.put("force_stop");
            if (profile.clearCache) jsonArray.put("clear_cache");
            if (profile.clearData) jsonArray.put("clear_data");
            if (profile.blockTrackers) jsonArray.put("block_trackers");
            if (profile.backupApk) jsonArray.put("backup_apk");
            if (jsonArray.length() > 0) profileObj.put("misc", jsonArray);
            outputStream.write(profileObj.toString().getBytes());
        }
    }

    @NonNull
    public List<String> getLocalisedSummary(Context context) {
        List<String> arrayList = new ArrayList<>();
        if (profile == null) return arrayList;
        if (profile.components != null) arrayList.add(context.getString(R.string.components));
        if (profile.appOps != null) arrayList.add(context.getString(R.string.app_ops));
        if (profile.permissions != null) arrayList.add(context.getString(R.string.permissions));
        if (profile.backupData != null) arrayList.add(context.getString(R.string.backup_restore));
        if (profile.exportRules != null) arrayList.add(context.getString(R.string.blocking_rules));
        if (profile.disable) arrayList.add(context.getString(R.string.disable));
        if (profile.forceStop) arrayList.add(context.getString(R.string.force_stop));
        if (profile.clearCache) arrayList.add(context.getString(R.string.clear_cache));
        if (profile.clearData) arrayList.add(context.getString(R.string.clear_data));
        if (profile.blockTrackers) arrayList.add(context.getString(R.string.trackers));
        if (profile.backupApk) arrayList.add(context.getString(R.string.backup_apk));
        return arrayList;
    }

    @NonNull
    private static String getCleanedProfileName(String dirtyProfileName) {
        return TextUtils.replace(dirtyProfileName, new String[]{".", " "}, new String[]{"_", "_"}).toString();
    }

    @NonNull
    private File getProfilePath() {
        return new File(profilePath, profileName + PROFILE_EXT);
    }
}
