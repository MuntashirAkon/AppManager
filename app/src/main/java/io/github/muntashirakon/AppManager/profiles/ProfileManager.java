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

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;

public class ProfileManager {
    public static final String TAG = "ProfileManager";

    @NonNull
    public static HashMap<String, String> getProfiles() {
        File profilesPath = ProfileMetaManager.getProfilesDir();
        String[] profilesFiles = ArrayUtils.defeatNullable(profilesPath.list((dir, name) -> name.endsWith(ProfileMetaManager.PROFILE_EXT)));
        HashMap<String, String> profiles = new HashMap<>(profilesFiles.length);
        Context context = AppManager.getContext();
        String summary;
        for (String profile : profilesFiles) {
            int index = profile.indexOf(ProfileMetaManager.PROFILE_EXT);
            profile = profile.substring(0, index);
            summary = TextUtils.join(", ", new ProfileMetaManager(profile).getLocalisedSummaryOrComment(context));
            if (summary.length() == 0) {
                summary = context.getString(R.string.no_configurations);
            }
            profiles.put(profile, summary);
        }
        return profiles;
    }

    @NonNull
    public static HashMap<String, ProfileMetaManager> getProfileMetadata() {
        File profilesPath = ProfileMetaManager.getProfilesDir();
        String[] profilesFiles = ArrayUtils.defeatNullable(profilesPath.list((dir, name) -> name.endsWith(ProfileMetaManager.PROFILE_EXT)));
        HashMap<String, ProfileMetaManager> profiles = new HashMap<>(profilesFiles.length);
        for (String profile : profilesFiles) {
            int index = profile.indexOf(ProfileMetaManager.PROFILE_EXT);
            profile = profile.substring(0, index);
            profiles.put(profile, new ProfileMetaManager(profile));
        }
        return profiles;
    }

    @NonNull
    private final ProfileMetaManager.Profile profile;

    public ProfileManager(@NonNull ProfileMetaManager metaManager) throws FileNotFoundException {
        if (metaManager.profile == null)
            throw new FileNotFoundException("Profile cannot be empty.");
        this.profile = metaManager.profile;
    }

