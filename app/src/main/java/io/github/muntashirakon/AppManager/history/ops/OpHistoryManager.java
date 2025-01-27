// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;

import org.json.JSONException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.logs.Log;

public final class OpHistoryManager {
    public static final String TAG = OpHistoryManager.class.getSimpleName();

    public static final String HISTORY_TYPE_BATCH_OPS = "batch_ops";
    public static final String HISTORY_TYPE_INSTALLER = "installer";
    public static final String HISTORY_TYPE_PROFILE = "profile";
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({HISTORY_TYPE_BATCH_OPS, HISTORY_TYPE_INSTALLER, HISTORY_TYPE_PROFILE})
    public @interface HistoryType{}

    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILURE = "success";

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({STATUS_SUCCESS, STATUS_FAILURE})
    public @interface Status{}

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
            return AppsDb.getInstance().opHistoryDao().insert(opHistory);
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
}
