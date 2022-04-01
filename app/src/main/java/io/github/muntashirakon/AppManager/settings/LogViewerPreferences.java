// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Bundle;
import android.util.Log;

import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logcat.helper.LogcatHelper;
import io.github.muntashirakon.AppManager.logcat.helper.PreferenceHelper;
import io.github.muntashirakon.AppManager.logcat.struct.LogLine;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;

public class LogViewerPreferences extends PreferenceFragmentCompat {
    public static final List<Integer> LOG_LEVEL_VALUES = Arrays.asList(Log.VERBOSE, Log.DEBUG, Log.INFO, Log.WARN,
            Log.ERROR, LogLine.LOG_FATAL);
    public static final List<CharSequence> LOG_BUFFER_NAMES = Arrays.asList(LogcatHelper.BUFFER_MAIN, LogcatHelper.BUFFER_RADIO,
            LogcatHelper.BUFFER_EVENTS, LogcatHelper.BUFFER_SYSTEM, LogcatHelper.BUFFER_CRASH);
    public static final List<Integer> LOG_BUFFERS = Arrays.asList(LogcatHelper.LOG_ID_MAIN, LogcatHelper.LOG_ID_RADIO,
            LogcatHelper.LOG_ID_EVENTS, LogcatHelper.LOG_ID_SYSTEM, LogcatHelper.LOG_ID_CRASH);

