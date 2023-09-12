// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils.appearance;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.UiModeManager;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.PowerManager;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.PublicTwilightManager;
import androidx.collection.ArrayMap;
import androidx.core.app.ActivityCompat;
import androidx.core.view.WindowCompat;

import com.google.android.material.color.DynamicColors;

import java.lang.ref.WeakReference;
import java.util.LinkedHashSet;
import java.util.Locale;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public final class AppearanceUtils {
    public static final String TAG = AppearanceUtils.class.getSimpleName();

    private static final ArrayMap<Integer, WeakReference<Activity>> sActivityReferences = new ArrayMap<>();

    private static class AppearanceOptions {
        public Locale locale;
        public Integer layoutDirection;
        public Integer theme;
        public Integer nightMode;
    }

    public static void applyOnlyLocale(@NonNull Context context) {
        // Update locale and layout direction for the application
        AppearanceOptions options = new AppearanceOptions();
        options.locale = LangUtils.getFromPreference(context);
        options.layoutDirection = Prefs.Appearance.getLayoutDirection();
        updateConfiguration(context, options);
        if (!context.equals(context.getApplicationContext())) {
            updateConfiguration(context.getApplicationContext(), options);
        }
    }

    /**
     * Return a {@link ContextThemeWrapper} with the default locale, layout direction, theme and night mode.
     */
    @NonNull
    public static Context getThemedContext(@NonNull Context context, boolean transparent) {
        AppearanceOptions options = new AppearanceOptions();
        options.locale = LangUtils.getFromPreference(context);
        options.layoutDirection = Prefs.Appearance.getLayoutDirection();
        options.theme = transparent ? Prefs.Appearance.getTransparentAppTheme() : Prefs.Appearance.getAppTheme();
        options.nightMode = Prefs.Appearance.getNightMode();
        ContextThemeWrapper newCtx = new ContextThemeWrapper(context, options.theme);
        newCtx.applyOverrideConfiguration(createOverrideConfiguration(context, options));
        return newCtx;
    }

    /**
     * Return a {@link ContextWrapper} with system configuration. This is helpful when it is necessary to access system
     * configurations instead of the one used in the app.
     */
    @NonNull
    public static Context getSystemContext(@NonNull Context context) {
        Resources res = Resources.getSystem();
        Configuration configuration = res.getConfiguration();
        return new ContextWrapper(context.createConfigurationContext(configuration));
    }

    /**
     * Initialize appearance in the app. Must be called from {@link Application#onCreate()}.
     */
    public static void init(@NonNull Application application) {
        application.registerActivityLifecycleCallbacks(new ActivityAppearanceCallback());
        application.registerComponentCallbacks(new ComponentAppearanceCallback(application));
        applyOnlyLocale(application);
    }

    /**
     * This is similar to what the delegate methods such as {@link AppCompatDelegate#setDefaultNightMode(int)} does.
     * This is required because simply calling {@link ActivityCompat#recreate(Activity)} cannot apply the changes to
     * all the active activities.
     */
    public static void applyConfigurationChangesToActivities() {
        for (WeakReference<Activity> activityRef : sActivityReferences.values()) {
            Activity activity = activityRef.get();
            if (activity != null) {
                ActivityCompat.recreate(activity);
            }
        }
    }

    private static class ActivityAppearanceCallback implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityPreCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            if (activity instanceof BaseActivity) {
                boolean transparentBackground = ((BaseActivity) activity).getTransparentBackground();
                activity.setTheme(transparentBackground
                        ? Prefs.Appearance.getTransparentAppTheme()
                        : Prefs.Appearance.getAppTheme());
            }
            // Theme must be set first because the method below will add dynamic attributes to the theme
            DynamicColors.applyToActivityIfAvailable(activity);
        }

        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                onActivityPreCreated(activity, savedInstanceState);
            }
            Window window = activity.getWindow();
            WindowCompat.setDecorFitsSystemWindows(window, false);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                onActivityPostCreated(activity, savedInstanceState);
            }
        }

        @Override
        public void onActivityPostCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            applyOnlyLocale(activity);
            AppCompatDelegate.setDefaultNightMode(Prefs.Appearance.getNightMode());

            sActivityReferences.put(activity.hashCode(), new WeakReference<>(activity));
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
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
        public void onActivityPreDestroyed(@NonNull Activity activity) {
            sActivityReferences.remove(activity.hashCode());
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                onActivityPreDestroyed(activity);
            }
        }
    }

    private static class ComponentAppearanceCallback implements ComponentCallbacks2 {
        private final Application mApplication;

        public ComponentAppearanceCallback(@NonNull Application application) {
            mApplication = application;
        }

        @Override
        public void onConfigurationChanged(@NonNull Configuration newConfig) {
            applyOnlyLocale(mApplication);
        }

        @Override
        public void onLowMemory() {
        }

        @Override
        public void onTrimMemory(int level) {
        }
    }

    private static void updateConfiguration(@NonNull Context context, @NonNull AppearanceOptions options) {
        Resources res = context.getResources();
        // Set theme
        if (options.theme != null) {
            context.setTheme(options.theme);
        }
        // Update configuration
        Configuration overrideConf = createOverrideConfiguration(context, options);
        //noinspection deprecation
        res.updateConfiguration(overrideConf, res.getDisplayMetrics());
    }

    @NonNull
    private static Configuration createOverrideConfiguration(@NonNull Context context, @NonNull AppearanceOptions options) {
        return createOverrideConfiguration(context, options, null, false);
    }

    @SuppressLint("AppBundleLocaleChanges") // We don't use Play Store
    @NonNull
    private static Configuration createOverrideConfiguration(@NonNull Context context,
                                                             @NonNull AppearanceOptions options,
                                                             @Nullable Configuration configOverlay,
                                                             boolean ignoreFollowSystem) {
        // Order matters!
        Resources res = context.getResources();
        Configuration oldConf = res.getConfiguration();
        Configuration overrideConf = new Configuration(oldConf);

        // Set locale
        if (options.locale != null) {
            Locale.setDefault(options.locale);
            //noinspection deprecation
            Locale currentLocale = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? oldConf.getLocales().get(0) : oldConf.locale;
            if (currentLocale != options.locale) {
                // Locale has changed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setLocaleApi24(overrideConf, options.locale);
                } else {
                    overrideConf.setLocale(options.locale);
                    overrideConf.setLayoutDirection(options.locale);
                }
            }
        }
        // Set layout direction
        if (options.layoutDirection != null) {
            int currentLayoutDirection = overrideConf.getLayoutDirection();
            if (currentLayoutDirection != options.layoutDirection) {
                switch (options.layoutDirection) {
                    case View.LAYOUT_DIRECTION_RTL:
                        overrideConf.setLayoutDirection(Locale.forLanguageTag("ar"));
                        break;
                    case View.LAYOUT_DIRECTION_LTR:
                        overrideConf.setLayoutDirection(Locale.ENGLISH);
                }
            }
        }

        // Set night mode
        if (options.nightMode != null) {
            // Follow AppCompatDelegateImpl
            int nightMode = options.nightMode != AppCompatDelegate.MODE_NIGHT_UNSPECIFIED ? options.nightMode
                    : AppCompatDelegate.getDefaultNightMode();
            int modeToApply = mapNightModeOnce(context, nightMode);
            int newNightMode;
            switch (modeToApply) {
                case AppCompatDelegate.MODE_NIGHT_YES:
                    newNightMode = Configuration.UI_MODE_NIGHT_YES;
                    break;
                case AppCompatDelegate.MODE_NIGHT_NO:
                    newNightMode = Configuration.UI_MODE_NIGHT_NO;
                    break;
                default:
                case AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM:
                    if (ignoreFollowSystem) {
                        // We're generating an overlay to be used on top of the system configuration,
                        // so use whatever's already there.
                        newNightMode = Configuration.UI_MODE_NIGHT_UNDEFINED;
                    } else {
                        // If we're following the system, we just use the system default from the
                        // application context
                        final Configuration sysConf = Resources.getSystem().getConfiguration();
                        newNightMode = sysConf.uiMode & Configuration.UI_MODE_NIGHT_MASK;
                    }
                    break;
            }
            overrideConf.uiMode = newNightMode | (overrideConf.uiMode & ~Configuration.UI_MODE_NIGHT_MASK);
        }

        // Apply overlay
        if (configOverlay != null) {
            overrideConf.setTo(configOverlay);
        }
        return overrideConf;
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private static void setLocaleApi24(@NonNull Configuration config, @NonNull Locale locale) {
        LocaleList defaultLocales = LocaleList.getDefault();
        LinkedHashSet<Locale> locales = new LinkedHashSet<>(defaultLocales.size() + 1);
        // Bring the target locale to the front of the list
        // There's a hidden API, but it's not currently used here.
        locales.add(locale);
        for (int i = 0; i < defaultLocales.size(); ++i) {
            locales.add(defaultLocales.get(i));
        }
        config.setLocales(new LocaleList(locales.toArray(new Locale[0])));
    }

    @SuppressWarnings("deprecation")
    private static int mapNightModeOnce(@NonNull Context context, @AppCompatDelegate.NightMode final int mode) {
        switch (mode) {
            case AppCompatDelegate.MODE_NIGHT_NO:
            case AppCompatDelegate.MODE_NIGHT_YES:
            case AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM:
                // $FALLTHROUGH since these are all valid modes to return
                return mode;
            case AppCompatDelegate.MODE_NIGHT_AUTO_TIME:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    UiModeManager uiModeManager = (UiModeManager) context.getApplicationContext()
                            .getSystemService(Context.UI_MODE_SERVICE);
                    if (uiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_AUTO) {
                        // If we're set to AUTO and the system's auto night mode is already enabled,
                        // we'll just let the system handle it by returning FOLLOW_SYSTEM
                        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                    }
                }
                // Unlike AppCompatDelegateImpl, we don't need to change it based on configuration
                return PublicTwilightManager.isNight(context) ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO;
            case AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY: {
                // Unlike AppCompatDelegateImpl, we don't need to change it based on configuration
                PowerManager pm = (PowerManager) context.getApplicationContext()
                        .getSystemService(Context.POWER_SERVICE);
                return pm.isPowerSaveMode() ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            }
            case AppCompatDelegate.MODE_NIGHT_UNSPECIFIED:
                // If we don't have a mode specified, let the system handle it
                return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            default:
                throw new IllegalStateException("Unknown value set for night mode. Please use one"
                        + " of the MODE_NIGHT values from AppCompatDelegate.");
        }
    }

}
