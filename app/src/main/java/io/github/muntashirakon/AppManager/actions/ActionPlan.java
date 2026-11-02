// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.logs.Logger;
import io.github.muntashirakon.util.LocalizedString;

/**
 * Ordered collection of actions.
 */
public final class ActionPlan implements IJsonSerializer, Parcelable, LocalizedString {
    private final String mId;
    private final String mName;
    private final List<Action> mActions;
    private final List<ActionExecutionRecord> mExecutionRecords;

    public ActionPlan(@NonNull String name, @NonNull List<Action> actions) {
        this(UUID.randomUUID().toString(), name, actions, Collections.emptyList());
    }

    public ActionPlan(@NonNull String id, @NonNull String name, @NonNull List<Action> actions) {
        this(id, name, actions, Collections.emptyList());
    }

    private ActionPlan(@NonNull String id, @NonNull String name, @NonNull List<Action> actions,
                       @NonNull List<ActionExecutionRecord> executionRecords) {
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Empty plan id");
        }
        mId = id;
        mName = name;
        mActions = Collections.unmodifiableList(new ArrayList<>(actions));
        mExecutionRecords = new ArrayList<>(executionRecords);
    }

    private ActionPlan(@NonNull JSONObject object) throws JSONException {
        mId = object.getString("id");
        mName = object.getString("name");
        JSONArray array = object.getJSONArray("actions");
        ArrayList<Action> definitions = new ArrayList<>();
        for (int i = 0; i < array.length(); ++i) {
            definitions.add(Action.DESERIALIZER.deserialize(array.getJSONObject(i)));
        }
        mActions = Collections.unmodifiableList(definitions);
        ArrayList<ActionExecutionRecord> records = new ArrayList<>();
        JSONArray recordArray = object.optJSONArray("records");
        if (recordArray != null) {
            for (int i = 0; i < recordArray.length(); ++i) {
                records.add(ActionExecutionRecord.DESERIALIZER.deserialize(recordArray.getJSONObject(i)));
            }
        }
        mExecutionRecords = records;
    }

    public static final JsonDeserializer.Creator<ActionPlan> DESERIALIZER = ActionPlan::new;

    @NonNull
    public String getId() {
        return mId;
    }

    @NonNull
    public String getName() {
        return mName;
    }

    @NonNull
    public List<Action> getActions() {
        return mActions;
    }

    @NonNull
    public List<ActionExecutionRecord> getExecutionRecords() {
        return Collections.unmodifiableList(mExecutionRecords);
    }

    @NonNull
    public synchronized List<ActionResult> execute(@NonNull ActionContext context,
                                                   @NonNull ActionMode mode,
                                                   @NonNull Logger logger) {
        List<ActionResult> results = new ArrayList<>();
        List<ActionWork> actionWork = new ArrayList<>(mActions.size());
        int offset = 0;
        boolean singleIndeterminate = context.executionMode == ActionExecutionMode.SINGLETON
                && mActions.size() == 1;
        for (Action action : mActions) {
            int maxProgress = action.getProgressMax(context);
            if (maxProgress < ActionProgress.INDETERMINATE) {
                throw new IllegalStateException("Invalid Action progress maximum: " + maxProgress);
            }
            int workload = maxProgress == ActionProgress.INDETERMINATE ? 1 : maxProgress;
            actionWork.add(new ActionWork(action, offset, workload));
            offset += workload;
            if (maxProgress != ActionProgress.INDETERMINATE) {
                singleIndeterminate = false;
            }
        }
        int progressMax = singleIndeterminate ? ActionProgress.INDETERMINATE : offset;
        if (context.progressHandler != null) {
            context.addProgressHandler(context.progressHandler);
        }
        // Dispatch initial progress, 0
        context.dispatch(new ActionProgress(null, progressMax, 0,
                ActionProgress.State.STARTED, context.executionMode, results));
        int completedWork = 0;
        try {
            logger.println("====> Started action plan: " + mName + " with mode: " + mode);
            for (ActionWork work : actionWork) {
                Action action = work.action;
                logger.println("====> Started action: " + action.getKind() + " (" + action.getId() + ")");
                List<ActionExecutionRecord> previous = new ArrayList<>();
                for (ActionExecutionRecord record : mExecutionRecords) {
                    if (record.actionId.equals(action.getId())) {
                        previous.add(record);
                    }
                }
                List<ActionResult> actionResults;
                context.beginActionProgress(progressMax, work.offset, work.workload);
                try {
                    actionResults = action.execute(context, mode, previous);
                } finally {
                    context.endActionProgress();
                }
                results.addAll(actionResults);
                completedWork += work.workload;
                for (ActionResult result : actionResults) {
                    if (result.status != ActionResultStatus.SUCCESS) {
                        logger.println("[!] action=" + action.getKind() + ", target=" + result.target
                                + ", status=" + result.status
                                + (result.message == null ? "" : ", message=" + result.message));
                    }
                    ActionState original = result.observedState;
                    ActionState written = result.writtenState;
                    ActionState state = original == null ? written : original;
                    if (state != null) {
                        mExecutionRecords.add(new ActionExecutionRecord(context.executionId,
                                action.getId(), mId, state.getStateKey(), result.target, original,
                                written, System.currentTimeMillis(), result.status));
                    }
                }
            }
        } finally {
            // Dispatch final progress, completedWork
            context.dispatch(new ActionProgress(null, progressMax,
                    progressMax == ActionProgress.INDETERMINATE ? 0 : completedWork,
                    ActionProgress.State.COMPLETED, context.executionMode, results));
        }
        return Collections.unmodifiableList(results);
    }

    private static final class ActionWork {
        @NonNull
        final Action action;
        final int offset;
        final int workload;

        ActionWork(@NonNull Action action, int offset, int workload) {
            this.action = action;
            this.offset = offset;
            this.workload = workload;
        }
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        return mName;
    }

    @NonNull
    @Override
    public String toString() {
        return mName + "[" + mId + "]";
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", mId);
        object.put("name", mName);
        JSONArray array = new JSONArray();
        for (Action action : mActions) {
            array.put(action.serializeToJson());
        }
        object.put("actions", array);
        JSONArray records = new JSONArray();
        for (ActionExecutionRecord record : mExecutionRecords) {
            records.put(record.serializeToJson());
        }
        object.put("records", records);
        return object;
    }

    private ActionPlan(@NonNull Parcel in) {
        mId = in.readString();
        mName = in.readString();
        mActions = Collections.unmodifiableList(in.createTypedArrayList(Action.CREATOR));
        mExecutionRecords = new ArrayList<>(in.createTypedArrayList(ActionExecutionRecord.CREATOR));
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mId);
        dest.writeString(mName);
        dest.writeTypedList(mActions);
        dest.writeTypedList(mExecutionRecords);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ActionPlan> CREATOR = new Creator<ActionPlan>() {
        @NonNull
        @Override
        public ActionPlan createFromParcel(Parcel in) {
            return new ActionPlan(in);
        }

        @NonNull
        @Override
        public ActionPlan[] newArray(int size) {
            return new ActionPlan[size];
        }
    };
}
