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

import androidx.annotation.NonNull;
import androidx.preference.PreferenceDataStore;

public class ConfDataStore extends PreferenceDataStore {
    private final ProfileMetaManager.Profile profile;

    public ConfDataStore(final ProfileMetaManager.Profile profile) {
        super();
        this.profile = profile;
    }

    @Override
    public void putBoolean(@NonNull String key, boolean value) {
        switch (key) {
            case "disable":
                profile.disable = value;
                break;
            case "force_stop":
                profile.forceStop = value;
                break;
            case "clear_cache":
                profile.clearCache = value;
                break;
            case "clear_data":
                profile.clearData = value;
                break;
            case "block_trackers":
                profile.blockTrackers = value;
                break;
            case "backup_apk":
                profile.backupApk = value;
                break;
            case "allow_routine":
                profile.allowRoutine = value;
                break;
        }
    }

    @Override
    public boolean getBoolean(@NonNull String key, boolean defValue) {
        switch (key) {
            case "disable": return profile.disable;
            case "force_stop": return profile.forceStop;
            case "clear_cache": return profile.clearCache;
            case "clear_data": return profile.clearData;
            case "block_trackers": return profile.blockTrackers;
            case "backup_apk": return profile.backupApk;
            case "allow_routine": return profile.allowRoutine;
            default: return defValue;
        }
    }
}
