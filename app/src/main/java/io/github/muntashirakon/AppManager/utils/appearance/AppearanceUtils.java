// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils.appearance;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public final class AppearanceUtils {
    public static final String TAG = AppearanceUtils.class.getSimpleName();

    public static void applyToActivity(@NonNull FragmentActivity activity, boolean transparentBackground) {
        activity.setTheme(transparentBackground ? AppPref.getTransparentAppTheme() : AppPref.getAppTheme());
    }

    public static void init(@NonNull Application application) {
        application.registerActivityLifecycleCallbacks(new ActivityAppearanceCallback());
        application.registerComponentCallbacks(new ComponentAppearanceCallback(application));
        LangUtils.applyLocale(application);
    }

    public static class ActivityAppearanceCallback implements Application.ActivityLifecycleCallbacks {
        private final SparseArray<Locale> mLastLocales = new SparseArray<>();
        private final SparseIntArray mLastLayoutDirection = new SparseIntArray();
        private final SparseBooleanArray mLastAppTheme = new SparseBooleanArray();

        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            mLastLocales.put(activity.hashCode(), LangUtils.applyLocaleToActivity(activity));
            mLastLayoutDirection.put(activity.hashCode(), AppPref.getLayoutOrientation());
            mLastAppTheme.put(activity.hashCode(), AppPref.isPureBlackTheme());
            AppCompatDelegate.setDefaultNightMode(AppPref.getInt(AppPref.PrefKey.PREF_APP_THEME_INT));
            Window window = activity.getWindow();
            WindowCompat.setDecorFitsSystemWindows(window, false);
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
            if (!Objects.equals(mLastLocales.get(activity.hashCode()), LangUtils.getFromPreference(activity))) {
                Log.d(TAG, "Locale changed in activity " + activity.getComponentName());
                ActivityCompat.recreate(activity);
                return;
            }
            if (!Objects.equals(mLastLayoutDirection.get(activity.hashCode()), AppPref.getLayoutOrientation())) {
                Log.d(TAG, "Layout orientation changed in activity " + activity.getComponentName());
                ActivityCompat.recreate(activity);
            }
            if (!Objects.equals(mLastAppTheme.get(activity.hashCode()), AppPref.isPureBlackTheme())) {
                Log.d(TAG, "App theme changed in activity " + activity.getComponentName());
                ActivityCompat.recreate(activity);
            }
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
            mLastLocales.delete(activity.hashCode());
            mLastLayoutDirection.delete(activity.hashCode());
            mLastAppTheme.delete(activity.hashCode());
        }
    }

    public static class ComponentAppearanceCallback implements ComponentCallbacks {
        public final Application mApplication;

        public ComponentAppearanceCallback(@NonNull Application application) {
            mApplication = application;
        }

        @Override
        public void onConfigurationChanged(@NonNull Configuration newConfig) {
            LangUtils.applyLocale(mApplication);
        }

        @Override
        public void onLowMemory() {
        }
    }
}
