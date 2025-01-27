// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops.struct;

import android.os.Parcelable;

import org.json.JSONException;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.utils.JSONUtils;

public interface IBatchOpOptions extends Parcelable, IJsonSerializer {
    JsonDeserializer.Creator<IBatchOpOptions> DESERIALIZER = jsonObject -> {
        String tag = JSONUtils.getString(jsonObject, "tag");
        if (BatchAppOpsOptions.TAG.equals(tag)) {
            return BatchAppOpsOptions.DESERIALIZER.deserialize(jsonObject);
        } else if (BatchBackupImportOptions.TAG.equals(tag)) {
            return BatchBackupImportOptions.DESERIALIZER.deserialize(jsonObject);
        } else if (BatchBackupOptions.TAG.equals(tag)) {
            return BatchBackupOptions.DESERIALIZER.deserialize(jsonObject);
        } else if (BatchComponentOptions.TAG.equals(tag)) {
            return BatchComponentOptions.DESERIALIZER.deserialize(jsonObject);
        } else if (BatchDexOptOptions.TAG.equals(tag)) {
            return BatchComponentOptions.DESERIALIZER.deserialize(jsonObject);
        } else if (BatchNetPolicyOptions.TAG.equals(tag)) {
            return BatchNetPolicyOptions.DESERIALIZER.deserialize(jsonObject);
        } else if (BatchPermissionOptions.TAG.equals(tag)) {
            return BatchNetPolicyOptions.DESERIALIZER.deserialize(jsonObject);
        } else throw new JSONException("Invalid tag: " + tag);
    };
}
