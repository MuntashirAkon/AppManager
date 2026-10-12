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

public final class ActionTargetSpec implements IJsonSerializer, Parcelable {
    private final ActionScope mScope;
    @Nullable
    private final PackageTargetSpec mPackageTarget;
    @Nullable
    private final GlobalTargetSpec mGlobalTarget;

    public ActionTargetSpec(@NonNull PackageTargetSpec target) {
        mScope = ActionScope.PACKAGE;
        mPackageTarget = target;
        mGlobalTarget = null;
    }

    public ActionTargetSpec(@NonNull GlobalTargetSpec target) {
        mScope = ActionScope.GLOBAL;
        mPackageTarget = null;
        mGlobalTarget = target;
    }

    private ActionTargetSpec(@NonNull JSONObject object) throws JSONException {
        mScope = ActionScope.valueOf(object.getString("scope"));
        JSONObject target = object.getJSONObject("target");
        if (mScope == ActionScope.PACKAGE) {
            mPackageTarget = PackageTargetSpec.DESERIALIZER.deserialize(target);
            mGlobalTarget = null;
        } else {
            mPackageTarget = null;
            mGlobalTarget = GlobalTargetSpec.DESERIALIZER.deserialize(target);
        }
    }

    public static final JsonDeserializer.Creator<ActionTargetSpec> DESERIALIZER = ActionTargetSpec::new;

    @NonNull
    public ActionScope getScope() {
        return mScope;
    }

    @NonNull
    public PackageTargetSpec getPackageTarget() {
        if (mPackageTarget == null) {
            throw new IllegalStateException("Not a package target");
        }
        return mPackageTarget;
    }

    @NonNull
    public GlobalTargetSpec getGlobalTarget() {
        if (mGlobalTarget == null) {
            throw new IllegalStateException("Not a global target");
        }
        return mGlobalTarget;
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("scope", mScope.name());
        object.put("target", (mScope == ActionScope.PACKAGE ? mPackageTarget : mGlobalTarget).serializeToJson());
        return object;
    }

    private ActionTargetSpec(@NonNull Parcel in) {
        mScope = ActionScope.valueOf(in.readString());
        if (mScope == ActionScope.PACKAGE) {
            mPackageTarget = in.readParcelable(PackageTargetSpec.class.getClassLoader());
            mGlobalTarget = null;
        } else {
            mPackageTarget = null;
            mGlobalTarget = in.readParcelable(GlobalTargetSpec.class.getClassLoader());
        }
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mScope.name());
        dest.writeParcelable(mScope == ActionScope.PACKAGE ? mPackageTarget : mGlobalTarget, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ActionTargetSpec> CREATOR = new Creator<ActionTargetSpec>() {
        @NonNull
        @Override
        public ActionTargetSpec createFromParcel(Parcel in) {
            return new ActionTargetSpec(in);
        }

        @NonNull
        @Override
        public ActionTargetSpec[] newArray(int size) {
            return new ActionTargetSpec[size];
        }
    };
}
