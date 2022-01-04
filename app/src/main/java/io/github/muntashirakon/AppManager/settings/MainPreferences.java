// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.pm.UserInfo;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spanned;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.collection.ArrayMap;
import androidx.core.app.ActivityCompat;
import androidx.core.text.HtmlCompat;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.adb.AdbConnectionManager;
import io.github.muntashirakon.AppManager.misc.DeviceInfo2;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.settings.crypto.ImportExportKeyStoreDialogFragment;
import io.github.muntashirakon.AppManager.types.FullscreenDialog;
import io.github.muntashirakon.AppManager.types.ScrollableDialogBuilder;
import io.github.muntashirakon.AppManager.types.TextInputDialogBuilder;
import io.github.muntashirakon.AppManager.types.TextInputDropdownDialogBuilder;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.UiThreadHandler;
import io.github.muntashirakon.AppManager.utils.Utils;

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
    private int threadCount;
    private final ExecutorService executor = Executors.newFixedThreadPool(1);

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
                        executor.submit(() -> RunnerUtils.setModeOfOps(activity, true));
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        Preference usersPref = Objects.requireNonNull(findPreference("selected_users"));
        usersPref.setOnPreferenceClickListener(preference -> {
            executor.submit(() -> model.loadAllUsers());
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
        // Thread count
        Preference threadCountPref = Objects.requireNonNull(findPreference("thread_count"));
        threadCount = MultithreadedExecutor.getThreadCount();
        threadCountPref.setSummary(getResources().getQuantityString(R.plurals.pref_thread_count_msg, threadCount, threadCount));
        threadCountPref.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(activity, null)
                    .setTitle(R.string.pref_thread_count)
                    .setHelperText(getString(R.string.pref_thread_count_hint, Utils.getTotalCores()))
                    .setInputText(String.valueOf(threadCount))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.save, (dialog, which, inputText, isChecked) -> {
                        if (inputText != null && TextUtils.isDigitsOnly(inputText)) {
                            int c = Integer.decode(inputText.toString());
                            AppPref.set(AppPref.PrefKey.PREF_CONCURRENCY_THREAD_COUNT_INT, c);
                            threadCount = MultithreadedExecutor.getThreadCount();
                            threadCountPref.setSummary(getResources().getQuantityString(R.plurals.pref_thread_count_msg, threadCount, threadCount));
                        }
                    })
                    .show();
            return true;
        });
        // VT APK key
        ((Preference) Objects.requireNonNull(findPreference("vt_apikey"))).setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(activity, null)
                    .setTitle(R.string.pref_vt_apikey)
                    .setHelperText(getString(R.string.pref_vt_apikey_description) + "\n\n" + getString(R.string.vt_disclaimer))
                    .setInputText(AppPref.getVtApiKey())
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.save, (dialog, which, inputText, isChecked) -> {
                        if (inputText != null) {
                            AppPref.set(AppPref.PrefKey.PREF_VIRUS_TOTAL_API_KEY_STR, inputText.toString());
                        }
                    })
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
                    executor.submit(() -> model.loadDeviceInfo(new DeviceInfo2(activity)));
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
                    executor.submit(() -> model.loadChangeLog());
                    return true;
                });

        // Preference loaders
        model.selectUsers().observe(this, users -> {
            if (users == null) return;
            int[] selectedUsers = AppPref.getSelectedUsers();
            int[] userIds = new int[users.size()];
            boolean[] choices = new boolean[users.size()];
            Arrays.fill(choices, false);
            CharSequence[] userInfo = new CharSequence[users.size()];
            for (int i = 0; i < users.size(); ++i) {
                userIds[i] = users.get(i).id;
                userInfo[i] = userIds[i] + " (" + users.get(i).name + ")";
                if (selectedUsers == null || ArrayUtils.contains(selectedUsers, userIds[i])) {
                    choices[i] = true;
                }
            }
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.pref_selected_users)
                    .setMultiChoiceItems(userInfo, choices, (dialog, which, isChecked) -> choices[which] = isChecked)
                    .setPositiveButton(R.string.save, (dialog, which) -> {
                        List<Integer> selectedUserIds = new ArrayList<>(users.size());
                        for (int i = 0; i < choices.length; ++i) {
                            if (choices[i]) {
                                selectedUserIds.add(userIds[i]);
                            }
                        }
                        if (selectedUserIds.size() > 0) {
                            AppPref.setSelectedUsers(ArrayUtils.convertToIntArray(selectedUserIds));
                        } else AppPref.setSelectedUsers(null);
                        Utils.relaunchApp(activity);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.use_default, (dialog, which) -> {
                        AppPref.setSelectedUsers(null);
                        Utils.relaunchApp(activity);
                    })
                    .show();
        });
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
            ((TextView) view.findViewById(android.R.id.content)).setText(deviceInfo.toLocalizedString(activity));
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

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.settings);
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

    @RequiresApi(Build.VERSION_CODES.R)
    @UiThread
    public static void configureWirelessDebugging(FragmentActivity activity, CountDownLatch waitForConfig) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.wireless_debugging)
                .setMessage(R.string.choose_what_to_do)
                .setCancelable(false)
                .setPositiveButton(R.string.adb_connect, (dialog1, which1) -> displayAdbConnect(activity, waitForConfig))
                .setNeutralButton(R.string.adb_pair, (dialog1, which1) -> {
                    TextInputDropdownDialogBuilder builder = new TextInputDropdownDialogBuilder(activity,
                            R.string.adb_wifi_paring_code);
                    builder.setTitle(R.string.wireless_debugging)
                            .setAuxiliaryInput(R.string.port_number, null, null, null, true)
                            .setPositiveButton(R.string.ok, (dialog, which, pairingCode, isChecked) -> {
                                Editable portString = builder.getAuxiliaryInput();
                                if (TextUtils.isEmpty(pairingCode) || TextUtils.isEmpty(portString)) {
                                    UIUtils.displayShortToast(R.string.port_number_pairing_code_empty);
                                    waitForConfig.countDown();
                                    return;
                                }
                                int port;
                                try {
                                    port = Integer.decode(portString.toString().trim());
                                } catch (NumberFormatException e) {
                                    UIUtils.displayShortToast(R.string.port_number_invalid);
                                    waitForConfig.countDown();
                                    return;
                                }
                                new Thread(() -> {
                                    try {
                                        AdbConnectionManager.getInstance().pair(ServerConfig.getAdbHost(), port, pairingCode.toString().trim());
                                        UiThreadHandler.run(() -> {
                                            UIUtils.displayShortToast(R.string.paired_successfully);
                                            if (!activity.isDestroyed()) displayAdbConnect(activity, waitForConfig);
                                            else waitForConfig.countDown();
                                        });
                                    } catch (Exception e) {
                                        UiThreadHandler.run(() -> UIUtils.displayShortToast(R.string.failed));
                                        waitForConfig.countDown();
                                        e.printStackTrace();
                                    }
                                }).start();
                            })
                            .setNegativeButton(R.string.cancel, (dialog, which, inputText, isChecked) -> waitForConfig.countDown());
                    AlertDialog dialog = builder.create();
                    dialog.setCancelable(false);
                    dialog.show();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> waitForConfig.countDown())
                .show();
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

    public static class MainPreferencesViewModel extends AndroidViewModel {
        public MainPreferencesViewModel(@NonNull Application application) {
            super(application);
        }

        private final MutableLiveData<List<UserInfo>> selectUsers = new MutableLiveData<>();

        public LiveData<List<UserInfo>> selectUsers() {
            return selectUsers;
        }

        @WorkerThread
        public void loadAllUsers() {
            selectUsers.postValue(Users.getAllUsers());
        }

        private final MutableLiveData<CharSequence> changeLog = new MutableLiveData<>();

        public LiveData<CharSequence> getChangeLog() {
            return changeLog;
        }

        @WorkerThread
        public void loadChangeLog() {
            Spanned spannedChangelog = HtmlCompat.fromHtml(FileUtils.getContentFromAssets(getApplication(),
                    "changelog.html"), HtmlCompat.FROM_HTML_MODE_COMPACT);
            changeLog.postValue(spannedChangelog);
        }

        private final MutableLiveData<DeviceInfo2> deviceInfo = new MutableLiveData<>();

        public LiveData<DeviceInfo2> getDeviceInfo() {
            return deviceInfo;
        }

        @WorkerThread
        private void loadDeviceInfo(@NonNull DeviceInfo2 di) {
            di.loadInfo();
            deviceInfo.postValue(di);
        }
    }
}
