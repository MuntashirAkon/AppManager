// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner.vt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A VirusTotal analysis resource returned after an upload or explicit re-analysis.
 */
public class VtAnalysis {
    @NonNull
    public final String id;
    @NonNull
    public final String type;
    @Nullable
    public final String status;
    @NonNull
    public final String rawJson;

    public VtAnalysis(@NonNull JSONObject jsonObject) throws JSONException {
        JSONObject data = jsonObject.getJSONObject("data");
        id = data.getString("id");
        type = data.getString("type");
        JSONObject attributes = data.optJSONObject("attributes");
        status = attributes != null ? attributes.optString("status", null) : null;
        rawJson = jsonObject.toString();
    }

    public boolean isCompleted() {
        return "completed".equals(status);
    }

    public boolean isPending() {
        return !isCompleted();
    }
}
