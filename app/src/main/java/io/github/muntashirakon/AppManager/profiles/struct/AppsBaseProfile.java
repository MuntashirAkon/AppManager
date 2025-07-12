// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.struct;

import android.app.AppOpsManager;
import android.content.Context;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.struct.BatchAppOpsOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchBackupOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchComponentOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchPermissionOptions;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.profiles.ProfileLogger;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.JSONUtils;


public abstract class AppsBaseProfile extends BaseProfile {
    public static final String TAG = AppsBaseProfile.class.getSimpleName();

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

    public int version = 1;  // version
    public boolean allowRoutine = true;  // allow_routine
    @Nullable
    public int[] users;  // users
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

    protected AppsBaseProfile(@NonNull String profileId, @NonNull String profileName, int profileType) {
        super(profileId, profileName, profileType);
    }

    protected AppsBaseProfile(@NonNull String profileId, @NonNull String profileName, @NonNull AppsBaseProfile profile) {
        super(profileId, profileName, profile.type);
        version = profile.version;
        allowRoutine = profile.allowRoutine;
        state = profile.state;
        users = profile.users != null ? profile.users.clone() : null;
        comment = profile.comment;
        components = profile.components != null ? profile.components.clone() : null;
        appOps = profile.appOps != null ? profile.appOps.clone() : null;
        permissions = profile.permissions != null ? profile.permissions.clone() : null;
        backupData = profile.backupData != null ? new AppsBaseProfile.BackupInfo(profile.backupData) : null;
        exportRules = profile.exportRules != null ? profile.exportRules : null;
        freeze = profile.freeze;
        forceStop = profile.forceStop;
        clearCache = profile.clearCache;
        clearData = profile.clearData;
        blockTrackers = profile.blockTrackers;
        saveApk = profile.saveApk;
    }

