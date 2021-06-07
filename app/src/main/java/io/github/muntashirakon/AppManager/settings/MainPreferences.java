// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.collection.ArrayMap;
import androidx.core.app.ActivityCompat;
import androidx.core.os.LocaleListCompat;
import androidx.core.text.HtmlCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.util.TextUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.yariksoffice.lingver.Lingver;

import java.io.BufferedReader;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.StaticDataset;
import io.github.muntashirakon.AppManager.misc.SystemProperties;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.settings.crypto.ImportExportKeyStoreDialogFragment;
import io.github.muntashirakon.AppManager.types.FullscreenDialog;
import io.github.muntashirakon.AppManager.types.ScrollableDialogBuilder;
import io.github.muntashirakon.AppManager.types.TextInputDialogBuilder;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.io.ProxyFileReader;

public class MainPreferences extends PreferenceFragmentCompat {
    private static final List<Integer> THEME_CONST = Arrays.asList(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES);
    private static final List<Integer> LAYOUT_ORIENTATION_CONST = Arrays.asList(
            View.LAYOUT_DIRECTION_LOCALE,
            View.LAYOUT_DIRECTION_LTR,
            View.LAYOUT_DIRECTION_RTL);
    private static final List<String> MODE_NAMES = Arrays.asList(
            Runner.MODE_AUTO,
            Runner.MODE_ROOT,
            Runner.MODE_ADB_OVER_TCP,
            Runner.MODE_ADB_WIFI,
            Runner.MODE_NO_ROOT);

