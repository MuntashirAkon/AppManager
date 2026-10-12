// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.util.LocalizedString;

/**
 * Common persistent contract for a self-contained Action.
 * <p>
 * Concrete Actions own payload decoding, target handling, state comparison, mutation,
 * and revert behavior. This class only owns common configuration and transport.
 */
public abstract class Action implements IJsonSerializer, Parcelable, LocalizedString {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final String mId;
    @Nullable
    private final String mName;
    private final int mSchemaVersion;
    private final ActionKind mKind;
    private final ActionTargetSpec mTarget;
    private final ActionParameters mOnParameters;
    @Nullable
    private final ActionParameters mOffParameters;
    private final Strictness mStrictness;
    private final RevertMode mRevertMode;

    protected Action(@NonNull ActionKind kind, @NonNull ActionTargetSpec target,
                     @NonNull ActionParameters onParameters, @Nullable ActionParameters offParameters) {
        this(UUID.randomUUID().toString(), null, CURRENT_SCHEMA_VERSION, kind, target, onParameters,
                offParameters, Strictness.STRICT,
                offParameters == null ? RevertMode.NONE : RevertMode.EXPLICIT);
    }

    protected Action(@NonNull String id, @Nullable String name, int schemaVersion,
                     @NonNull ActionKind kind, @NonNull ActionTargetSpec target,
                     @NonNull ActionParameters onParameters, @Nullable ActionParameters offParameters,
                     @NonNull Strictness strictness, @NonNull RevertMode revertMode) {
        if (id.trim().isEmpty()) throw new IllegalArgumentException("Action id cannot be empty");
        if (target.getScope() != kind.getScope()) {
            throw new IllegalArgumentException("Action scope does not match kind");
        }
        if (revertMode == RevertMode.EXPLICIT && offParameters == null) {
            throw new IllegalArgumentException("Explicit revert requires off parameters");
        }
        if (revertMode != RevertMode.EXPLICIT && offParameters != null) {
            throw new IllegalArgumentException("Only explicit revert may have off parameters");
        }
        mId = id;
        mName = name;
        mSchemaVersion = schemaVersion;
        mKind = kind;
        mTarget = target;
        mOnParameters = onParameters;
        mOffParameters = offParameters;
        mStrictness = strictness;
        mRevertMode = revertMode;
    }

    protected Action(@NonNull Parcel in, @NonNull ActionKind kind) {
        mId = in.readString();
        mName = in.readString();
        mSchemaVersion = in.readInt();
        mKind = kind;
        mTarget = in.readParcelable(ActionTargetSpec.class.getClassLoader());
        mOnParameters = in.readParcelable(ActionParameters.class.getClassLoader());
        mOffParameters = in.readParcelable(ActionParameters.class.getClassLoader());
        mStrictness = Strictness.valueOf(in.readString());
        mRevertMode = RevertMode.valueOf(in.readString());
    }

    @NonNull
    public final String getId() {
        return mId;
    }

    @Nullable
    public final String getName() {
        return mName;
    }

    public final int getSchemaVersion() {
        return mSchemaVersion;
    }

    @NonNull
    public final ActionKind getKind() {
        return mKind;
    }

    @NonNull
    public final ActionTargetSpec getTarget() {
        return mTarget;
    }

    @NonNull
    public final ActionParameters getOnParameters() {
        return mOnParameters;
    }

    @Nullable
    public final ActionParameters getOffParameters() {
        return mOffParameters;
    }

    @NonNull
    public final Strictness getStrictness() {
        return mStrictness;
    }

    @NonNull
    public final RevertMode getRevertMode() {
        return mRevertMode;
    }

    @NonNull
    public abstract List<ActionResult> execute(@NonNull ActionContext context,
                                               @NonNull ActionMode mode,
                                               @NonNull List<ActionExecutionRecord> previousRecords);

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        return mName == null || mName.isEmpty() ? mKind.name() : mName;
    }

    @NonNull
    @Override
    public String toString() {
        return (mName == null ? mKind.name() : mName) + "[" + mId + "]";
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", mId);
        if (mName != null) object.put("name", mName);
        object.put("schema", mSchemaVersion);
        object.put("kind", mKind.name());
        object.put("target", mTarget.serializeToJson());
        object.put("on", mOnParameters.serializeToJson());
        if (mOffParameters != null) object.put("off", mOffParameters.serializeToJson());
        object.put("strict", mStrictness == Strictness.STRICT);
        object.put("revert", mRevertMode.name());
        return object;
    }

    @NonNull
    public static Action fromJson(@NonNull JSONObject object) throws JSONException {
        String id = object.getString("id");
        String name = object.has("name") ? object.getString("name") : null;
        int schema = object.optInt("schema", CURRENT_SCHEMA_VERSION);
        ActionKind kind = ActionKind.valueOf(object.getString("kind"));
        ActionTargetSpec target = ActionTargetSpec.DESERIALIZER.deserialize(object.getJSONObject("target"));
        ActionParameters on = ActionParameters.DESERIALIZER.deserialize(object.getJSONObject("on"));
        ActionParameters off = object.has("off")
                ? ActionParameters.DESERIALIZER.deserialize(object.getJSONObject("off")) : null;
        Strictness strictness = object.optBoolean("strict", true)
                ? Strictness.STRICT : Strictness.NON_STRICT;
        RevertMode revert = RevertMode.valueOf(object.optString("revert",
                off == null ? RevertMode.NONE.name() : RevertMode.EXPLICIT.name()));
        return create(kind, id, name, schema, target, on, off, strictness, revert);
    }

    @NonNull
    private static Action create(@NonNull ActionKind kind, @NonNull String id, @Nullable String name,
                                 int schema, @NonNull ActionTargetSpec target,
                                 @NonNull ActionParameters on, @Nullable ActionParameters off,
                                 @NonNull Strictness strictness, @NonNull RevertMode revert) {
        if (kind == ActionKind.FREEZE) {
            return new FreezeAction(id, name, schema, target, on, off, strictness, revert);
        }
        throw new IllegalArgumentException("No concrete Action implementation registered for " + kind);
    }

    public static final JsonDeserializer.Creator<Action> DESERIALIZER = object -> {
        try {
            return fromJson(object);
        } catch (IllegalArgumentException | JSONException e) {
            throw new JSONException(e.getMessage());
        }
    };

    @Override
    public final void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mKind.name());
        dest.writeString(mId);
        dest.writeString(mName);
        dest.writeInt(mSchemaVersion);
        dest.writeParcelable(mTarget, flags);
        dest.writeParcelable(mOnParameters, flags);
        dest.writeParcelable(mOffParameters, flags);
        dest.writeString(mStrictness.name());
        dest.writeString(mRevertMode.name());
    }

    @Override
    public final int describeContents() {
        return 0;
    }

    public static final Creator<Action> CREATOR = new Creator<Action>() {
        @NonNull
        @Override
        public Action createFromParcel(Parcel in) {
            ActionKind kind = ActionKind.valueOf(in.readString());
            if (kind == ActionKind.FREEZE) return FreezeAction.fromParcel(in, kind);
            throw new IllegalArgumentException("No concrete Action implementation registered for " + kind);
        }

        @NonNull
        @Override
        public Action[] newArray(int size) {
            return new Action[size];
        }
    };
}