    protected ProfileApplierResult apply(@NonNull List<String> packageList, List<Integer> assocUsers, @NonNull String state, @Nullable ProfileLogger logger, @Nullable ProgressHandler progressHandler) {
        // Send progress
        if (progressHandler != null) {
            progressHandler.postUpdate(calculateMaxProgress(packageList), 0);
        }
        ProfileApplierResult profileApplierResult = new ProfileApplierResult();
        BatchOpsManager batchOpsManager = new BatchOpsManager(logger);
        BatchOpsManager.Result result;
        // Apply component blocking
        String[] components = this.components;
        if (components != null) {
            log(logger, "====> Started block/unblock components. State: " + state);
            BatchComponentOptions options = new BatchComponentOptions(components);
            int op;
            switch (state) {
                case BaseProfile.STATE_ON:
                    op = BatchOpsManager.OP_BLOCK_COMPONENTS;
                    break;
                case BaseProfile.STATE_OFF:
                    op = BatchOpsManager.OP_UNBLOCK_COMPONENTS;
                    break;
                default:
                    op = BatchOpsManager.OP_NONE;
            }
            BatchOpsManager.BatchOpsInfo info = BatchOpsManager.BatchOpsInfo.getInstance(op, packageList, assocUsers, options);
            result = batchOpsManager.performOp(info, progressHandler);
            if (!result.isSuccessful()) {
                Log.d(TAG, "Failed packages: %s", result);
            }
        } else Log.d(TAG, "Skipped components.");
        // Apply app ops blocking
        int[] appOps = this.appOps;
        if (appOps != null) {
            log(logger, "====> Started ignore/default components. State: " + state);
            int mode;
            switch (state) {
                case BaseProfile.STATE_ON:
                    mode = AppOpsManager.MODE_IGNORED;
                    break;
                case BaseProfile.STATE_OFF:
                default:
                    mode = AppOpsManager.MODE_DEFAULT;
            }
            BatchAppOpsOptions options = new BatchAppOpsOptions(appOps, mode);
            BatchOpsManager.BatchOpsInfo info = BatchOpsManager.BatchOpsInfo.getInstance(BatchOpsManager.OP_SET_APP_OPS, packageList,
                    assocUsers, options);
            result = batchOpsManager.performOp(info, progressHandler);
            if (!result.isSuccessful()) {
                Log.d(TAG, "Failed packages: %s", result);
            }
        } else Log.d(TAG, "Skipped app ops.");
        // Apply permissions
        String[] permissions = this.permissions;
        if (permissions != null) {
            log(logger, "====> Started grant/revoke permissions.");
            int op;
            switch (state) {
                case BaseProfile.STATE_ON:
                    op = BatchOpsManager.OP_REVOKE_PERMISSIONS;
                    break;
                case BaseProfile.STATE_OFF:
                    op = BatchOpsManager.OP_GRANT_PERMISSIONS;
                    break;
                default:
                    op = BatchOpsManager.OP_NONE;
            }
            BatchPermissionOptions options = new BatchPermissionOptions(permissions);
            BatchOpsManager.BatchOpsInfo info = BatchOpsManager.BatchOpsInfo.getInstance(op, packageList, assocUsers, options);
            result = batchOpsManager.performOp(info, progressHandler);
            if (!result.isSuccessful()) {
                Log.d(TAG, "Failed packages: %s", result);
            }
        } else Log.d(TAG, "Skipped permissions.");
        // Backup rules
        Integer rulesFlag = this.exportRules;
        if (rulesFlag != null) {
            log(logger, "====> Not implemented export rules.");
            // TODO(18/11/20): Export rules
        } else Log.d(TAG, "Skipped export rules.");
        // Disable/enable
        if (this.freeze) {
            log(logger, "====> Started freeze/unfreeze. State: " + state);
            int op;
            switch (state) {
                case BaseProfile.STATE_ON:
                    op = BatchOpsManager.OP_FREEZE;
                    break;
                case BaseProfile.STATE_OFF:
                    op = BatchOpsManager.OP_UNFREEZE;
                    break;
                default:
                    op = BatchOpsManager.OP_NONE;
            }
            BatchOpsManager.BatchOpsInfo info = BatchOpsManager.BatchOpsInfo.getInstance(op, packageList, assocUsers, null);
            result = batchOpsManager.performOp(info, progressHandler);
            if (!result.isSuccessful()) {
                Log.d(TAG, "Failed packages: %s", result);
            }
        } else Log.d(TAG, "Skipped disable/enable.");
        // Force-stop
        if (this.forceStop) {
            log(logger, "====> Started force-stop.");
            BatchOpsManager.BatchOpsInfo info = BatchOpsManager.BatchOpsInfo.getInstance(BatchOpsManager.OP_FORCE_STOP, packageList, assocUsers, null);
            result = batchOpsManager.performOp(info, progressHandler);
            if (!result.isSuccessful()) {
                Log.d(TAG, "Failed packages: %s", result);
            }
        } else Log.d(TAG, "Skipped force stop.");
        // Clear cache
        if (this.clearCache) {
            log(logger, "====> Started clear cache.");
            BatchOpsManager.BatchOpsInfo info = BatchOpsManager.BatchOpsInfo.getInstance(BatchOpsManager.OP_CLEAR_CACHE, packageList, assocUsers, null);
            result = batchOpsManager.performOp(info, progressHandler);
            if (!result.isSuccessful()) {
                Log.d(TAG, "Failed packages: %s", result);
            }
        } else Log.d(TAG, "Skipped clear cache.");
        // Clear data
        if (this.clearData) {
            log(logger, "====> Started clear data.");
            BatchOpsManager.BatchOpsInfo info = BatchOpsManager.BatchOpsInfo.getInstance(BatchOpsManager.OP_CLEAR_DATA, packageList, assocUsers, null);
            result = batchOpsManager.performOp(info, progressHandler);
            if (!result.isSuccessful()) {
                Log.d(TAG, "Failed packages: %s", result);
            }
        } else Log.d(TAG, "Skipped clear data.");
        // Block trackers
        if (this.blockTrackers) {
            log(logger, "====> Started block trackers. State: " + state);
            int op;
            switch (state) {
                case BaseProfile.STATE_ON:
                    op = BatchOpsManager.OP_BLOCK_TRACKERS;
                    break;
                case BaseProfile.STATE_OFF:
                    op = BatchOpsManager.OP_UNBLOCK_TRACKERS;
                    break;
                default:
                    op = BatchOpsManager.OP_NONE;
            }
            BatchOpsManager.BatchOpsInfo info = BatchOpsManager.BatchOpsInfo.getInstance(op, packageList, assocUsers, null);
            result = batchOpsManager.performOp(info, progressHandler);
            if (!result.isSuccessful()) {
                Log.d(TAG, "Failed packages: %s", result);
            }
        } else Log.d(TAG, "Skipped block trackers.");
        // Backup apk
        if (this.saveApk) {
            log(logger, "====> Started backup apk.");
            BatchOpsManager.BatchOpsInfo info = BatchOpsManager.BatchOpsInfo.getInstance(BatchOpsManager.OP_BACKUP_APK, packageList, assocUsers, null);
            result = batchOpsManager.performOp(info, progressHandler);
            if (!result.isSuccessful()) {
                Log.d(TAG, "Failed packages: %s", result);
            }
        } else Log.d(TAG, "Skipped backup apk.");
        // Backup/restore data
        AppsBaseProfile.BackupInfo backupInfo = this.backupData;
        if (backupInfo != null) {
            log(logger, "====> Started backup/restore.");
            BackupFlags backupFlags = new BackupFlags(backupInfo.flags);
            String[] backupNames = null;
            if (backupFlags.backupMultiple() && backupInfo.name != null) {
                if (state.equals(BaseProfile.STATE_OFF)) {
                    backupNames = new String[]{UserHandleHidden.myUserId() + '_' + backupInfo.name};
                } else {
                    backupNames = new String[]{backupInfo.name};
                }
            }
            // Always add backup custom users
            backupFlags.addFlag(BackupFlags.BACKUP_CUSTOM_USERS);
            BatchBackupOptions options = new BatchBackupOptions(backupFlags.getFlags(), backupNames);
            int op;
            switch (state) {
                case BaseProfile.STATE_ON:  // Take backup
                    op = BatchOpsManager.OP_BACKUP;
                    break;
                case BaseProfile.STATE_OFF:  // Restore backup
                    op = BatchOpsManager.OP_RESTORE_BACKUP;
                    break;
                default:
                    op = BatchOpsManager.OP_NONE;
            }
            BatchOpsManager.BatchOpsInfo info = BatchOpsManager.BatchOpsInfo.getInstance(op, packageList, assocUsers, options);
            result = batchOpsManager.performOp(info, progressHandler);
            profileApplierResult.setRequiresRestart(profileApplierResult.requiresRestart() | result.requiresRestart());
            if (!result.isSuccessful()) {
                Log.d(TAG, "Failed packages: %s", result);
            }
        } else Log.d(TAG, "Skipped backup/restore.");
        batchOpsManager.conclude();
        return profileApplierResult;
    }