    private static final int MAX_LOG_WRITE_PERIOD = 1000;
    private static final int MIN_LOG_WRITE_PERIOD = 1;
    private static final int MAX_DISPLAY_LIMIT = 100000;
    private static final int MIN_DISPLAY_LIMIT = 1000;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_log_viewer);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());

        FragmentActivity activity = requireActivity();

        SwitchPreference expandByDefault = Objects.requireNonNull(findPreference("log_viewer_expand_by_default"));
        expandByDefault.setChecked(AppPref.getBoolean(AppPref.PrefKey.PREF_LOG_VIEWER_EXPAND_BY_DEFAULT_BOOL));

        SwitchPreference showPidTidTimestamp = Objects.requireNonNull(findPreference("log_viewer_show_pid_tid_timestamp"));
        showPidTidTimestamp.setChecked(AppPref.getBoolean(AppPref.PrefKey.PREF_LOG_VIEWER_SHOW_PID_TID_TIMESTAMP_BOOL));

        SwitchPreference omitSensitiveInfo = Objects.requireNonNull(findPreference("log_viewer_omit_sensitive_info"));
        omitSensitiveInfo.setChecked(AppPref.getBoolean(AppPref.PrefKey.PREF_LOG_VIEWER_OMIT_SENSITIVE_INFO_BOOL));
        omitSensitiveInfo.setOnPreferenceChangeListener((preference, newValue) -> {
            LogLine.omitSensitiveInfo = (boolean) newValue;
            return true;
        });

        Preference filterPattern = Objects.requireNonNull(findPreference("log_viewer_filter_pattern"));
        filterPattern.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(activity, R.string.pref_filter_pattern_title)
                    .setTitle(R.string.pref_filter_pattern_title)
                    .setInputText(AppPref.getString(AppPref.PrefKey.PREF_LOG_VIEWER_FILTER_PATTERN_STR))
                    .setPositiveButton(R.string.save, (dialog, which, inputText, isChecked) -> {
                        if (inputText == null) return;
                        AppPref.set(AppPref.PrefKey.PREF_LOG_VIEWER_FILTER_PATTERN_STR, inputText.toString().trim());
                        UIUtils.displayLongToast(R.string.restart_log_viewer_to_see_changes);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.reset_to_default, (dialog, which, inputText, isChecked) -> {
                        AppPref.setDefault(AppPref.PrefKey.PREF_LOG_VIEWER_FILTER_PATTERN_STR);
                        UIUtils.displayLongToast(R.string.restart_log_viewer_to_see_changes);
                    })
                    .show();
            return true;
        });

        Preference displayLimit = Objects.requireNonNull(findPreference("log_viewer_display_limit"));
        displayLimit.setSummary(getString(R.string.pref_display_limit_summary, AppPref.getInt(AppPref.PrefKey
                .PREF_LOG_VIEWER_DISPLAY_LIMIT_INT)));
        displayLimit.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(activity, R.string.pref_display_limit_title)
                    .setTitle(R.string.pref_display_limit_title)
                    .setHelperText(getString(R.string.pref_display_limit_hint, MIN_DISPLAY_LIMIT, MAX_DISPLAY_LIMIT))
                    .setInputText(String.valueOf(AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_DISPLAY_LIMIT_INT)))
                    .setPositiveButton(R.string.save, (dialog, which, inputText, isChecked) -> {
                        if (inputText == null) return;
                        try {
                            int displayLimitInt = Integer.parseInt(inputText.toString().trim());
                            if (displayLimitInt >= MIN_DISPLAY_LIMIT && displayLimitInt <= MAX_DISPLAY_LIMIT) {
                                AppPref.set(AppPref.PrefKey.PREF_LOG_VIEWER_DISPLAY_LIMIT_INT, displayLimitInt);
                                UIUtils.displayLongToast(R.string.restart_log_viewer_to_see_changes);
                                displayLimit.setSummary(getString(R.string.pref_display_limit_summary, displayLimitInt));
                            }
                        } catch (NumberFormatException ignore) {
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.reset_to_default, (dialog, which, inputText, isChecked) -> {
                        AppPref.setDefault(AppPref.PrefKey.PREF_LOG_VIEWER_DISPLAY_LIMIT_INT);
                        UIUtils.displayLongToast(R.string.restart_log_viewer_to_see_changes);
                        displayLimit.setSummary(getString(R.string.pref_display_limit_summary, AppPref
                                .getInt(AppPref.PrefKey.PREF_LOG_VIEWER_DISPLAY_LIMIT_INT)));
                    })
                    .show();
            return true;
        });

        Preference writePeriod = Objects.requireNonNull(findPreference("log_viewer_write_period"));
        writePeriod.setSummary(getString(R.string.pref_log_write_period_summary, AppPref.getInt(AppPref.PrefKey
                .PREF_LOG_VIEWER_WRITE_PERIOD_INT)));
        writePeriod.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(activity, R.string.pref_log_write_period_title)
                    .setTitle(R.string.pref_log_write_period_title)
                    .setHelperText(getString(R.string.pref_log_line_period_error))
                    .setInputText(String.valueOf((int) AppPref.get(AppPref.PrefKey.PREF_LOG_VIEWER_WRITE_PERIOD_INT)))
                    .setPositiveButton(R.string.save, (dialog, which, inputText, isChecked) -> {
                        if (inputText == null) return;
                        try {
                            int writePeriodInt = Integer.parseInt(inputText.toString().trim());
                            if (writePeriodInt >= MIN_LOG_WRITE_PERIOD && writePeriodInt <= MAX_LOG_WRITE_PERIOD) {
                                AppPref.set(AppPref.PrefKey.PREF_LOG_VIEWER_WRITE_PERIOD_INT, writePeriodInt);
                                UIUtils.displayLongToast(R.string.restart_log_viewer_to_see_changes);
                                writePeriod.setSummary(getString(R.string.pref_log_write_period_summary, writePeriodInt));
                            }
                        } catch (NumberFormatException ignore) {
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.reset_to_default, (dialog, which, inputText, isChecked) -> {
                        AppPref.setDefault(AppPref.PrefKey.PREF_LOG_VIEWER_WRITE_PERIOD_INT);
                        UIUtils.displayLongToast(R.string.restart_log_viewer_to_see_changes);
                        writePeriod.setSummary(getString(R.string.pref_log_write_period_summary, AppPref
                                .getInt(AppPref.PrefKey.PREF_LOG_VIEWER_WRITE_PERIOD_INT)));
                    })
                    .show();
            return true;
        });

        Preference logLevel = Objects.requireNonNull(findPreference("log_viewer_default_log_level"));
        logLevel.setOnPreferenceClickListener(preference -> {
            CharSequence[] logLevelsLocalised = getResources().getStringArray(R.array.log_levels);
            int idx = LOG_LEVEL_VALUES.indexOf(AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_DEFAULT_LOG_LEVEL_INT));
            AtomicInteger newIdx = new AtomicInteger(idx);
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.pref_default_log_level_title)
                    .setSingleChoiceItems(logLevelsLocalised, idx, (dialog, which) -> newIdx.set(which))
                    .setPositiveButton(R.string.save, (dialog, which) -> AppPref.set(AppPref.PrefKey
                            .PREF_LOG_VIEWER_DEFAULT_LOG_LEVEL_INT, LOG_LEVEL_VALUES.get(newIdx.get())))
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.reset_to_default, (dialog, which) -> AppPref.setDefault(
                            AppPref.PrefKey.PREF_LOG_VIEWER_DEFAULT_LOG_LEVEL_INT))
                    .show();
            return true;
        });

        Preference logBuffers = Objects.requireNonNull(findPreference("log_viewer_buffer"));
        logBuffers.setOnPreferenceClickListener(preference -> {
            new SearchableMultiChoiceDialogBuilder<>(activity, LOG_BUFFERS, LOG_BUFFER_NAMES)
                    .setTitle(R.string.pref_buffer_title)
                    .addSelections(PreferenceHelper.getBuffers())
                    .setPositiveButton(R.string.save, (dialog, which, selectedItems) -> {
                        if (selectedItems.size() == 0) return;
                        int bufferFlags = 0;
                        for (int flag : selectedItems) {
                            bufferFlags |= flag;
                        }
                        AppPref.set(AppPref.PrefKey.PREF_LOG_VIEWER_BUFFER_INT, bufferFlags);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.reset_to_default, (dialog, which, selectedItems) ->
                            AppPref.setDefault(AppPref.PrefKey.PREF_LOG_VIEWER_BUFFER_INT))
                    .show();
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.log_viewer);
    }
}