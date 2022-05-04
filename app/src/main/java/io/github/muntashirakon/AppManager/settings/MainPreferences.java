// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spanned;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
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
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.util.TextUtils;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.yariksoffice.lingver.Lingver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.crypto.auth.AuthManagerActivity;
import io.github.muntashirakon.AppManager.misc.DeviceInfo2;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.settings.crypto.ImportExportKeyStoreDialogFragment;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.AlertDialogBuilder;
import io.github.muntashirakon.dialog.ScrollableDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;

public class MainPreferences extends PreferenceFragment {
    @NonNull
    public static MainPreferences getInstance(@Nullable String key) {
        MainPreferences preferences = new MainPreferences();
        Bundle args = new Bundle();
        args.putString(PREF_KEY, key);
        preferences.setArguments(args);
        return preferences;
    }

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
            Ops.MODE_AUTO,
            Ops.MODE_ROOT,
            Ops.MODE_ADB_OVER_TCP,
            Ops.MODE_ADB_WIFI,
            Ops.MODE_NO_ROOT);
    public static final String[] APK_NAME_FORMATS = new String[] {
            "%label%",
            "%package_name%",
            "%version%",
            "%version_code%",
            "%min_sdk%",
            "%target_sdk%",
            "%datetime%"
    };

    private FragmentActivity activity;
    private int currentTheme;
    private int currentLayoutOrientation;
    private String currentLang;
    @Ops.Mode
    private String currentMode;
    private MainPreferencesViewModel model;
    private int threadCount;
    private final ExecutorService executor = Executors.newFixedThreadPool(1);

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        model = new ViewModelProvider(this).get(MainPreferencesViewModel.class);
        activity = requireActivity();
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
        if (currentMode.equals("adb")) currentMode = Ops.MODE_ADB_OVER_TCP;
        mode.setSummary(getString(R.string.mode_of_op_with_inferred_mode_of_op, modes[MODE_NAMES.indexOf(currentMode)],
                getInferredMode()));
        mode.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.pref_mode_of_operations)
                    .setSingleChoiceItems(modes, MODE_NAMES.indexOf(currentMode), (dialog, which) -> {
                        String modeName = MODE_NAMES.get(which);
                        if (Ops.MODE_ADB_WIFI.equals(modeName)) {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                UIUtils.displayShortToast(R.string.wireless_debugging_not_supported);
                                return;
                            }
                        } else {
                            ServerConfig.setAdbPort(ServerConfig.DEFAULT_ADB_PORT);
                        }
                        currentMode = modeName;
                    })
                    .setPositiveButton(R.string.apply, (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_MODE_OF_OPS_STR, currentMode);
                        mode.setSummary(modes[MODE_NAMES.indexOf(currentMode)]);
                        executor.submit(() -> {
                            Ops.init(activity, true);
                            if (isVisible()) {
                                mode.setSummary(getString(R.string.mode_of_op_with_inferred_mode_of_op,
                                        modes[MODE_NAMES.indexOf(currentMode)], getInferredMode()));
                            }
                        });
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
        // Saved apk name format
        Preference savedApkFormatPref = Objects.requireNonNull(findPreference("saved_apk_format"));
        savedApkFormatPref.setOnPreferenceClickListener(preference -> {
            View view = getLayoutInflater().inflate(R.layout.dialog_set_apk_format, null);
            TextInputEditText inputApkNameFormat = view.findViewById(R.id.input_apk_name_format);
            inputApkNameFormat.setText(AppPref.getString(AppPref.PrefKey.PREF_SAVED_APK_FORMAT_STR));
            ChipGroup apkNameFormats = view.findViewById(R.id.apk_name_formats);
            for (String apkNameFormatStr : APK_NAME_FORMATS) {
                if ("%min_sdk%".equals(apkNameFormatStr) && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    // Old devices does not support min SDK
                    continue;
                }
                addChip(apkNameFormats, apkNameFormatStr).setOnClickListener(v -> {
                    Editable apkFormat = inputApkNameFormat.getText();
                    if (apkFormat != null) {
                        apkFormat.insert(inputApkNameFormat.getSelectionStart(), ((Chip) v).getText());
                    }
                });
            }
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.pref_saved_apk_name_format)
                    .setView(view)
                    .setPositiveButton(R.string.save, (dialog, which) -> {
                        Editable apkFormat = inputApkNameFormat.getText();
                        if (!TextUtils.isEmpty(apkFormat)) {
                            AppPref.set(AppPref.PrefKey.PREF_SAVED_APK_FORMAT_STR, apkFormat.toString().trim());
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
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
        // VT API key
        ((Preference) Objects.requireNonNull(findPreference("vt_apikey"))).setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(activity, null)
                    .setTitle(R.string.pref_vt_apikey)
                    .setHelperText(getString(R.string.pref_vt_apikey_description) + "\n\n" + getString(R.string.vt_disclaimer))
                    .setInputText(AppPref.getVtApiKey())
                    .setCheckboxLabel(R.string.pref_vt_prompt_before_uploading)
                    .setChecked(AppPref.getBoolean(AppPref.PrefKey.PREF_VIRUS_TOTAL_PROMPT_BEFORE_UPLOADING_BOOL))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.save, (dialog, which, inputText, isChecked) -> {
                        if (inputText != null) {
                            AppPref.set(AppPref.PrefKey.PREF_VIRUS_TOTAL_API_KEY_STR, inputText.toString());
                        }
                        AppPref.set(AppPref.PrefKey.PREF_VIRUS_TOTAL_PROMPT_BEFORE_UPLOADING_BOOL, isChecked);
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
            ((TextView) view.findViewById(R.id.version)).setText(String.format(Locale.getDefault(),
                    "%s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
            new AlertDialogBuilder(activity, true)
                    .setTitle(R.string.about)
                    .setView(view)
                    .show();
            return true;
        });
        // Changelog
        ((Preference) Objects.requireNonNull(findPreference("changelog")))
                .setOnPreferenceClickListener(preference -> {
                    executor.submit(() -> model.loadChangeLog());
                    return true;
                });
        // Authorization Management
        ((Preference) Objects.requireNonNull(findPreference("auth_manager")))
                .setOnPreferenceClickListener(preference -> {
                    startActivity(new Intent(activity, AuthManagerActivity.class));
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
        model.getChangeLog().observe(this, changeLog -> new ScrollableDialogBuilder(activity, changeLog, true)
                .linkifyAll()
                .setTitle(R.string.changelog)
                .show());
        // Device info
        model.getDeviceInfo().observe(this, deviceInfo -> {
            @SuppressLint("InflateParams")
            View view = getLayoutInflater().inflate(R.layout.dialog_scrollable_text_view, null);
            ((TextView) view.findViewById(android.R.id.content)).setText(deviceInfo.toLocalizedString(activity));
            view.findViewById(android.R.id.checkbox).setVisibility(View.GONE);
            new AlertDialogBuilder(activity, true).setTitle(R.string.about_device).setView(view).show();
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

    @NonNull
    private CharSequence getInferredMode() {
        if (Ops.isRoot()) {
            return getString(R.string.root);
        }
        if (Ops.isAdb()) {
            return "ADB";
        }
        return getString(R.string.no_root);
    }

    @NonNull
    private static Chip addChip(@NonNull ChipGroup apkFormats, @NonNull CharSequence text) {
        Chip chip = new Chip(apkFormats.getContext());
        chip.setText(text);
        apkFormats.addView(chip);
        return chip;
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