    private int calculateMaxProgress(@NonNull List<String> userPackagePairs) {
        int packageCount = userPackagePairs.size();
        int opCount = 0;
        if (components != null) ++opCount;
        if (appOps != null) ++opCount;
        if (permissions != null) ++opCount;
        // if (profile.exportRules != null) ++opCount; todo
        if (freeze) ++opCount;
        if (forceStop) ++opCount;
        if (clearCache) ++opCount;
        if (clearData) ++opCount;
        if (blockTrackers) ++opCount;
        if (saveApk) ++opCount;
        if (backupData != null) ++opCount;
        return opCount * packageCount;
    }

    private void log(@Nullable ProfileLogger logger, @Nullable String message) {
        if (logger != null) {
            logger.println(message);
        }
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

    @CallSuper
    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject profileObj = super.serializeToJson();
        profileObj.put("version", version);
        if (!allowRoutine) {
            // Only save allow_routine if it's set to false
            profileObj.put("allow_routine", false);
        }
        profileObj.put("comment", comment);
        profileObj.put("users", JSONUtils.getJSONArray(users));
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
        return profileObj;
    }

    protected AppsBaseProfile(@NonNull JSONObject profileObj) throws JSONException {
        super(profileObj);
        comment = JSONUtils.getString(profileObj, "comment", null);
        version = profileObj.getInt("version");
        allowRoutine = profileObj.optBoolean("allow_routine", true);
        try {
            users = JSONUtils.getIntArray(profileObj.getJSONArray("users"));
        } catch (JSONException ignore) {
        }
        try {
            components = JSONUtils.getArray(String.class, profileObj.getJSONArray("components"));
        } catch (JSONException ignore) {
        }
        try {
            appOps = JSONUtils.getIntArray(profileObj.getJSONArray("app_ops"));
        } catch (JSONException ignore) {
        }
        try {
            permissions = JSONUtils.getArray(String.class, profileObj.getJSONArray("permissions"));
        } catch (JSONException ignore) {
        }
        // Backup info
        try {
            JSONObject backupInfo = profileObj.getJSONObject("backup_data");
            backupData = new AppsBaseProfile.BackupInfo();
            backupData.name = JSONUtils.getString(backupInfo, "name", null);
            backupData.flags = backupInfo.getInt("flags");
        } catch (JSONException ignore) {
        }
        exportRules = JSONUtils.getIntOrNull(profileObj, "export_rules");
        // Misc
        try {
            List<String> miscConfig = JSONUtils.getArray(profileObj.getJSONArray("misc"));
            freeze = miscConfig.contains("disable") || miscConfig.contains("freeze");
            forceStop = miscConfig.contains("force_stop");
            clearCache = miscConfig.contains("clear_cache");
            clearData = miscConfig.contains("clear_data");
            blockTrackers = miscConfig.contains("block_trackers");
            saveApk = miscConfig.contains("save_apk");
        } catch (Exception ignore) {
        }
    }
}
