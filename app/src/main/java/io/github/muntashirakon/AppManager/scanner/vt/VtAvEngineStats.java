// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner.vt;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public class VtAvEngineStats {
    public final int confirmedTimeout;
    public final int failure;
    public final int harmless;
    public final int malicious;
    public final int suspicious;
    public final int timeout;
    public final int unsupported;
    public final int undetected;

    private final int mTotal;
    private final int mDetected;

    public VtAvEngineStats(@NonNull JSONObject stats) throws JSONException {
        confirmedTimeout = stats.getInt("confirmed-timeout");
        failure = stats.getInt("failure");
        harmless = stats.getInt("harmless");
        malicious = stats.getInt("malicious");
        suspicious = stats.getInt("suspicious");
        timeout = stats.getInt("timeout");
        unsupported = stats.getInt("type-unsupported");
        undetected = stats.getInt("undetected");

        mTotal = harmless + malicious + suspicious + undetected;
        mDetected = malicious;
    }

    public int getTotal() {
        return mTotal;
    }

    public int getDetected() {
        return mDetected;
    }
}
