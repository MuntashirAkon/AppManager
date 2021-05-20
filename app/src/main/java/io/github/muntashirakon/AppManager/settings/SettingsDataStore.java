// SPDX-License-Identifier: GPL-3.0-or-later

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
