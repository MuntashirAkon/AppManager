// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils.appearance;

import android.view.Window;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.FragmentActivity;

import io.github.muntashirakon.AppManager.utils.AppPref;

public final class AppearanceUtils {
    public static void applyToActivity(@NonNull FragmentActivity activity) {
        AppCompatDelegate.setDefaultNightMode(AppPref.getInt(AppPref.PrefKey.PREF_APP_THEME_INT));
        Window window = activity.getWindow();
        window.getDecorView().setLayoutDirection(AppPref.getInt(AppPref.PrefKey.PREF_LAYOUT_ORIENTATION_INT));
        WindowCompat.setDecorFitsSystemWindows(window, false);
    }
}
