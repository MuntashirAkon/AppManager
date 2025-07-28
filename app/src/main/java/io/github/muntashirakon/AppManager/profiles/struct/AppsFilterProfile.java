// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.struct;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.filters.FilterItem;
import io.github.muntashirakon.AppManager.filters.FilteringUtils;
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.profiles.ProfileLogger;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.users.Users;

public class AppsFilterProfile extends AppsBaseProfile {
    private final FilterItem mFilterItem;

    protected AppsFilterProfile(@NonNull String profileId, @NonNull String profileName) {
        super(profileId, profileName, PROFILE_TYPE_APPS_FILTER);
        mFilterItem = new FilterItem();
    }

    protected AppsFilterProfile(@NonNull String profileId, @NonNull String profileName, @NonNull AppsFilterProfile profile) {
        super(profileId, profileName, profile.type);
        try {
            // Shorthand for cloning filter items
            mFilterItem = FilterItem.DESERIALIZER.deserialize(profile.mFilterItem.serializeToJson());
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid profile", e);
        }
    }

    @Override
    public ProfileApplierResult apply(@NonNull String state, @Nullable ProfileLogger logger, @Nullable ProgressHandler progressHandler) {
        // Filter results
        int[] users = this.users == null ? Users.getUsersIds() : this.users;
        List<IFilterableAppInfo> filterableAppInfoList = FilteringUtils.loadFilterableAppInfo(users);
        List<FilterItem.FilteredItemInfo> filteredList = mFilterItem.getFilteredList(filterableAppInfoList);
        if (filteredList.isEmpty()) {
            return ProfileApplierResult.EMPTY_RESULT;
        }
        List<String> packages = new ArrayList<>(filteredList.size());
        List<Integer> assocUsers = new ArrayList<>(filteredList.size());
        for (FilterItem.FilteredItemInfo info : filteredList) {
            packages.add(info.info.getPackageName());
            assocUsers.add(info.info.getUserId());
        }
        return apply(packages, assocUsers, state, logger, progressHandler);
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        return super.serializeToJson().put("filters", mFilterItem.serializeToJson());
    }

    protected AppsFilterProfile(@NonNull JSONObject profileObj) throws JSONException {
        super(profileObj);
        mFilterItem = FilterItem.DESERIALIZER.deserialize(profileObj.getJSONObject("filters"));
    }

    public static final JsonDeserializer.Creator<AppsFilterProfile> DESERIALIZER = AppsFilterProfile::new;
}
