// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner.vt;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public class VtError {
    public final int httpErrorCode;
    public final String code;
    public final String message;

    public VtError(int httpErrorCode, @Nullable String rawJson) {
        this.httpErrorCode = httpErrorCode;
        if (TextUtils.isEmpty(rawJson)) {
            code = null;
            message = null;
        } else {
            String code = null;
            String message = null;
            try {
                JSONObject errorObject = new JSONObject(rawJson).optJSONObject("error");
                if (errorObject != null) {
                    code = errorObject.getString("code");
                    message = errorObject.getString("message");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            this.code = code;
            this.message = message;
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
