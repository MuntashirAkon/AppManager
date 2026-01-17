// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.utils.JSONUtils;

public class OpHistoryItem {
    private final OpHistory opHistory;
    public final JSONObject jsonData;

    public OpHistoryItem(@NonNull OpHistory opHistory) throws JSONException {
        this.opHistory = opHistory;
        jsonData = new JSONObject(opHistory.serializedData);
    }

    @OpHistoryManager.HistoryType
    public String getType() {
        return opHistory.type;
    }

    @NonNull
    public String getLocalizedType(@NonNull Context context) {
        switch (opHistory.type) {
            case OpHistoryManager.HISTORY_TYPE_BATCH_OPS:
                try {
                    return context.getString(jsonData.getInt("title_res"));
                } catch (Resources.NotFoundException | JSONException e) {
                    return context.getString(R.string.batch_ops);
                }
            case OpHistoryManager.HISTORY_TYPE_INSTALLER:
                return context.getString(R.string.installer);
            case OpHistoryManager.HISTORY_TYPE_PROFILE:
                return context.getString(R.string.profiles);
        }
        throw new IllegalStateException("Invalid type: " + opHistory.type);
    }

    @NonNull
    public String getLabel(@NonNull Context context) {
        switch (opHistory.type) {
            case OpHistoryManager.HISTORY_TYPE_BATCH_OPS:
                try {
                    int op = jsonData.getInt("op");
                    return BatchOpsService.getDesiredOpTitle(context, op);
                } catch (JSONException e) {
                    return context.getString(R.string.unknown_op);
                }
            case OpHistoryManager.HISTORY_TYPE_INSTALLER: {
                String label = JSONUtils.optString(jsonData, "app_label");
                if (label != null) {
                    return label;
                }
                return context.getString(R.string.state_unknown);
            }
            case OpHistoryManager.HISTORY_TYPE_PROFILE: {
                String label = JSONUtils.optString(jsonData, "profile_name");
                if (label != null) {
                    return label;
                }
                return context.getString(R.string.state_unknown);
            }
        }
        throw new IllegalStateException("Invalid type: " + opHistory.type);
    }

    public long getTimestamp() {
        return opHistory.execTime;
    }

    public boolean getStatus() {
        return opHistory.status.equals(OpHistoryManager.STATUS_SUCCESS);
    }
}
