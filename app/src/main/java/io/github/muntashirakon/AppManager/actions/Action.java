// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.content.Context;
import android.app.Service;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Collections;
import java.util.UUID;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
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
    public final ActionParameters requireOffParameters() {
        if (mOffParameters == null) {
            throw new IllegalStateException(mKind + " has no reverse parameters");
        }
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

    /**
     * Number of progress units this action is expected to consume.
     */
    public int getProgressMax(@NonNull ActionContext context) {
        return 1;
    }

    protected final void reportProgress(@NonNull ActionContext context,
                                        @NonNull ActionProgress progress) {
        context.dispatch(progress);
    }

    protected final void reportProgress(@NonNull ActionContext context,
                                        @Nullable CharSequence label, int max, float current,
                                        @NonNull ActionProgress.State state,
                                        @NonNull List<ActionResult> results) {
        reportProgress(context, new ActionProgress(label, max, current, state,
                context.executionMode, results));
    }

    protected final void reportProgress(@NonNull ActionContext context,
                                        @Nullable CharSequence label, int max, float current) {
        reportProgress(context, label, max, current, ActionProgress.State.UPDATED,
                java.util.Collections.emptyList());
    }

    protected final void reportIndeterminateProgress(@NonNull ActionContext context,
                                                     @Nullable CharSequence label) {
        reportProgress(context, label, ActionProgress.INDETERMINATE, 0);
    }

    @NonNull
    protected final ActionResult actionResult(@Nullable UserPackagePair target,
                                              @NonNull ActionResultStatus status,
                                              @Nullable ActionState observedState,
                                              @Nullable ActionState writtenState,
                                              @Nullable String message) {
        return actionResult(target, status, ActionConflict.NO_CONFLICT, observedState,
                writtenState, message);
    }

    @NonNull
    protected final ActionResult actionResult(@Nullable UserPackagePair target,
                                              @NonNull ActionResultStatus status,
                                              @NonNull ActionConflict conflict,
                                              @Nullable ActionState observedState,
                                              @Nullable ActionState writtenState,
                                              @Nullable String message) {
        return new ActionResult(getId(), target, status, conflict,
                observedState, writtenState, message);
    }

    /**
     * Creates an independently measured child progress on the context's progress stream. This is
     * useful for actions (e.g., backup/restore) that have individual progress.
     */
    @NonNull
    protected final SubProgressHandler newSubProgressHandler(@NonNull ActionContext context,
                                                             @NonNull UserPackagePair target,
                                                             @NonNull CharSequence operationName,
                                                             @NonNull CharSequence title) {
        return new SubProgressHandler(context, new ActionProgress.Child(getId(), target, operationName, title));
    }

    protected static final class SubProgressHandler extends ProgressHandler implements AutoCloseable {
        private final ActionContext mContext;
        private final ActionProgress.Child mChild;
        private int mMax = ActionProgress.INDETERMINATE;
        private float mCurrent;
        private boolean mCompleted;

        private SubProgressHandler(ActionContext context, ActionProgress.Child child) {
            mContext = context;
            mChild = child;
            publish(ActionProgress.State.STARTED);
        }

        private void publish(ActionProgress.State state) {
            mContext.dispatch(new ActionProgress(mChild.title, mMax, mCurrent, state,
                    mContext.executionMode, Collections.emptyList(), mChild));
        }

        @Override
        public synchronized void postUpdate(int max, float current, @Nullable Object message) {
            if (mCompleted) {
                return;
            }
            mMax = max;
            mCurrent = max == ActionProgress.INDETERMINATE ? 0 : current;
            publish(ActionProgress.State.UPDATED);
        }

        @Override
        public void onProgressStart(int max, float current, @Nullable Object message) {
            postUpdate(max, current, message);
        }

        @Override
        public void onProgressUpdate(int max, float current, @Nullable Object message) {
            postUpdate(max, current, message);
        }

        @Override
        public void onResult(@Nullable Object message) {
            close();
        }

        @Override
        public synchronized void close() {
            if (mCompleted) return;
            mCompleted = true;
            publish(ActionProgress.State.COMPLETED);
        }

        @Override
        public synchronized int getLastMax() {
            return mMax;
        }

        @Override
        public synchronized float getLastProgress() {
            return mCurrent;
        }

        @Nullable
        @Override
        public Object getLastMessage() {
            return null;
        }

        @Override
        public void onAttach(@Nullable Service service, @NonNull Object message) {
        }

        @Override
        public void onDetach(@Nullable Service service) {
        }

        @NonNull
        @Override
        public ProgressHandler newSubProgressHandler(@Nullable CharSequence operationName) {
            throw new UnsupportedOperationException("Backup progress has no further nested operations");
        }
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        return TextUtils.isEmpty(mName) ? mKind.toLocalizedString(context) : mName;
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
    protected final ActionParameters requireParameters(@NonNull ActionMode mode) {
        return mode == ActionMode.ON ? getOnParameters() : requireOffParameters();
    }

    protected final boolean requireBoolean(@NonNull ActionParameters parameters,
                                           @NonNull String key) {
        Object value = parameters.get(key);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException("Expected boolean parameter: " + key);
        }
        return (Boolean) value;
    }

    protected final int requireInt(@NonNull ActionParameters parameters, @NonNull String key) {
        Object value = parameters.get(key);
        if (!(value instanceof Integer)) {
            throw new IllegalArgumentException("Expected int parameter: " + key);
        }
        return (Integer) value;
    }

    @NonNull
    protected final int[] requireIntArray(@NonNull ActionParameters parameters, @NonNull String key) {
        Object value = parameters.get(key);
        if (!(value instanceof int[])) {
            throw new IllegalArgumentException("Expected int[] parameter: " + key);
        }
        return (int[]) value;
    }

    @NonNull
    protected final String requireString(@NonNull ActionParameters parameters, @NonNull String key) {
        Object value = parameters.get(key);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Expected String parameter: " + key);
        }
        return (String) value;
    }

    @NonNull
    protected final String[] requireStringArray(@NonNull ActionParameters parameters, @NonNull String key) {
        Object value = parameters.get(key);
        if (!(value instanceof String[])) {
            throw new IllegalArgumentException("Expected String[] parameter: " + key);
        }
        return (String[]) value;
    }

    protected final boolean optionalBoolean(@NonNull ActionParameters parameters, @NonNull String key, boolean defaultValue) {
        Object value = parameters.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException("Expected boolean parameter: " + key);
        }
        return (boolean) value;
    }

    @Nullable
    protected final String optionalString(@NonNull ActionParameters parameters, @NonNull String key) {
        Object value = parameters.get(key);
        if (value != null && !(value instanceof String)) {
            throw new IllegalArgumentException("Expected String parameter: " + key);
        }
        return (String) value;
    }

    @Nullable
    protected final String[] optionalStringArray(@NonNull ActionParameters parameters, @NonNull String key) {
        Object value = parameters.get(key);
        if (value != null && !(value instanceof String[])) {
            throw new IllegalArgumentException("Expected String[] parameter: " + key);
        }
        return (String[]) value;
    }

    @NonNull
    private static Action create(@NonNull ActionKind kind, @NonNull String id, @Nullable String name,
                                 int schema, @NonNull ActionTargetSpec target,
                                 @NonNull ActionParameters on, @Nullable ActionParameters off,
                                 @NonNull Strictness strictness, @NonNull RevertMode revert) {
        if (kind == ActionKind.FREEZE) {
            return new FreezeAction(id, name, schema, target, on, off, strictness, revert);
        }
        if (kind == ActionKind.FORCE_STOP) {
            return new ForceStopAction(id, name, schema, target, on, strictness, revert);
        }
        if (kind == ActionKind.COMPONENT_STATE) {
            return new ComponentStateAction(id, name, schema, target, on, off, strictness, revert);
        }
        if (kind == ActionKind.PERMISSION_STATE) {
            return new PermissionStateAction(id, name, schema, target, on, off, strictness, revert);
        }
        if (kind == ActionKind.APP_OP_MODE) {
            return new AppOpModeAction(id, name, schema, target, on, off, strictness, revert);
        }
        if (kind == ActionKind.TRACKER_BLOCKING) {
            return new TrackerStateAction(id, name, schema, target, on, off, strictness, revert);
        }
        if (kind == ActionKind.NETWORK_POLICY) {
            return new NetworkPolicyAction(id, name, schema, target, on, off, strictness, revert);
        }
        if (kind == ActionKind.SENSOR_ACCESS) {
            return new SensorAccessAction(id, name, schema, target, on, off, strictness, revert);
        }
        if (kind == ActionKind.BATTERY_OPTIMIZATION) {
            return new BatteryOptimizationAction(id, name, schema, target, on, off, strictness, revert);
        }
        if (kind == ActionKind.CLEAR_CACHE) {
            return new ClearCacheAction(id, name, schema, target, on, strictness, revert);
        }
        if (kind == ActionKind.CLEAR_DATA) {
            return new ClearDataAction(id, name, schema, target, on, strictness, revert);
        }
        if (kind == ActionKind.DEXOPT) {
            return new DexOptAction(id, name, schema, target, on, strictness, revert);
        }
        if (kind == ActionKind.BACKUP) {
            return new BackupAction(id, name, schema, target, on, strictness, revert);
        }
        if (kind == ActionKind.BACKUP_APK) {
            return new BackupApkAction(id, name, schema, target, on, strictness, revert);
        }
        if (kind == ActionKind.RESTORE_BACKUP) {
            return new RestoreBackupAction(id, name, schema, target, on, strictness, revert);
        }
        if (kind == ActionKind.DELETE_BACKUP) {
            return new DeleteBackupAction(id, name, schema, target, on, strictness, revert);
        }
        if (kind == ActionKind.UNINSTALL) {
            return new UninstallAction(id, name, schema, target, on, strictness, revert);
        }
        if (kind == ActionKind.INSTALL_EXISTING) {
            return new InstallExistingAction(id, name, schema, target, on, strictness, revert);
        }
        if (kind == ActionKind.MAGISK_HIDE) {
            return new MagiskHideAction(id, name, schema, target, on, off, strictness, revert);
        }
        if (kind == ActionKind.MAGISK_DENYLIST) {
            return new MagiskDenylistAction(id, name, schema, target, on, off, strictness, revert);
        }
        if (kind == ActionKind.SETTING) {
            return new SettingAction(id, name, schema, target, on, off, strictness, revert);
        }
        if (kind == ActionKind.REBOOT) {
            return new RebootAction(id, name, schema, target, on, strictness, revert);
        }
        if (kind == ActionKind.TRIM_CACHES) {
            return new TrimCachesAction(id, name, schema, target, on, strictness, revert);
        }
        if (kind == ActionKind.DEVICE_STATE) {
            return new DeviceStateAction(id, name, schema, target, on, off, strictness, revert);
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
        public Action createFromParcel(@NonNull Parcel in) {
            ActionKind kind = ActionKind.valueOf(in.readString());
            if (kind == ActionKind.FREEZE) {
                return FreezeAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.FORCE_STOP) {
                return ForceStopAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.COMPONENT_STATE) {
                return ComponentStateAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.PERMISSION_STATE) {
                return PermissionStateAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.APP_OP_MODE) {
                return AppOpModeAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.TRACKER_BLOCKING) {
                return TrackerStateAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.NETWORK_POLICY) {
                return NetworkPolicyAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.SENSOR_ACCESS) {
                return SensorAccessAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.BATTERY_OPTIMIZATION) {
                return BatteryOptimizationAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.CLEAR_CACHE) {
                return ClearCacheAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.CLEAR_DATA) {
                return ClearDataAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.DEXOPT) {
                return DexOptAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.BACKUP) {
                return BackupAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.BACKUP_APK) {
                return BackupApkAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.RESTORE_BACKUP) {
                return RestoreBackupAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.DELETE_BACKUP) {
                return DeleteBackupAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.UNINSTALL) {
                return UninstallAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.INSTALL_EXISTING) {
                return InstallExistingAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.MAGISK_HIDE) {
                return MagiskHideAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.MAGISK_DENYLIST) {
                return MagiskDenylistAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.SETTING) {
                return SettingAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.REBOOT) {
                return RebootAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.TRIM_CACHES) {
                return TrimCachesAction.fromParcel(in, kind);
            }
            if (kind == ActionKind.DEVICE_STATE) {
                return DeviceStateAction.fromParcel(in, kind);
            }
            throw new IllegalArgumentException("No concrete Action implementation registered for " + kind);
        }

        @NonNull
        @Override
        public Action[] newArray(int size) {
            return new Action[size];
        }
    };
}
