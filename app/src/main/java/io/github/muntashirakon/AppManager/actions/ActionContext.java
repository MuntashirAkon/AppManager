// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;

/**
 * Runtime-only inputs used when resolving configured and inherited targets.
 */
public final class ActionContext implements Parcelable {
    @NonNull
    public final String executionId;
    @NonNull
    public final String planId;
    @Nullable
    public final UserPackagePair triggerTarget;
    @NonNull
    public final List<UserPackagePair> selectedTargets;
    public final long startedAt;
    public final boolean automatic;
    @Nullable
    public final transient ProgressHandler progressHandler;

    public ActionContext(@Nullable UserPackagePair triggerTarget, @NonNull List<UserPackagePair> selectedTargets,
                         @NonNull String planId, long startedAt, boolean automatic) {
        this(triggerTarget, selectedTargets, planId, startedAt, automatic, null);
    }

    public ActionContext(@Nullable UserPackagePair triggerTarget, @NonNull List<UserPackagePair> selectedTargets,
                         @NonNull String planId, long startedAt, boolean automatic,
                         @Nullable ProgressHandler progressHandler) {
        executionId = UUID.randomUUID().toString();
        this.planId = planId;
        this.triggerTarget = triggerTarget;
        this.selectedTargets = Collections.unmodifiableList(new ArrayList<>(selectedTargets));
        this.startedAt = startedAt;
        this.automatic = automatic;
        this.progressHandler = progressHandler;
    }

    private ActionContext(@NonNull Parcel in) {
        executionId = in.readString();
        planId = in.readString();
        triggerTarget = in.readParcelable(UserPackagePair.class.getClassLoader());
        selectedTargets = Collections.unmodifiableList(in.createTypedArrayList(UserPackagePair.CREATOR));
        startedAt = in.readLong();
        automatic = in.readInt() != 0;
        progressHandler = null;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(executionId);
        dest.writeString(planId);
        dest.writeParcelable(triggerTarget, flags);
        dest.writeTypedList(selectedTargets);
        dest.writeLong(startedAt);
        dest.writeInt(automatic ? 1 : 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ActionContext> CREATOR = new Creator<ActionContext>() {
        @NonNull
        @Override
        public ActionContext createFromParcel(Parcel in) {
            return new ActionContext(in);
        }

        @NonNull
        @Override
        public ActionContext[] newArray(int size) {
            return new ActionContext[size];
        }
    };
}
