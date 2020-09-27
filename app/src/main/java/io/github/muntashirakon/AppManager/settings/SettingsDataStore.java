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

package io.github.muntashirakon.AppManager.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;
import io.github.muntashirakon.AppManager.utils.AppPref;

public class SettingsDataStore extends PreferenceDataStore {
    AppPref appPref;

    public SettingsDataStore() {
        super();
        appPref = AppPref.getInstance();
    }

    @Override
    public void putString(String key, @Nullable String value) {
        appPref.setPref(key, value);
    }

    @Override
    public void putInt(String key, int value) {
        appPref.setPref(key, value);
    }

    @Override
    public void putLong(String key, long value) {
        appPref.setPref(key, value);
    }

    @Override
    public void putFloat(String key, float value) {
        appPref.setPref(key, value);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        appPref.setPref(key, value);
    }

    @NonNull
    @Override
    public String getString(String key, @Nullable String defValue) {
        return (String) appPref.get(key);
    }

    @Override
    public int getInt(String key, int defValue) {
        return (int) appPref.get(key);
    }

    @Override
    public long getLong(String key, long defValue) {
        return (long) appPref.get(key);
    }

    @Override
    public float getFloat(String key, float defValue) {
        return (float) appPref.get(key);
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return (boolean) appPref.get(key);
    }
}
