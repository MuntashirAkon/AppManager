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

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.Formatter;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.collection.ArrayMap;
import androidx.core.app.ActivityCompat;
import androidx.core.os.LocaleListCompat;
import androidx.core.text.HtmlCompat;
import androidx.core.util.Pair;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import com.android.internal.util.TextUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.yariksoffice.lingver.Lingver;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.intercept.ActivityInterceptor;
import io.github.muntashirakon.AppManager.misc.SystemProperties;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.types.FullscreenDialog;
import io.github.muntashirakon.AppManager.types.ScrollableDialogBuilder;
import io.github.muntashirakon.AppManager.utils.*;
import io.github.muntashirakon.io.ProxyFileReader;

import java.io.BufferedReader;
import java.security.Provider;
import java.security.Security;
import java.util.*;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getPrimaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getTitleText;

public class MainPreferences extends PreferenceFragmentCompat {
    private static final List<Integer> THEME_CONST = Arrays.asList(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES);
    private static final List<String> MODE_NAMES = Arrays.asList(
            Runner.MODE_AUTO,
            Runner.MODE_ROOT,
            Runner.MODE_ADB,
            Runner.MODE_NO_ROOT);

    private static final int[] installLocationNames = new int[]{
            R.string.auto,  // PackageInfo.INSTALL_LOCATION_AUTO
            R.string.install_location_internal_only,  // PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY
            R.string.install_location_prefer_external,  // PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL
    };