    SettingsActivity activity;
    private int currentTheme;
    private int currentLayoutOrientation;
    private String currentLang;
    @Runner.Mode
    private String currentMode;
    private MainPreferencesViewModel model;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        model = new ViewModelProvider(this).get(MainPreferencesViewModel.class);
        activity = (SettingsActivity) requireActivity();
        // Custom locale
        currentLang = AppPref.getString(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR);
        ArrayMap<String, Locale> locales = LangUtils.getAppLanguages(activity);
        final CharSequence[] languages = getLanguagesL(locales);
        Preference locale = Objects.requireNonNull(findPreference("custom_locale"));
        locale.setSummary(languages[locales.indexOfKey(currentLang)]);
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
        currentTheme = AppPref.getInt(AppPref.PrefKey.PREF_APP_THEME_INT);
        Preference appTheme = Objects.requireNonNull(findPreference("app_theme"));
        appTheme.setSummary(themes[THEME_CONST.indexOf(currentTheme)]);
        appTheme.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.select_theme)
                    .setSingleChoiceItems(themes, THEME_CONST.indexOf(currentTheme),
                            (dialog, which) -> currentTheme = THEME_CONST.get(which))
                    .setPositiveButton(R.string.apply, (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_APP_THEME_INT, currentTheme);
                        AppCompatDelegate.setDefaultNightMode(currentTheme);
                        appTheme.setSummary(themes[THEME_CONST.indexOf(currentTheme)]);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Layout orientation
        final String[] layoutOrientations = getResources().getStringArray(R.array.layout_orientations);
        currentLayoutOrientation = AppPref.getInt(AppPref.PrefKey.PREF_LAYOUT_ORIENTATION_INT);
        Preference layoutOrientation = Objects.requireNonNull(findPreference("layout_orientation"));
        layoutOrientation.setSummary(layoutOrientations[LAYOUT_ORIENTATION_CONST.indexOf(currentLayoutOrientation)]);
        layoutOrientation.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.pref_layout_orientation)
                    .setSingleChoiceItems(layoutOrientations, LAYOUT_ORIENTATION_CONST.indexOf(currentLayoutOrientation),
                            (dialog, which) -> currentLayoutOrientation = LAYOUT_ORIENTATION_CONST.get(which))
                    .setPositiveButton(R.string.apply, (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_LAYOUT_ORIENTATION_INT, currentLayoutOrientation);
                        ActivityCompat.recreate(activity);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Screen lock
        SwitchPreferenceCompat screenLock = Objects.requireNonNull(findPreference("enable_screen_lock"));
        screenLock.setChecked(AppPref.getBoolean(AppPref.PrefKey.PREF_ENABLE_SCREEN_LOCK_BOOL));
        // Mode of operation
        Preference mode = Objects.requireNonNull(findPreference("mode_of_operations"));
        final String[] modes = getResources().getStringArray(R.array.modes);
        currentMode = AppPref.getString(AppPref.PrefKey.PREF_MODE_OF_OPS_STR);
        // Backward compatibility for v2.6.0
        if (currentMode.equals("adb")) currentMode = Runner.MODE_ADB_OVER_TCP;
        mode.setSummary(modes[MODE_NAMES.indexOf(currentMode)]);
        mode.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.pref_mode_of_operations)
                    .setSingleChoiceItems(modes, MODE_NAMES.indexOf(currentMode), (dialog, which) -> {
                        String modeName = MODE_NAMES.get(which);
                        if (Runner.MODE_ADB_WIFI.equals(modeName)) {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                UIUtils.displayShortToast(R.string.wireless_debugging_not_supported);
                                return;
                            }
                        } else {
                            ServerConfig.setAdbPort(ServerConfig.DEFAULT_ADB_PORT);
                            LocalServer.updateConfig();
                        }
                        currentMode = modeName;
                    })
                    .setPositiveButton(R.string.apply, (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_MODE_OF_OPS_STR, currentMode);
                        mode.setSummary(modes[MODE_NAMES.indexOf(currentMode)]);
                        new Thread(() -> RunnerUtils.setModeOfOps(activity, true)).start();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Enable/disable features
        FeatureController fc = FeatureController.getInstance();
        ((Preference) Objects.requireNonNull(findPreference("enabled_features")))
                .setOnPreferenceClickListener(preference -> {
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.enable_disable_features)
                            .setMultiChoiceItems(FeatureController.getFormattedFlagNames(activity), fc.flagsToCheckedItems(),
                                    (dialog, index, isChecked) -> fc.modifyState(FeatureController.featureFlags.get(index), isChecked))
                            .setNegativeButton(R.string.close, null)
                            .show();
                    return true;
                });
        // Import/export App Manager's KeyStore
        ((Preference) Objects.requireNonNull(findPreference("import_export_keystore")))
                .setOnPreferenceClickListener(preference -> {
                    DialogFragment fragment = new ImportExportKeyStoreDialogFragment();
                    fragment.show(getParentFragmentManager(), ImportExportKeyStoreDialogFragment.TAG);
                    return true;
                });
        // About device
        ((Preference) Objects.requireNonNull(findPreference("about_device")))
                .setOnPreferenceClickListener(preference -> {
                    new Thread(() -> model.loadDeviceInfo(getDisplay())).start();
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
        ((Preference) Objects.requireNonNull(findPreference("changelog")))
                .setOnPreferenceClickListener(preference -> {
                    model.loadChangeLog();
                    return true;
                });

        // Preference loaders
        // Changelog
        model.getChangeLog().observe(this, changeLog -> new ScrollableDialogBuilder(activity, changeLog)
                .linkifyAll()
                .setTitle(R.string.changelog)
                .setNegativeButton(R.string.ok, null)
                .show());
        // Device info
        model.getDeviceInfo().observe(this, deviceInfo -> {
            @SuppressLint("InflateParams")
            View view = getLayoutInflater().inflate(R.layout.dialog_scrollable_text_view, null);
            ((TextView) view.findViewById(android.R.id.content)).setText(deviceInfo);
            view.findViewById(android.R.id.checkbox).setVisibility(View.GONE);
            new FullscreenDialog(activity).setTitle(R.string.about_device).setView(view).show();
        });

        // Hide preferences for disabled features
        if (!FeatureController.isInstallerEnabled()) {
            ((Preference) Objects.requireNonNull(findPreference("installer"))).setVisible(false);
        }
        if (!FeatureController.isLogViewerEnabled()) {
            ((Preference) Objects.requireNonNull(findPreference("log_viewer_prefs"))).setVisible(false);
        }
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

    @UiThread
    public static void displayAdbConnect(FragmentActivity activity, CountDownLatch waitForConfig) {
        AlertDialog alertDialog = new TextInputDialogBuilder(activity, R.string.port_number)
                .setTitle(R.string.wireless_debugging)
                .setInputText(String.valueOf(ServerConfig.getAdbPort()))
                .setHelperText(R.string.adb_connect_port_number_description)
                .setPositiveButton(R.string.ok, (dialog2, which2, inputText, isChecked) -> {
                    if (TextUtils.isEmpty(inputText)) {
                        UIUtils.displayShortToast(R.string.port_number_empty);
                        waitForConfig.countDown();
                        return;
                    }
                    int port;
                    try {
                        port = Integer.decode(inputText.toString().trim());
                    } catch (NumberFormatException e) {
                        UIUtils.displayShortToast(R.string.port_number_invalid);
                        waitForConfig.countDown();
                        return;
                    }
                    ServerConfig.setAdbPort(port);
                    LocalServer.updateConfig();
                    waitForConfig.countDown();
                })
                .setNegativeButton(R.string.cancel, (dialog, which, inputText, isChecked) -> waitForConfig.countDown())
                .create();
        alertDialog.setCancelable(false);
        alertDialog.show();
    }

    private Display getDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return activity.getDisplay();
        } else {
            //noinspection deprecation
            return activity.getWindowManager().getDefaultDisplay();
        }
    }

    public static class MainPreferencesViewModel extends AndroidViewModel {
        @SuppressLint("StaticFieldLeak")
        private final Context ctx;

        public MainPreferencesViewModel(@NonNull Application application) {
            super(application);
            ctx = application;
        }

        private final MutableLiveData<CharSequence> changeLog = new MutableLiveData<>();

        public LiveData<CharSequence> getChangeLog() {
            return changeLog;
        }

        public void loadChangeLog() {
            new Thread(() -> {
                Spanned spannedChangelog = HtmlCompat.fromHtml(IOUtils.getContentFromAssets(ctx,
                        "changelog.html"), HtmlCompat.FROM_HTML_MODE_COMPACT);
                changeLog.postValue(spannedChangelog);
            }).start();
        }

        private final MutableLiveData<CharSequence> deviceInfo = new MutableLiveData<>();

        public LiveData<CharSequence> getDeviceInfo() {
            return deviceInfo;
        }

        @WorkerThread
        private void loadDeviceInfo(Display display) {
            ActivityManager activityManager = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            PackageManager pm = ctx.getPackageManager();
            SpannableStringBuilder builder = new SpannableStringBuilder();
            // Android platform info
            builder.append(getPrimaryText(R.string.os_version))
                    .append(Build.VERSION.RELEASE).append("\n")
                    .append(getPrimaryText(R.string.bootloader))
                    .append(Build.BOOTLOADER).append(", ")
                    .append(UIUtils.getPrimaryText(ctx, "VM: "))
                    .append(getVmVersion()).append("\n")
                    .append(getPrimaryText(R.string.kernel))
                    .append(getKernel()).append("\n")
                    .append(getPrimaryText(R.string.brand_name))
                    .append(Build.BRAND).append(", ")
                    .append(getPrimaryText(R.string.model))
                    .append(Build.MODEL).append("\n")
                    .append(getPrimaryText(R.string.board_name))
                    .append(Build.BOARD).append(", ")
                    .append(getPrimaryText(R.string.manufacturer))
                    .append(Build.MANUFACTURER).append("\n");
            // SDK
            builder.append("\n").append(getTitleText(R.string.sdk)).append("\n")
                    .append(getPrimaryText(R.string.sdk_max))
                    .append(String.valueOf(Build.VERSION.SDK_INT));
            String minSdk = SystemProperties.get("ro.build.version.min_supported_target_sdk", "");
            if (!TextUtils.isEmpty(minSdk)) {
                builder.append(", ").append(getPrimaryText(R.string.sdk_min)).append(minSdk);
            }
            builder.append("\n");
            // Security
            builder.append("\n").append(getTitleText(R.string.security)).append("\n");
            builder.append(getPrimaryText(R.string.root))
                    .append(String.valueOf(RunnerUtils.isRootAvailable())).append("\n");
            int selinux = getSelinuxStatus();
            if (selinux != 2) {
                builder.append(getPrimaryText(R.string.selinux))
                        .append(getString(selinux == 1 ? R.string.enforcing : R.string.permissive))
                        .append("\n");
            }
            builder.append(getPrimaryText(R.string.encryption))
                    .append(getEncryptionStatus()).append("\n");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                builder.append(getPrimaryText(R.string.patch_level))
                        .append(Build.VERSION.SECURITY_PATCH).append("\n");
            }
            List<CharSequence> securityProviders = new ArrayList<>();
            builder.append(getPrimaryText(R.string.security_providers));
            for (Provider provider : Security.getProviders()) {
                securityProviders.add(provider.getName() + " (v" + provider.getVersion() + ")");
            }
            builder.append(TextUtils.joinSpannable(", ", securityProviders)).append("\n");
            // CPU info
            builder.append("\n").append(getTitleText(R.string.cpu)).append("\n");
            String cpuHardware = getCpuHardware();
            if (cpuHardware != null) {
                builder.append(getPrimaryText(R.string.hardware))
                        .append(cpuHardware).append("\n");
            }
            builder.append(getPrimaryText(R.string.support_architectures))
                    .append(TextUtils.join(", ", Build.SUPPORTED_ABIS)).append("\n")
                    .append(getPrimaryText(R.string.no_of_cores))
                    .append(String.valueOf(Runtime.getRuntime().availableProcessors())).append("\n");
            // GPU info
            builder.append("\n").append(getTitleText(R.string.graphics)).append("\n");
            builder.append(getPrimaryText(R.string.gles_version))
                    .append(Utils.getGlEsVersion(activityManager.getDeviceConfigurationInfo().reqGlEsVersion)).append("\n");
            // TODO(19/12/20): Get vendor name
            // RAM info
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            long memorySize = memoryInfo.totalMem;
            builder.append("\n").append(getTitleText(R.string.memory)).append("\n")
                    .append(Formatter.formatFileSize(ctx, memorySize)).append("\n");
            // Battery info
            builder.append("\n").append(getTitleText(R.string.battery)).append("\n");
            builder.append(getPrimaryText(R.string.battery_capacity))
                    .append(String.valueOf(getBatteryCapacity())).append("mAh").append("\n");
            // TODO(19/12/20): Get more battery info
            // Screen resolution
            builder.append("\n").append(getTitleText(R.string.screen)).append("\n")
                    .append(getPrimaryText(R.string.density))
                    .append(getDensity()).append(" (")
                    .append(String.valueOf(StaticDataset.DEVICE_DENSITY)).append(" DPI)").append("\n");
            DisplayMetrics displayMetrics = new DisplayMetrics();
            // Actual size
            display.getRealMetrics(displayMetrics);
            builder.append(getPrimaryText(R.string.scaling_factor))
                    .append(String.valueOf(displayMetrics.density)).append("\n")
                    .append(getPrimaryText(R.string.size))
                    .append(String.valueOf(displayMetrics.widthPixels)).append("px × ")
                    .append(String.valueOf(displayMetrics.heightPixels)).append("px\n");
            // Window size
            display.getMetrics(displayMetrics);
            builder.append(getPrimaryText(R.string.window_size))
                    .append(String.valueOf(displayMetrics.widthPixels)).append("px × ")
                    .append(String.valueOf(displayMetrics.heightPixels)).append("px\n");
            // Refresh rate
            builder.append(getPrimaryText(R.string.refresh_rate))
                    .append(String.format(Locale.ROOT, "%.1f", display.getRefreshRate()))
                    .append("\n");
            // List system locales
            LocaleListCompat locales = LocaleListCompat.getDefault();
            List<String> localeStrings = new ArrayList<>(locales.size());
            for (int i = 0; i < locales.size(); ++i) {
                localeStrings.add(locales.get(i).getDisplayName());
            }
            builder.append("\n").append(getTitleText(R.string.languages))
                    .append("\n").append(TextUtils.joinSpannable(", ", localeStrings))
                    .append("\n");
            List<UserInfo> users = Users.getUsers();
            if (users != null) {
                // Users
                builder.append("\n").append(getTitleText(R.string.users))
                        .append("\n");
                List<String> userNames = new ArrayList<>();
                for (UserInfo user : users) {
                    userNames.add(user.name);
                }
                builder.append(String.valueOf(users.size())).append(" (")
                        .append(TextUtils.joinSpannable(", ", userNames))
                        .append(")\n");
                // App stats per user
                builder.append("\n").append(getTitleText(R.string.apps)).append("\n");
                for (UserInfo user : users) {
                    Pair<Integer, Integer> packageSizes = getPackageStats(user.id);
                    if (packageSizes.first + packageSizes.second == 0) continue;
                    builder.append(getPrimaryText(R.string.user))
                            .append(user.name).append("\n   ")
                            .append(getPrimaryText(R.string.total_size))
                            .append(String.valueOf(packageSizes.first + packageSizes.second)).append(", ")
                            .append(getPrimaryText(R.string.user))
                            .append(String.valueOf(packageSizes.first)).append(", ")
                            .append(getPrimaryText(R.string.system))
                            .append(String.valueOf(packageSizes.second)).append("\n");
                }
            } else {
                builder.append("\n").append(getTitleText(R.string.apps)).append("\n");
                Pair<Integer, Integer> packageSizes = getPackageStats(Users.getCurrentUserHandle());
                builder.append(getPrimaryText(R.string.total_size))
                        .append(String.valueOf(packageSizes.first + packageSizes.second)).append(", ")
                        .append(getPrimaryText(R.string.user))
                        .append(String.valueOf(packageSizes.first)).append(", ")
                        .append(getPrimaryText(R.string.system))
                        .append(String.valueOf(packageSizes.second)).append("\n");
            }
            // List available hardware/features
            builder.append("\n").append(getTitleText(R.string.features)).append("\n");
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

            deviceInfo.postValue(builder);
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
                        .getConstructor(Context.class).newInstance(ctx);
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

        @NonNull
        private CharSequence getTitleText(@StringRes int strRes) {
            return UIUtils.getTitleText(ctx, getString(strRes));
        }

        @NonNull
        private CharSequence getPrimaryText(@StringRes int strRes) {
            return UIUtils.getPrimaryText(ctx, getString(strRes) + ": ");
        }

        private String getString(@StringRes int strRes) {
            return ctx.getString(strRes);
        }
    }
}
