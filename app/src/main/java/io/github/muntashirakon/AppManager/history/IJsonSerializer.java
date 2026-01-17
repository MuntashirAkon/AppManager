// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public interface IJsonSerializer {
    @NonNull
    JSONObject serializeToJson() throws JSONException;
}
