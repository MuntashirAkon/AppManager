// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.Nullable;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.transition.MaterialSharedAxis;

import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logcat.helper.LogcatHelper;
import io.github.muntashirakon.AppManager.logcat.helper.PreferenceHelper;
import io.github.muntashirakon.AppManager.logcat.struct.LogLine;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.preference.TopSwitchPreference;

public class LogViewerPreferences extends PreferenceFragment {
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
        boolean isLogViewerEnabled = FeatureController.isLogViewerEnabled();
        PreferenceCategory catAppearance = requirePreference("cat_appearance");
        PreferenceCategory catConf = requirePreference("cat_conf");
        PreferenceCategory catAdvanced = requirePreference("cat_advanced");
        TopSwitchPreference useLogViewer = requirePreference("use_log_viewer");
        useLogViewer.setChecked(isLogViewerEnabled);
        useLogViewer.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean isEnabled = (boolean) newValue;
            enablePrefs(isEnabled, catAppearance, catAdvanced, catConf);
            FeatureController.getInstance().modifyState(FeatureController.FEAT_LOG_VIEWER, isEnabled);
            return true;
        });
        enablePrefs(isLogViewerEnabled, catAppearance, catAdvanced, catConf);

        SwitchPreferenceCompat expandByDefault = requirePreference("log_viewer_expand_by_default");
        expandByDefault.setChecked(Prefs.LogViewer.expandByDefault());

        SwitchPreferenceCompat showPidTidTimestamp = requirePreference("log_viewer_show_pid_tid_timestamp");
        showPidTidTimestamp.setChecked(Prefs.LogViewer.showPidTidTimestamp());

        SwitchPreferenceCompat omitSensitiveInfo = requirePreference("log_viewer_omit_sensitive_info");
        omitSensitiveInfo.setChecked(Prefs.LogViewer.omitSensitiveInfo());
        omitSensitiveInfo.setOnPreferenceChangeListener((preference, newValue) -> {
            LogLine.omitSensitiveInfo = (boolean) newValue;
            return true;
        });

        Preference filterPattern = requirePreference("log_viewer_filter_pattern");
        filterPattern.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(activity, null)
                    .setTitle(R.string.pref_filter_pattern_title)
                    .setInputText(Prefs.LogViewer.getFilterPattern())
                    .setInputTypeface(Typeface.MONOSPACE)
                    .setInputImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING)
                    .setPositiveButton(R.string.save, (dialog, which, inputText, isChecked) -> {
                        if (inputText == null) return;
                        Prefs.LogViewer.setFilterPattern(inputText.toString().trim());
                        UIUtils.displayLongToast(R.string.restart_log_viewer_to_see_changes);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.reset_to_default, (dialog, which, inputText, isChecked) -> {
                        Prefs.LogViewer.setFilterPattern(activity.getString(R.string.pref_filter_pattern_default));
                        UIUtils.displayLongToast(R.string.restart_log_viewer_to_see_changes);
                    })
                    .show();
            return true;
        });

        Preference displayLimit = requirePreference("log_viewer_display_limit");
        displayLimit.setSummary(getString(R.string.pref_display_limit_summary, Prefs.LogViewer.getDisplayLimit()));
        displayLimit.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(activity, null)
                    .setTitle(R.string.pref_display_limit_title)
                    .setHelperText(getString(R.string.pref_display_limit_hint, MIN_DISPLAY_LIMIT, MAX_DISPLAY_LIMIT))
                    .setInputText(String.valueOf(Prefs.LogViewer.getDisplayLimit()))
                    .setInputInputType(InputType.TYPE_CLASS_NUMBER)
                    .setInputImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING)
                    .setPositiveButton(R.string.save, (dialog, which, inputText, isChecked) -> {
                        if (inputText == null) return;
                        try {
                            int displayLimitInt = Integer.parseInt(inputText.toString().trim());
                            if (displayLimitInt >= MIN_DISPLAY_LIMIT && displayLimitInt <= MAX_DISPLAY_LIMIT) {
                                Prefs.LogViewer.setDisplayLimit(displayLimitInt);
                                UIUtils.displayLongToast(R.string.restart_log_viewer_to_see_changes);
                                displayLimit.setSummary(getString(R.string.pref_display_limit_summary, displayLimitInt));
                            }
                        } catch (NumberFormatException ignore) {
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.reset_to_default, (dialog, which, inputText, isChecked) -> {
                        Prefs.LogViewer.setDisplayLimit(LogcatHelper.DEFAULT_DISPLAY_LIMIT);
                        UIUtils.displayLongToast(R.string.restart_log_viewer_to_see_changes);
                        displayLimit.setSummary(getString(R.string.pref_display_limit_summary,
                                Prefs.LogViewer.getDisplayLimit()));
                    })
                    .show();
            return true;
        });

        Preference writePeriod = requirePreference("log_viewer_write_period");
        writePeriod.setSummary(getString(R.string.pref_log_write_period_summary, Prefs.LogViewer.getLogWritingInterval()));
        writePeriod.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(activity, null)
                    .setTitle(R.string.pref_log_write_period_title)
                    .setHelperText(getString(R.string.pref_log_line_period_error))
                    .setInputText(String.valueOf(Prefs.LogViewer.getLogWritingInterval()))
                    .setInputInputType(InputType.TYPE_CLASS_NUMBER)
                    .setInputImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING)
                    .setPositiveButton(R.string.save, (dialog, which, inputText, isChecked) -> {
                        if (inputText == null) return;
                        try {
                            int writePeriodInt = Integer.parseInt(inputText.toString().trim());
                            if (writePeriodInt >= MIN_LOG_WRITE_PERIOD && writePeriodInt <= MAX_LOG_WRITE_PERIOD) {
                                Prefs.LogViewer.setLogWritingInterval(writePeriodInt);
                                UIUtils.displayLongToast(R.string.restart_log_viewer_to_see_changes);
                                writePeriod.setSummary(getString(R.string.pref_log_write_period_summary, writePeriodInt));
                            }
                        } catch (NumberFormatException ignore) {
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.reset_to_default, (dialog, which, inputText, isChecked) -> {
                        Prefs.LogViewer.setLogWritingInterval(LogcatHelper.DEFAULT_LOG_WRITE_INTERVAL);
                        UIUtils.displayLongToast(R.string.restart_log_viewer_to_see_changes);
                        writePeriod.setSummary(getString(R.string.pref_log_write_period_summary,
                                Prefs.LogViewer.getLogWritingInterval()));
                    })
                    .show();
            return true;
        });

        Preference logLevel = requirePreference("log_viewer_default_log_level");
        logLevel.setOnPreferenceClickListener(preference -> {
            CharSequence[] logLevelsLocalised = getResources().getStringArray(R.array.log_levels);
            new SearchableSingleChoiceDialogBuilder<>(activity, LOG_LEVEL_VALUES, logLevelsLocalised)
                    .setTitle(R.string.pref_default_log_level_title)
                    .setSelection(Prefs.LogViewer.getLogLevel())
                    .setPositiveButton(R.string.save, (dialog, which, newLogLevel) -> {
                        if (newLogLevel != null) {
                            Prefs.LogViewer.setLogLevel(newLogLevel);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.reset_to_default, (dialog, which, newLogLevel) -> Prefs.LogViewer.setLogLevel(Log.VERBOSE))
                    .show();
            return true;
        });

        Preference logBuffers = requirePreference("log_viewer_buffer");
        logBuffers.setOnPreferenceClickListener(preference -> {
            new SearchableMultiChoiceDialogBuilder<>(activity, LOG_BUFFERS, LOG_BUFFER_NAMES)
                    .setTitle(R.string.pref_buffer_title)
                    .addSelections(PreferenceHelper.getBuffers())
                    .setPositiveButton(R.string.save, (dialog, which, selectedItems) -> {
                        if (selectedItems.isEmpty()) return;
                        int bufferFlags = 0;
                        for (int flag : selectedItems) {
                            bufferFlags |= flag;
                        }
                        int previousFlags = Prefs.LogViewer.getBuffers();
                        Prefs.LogViewer.setBuffers(bufferFlags);
                        if (previousFlags != bufferFlags) {
                            sendBufferChanged(activity);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.reset_to_default, (dialog, which, selectedItems) -> {
                        int previousFlags = Prefs.LogViewer.getBuffers();
                        Prefs.LogViewer.setBuffers(LogcatHelper.LOG_ID_DEFAULT);
                        if (previousFlags != LogcatHelper.LOG_ID_DEFAULT) {
                            sendBufferChanged(activity);
                        }
                    })
                    .show();
            return true;
        });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, true));
        setReturnTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, false));
    }

    public static void sendBufferChanged(FragmentActivity activity) {
        Intent intent = new Intent().putExtra("bufferChanged", true);
        activity.setResult(Activity.RESULT_FIRST_USER, intent);
    }

    @Override
    public int getTitle() {
        return R.string.log_viewer;
    }
}