    @SuppressLint("SwitchIntDef")
    public void applyProfile(@Nullable String state) {
        // Set state
        if (state == null) state = profile.state;
        if (state == null) state = ProfileMetaManager.STATE_OFF;

        if (profile.packages.length == 0) return;
        int[] users = profile.users == null ? Users.getUsersHandles() : profile.users;
        List<UserPackagePair> userPackagePairs = new ArrayList<>();
        for (String packageName : profile.packages) {
            for (int user : users) {
                userPackagePairs.add(new UserPackagePair(packageName, user));
            }
        }
        BatchOpsManager batchOpsManager = new BatchOpsManager();
        BatchOpsManager.Result result;
        // Apply component blocking
        String[] components = profile.components;
        if (components != null) {
            Log.d(TAG, "Started block/unblock components. State: " + state);
            Bundle args = new Bundle();
            args.putStringArray(BatchOpsManager.ARG_SIGNATURES, components);
            batchOpsManager.setArgs(args);
            switch (state) {
                case ProfileMetaManager.STATE_ON:
                    result = batchOpsManager.performOp(BatchOpsManager.OP_BLOCK_COMPONENTS, userPackagePairs);
                    break;
                case ProfileMetaManager.STATE_OFF:
                default:
                    result = batchOpsManager.performOp(BatchOpsManager.OP_UNBLOCK_COMPONENTS, userPackagePairs);
            }
            if (!result.isSuccessful()) {
                Log.d(TAG, "Failed packages: " + result.toString());
            }
        } else Log.d(TAG, "Skipped components.");
        // Apply app ops blocking
        int[] appOps = profile.appOps;
        if (appOps != null) {
            Log.d(TAG, "Started ignore/default components. State: " + state);
            Bundle args = new Bundle();
            args.putIntArray(BatchOpsManager.ARG_APP_OPS, appOps);
            switch (state) {
                case ProfileMetaManager.STATE_ON:
                    args.putInt(BatchOpsManager.ARG_APP_OP_MODE, AppOpsManager.MODE_IGNORED);
                    break;
                case ProfileMetaManager.STATE_OFF:
                default:
                    args.putInt(BatchOpsManager.ARG_APP_OP_MODE, AppOpsManager.MODE_DEFAULT);
            }
            batchOpsManager.setArgs(args);
            result = batchOpsManager.performOp(BatchOpsManager.OP_SET_APP_OPS, userPackagePairs);
            if (!result.isSuccessful()) {
                Log.d(TAG, "Failed packages: " + result.toString());
            }
        } else Log.d(TAG, "Skipped app ops.");
        // Apply permissions
        String[] permissions = profile.permissions;
        if (permissions != null) {
            Log.d(TAG, "Started grant/revoke permissions.");
            Bundle args = new Bundle();
            args.putStringArray(BatchOpsManager.ARG_PERMISSIONS, permissions);
            batchOpsManager.setArgs(args);
            switch (state) {
                case ProfileMetaManager.STATE_ON:
                    result = batchOpsManager.performOp(BatchOpsManager.OP_REVOKE_PERMISSIONS, userPackagePairs);
                    break;
                case ProfileMetaManager.STATE_OFF:
                default:
                    result = batchOpsManager.performOp(BatchOpsManager.OP_GRANT_PERMISSIONS, userPackagePairs);
            }
            if (!result.isSuccessful()) {
                Log.d(TAG, "Failed packages: " + result.toString());
            }
        } else Log.d(TAG, "Skipped permissions.");
        // Backup/restore data
        ProfileMetaManager.Profile.BackupInfo backupInfo = profile.backupData;
        if (backupInfo != null) {
            Log.d(TAG, "Started backup/restore.");
            BackupFlags backupFlags = new BackupFlags(backupInfo.flags);
            Bundle args = new Bundle();
            if (backupFlags.backupMultiple() && backupInfo.name != null) {
                if (state.equals(ProfileMetaManager.STATE_OFF)) {
                    args.putStringArray(BatchOpsManager.ARG_BACKUP_NAMES, new String[]{Users.getCurrentUserHandle() + '_' + backupInfo.name});
                } else {
                    args.putStringArray(BatchOpsManager.ARG_BACKUP_NAMES, new String[]{backupInfo.name});
                }
            }
            // Always add backup custom users
            backupFlags.addFlag(BackupFlags.BACKUP_CUSTOM_USERS);
            args.putInt(BatchOpsManager.ARG_FLAGS, backupFlags.getFlags());
            batchOpsManager.setArgs(args);
            switch (state) {
                case ProfileMetaManager.STATE_ON:  // Take backup
                    result = batchOpsManager.performOp(BatchOpsManager.OP_BACKUP, userPackagePairs);
                    break;
                case ProfileMetaManager.STATE_OFF:  // Restore backup
                default:
                    result = batchOpsManager.performOp(BatchOpsManager.OP_RESTORE_BACKUP, userPackagePairs);
            }
            if (!result.isSuccessful()) {
                Log.d(TAG, "Failed packages: " + result.toString());
            }
        } else Log.d(TAG, "Skipped backup/restore.");
        // Backup rules
        Integer rulesFlag = profile.exportRules;
        if (rulesFlag != null) {
            Log.d(TAG, "Not implemented export rules.");
            // TODO(18/11/20): Export rules
        } else Log.d(TAG, "Skipped export rules.");
        // Disable/enable
        if (profile.disable) {
            Log.d(TAG, "Started disable/enable. State: " + state);
            switch (state) {
                case ProfileMetaManager.STATE_ON:
                    result = batchOpsManager.performOp(BatchOpsManager.OP_DISABLE, userPackagePairs);
                    break;
                case ProfileMetaManager.STATE_OFF:
                default:
                    result = batchOpsManager.performOp(BatchOpsManager.OP_ENABLE, userPackagePairs);
            }
            if (!result.isSuccessful()) {
                Log.d(TAG, "Failed packages: " + result.toString());
            }
        } else Log.d(TAG, "Skipped disable/enable.");
        // Force-stop
        if (profile.forceStop) {
            Log.d(TAG, "Started force-stop.");
            result = batchOpsManager.performOp(BatchOpsManager.OP_FORCE_STOP, userPackagePairs);
            if (!result.isSuccessful()) {
                Log.d(TAG, "Failed packages: " + result.toString());
            }
        } else Log.d(TAG, "Skipped force stop.");
        // Clear cache
        if (profile.clearCache) {
            Log.d(TAG, "Started clear cache.");
            result = batchOpsManager.performOp(BatchOpsManager.OP_CLEAR_CACHE, userPackagePairs);
            if (!result.isSuccessful()) {
                Log.d(TAG, "Failed packages: " + result.toString());
            }
        } else Log.d(TAG, "Skipped clear cache.");
        // Clear data
        if (profile.clearData) {
            Log.d(TAG, "Started clear data.");
            result = batchOpsManager.performOp(BatchOpsManager.OP_CLEAR_DATA, userPackagePairs);
            if (!result.isSuccessful()) {
                Log.d(TAG, "Failed packages: " + result.toString());
            }
        } else Log.d(TAG, "Skipped clear data.");
        // Block trackers
        if (profile.blockTrackers) {
            Log.d(TAG, "Started block trackers. State: " + state);
            switch (state) {
                case ProfileMetaManager.STATE_ON:
                    result = batchOpsManager.performOp(BatchOpsManager.OP_BLOCK_TRACKERS, userPackagePairs);
                    break;
                case ProfileMetaManager.STATE_OFF:
                default:
                    result = batchOpsManager.performOp(BatchOpsManager.OP_UNBLOCK_TRACKERS, userPackagePairs);
            }
            if (!result.isSuccessful()) {
                Log.d(TAG, "Failed packages: " + result.toString());
            }
        } else Log.d(TAG, "Skipped block trackers.");
        // Backup apk
        if (profile.backupApk) {
            Log.d(TAG, "Started backup apk.");
            result = batchOpsManager.performOp(BatchOpsManager.OP_BACKUP_APK, userPackagePairs);
            if (!result.isSuccessful()) {
                Log.d(TAG, "Failed packages: " + result.toString());
            }
        } else Log.d(TAG, "Skipped backup apk.");
    }
}
