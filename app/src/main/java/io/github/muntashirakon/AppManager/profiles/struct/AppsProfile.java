// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.struct;

import static io.github.muntashirakon.AppManager.profiles.ProfileManager.PROFILE_EXT;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

import org.jetbrains.annotations.Contract;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import aosp.libcore.util.EmptyArray;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.profiles.ProfileManager;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;


public class AppsProfile extends AbsProfile {
    @Contract("null -> fail")
    @NonNull
    public static AppsProfile fromPath(@Nullable Path profilePath) throws IOException, JSONException {
        if (profilePath == null) {
            throw new IOException("Empty profile path");
        }
        return read(profilePath.getContentAsString());
    }

    @NonNull
    public static AppsProfile newProfile(@NonNull String newProfileName, @Nullable AppsProfile profile) {
        String profileId = generateProfileId(newProfileName);
        // TODO: 17/9/23 TODO: Remove these once we migrated to UUID based profile ID
        // BEGIN legacy: For legacy profile, the generated ID can be the same as an existing profile
        Path profilesDir = ProfileManager.getProfilesDir();
        Path profilePath = Paths.build(profilesDir, profileId + PROFILE_EXT);
        String profileName = newProfileName;
        int i = 1;
        while (profilePath != null && profilePath.exists()) {
            // Try another name
            profileName = newProfileName + " (" + i + ")";
            profileId = generateProfileId(profileName);
            profilePath = Paths.build(profilesDir, profileId + PROFILE_EXT);
            ++i;
        }
        // END legacy: For legacy profile, the generated ID can be the same as an existing profile
        if (profile != null) {
            return new AppsProfile(profileId, profileName, profile);
        }
        return new AppsProfile(profileId, profileName);
    }

    @StringDef({STATE_ON, STATE_OFF})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ProfileState {
    }

    public static final String STATE_ON = "on";
    public static final String STATE_OFF = "off";

    public static class BackupInfo {
        @Nullable
        public String name;
        @BackupFlags.BackupFlag
        public int flags = Prefs.BackupRestore.getBackupFlags();

        public BackupInfo() {
        }

        public BackupInfo(@NonNull BackupInfo backupInfo) {
            name = backupInfo.name;
            flags = backupInfo.flags;
        }
    }

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

    protected AppsProfile(@NonNull String profileId, @NonNull String profileName) {
        super(profileId);
        this.name = profileName;
        this.packages = EmptyArray.STRING;
    }

    protected AppsProfile(@NonNull String profileId, @NonNull String profileName, @NonNull AppsProfile profile) {
        super(profileId);
        name = profileName;
        type = profile.type;
        version = profile.version;
        allowRoutine = profile.allowRoutine;
        state = profile.state;
        users = profile.users != null ? profile.users.clone() : null;
        packages = profile.packages.clone();
        comment = profile.comment;
        components = profile.components != null ? profile.components.clone() : null;
        appOps = profile.appOps != null ? profile.appOps.clone() : null;
        permissions = profile.permissions != null ? profile.permissions.clone() : null;
        backupData = profile.backupData != null ? new AppsProfile.BackupInfo(profile.backupData) : null;
        exportRules = profile.exportRules != null ? profile.exportRules : null;
        freeze = profile.freeze;
        forceStop = profile.forceStop;
        clearCache = profile.clearCache;
        clearData = profile.clearData;
        blockTrackers = profile.blockTrackers;
        saveApk = profile.saveApk;
    }

    public void appendPackages(@NonNull String[] packageList) {
        List<String> uniquePackages = new ArrayList<>();
        for (String newPackage : packageList) {
            if (!ArrayUtils.contains(packages, newPackage)) {
                uniquePackages.add(newPackage);
            }
        }
        packages = ArrayUtils.concatElements(String.class, packages, uniquePackages.toArray(new String[0]));
    }

    @NonNull
    private List<String> getLocalisedSummaryOrComment(Context context) {
        if (comment != null) {
            return Collections.singletonList(comment);
        }

        List<String> arrayList = new ArrayList<>();
        if (components != null) arrayList.add(context.getString(R.string.components));
        if (appOps != null) arrayList.add(context.getString(R.string.app_ops));
        if (permissions != null) arrayList.add(context.getString(R.string.permissions));
        if (backupData != null) arrayList.add(context.getString(R.string.backup_restore));
        if (exportRules != null) arrayList.add(context.getString(R.string.blocking_rules));
        if (freeze) arrayList.add(context.getString(R.string.freeze));
        if (forceStop) arrayList.add(context.getString(R.string.force_stop));
        if (clearCache) arrayList.add(context.getString(R.string.clear_cache));
        if (clearData) arrayList.add(context.getString(R.string.clear_data));
        if (blockTrackers) arrayList.add(context.getString(R.string.trackers));
        if (saveApk) arrayList.add(context.getString(R.string.save_apk));
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

    @Override
    public void write(@NonNull OutputStream out) throws IOException {
        try {
            JSONObject profileObj = new JSONObject();
            profileObj.put("type", type);
            profileObj.put("version", version);
            if (!allowRoutine) {
                // Only save allow_routine if it's set to false
                profileObj.put("allow_routine", false);
            }
            profileObj.put("name", name);
            profileObj.put("comment", comment);
            profileObj.put("state", state);
            profileObj.put("users", JSONUtils.getJSONArray(users));
            profileObj.put("packages", JSONUtils.getJSONArray(packages));
            profileObj.put("components", JSONUtils.getJSONArray(components));
            profileObj.put("app_ops", JSONUtils.getJSONArray(appOps));
            profileObj.put("permissions", JSONUtils.getJSONArray(permissions));
            // Backup info
            if (backupData != null) {
                JSONObject backupInfo = new JSONObject();
                backupInfo.put("name", backupData.name);
                backupInfo.put("flags", backupData.flags);
                profileObj.put("backup_data", backupInfo);
            }
            profileObj.put("export_rules", exportRules);
            // Misc
            JSONArray jsonArray = new JSONArray();
            if (freeze) jsonArray.put("freeze");
            if (forceStop) jsonArray.put("force_stop");
            if (clearCache) jsonArray.put("clear_cache");
            if (clearData) jsonArray.put("clear_data");
            if (blockTrackers) jsonArray.put("block_trackers");
            if (saveApk) jsonArray.put("save_apk");
            if (jsonArray.length() > 0) profileObj.put("misc", jsonArray);
            out.write(profileObj.toString().getBytes());
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    @Contract("!null -> !null")
    @Nullable
    protected static AppsProfile read(@Nullable String profileStr) throws JSONException {
        if (profileStr == null) {
            return null;
        }
        JSONObject profileObj = new JSONObject(profileStr);
        String profileName = profileObj.getString("name");
        String[] packageNames = JSONUtils.getArray(String.class, profileObj.getJSONArray("packages"));
        AppsProfile profile = new AppsProfile(ProfileManager.getProfileIdCompat(profileName), profileName);
        profile.packages = packageNames;
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
            profile.backupData = new AppsProfile.BackupInfo();
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

    @NonNull
    private static String generateProfileId(@NonNull String profileName) {
        // TODO: 16/9/23 Use UUID instead
        return ProfileManager.getProfileIdCompat(profileName);
    }
}
