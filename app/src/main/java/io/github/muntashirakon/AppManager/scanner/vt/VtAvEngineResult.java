// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.scanner.vt;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.utils.JSONUtils;

public class VtAvEngineResult {
    public static final int CAT_UNSUPPORTED = 0;
    public static final int CAT_TIMEOUT = 1;
    public static final int CAT_FAILURE = 2;
    public static final int CAT_UNDETECTED = 3;
    public static final int CAT_HARMLESS = 4;
    public static final int CAT_SUSPICIOUS = 5;
    public static final int CAT_MALICIOUS = 6;

    @IntDef({CAT_UNSUPPORTED, CAT_TIMEOUT, CAT_FAILURE, CAT_UNDETECTED, CAT_HARMLESS,
            CAT_SUSPICIOUS, CAT_MALICIOUS})
    public @interface Category {
    }

    @NonNull
    private final String internalCategory;
    @Category
    public final int category;
    @NonNull
    public final String engineName;
    @Nullable
    public final String engineUpdate;
    @Nullable
    public final String engineVersion;
    @NonNull
    public final String method;
    @Nullable
    public final String result;

    public VtAvEngineResult(@NonNull JSONObject avResult) throws JSONException {
        internalCategory = avResult.getString("category");
        category = getCategory(internalCategory);
        engineName = avResult.getString("engine_name");
        engineUpdate = JSONUtils.optString(avResult, "engine_update", null);
        engineVersion = JSONUtils.optString(avResult, "engine_version", null);
        method = avResult.getString("method");
        result = JSONUtils.optString(avResult, "result", null);
    }

    @NonNull
    @Override
    public String toString() {
        return "VtFileReportScanItem{" +
                "category='" + internalCategory + '\'' +
                ", engineName='" + engineName + '\'' +
                ", engineUpdate='" + engineUpdate + '\'' +
                ", engineVersion='" + engineVersion + '\'' +
                ", method='" + method + '\'' +
                ", result='" + result + '\'' +
                '}';
    }

    @Category
    private static int getCategory(@NonNull String internalCategory) {
        switch (internalCategory) {
            case "confirmed-timeout":
            case "timeout":
                return CAT_TIMEOUT;
            case "harmless":
                return CAT_HARMLESS;
            case "undetected":
                return CAT_UNDETECTED;
            case "suspicious":
                return CAT_SUSPICIOUS;
            case "malicious":
                return CAT_MALICIOUS;
            case "type-unsupported":
                return CAT_UNSUPPORTED;
            case "failure":
            default:
                return CAT_FAILURE;
        }
    }
}