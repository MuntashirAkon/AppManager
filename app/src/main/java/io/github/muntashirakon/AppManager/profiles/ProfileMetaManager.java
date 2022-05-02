// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.content.Context;
import android.os.RemoteException;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.io.ProxyOutputStream;
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
        public boolean saveApk = false;  // misc.save_apk (false = remove)

        Profile(@NonNull String profileName, @NonNull String[] packageNames) {
            name = profileName;
            packages = packageNames;
        }

        public static class BackupInfo {
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
        return new ProfileMetaManager(profileName, profileContents);
    }

    @NonNull
    private final String mProfileName;
    @NonNull
    private final File mProfilePath;
    @Nullable
    public Profile profile;

    public ProfileMetaManager(@NonNull String profileName) {
        this(profileName, null, null);
    }

    public ProfileMetaManager(@NonNull String profileName, boolean require) throws ProfileNotFoundException {
        this(profileName, null, null);
        if (require && this.profile == null) {
            throw new ProfileNotFoundException("Profile " + profileName + " not found.");
        }
    }

    public ProfileMetaManager(@NonNull String profileName, @Nullable String jsonContent) {
        this(profileName, null, jsonContent);
    }

    public ProfileMetaManager(@NonNull String profileName, @Nullable String[] packages) {
        this(profileName, packages, null);
    }

    public ProfileMetaManager(@NonNull String profileName, @Nullable String[] packages, @Nullable String jsonContent) {
        File profilesDir = getProfilesDir();
        if (!profilesDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            profilesDir.mkdirs();
        }
        mProfilePath = new File(profilesDir, getCleanedProfileName(profileName) + PROFILE_EXT);
        if (jsonContent != null) {
            try {
                profile = readProfile(jsonContent);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if (mProfilePath.exists()) {
            try {
                profile = readProfile(FileUtils.getFileContent(mProfilePath));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (packages != null) {
            if (profile != null) {
                profile.packages = packages;
            } else {
                profile = new Profile(profileName, packages);
            }
        }
        mProfileName = profile != null ? profile.name : profileName;
    }

    @NonNull
    public Profile newProfile(@NonNull String[] packages) {
        return profile = new Profile(mProfileName, packages);
    }

    @NonNull
    public String getProfileName() {
        return mProfileName;
    }

    @NonNull
    public static Profile readProfile(@Nullable String profileStr) throws JSONException {
        if (TextUtils.isEmpty(profileStr)) throw new JSONException("Empty JSON string");
        @SuppressWarnings("ConstantConditions")  // Never null here
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
            profile.disable = miscConfig.contains("disable");
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
        try (OutputStream outputStream = new ProxyOutputStream(mProfilePath)) {
            writeProfile(outputStream);
        }
    }

    public void writeProfile(OutputStream outputStream) throws IOException, JSONException {
        if (profile == null) throw new IOException("Profile is not set");
        JSONObject profileObj = new JSONObject();
        profileObj.put("type", profile.type);
        profileObj.put("version", profile.version);
        if (!profile.allowRoutine) {
            // Only save allow_routine if it's set to false
            profileObj.put("allow_routine", false);
        }
        profileObj.put("name", profile.name);
        profileObj.put("comment", profile.comment);
        profileObj.put("state", profile.state);
        profileObj.put("users", JSONUtils.getJSONArray(profile.users));
        profileObj.put("packages", JSONUtils.getJSONArray(profile.packages));
        profileObj.put("components", JSONUtils.getJSONArray(profile.components));
        profileObj.put("app_ops", JSONUtils.getJSONArray(profile.appOps));
        profileObj.put("permissions", JSONUtils.getJSONArray(profile.permissions));
        // Backup info
        if (profile.backupData != null) {
            JSONObject backupInfo = new JSONObject();
            backupInfo.put("name", profile.backupData.name);
            backupInfo.put("flags", profile.backupData.flags);
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
        if (profile.saveApk) jsonArray.put("save_apk");
        if (jsonArray.length() > 0) profileObj.put("misc", jsonArray);
        outputStream.write(profileObj.toString().getBytes());
    }

    public boolean deleteProfile() {
        if (mProfilePath.exists()) {
            return mProfilePath.delete();
        }
        // Profile doesn't exist
        return true;
    }

    @NonNull
    public List<String> getLocalisedSummaryOrComment(Context context) {
        if (profile != null && profile.comment != null)
            return Collections.singletonList(profile.comment);

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
        if (profile.saveApk) arrayList.add(context.getString(R.string.save_apk));
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
