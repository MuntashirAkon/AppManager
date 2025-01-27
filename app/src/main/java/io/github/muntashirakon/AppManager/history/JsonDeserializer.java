// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public class JsonDeserializer {
    public interface Creator<T> {
        @NonNull
        T deserialize(@NonNull JSONObject jsonObject) throws JSONException;
    }
}
