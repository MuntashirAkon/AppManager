// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner.vt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public class VtError {
    public final int httpErrorCode;
    public final String code;
    public final String message;

    public VtError(int httpErrorCode, @Nullable String rawJson) {
        this(httpErrorCode, parseCode(rawJson), parseMessage(rawJson));
    }

    VtError(int httpErrorCode, @Nullable String code, @Nullable String message) {
        this.httpErrorCode = httpErrorCode;
        this.code = code;
        this.message = message;
    }

    @Nullable
    private static String parseCode(@Nullable String rawJson) {
        JSONObject errorObject = parseError(rawJson);
        return errorObject == null ? null : errorObject.optString("code", null);
    }

    @Nullable
    private static String parseMessage(@Nullable String rawJson) {
        JSONObject errorObject = parseError(rawJson);
        return errorObject == null ? null : errorObject.optString("message", null);
    }

    @Nullable
    private static JSONObject parseError(@Nullable String rawJson) {
        if (rawJson == null || rawJson.isEmpty()) return null;
        try {
            return new JSONObject(rawJson).optJSONObject("error");
        } catch (JSONException e) {
            return null;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "VtError{" +
                "httpErrorCode=" + httpErrorCode +
                ", code='" + code + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
