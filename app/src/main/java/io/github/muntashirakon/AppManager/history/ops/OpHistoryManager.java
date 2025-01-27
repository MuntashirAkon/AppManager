// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import io.github.muntashirakon.AppManager.apk.installer.ApkQueueItem;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerService;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.profiles.ProfileApplierService;
import io.github.muntashirakon.AppManager.profiles.ProfileQueueItem;

public final class OpHistoryManager {
    public static final String TAG = OpHistoryManager.class.getSimpleName();

    public static final String HISTORY_TYPE_BATCH_OPS = "batch_ops";
    public static final String HISTORY_TYPE_INSTALLER = "installer";
    public static final String HISTORY_TYPE_PROFILE = "profile";

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({HISTORY_TYPE_BATCH_OPS, HISTORY_TYPE_INSTALLER, HISTORY_TYPE_PROFILE})
    public @interface HistoryType {
    }

    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILURE = "failure";

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({STATUS_SUCCESS, STATUS_FAILURE})
    public @interface Status {
    }

    private static final MutableLiveData<OpHistory> sHistoryAddedLiveData = new MutableLiveData<>();

    public static LiveData<OpHistory> getHistoryAddedLiveData() {
        return sHistoryAddedLiveData;
    }

    @WorkerThread
    public static long addHistoryItem(@HistoryType String historyType,
                                      @NonNull IJsonSerializer item,
                                      boolean success) {
        try {
            OpHistory opHistory = new OpHistory();
            opHistory.type = historyType;
            opHistory.execTime = System.currentTimeMillis();
            opHistory.serializedData = item.serializeToJson().toString();
            opHistory.status = success ? STATUS_SUCCESS : STATUS_FAILURE;
            opHistory.serializedExtra = null;
            long id = AppsDb.getInstance().opHistoryDao().insert(opHistory);
            opHistory.id = id;
            sHistoryAddedLiveData.postValue(opHistory);
            return id;
        } catch (JSONException e) {
            Log.e(TAG, "Could not serialize " + item.getClass(), e);
            return -1;
        }
    }

    @WorkerThread
    public static List<OpHistory> getAllHistoryItems() {
        return AppsDb.getInstance().opHistoryDao().getAll();
    }

    @WorkerThread
    public static void clearAllHistory() {
        AppsDb.getInstance().opHistoryDao().deleteAll();
    }

    @NonNull
    public static Intent getExecutableIntent(@NonNull Context context, @NonNull OpHistoryItem item)
            throws JSONException {
        switch (item.getType()) {
            case HISTORY_TYPE_BATCH_OPS: {
                BatchQueueItem batchQueueItem = BatchQueueItem.DESERIALIZER.deserialize(item.jsonData);
                Intent intent = new Intent(context, BatchOpsService.class);
                intent.putExtra(BatchOpsService.EXTRA_QUEUE_ITEM, batchQueueItem);
                return intent;
            }
            case HISTORY_TYPE_INSTALLER: {
                ApkQueueItem apkQueueItem = ApkQueueItem.DESERIALIZER.deserialize(item.jsonData);
                Intent intent = new Intent(context, PackageInstallerService.class);
                intent.putExtra(PackageInstallerService.EXTRA_QUEUE_ITEM, apkQueueItem);
                return intent;
            }
            case HISTORY_TYPE_PROFILE: {
                ProfileQueueItem profileQueueItem = ProfileQueueItem.DESERIALIZER.deserialize(item.jsonData);
                Intent intent = new Intent(context, ProfileApplierService.class);
                intent.putExtra(ProfileApplierService.EXTRA_QUEUE_ITEM, profileQueueItem);
                return intent;
            }
        }
        throw new IllegalStateException("Invalid type: " + item.getType());
    }
}
