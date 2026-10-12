// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.actions;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.core.os.ParcelCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.types.UserPackagePair;

public final class PackageTargetSpec implements IJsonSerializer, Parcelable {
    private final List<UserPackagePair> mConfiguredTargets;
    private final boolean mIncludeSelected;
    private final boolean mIncludeInherited;

    public PackageTargetSpec(@NonNull List<UserPackagePair> configuredTargets,
                             boolean includeSelected, boolean includeInherited) {
        mConfiguredTargets = Collections.unmodifiableList(new ArrayList<>(configuredTargets));
        mIncludeSelected = includeSelected;
        mIncludeInherited = includeInherited;
    }

    private PackageTargetSpec(@NonNull JSONObject object) throws JSONException {
        JSONArray targets = object.getJSONArray("configured");
        ArrayList<UserPackagePair> pairs = new ArrayList<>();
        for (int i = 0; i < targets.length(); ++i) {
            JSONObject target = targets.getJSONObject(i);
            pairs.add(new UserPackagePair(target.getString("package"), target.getInt("user")));
        }
        mConfiguredTargets = Collections.unmodifiableList(pairs);
        mIncludeSelected = object.optBoolean("selected");
        mIncludeInherited = object.optBoolean("inherited");
    }

    public static final JsonDeserializer.Creator<PackageTargetSpec> DESERIALIZER = PackageTargetSpec::new;

    @NonNull
    public List<UserPackagePair> getConfiguredTargets() {
        return mConfiguredTargets;
    }

    public boolean includesSelected() {
        return mIncludeSelected;
    }

    public boolean includesInherited() {
        return mIncludeInherited;
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        JSONObject object = new JSONObject();
        JSONArray targets = new JSONArray();
        for (UserPackagePair target : mConfiguredTargets) {
            JSONObject item = new JSONObject();
            item.put("package", target.getPackageName());
            item.put("user", target.getUserId());
            targets.put(item);
        }
        object.put("configured", targets);
        object.put("selected", mIncludeSelected);
        object.put("inherited", mIncludeInherited);
        return object;
    }

    private PackageTargetSpec(@NonNull Parcel in) {
        mConfiguredTargets = Collections.unmodifiableList(in.createTypedArrayList(UserPackagePair.CREATOR));
        mIncludeSelected = ParcelCompat.readBoolean(in);
        mIncludeInherited = ParcelCompat.readBoolean(in);
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeTypedList(mConfiguredTargets);
        ParcelCompat.writeBoolean(dest, mIncludeSelected);
        ParcelCompat.writeBoolean(dest, mIncludeInherited);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<PackageTargetSpec> CREATOR = new Creator<PackageTargetSpec>() {
        @NonNull
        @Override
        public PackageTargetSpec createFromParcel(Parcel in) {
            return new PackageTargetSpec(in);
        }

        @NonNull
        @Override
        public PackageTargetSpec[] newArray(int size) {
            return new PackageTargetSpec[size];
        }
    };
}
