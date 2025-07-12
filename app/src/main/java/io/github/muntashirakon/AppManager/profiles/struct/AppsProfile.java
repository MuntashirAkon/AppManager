// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.struct;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import aosp.libcore.util.EmptyArray;
import io.github.muntashirakon.AppManager.history.JsonDeserializer;
import io.github.muntashirakon.AppManager.profiles.ProfileLogger;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;


public class AppsProfile extends AppsBaseProfile {
    @NonNull
    public String[] packages;  // packages (a list of packages)

    protected AppsProfile(@NonNull String profileId, @NonNull String profileName) {
        super(profileId, profileName, PROFILE_TYPE_APPS);
        packages = EmptyArray.STRING;
    }

    protected AppsProfile(@NonNull String profileId, @NonNull String profileName, @NonNull AppsProfile profile) {
        super(profileId, profileName, profile);
        packages = profile.packages.clone();
    }

    @Override
    public ProfileApplierResult apply(@NonNull String state, @Nullable ProfileLogger logger, @Nullable ProgressHandler progressHandler) {
        if (packages.length == 0) return ProfileApplierResult.EMPTY_RESULT;
        int[] users = this.users == null ? Users.getUsersIds() : this.users;
        int size = packages.length * users.length;
        List<String> packageList = new ArrayList<>(size);
        List<Integer> assocUsers = new ArrayList<>(size);
        for (String packageName : packages) {
            for (int user : users) {
                packageList.add(packageName);
                assocUsers.add(user);
            }
        }
        return apply(packageList, assocUsers, state, logger, progressHandler);
    }

    public void appendPackages(@NonNull String[] packageList) {
        List<String> uniquePackages = new ArrayList<>();
        for (String newPackage : packageList) {
            if (!ArrayUtils.contains(packages, newPackage)) {
                uniquePackages.add(newPackage);
            }
        }
        packages = ArrayUtils.concatElements(String.class, packages, uniquePackages.toArray(new String[0]));
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        return super.serializeToJson()
                .put("packages", JSONUtils.getJSONArray(packages));
    }

    protected AppsProfile(@NonNull JSONObject profileObj) throws JSONException {
        super(profileObj);
        packages = JSONUtils.getArray(String.class, profileObj.getJSONArray("packages"));
    }

    public static final JsonDeserializer.Creator<AppsProfile> DESERIALIZER = AppsProfile::new;
}
