// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.types.UserPackagePair;

/**
 * A per-target execution outcome.
 */
public final class ActionResult implements Parcelable {
    @NonNull
    public final String actionId;
    @Nullable
    public final UserPackagePair target;
    @NonNull
    public final ActionResultStatus status;
    @NonNull
    public final ActionConflict conflict;
    @Nullable
    public final ActionState observedState;
    @Nullable
    public final ActionState writtenState;
    @Nullable
    public final String message;

    public ActionResult(@NonNull String actionId, @Nullable UserPackagePair target,
                        @NonNull ActionResultStatus status, @NonNull ActionConflict conflict,
                        @Nullable ActionState observedState, @Nullable ActionState writtenState,
                        @Nullable String message) {
        this.actionId = actionId;
        this.target = target;
        this.status = status;
        this.conflict = conflict;
        this.observedState = observedState;
        this.writtenState = writtenState;
        this.message = message;
    }

    @NonNull
    public ActionResult withStateSnapshots(@Nullable ActionState observedState,
                                           @Nullable ActionState writtenState) {
        return new ActionResult(actionId, target, status, conflict, observedState,
                writtenState, message);
    }

    private ActionResult(@NonNull Parcel in) {
        actionId = in.readString();
        target = in.readParcelable(UserPackagePair.class.getClassLoader());
        status = ActionResultStatus.valueOf(in.readString());
        conflict = ActionConflict.valueOf(in.readString());
        observedState = in.readParcelable(ActionState.class.getClassLoader());
        writtenState = in.readParcelable(ActionState.class.getClassLoader());
        message = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(actionId);
        dest.writeParcelable(target, flags);
        dest.writeString(status.name());
        dest.writeString(conflict.name());
        dest.writeParcelable(observedState, flags);
        dest.writeParcelable(writtenState, flags);
        dest.writeString(message);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ActionResult> CREATOR = new Creator<ActionResult>() {
        @NonNull
        @Override
        public ActionResult createFromParcel(Parcel in) {
            return new ActionResult(in);
        }

        @NonNull
        @Override
        public ActionResult[] newArray(int size) {
            return new ActionResult[size];
        }
    };
}
