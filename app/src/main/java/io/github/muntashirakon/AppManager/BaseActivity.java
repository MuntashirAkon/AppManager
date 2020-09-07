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

package io.github.muntashirakon.AppManager;

import android.content.Context;
import android.os.Bundle;

import java.util.Locale;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class BaseActivity extends AppCompatActivity {
    private Locale currentLocale;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        currentLocale = LangUtils.updateLanguage(this);
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode((int) AppPref.get(AppPref.PrefKey.PREF_APP_THEME_INT));
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LangUtils.attachBaseContext(newBase));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentLocale != LangUtils.getLocaleByLanguage()) recreate();
    }
}
