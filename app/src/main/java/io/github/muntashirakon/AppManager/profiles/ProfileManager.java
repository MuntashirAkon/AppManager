/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.profiles;

import android.content.Context;
import android.text.TextUtils;

import java.io.File;
import java.util.HashMap;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;

public class ProfileManager {
    @NonNull
    public static HashMap<String, String> getProfiles() {
        File profilesPath = ProfileMetaManager.getProfilesDir();
        String[] profilesFiles = ArrayUtils.defeatNullable(profilesPath.list((dir, name) -> name.endsWith(ProfileMetaManager.PROFILE_EXT)));
        HashMap<String, String> profiles = new HashMap<>(profilesFiles.length);
        Context context = AppManager.getContext();
        String summary;
        for (String profile: profilesFiles) {
            int index = profile.indexOf(ProfileMetaManager.PROFILE_EXT);
            profile = profile.substring(0, index);
            summary = TextUtils.join(", ", new ProfileMetaManager(profile).getLocalisedSummary(context));
            if (summary.length() == 0) {
                summary = context.getString(R.string.no_configurations);
            }
            profiles.put(profile, summary);
        }
        return profiles;
    }
}
