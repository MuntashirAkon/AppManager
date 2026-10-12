// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

/**
 * Journal entry written after each successful action execution.
 */
public final class ActionExecutionRecord implements IJsonSerializer, Parcelable {
    @NonNull
    public final String executionId;
    @NonNull
    public final String actionId;
    @NonNull
    public final String planId;
    @NonNull
    public final String stateKey;
    @Nullable
    public final UserPackagePair target;
    @Nullable
    public final ActionState originalState;
    @Nullable
    public final ActionState writtenState;
    public final long appliedAt;
    @NonNull
    public final ActionResultStatus status;

    public ActionExecutionRecord(@NonNull String executionId, @NonNull String actionId, @NonNull String planId,
                                 @NonNull String stateKey, @Nullable UserPackagePair target,
                                 @Nullable ActionState originalState, @Nullable ActionState writtenState,
                                 long appliedAt, @NonNull ActionResultStatus status) {
        this.executionId = executionId;
        this.actionId = actionId;
        this.planId = planId;
        this.stateKey = stateKey;
        this.target = target;
        this.originalState = originalState;
        this.writtenState = writtenState;
        this.appliedAt = appliedAt;
        this.status = status;
    }

    private ActionExecutionRecord(@NonNull JSONObject object) throws JSONException {
        executionId = object.getString("execution");
        actionId = object.getString("action");
        planId = object.getString("plan");
        stateKey = object.getString("key");
        target = object.has("package") ? new UserPackagePair(object.getString("package"), object.getInt("user")) : null;
        originalState = object.has("original") ? ActionState.DESERIALIZER.deserialize(object.getJSONObject("original")) : null;
        writtenState = object.has("written") ? ActionState.DESERIALIZER.deserialize(object.getJSONObject("written")) : null;
        appliedAt = object.getLong("applied_at");
        status = ActionResultStatus.valueOf(object.getString("status"));
    }

    public static final JsonDeserializer.Creator<ActionExecutionRecord> DESERIALIZER = ActionExecutionRecord::new;

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("execution", executionId);
        object.put("action", actionId);
        object.put("plan", planId);
        object.put("key", stateKey);
        if (target != null) {
            object.put("package", target.getPackageName());
            object.put("user", target.getUserId());
        }
        if (originalState != null) {
            object.put("original", originalState.serializeToJson());
        }
        if (writtenState != null) {
            object.put("written", writtenState.serializeToJson());
        }
        object.put("applied_at", appliedAt);
        object.put("status", status.name());
        return object;
    }

    private ActionExecutionRecord(@NonNull Parcel in) {
        executionId = in.readString();
        actionId = in.readString();
        planId = in.readString();
        stateKey = in.readString();
        target = in.readParcelable(UserPackagePair.class.getClassLoader());
        originalState = in.readParcelable(ActionState.class.getClassLoader());
        writtenState = in.readParcelable(ActionState.class.getClassLoader());
        appliedAt = in.readLong();
        status = ActionResultStatus.valueOf(in.readString());
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(executionId);
        dest.writeString(actionId);
        dest.writeString(planId);
        dest.writeString(stateKey);
        dest.writeParcelable(target, flags);
        dest.writeParcelable(originalState, flags);
        dest.writeParcelable(writtenState, flags);
        dest.writeLong(appliedAt);
        dest.writeString(status.name());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ActionExecutionRecord> CREATOR = new Creator<ActionExecutionRecord>() {
        @NonNull
        @Override
        public ActionExecutionRecord createFromParcel(Parcel in) {
            return new ActionExecutionRecord(in);
        }

        @NonNull
        @Override
        public ActionExecutionRecord[] newArray(int size) {
            return new ActionExecutionRecord[size];
        }
    };
}