    SettingsActivity activity;
    private int currentTheme;
    private String currentLang;
    @Runner.Mode
    private String currentMode;
    private String installerApp;
    private PackageManager pm;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        activity = (SettingsActivity) requireActivity();
        pm = activity.getPackageManager();
        // Custom locale
        currentLang = (String) AppPref.get(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR);
        ArrayMap<String, Locale> locales = LangUtils.getAppLanguages(activity);
        final CharSequence[] languages = getLanguagesL(locales);
        Preference locale = Objects.requireNonNull(findPreference("custom_locale"));
        locale.setSummary(getString(R.string.current_language, languages[locales.indexOfKey(currentLang)]));
        locale.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.choose_language)
                    .setSingleChoiceItems(languages, locales.indexOfKey(currentLang),
                            (dialog, which) -> currentLang = locales.keyAt(which))
                    .setPositiveButton(R.string.apply, (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR, currentLang);
                        Lingver.getInstance().setLocale(activity, LangUtils.getLocaleByLanguage(activity));
                        ActivityCompat.recreate(activity);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // App theme
        final String[] themes = getResources().getStringArray(R.array.themes);
        currentTheme = (int) AppPref.get(AppPref.PrefKey.PREF_APP_THEME_INT);
        Preference appTheme = Objects.requireNonNull(findPreference("app_theme"));
        appTheme.setSummary(getString(R.string.current_theme, themes[THEME_CONST.indexOf(currentTheme)]));
        appTheme.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.select_theme)
                    .setSingleChoiceItems(themes, THEME_CONST.indexOf(currentTheme),
                            (dialog, which) -> currentTheme = THEME_CONST.get(which))
                    .setPositiveButton(R.string.apply, (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_APP_THEME_INT, currentTheme);
                        AppCompatDelegate.setDefaultNightMode(currentTheme);
                        appTheme.setSummary(getString(R.string.current_theme, themes[THEME_CONST.indexOf(currentTheme)]));
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Screen lock
        // App usage permission toggle
        SwitchPreferenceCompat screenLock = Objects.requireNonNull(findPreference("enable_screen_lock"));
        screenLock.setChecked((boolean) AppPref.get(AppPref.PrefKey.PREF_ENABLE_SCREEN_LOCK_BOOL));
        // Mode of operation
        Preference mode = Objects.requireNonNull(findPreference("mode_of_operations"));
        final String[] modes = getResources().getStringArray(R.array.modes);
        currentMode = (String) AppPref.get(AppPref.PrefKey.PREF_MODE_OF_OPS_STR);
        mode.setSummary(getString(R.string.current_mode, modes[MODE_NAMES.indexOf(currentMode)]));
        mode.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.pref_mode_of_operations)
                    .setSingleChoiceItems(modes, MODE_NAMES.indexOf(currentMode),
                            (dialog, which) -> currentMode = MODE_NAMES.get(which))
                    .setPositiveButton(R.string.apply, (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_MODE_OF_OPS_STR, currentMode);
                        mode.setSummary(getString(R.string.current_mode, modes[MODE_NAMES.indexOf(currentMode)]));
                        new Thread(() -> RunnerUtils.setModeOfOps(activity)).start();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // App usage permission toggle
        SwitchPreferenceCompat usageEnabled = Objects.requireNonNull(findPreference("usage_access_enabled"));
        usageEnabled.setChecked((boolean) AppPref.get(AppPref.PrefKey.PREF_USAGE_ACCESS_ENABLED_BOOL));
        // Interceptor
        SwitchPreferenceCompat interceptorEnabled = Objects.requireNonNull(findPreference("interceptor_enabled"));
        ComponentName interceptorComponent = new ComponentName(activity.getPackageName(), ActivityInterceptor.class.getName());
        interceptorEnabled.setChecked(isComponentEnabled(interceptorComponent));
        interceptorEnabled.setOnPreferenceChangeListener((preference, isEnabled) -> {
            if ((boolean) isEnabled) {
                pm.setComponentEnabledSetting(interceptorComponent, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            } else {
                pm.setComponentEnabledSetting(interceptorComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
            interceptorEnabled.setChecked(isComponentEnabled(interceptorComponent));
            return true;
        });
        // Global blocking enabled
        SwitchPreferenceCompat gcb = Objects.requireNonNull(findPreference("global_blocking_enabled"));
        gcb.setChecked(AppPref.isGlobalBlockingEnabled());
        gcb.setOnPreferenceChangeListener((preference, isEnabled) -> {
            if (AppPref.isRootEnabled() && (boolean) isEnabled) {
                // Apply all rules immediately if GCB is true
                int userHandle = Users.getCurrentUserHandle();
                ComponentsBlocker.applyAllRules(activity, userHandle);
            }
            return true;
        });
        // Import/export rules
        ((Preference) Objects.requireNonNull(findPreference("import_export_rules"))).setOnPreferenceClickListener(preference -> {
            new ImportExportDialogFragment().show(getParentFragmentManager(), ImportExportDialogFragment.TAG);
            return true;
        });
        // Remove all rules
        ((Preference) Objects.requireNonNull(findPreference("remove_all_rules"))).setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.pref_remove_all_rules)
                    .setMessage(R.string.are_you_sure)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        activity.progressIndicator.show();
                        new Thread(() -> {
                            // TODO: Remove for all users
                            int userHandle = Users.getCurrentUserHandle();
                            List<String> packages = ComponentUtils.getAllPackagesWithRules();
                            for (String packageName : packages) {
                                ComponentUtils.removeAllRules(packageName, userHandle);
                            }
                            activity.runOnUiThread(() -> {
                                activity.progressIndicator.hide();
                                Toast.makeText(activity, R.string.the_operation_was_successful, Toast.LENGTH_SHORT).show();
                            });
                        }).start();
                    })
                    .setNegativeButton(R.string.no, null)
                    .show();
            return true;
        });
        // Display users in installer
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("installer_display_users")))
                .setChecked((boolean) AppPref.get(AppPref.PrefKey.PREF_INSTALLER_DISPLAY_USERS_BOOL));
        // Set install locations
        Preference installLocationPref = Objects.requireNonNull(findPreference("installer_install_location"));
        int installLocation = (int) AppPref.get(AppPref.PrefKey.PREF_INSTALLER_INSTALL_LOCATION_INT);
        installLocationPref.setSummary(installLocationNames[installLocation]);
        installLocationPref.setOnPreferenceClickListener(preference -> {
            CharSequence[] installLocationTexts = new CharSequence[installLocationNames.length];
            for (int i = 0; i < installLocationNames.length; ++i) {
                installLocationTexts[i] = getString(installLocationNames[i]);
            }
            int choice = (int) AppPref.get(AppPref.PrefKey.PREF_INSTALLER_INSTALL_LOCATION_INT);
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.install_location)
                    .setSingleChoiceItems(installLocationTexts, choice, (dialog, newInstallLocation) -> {
                        AppPref.set(AppPref.PrefKey.PREF_INSTALLER_INSTALL_LOCATION_INT, newInstallLocation);
                        installLocationPref.setSummary(installLocationNames[newInstallLocation]);
                    })
                    .setNegativeButton(R.string.cancel, (dialog, which) -> {
                        // Revert
                        AppPref.set(AppPref.PrefKey.PREF_INSTALLER_INSTALL_LOCATION_INT, installLocation);
                        installLocationPref.setSummary(installLocationNames[installLocation]);
                    })
                    .show();
            return true;
        });
        // Set installer app
        Preference installerAppPref = Objects.requireNonNull(findPreference("installer_installer_app"));
        installerApp = (String) AppPref.get(AppPref.PrefKey.PREF_INSTALLER_INSTALLER_APP_STR);
        installerAppPref.setSummary(PackageUtils.getPackageLabel(pm, installerApp));
        installerAppPref.setOnPreferenceClickListener(preference -> {
            activity.progressIndicator.show();
            new Thread(() -> {
                // List apps
                List<PackageInfo> packageInfoList = pm.getInstalledPackages(0);
                ArrayList<String> items = new ArrayList<>(packageInfoList.size());
                ArrayList<CharSequence> itemNames = new ArrayList<>(packageInfoList.size());
                for (PackageInfo info : packageInfoList) {
                    items.add(info.packageName);
                    itemNames.add(info.applicationInfo.loadLabel(pm));
                }
                int selectedApp = itemNames.indexOf(installerApp);
                activity.runOnUiThread(() -> {
                    activity.progressIndicator.hide();
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.installer_app)
                            .setSingleChoiceItems(itemNames.toArray(new CharSequence[0]),
                                    selectedApp, (dialog, which) -> installerApp = items.get(which))
                            .setPositiveButton(R.string.ok, (dialog, which) -> {
                                AppPref.set(AppPref.PrefKey.PREF_INSTALLER_INSTALLER_APP_STR, installerApp);
                                installerAppPref.setSummary(PackageUtils.getPackageLabel(pm, installerApp));
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });
            }).start();
            return true;
        });
        // Sign apk before install
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("installer_sign_apk")))
                .setChecked((boolean) AppPref.get(AppPref.PrefKey.PREF_INSTALLER_SIGN_APK_BOOL));
        // About device
        ((Preference) Objects.requireNonNull(findPreference("about_device"))).setOnPreferenceClickListener(preference -> {
            @SuppressLint("InflateParams")
            View view = getLayoutInflater().inflate(R.layout.dialog_scrollable_text_view, null);
            ((TextView) view.findViewById(android.R.id.content)).setText(getDeviceInfo());
            view.findViewById(android.R.id.checkbox).setVisibility(View.GONE);
            new FullscreenDialog(activity).setTitle(R.string.about_device).setView(view).show();
            return true;
        });

        // About
        ((Preference) Objects.requireNonNull(findPreference("about"))).setOnPreferenceClickListener(preference -> {
            @SuppressLint("InflateParams")
            View view = getLayoutInflater().inflate(R.layout.dialog_about, null);
            ((TextView) view.findViewById(R.id.version)).setText(String.format(Locale.ROOT,
                    "%s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
            new FullscreenDialog(activity).setTitle(R.string.about).setView(view).show();
            return true;
        });
        // Changelog
        ((Preference) Objects.requireNonNull(findPreference("changelog"))).setOnPreferenceClickListener(preference -> {
            new Thread(() -> {
                final Spanned spannedChangelog = HtmlCompat.fromHtml(IOUtils.getContentFromAssets(activity, "changelog.html"), HtmlCompat.FROM_HTML_MODE_COMPACT);
                activity.runOnUiThread(() ->
                        new ScrollableDialogBuilder(activity, spannedChangelog)
                                .linkifyAll()
                                .setTitle(R.string.changelog)
                                .setNegativeButton(R.string.ok, null)
                                .show());
            }).start();
            return true;
        });
    }

    @NonNull
    private CharSequence[] getLanguagesL(@NonNull ArrayMap<String, Locale> locales) {
        CharSequence[] localesL = new CharSequence[locales.size()];
        Locale locale;
        for (int i = 0; i < locales.size(); ++i) {
            locale = locales.valueAt(i);
            if (LangUtils.LANG_AUTO.equals(locales.keyAt(i))) {
                localesL[i] = activity.getString(R.string.auto);
            } else localesL[i] = locale.getDisplayName(locale);
        }
        return localesL;
    }

    @NonNull
    private CharSequence getDeviceInfo() {
        ActivityManager activityManager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        PackageManager pm = activity.getPackageManager();
        SpannableStringBuilder builder = new SpannableStringBuilder();
        // Android platform info
        builder.append(getPrimaryText(activity, getString(R.string.version) + ": "))
                .append(Build.VERSION.RELEASE).append("\n")
                .append(getPrimaryText(activity, getString(R.string.bootloader) + ": "))
                .append(Build.BOOTLOADER).append(", ")
                .append(getPrimaryText(activity, "VM: "))
                .append(getVmVersion()).append("\n")
                .append(getPrimaryText(activity, getString(R.string.kernel) + ": "))
                .append(getKernel()).append("\n")
                .append(getPrimaryText(activity, getString(R.string.brand_name) + ": "))
                .append(Build.BRAND).append(", ")
                .append(getPrimaryText(activity, getString(R.string.model) + ": "))
                .append(Build.MODEL).append("\n")
                .append(getPrimaryText(activity, getString(R.string.board_name) + ": "))
                .append(Build.BOARD).append(", ")
                .append(getPrimaryText(activity, getString(R.string.manufacturer) + ": "))
                .append(Build.MANUFACTURER).append("\n");
        // SDK
        builder.append("\n").append(getTitleText(activity, getString(R.string.sdk))).append("\n")
                .append(getPrimaryText(activity, getString(R.string.sdk_max) + ": "))
                .append(String.valueOf(Build.VERSION.SDK_INT));
        String minSdk = SystemProperties.get("ro.build.version.min_supported_target_sdk", "");
        if (!TextUtils.isEmpty(minSdk)){
            builder.append(", ").append(getPrimaryText(activity,
                    getString(R.string.sdk_min) + ": ")).append(minSdk);
        }
        builder.append("\n");
        // Security
        builder.append("\n").append(getTitleText(activity, getString(R.string.security))).append("\n");
        builder.append(getPrimaryText(activity, getString(R.string.root) + ": "))
                .append(String.valueOf(RunnerUtils.isRootAvailable())).append("\n");
        int selinux = getSelinuxStatus();
        if (selinux != 2) {
            builder.append(getPrimaryText(activity, getString(R.string.selinux) + ": "))
                    .append(getString(selinux == 1 ? R.string.enforcing : R.string.permissive))
                    .append("\n");
        }
        builder.append(getPrimaryText(activity, getString(R.string.encryption) + ": "))
                .append(getEncryptionStatus()).append("\n");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.append(getPrimaryText(activity, getString(R.string.patch_level) + ": "))
                    .append(Build.VERSION.SECURITY_PATCH).append("\n");
        }
        List<CharSequence> securityProviders = new ArrayList<>();
        builder.append(getPrimaryText(activity, getString(R.string.security_providers) + ": "));
        for (Provider provider : Security.getProviders()) {
            securityProviders.add(provider.getName() + " (v" + provider.getVersion() + ")");
        }
        builder.append(TextUtils.joinSpannable(", ", securityProviders)).append("\n");
        // CPU info
        builder.append("\n").append(getTitleText(activity, getString(R.string.cpu))).append("\n");
        String cpuHardware = getCpuHardware();
        if (cpuHardware != null) {
            builder.append(getPrimaryText(activity, getString(R.string.hardware) + ": "))
                    .append(cpuHardware).append("\n");
        }
        builder.append(getPrimaryText(activity, getString(R.string.support_architectures) + ": "))
                .append(TextUtils.join(", ", Build.SUPPORTED_ABIS)).append("\n")
                .append(getPrimaryText(activity, getString(R.string.no_of_cores) + ": "))
                .append(String.valueOf(Runtime.getRuntime().availableProcessors())).append("\n");
        // GPU info
        builder.append("\n").append(getTitleText(activity, getString(R.string.graphics))).append("\n");
        builder.append(getPrimaryText(activity, getString(R.string.gles_version) + ": "))
                .append(Utils.getGlEsVersion(activityManager.getDeviceConfigurationInfo().reqGlEsVersion)).append("\n");
        // TODO(19/12/20): Get vendor name
        // RAM info
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        long memorySize = memoryInfo.totalMem;
        builder.append("\n").append(getTitleText(activity, getString(R.string.memory))).append("\n")
                .append(Formatter.formatFileSize(activity, memorySize)).append("\n");
        // Battery info
        builder.append("\n").append(getTitleText(activity, getString(R.string.battery))).append("\n");
        builder.append(getPrimaryText(activity, getString(R.string.battery_capacity) + ": "))
                .append(String.valueOf(getBatteryCapacity())).append("mAh").append("\n");
        // TODO(19/12/20): Get more battery info
        // Screen resolution
        builder.append("\n").append(getTitleText(activity, getString(R.string.screen))).append("\n")
                .append(getPrimaryText(activity, getString(R.string.density) + ": "))
                .append(getDensity()).append(" (")
                .append(String.valueOf(StaticDataset.DEVICE_DENSITY)).append(" DPI)").append("\n");
        Display display;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            display = activity.getDisplay();
        } else {
            display = activity.getWindowManager().getDefaultDisplay();
        }
        DisplayMetrics displayMetrics = new DisplayMetrics();
        // Actual size
        display.getRealMetrics(displayMetrics);
        builder.append(getPrimaryText(activity, getString(R.string.scaling_factor) + ": "))
                .append(String.valueOf(displayMetrics.density)).append("\n")
                .append(getPrimaryText(activity, getString(R.string.size) + ": "))
                .append(String.valueOf(displayMetrics.widthPixels)).append("px × ")
                .append(String.valueOf(displayMetrics.heightPixels)).append("px\n");
        // Window size
        display.getMetrics(displayMetrics);
        builder.append(getPrimaryText(activity, getString(R.string.window_size) + ": "))
                .append(String.valueOf(displayMetrics.widthPixels)).append("px × ")
                .append(String.valueOf(displayMetrics.heightPixels)).append("px\n");
        // Refresh rate
        builder.append(getPrimaryText(activity, getString(R.string.refresh_rate) + ": "))
                .append(String.format(Locale.ROOT, "%.1f", display.getRefreshRate()))
                .append("\n");
        // List system locales
        LocaleListCompat locales = LocaleListCompat.getDefault();
        List<String> localeStrings = new ArrayList<>(locales.size());
        for (int i = 0; i < locales.size(); ++i) {
            localeStrings.add(locales.get(i).getDisplayName());
        }
        builder.append("\n").append(getTitleText(activity, getString(R.string.languages)))
                .append("\n").append(TextUtils.joinSpannable(", ", localeStrings))
                .append("\n");
        List<UserInfo> users = Users.getUsers();
        if (users != null) {
            // Users
            builder.append("\n").append(getTitleText(activity, getString(R.string.users)))
                    .append("\n");
            List<String> userNames = new ArrayList<>();
            for (UserInfo user : users) {
                userNames.add(user.name);
            }
            builder.append(String.valueOf(users.size())).append(" (")
                    .append(TextUtils.joinSpannable(", ", userNames))
                    .append(")\n");
            // App stats per user
            builder.append("\n").append(getTitleText(activity, getString(R.string.apps))).append("\n");
            for (UserInfo user : users) {
                Pair<Integer, Integer> packageSizes = getPackageStats(user.id);
                if (packageSizes.first + packageSizes.second == 0) continue;
                builder.append(getPrimaryText(activity, getString(R.string.user) + ": "))
                        .append(user.name).append("\n   ")
                        .append(getPrimaryText(activity, getString(R.string.total_size) + ": "))
                        .append(String.valueOf(packageSizes.first + packageSizes.second)).append(", ")
                        .append(getPrimaryText(activity, getString(R.string.user) + ": "))
                        .append(String.valueOf(packageSizes.first)).append(", ")
                        .append(getPrimaryText(activity, getString(R.string.system) + ": "))
                        .append(String.valueOf(packageSizes.second)).append("\n");
            }
        } else {
            Pair<Integer, Integer> packageSizes = getPackageStats(Users.getCurrentUserHandle());
            builder.append(getPrimaryText(activity, getString(R.string.total_size) + ": "))
                    .append(String.valueOf(packageSizes.first + packageSizes.second)).append(", ")
                    .append(getPrimaryText(activity, getString(R.string.user) + ": "))
                    .append(String.valueOf(packageSizes.first)).append(", ")
                    .append(getPrimaryText(activity, getString(R.string.system) + ": "))
                    .append(String.valueOf(packageSizes.second)).append("\n");
        }
        // List available hardware/features
        builder.append("\n").append(getTitleText(activity, getString(R.string.features))).append("\n");
        FeatureInfo[] features = pm.getSystemAvailableFeatures();
        List<CharSequence> featureStrings = new ArrayList<>(features.length);
        for (FeatureInfo info : features) {
            if (info.name != null) {
                // It's a feature
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    featureStrings.add(info.name + " (v" + info.version + ")");
                } else featureStrings.add(info.name);
            }
        }
        builder.append(TextUtils.joinSpannable("\n", featureStrings)).append("\n");
        return builder;
    }

    @NonNull
    private String getVmVersion() {
        String vm = "Dalvik";
        String vmVersion = System.getProperty("java.vm.version");
        if (vmVersion != null && vmVersion.startsWith("2")) {
            vm = "ART";
        }
        return vm;
    }

    @NonNull
    private String getKernel() {
        String kernel = System.getProperty("os.version");
        if (kernel == null) return "";
        else return kernel;
    }

    @SuppressLint("PrivateApi")
    private double getBatteryCapacity() {
        double capacity = -1.0;
        try {
            Object powerProfile = Class.forName("com.android.internal.os.PowerProfile")
                    .getConstructor(Context.class).newInstance(activity.getApplication());
            //noinspection ConstantConditions
            capacity = (double) Class.forName("com.android.internal.os.PowerProfile")
                    .getMethod("getAveragePower", String.class)
                    .invoke(powerProfile, "battery.capacity");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return capacity;
    }

    @NonNull
    private Pair<Integer, Integer> getPackageStats(int userHandle) {
        IPackageManager pm = AppManager.getIPackageManager();
        int systemApps = 0;
        int userApps = 0;
        try {
            List<ApplicationInfo> applicationInfoList = pm.getInstalledApplications(0, userHandle).getList();
            for (ApplicationInfo info : applicationInfoList) {
                if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 1) {
                    ++systemApps;
                } else ++userApps;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return new Pair<>(userApps, systemApps);
    }

    @Nullable
    private String getCpuHardware() {
        try (BufferedReader reader = new BufferedReader(new ProxyFileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("Hardware")) {
                    int colonLoc = line.indexOf(':');
                    if (colonLoc == -1) continue;
                    colonLoc += 2;
                    return line.substring(colonLoc).trim();
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    private int getSelinuxStatus() {
        Runner.Result result = Runner.runCommand("getenforce");
        if (result.isSuccessful()) {
            if (result.getOutput().trim().equals("Enforcing")) return 1;
            return 0;
        }
        return 2;
    }

    @NonNull
    private String getEncryptionStatus() {
        String state = SystemProperties.get("ro.crypto.state", "");
        if ("encrypted".equals(state)) {
            String encryptedMsg = getString(R.string.encrypted);
            String type = SystemProperties.get("ro.crypto.type", "");
            if ("file".equals(type)) return encryptedMsg + " (FBE)";
            else if ("block".equals(type)) return encryptedMsg + " (FDE)";
            else return encryptedMsg;
        } else if ("unencrypted".equals(state)) {
            return getString(R.string.unencrypted);
        } else return getString(R.string.state_unknown);
    }

    private String getDensity() {
        int dpi = StaticDataset.DEVICE_DENSITY;
        int smallestDiff = Integer.MAX_VALUE;
        String density = StaticDataset.XXXHDPI;
        // Find the smallest
        for (int i = 0; i < StaticDataset.DENSITY_NAME_TO_DENSITY.size(); ++i) {
            int diff = Math.abs(dpi - StaticDataset.DENSITY_NAME_TO_DENSITY.valueAt(i));
            if (diff < smallestDiff) {
                smallestDiff = diff;
                density = StaticDataset.DENSITY_NAME_TO_DENSITY.keyAt(i);
            }
        }
        return density;
    }

    private boolean isComponentEnabled(ComponentName componentName) {
        int status = pm.getComponentEnabledSetting(componentName);
        return status == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT || status == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }
}
