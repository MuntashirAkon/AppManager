// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper;
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper;
import io.github.muntashirakon.AppManager.logcat.helper.WidgetHelper;

import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.LogViewerPreferences;
import io.github.muntashirakon.AppManager.types.TextInputDialogBuilder;
import io.github.muntashirakon.AppManager.types.TextInputDropdownDialogBuilder;
import io.github.muntashirakon.AppManager.utils.AppPref;

// Copyright 2012 Nolan Lawson
public class RecordLogDialogFragment extends DialogFragment {
    public static final String TAG = "RecordLogDialogFragment";

    public static final String QUERY_SUGGESTIONS = "suggestions";

    private FragmentActivity activity;
    private String filterQuery;
    private int logLevel;

    @Override
    public void onResume() {
        super.onResume();
        requireDialog().setCancelable(false);
        requireDialog().setCanceledOnTouchOutside(false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        activity = requireActivity();
        final List<String> suggestions = Arrays.asList(requireArguments().getStringArray(QUERY_SUGGESTIONS));
        String logFilename = SaveLogHelper.createLogFilename();
        logLevel = AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_DEFAULT_LOG_LEVEL_INT);
        filterQuery = "";
        AlertDialog alertDialog = new TextInputDialogBuilder(activity, R.string.enter_filename)
                .setTitle(R.string.record_log)
                .setInputText(logFilename)
                .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                    if (SaveLogHelper.isInvalidFilename(inputText)) {
                        Toast.makeText(activity, R.string.enter_good_filename, Toast.LENGTH_SHORT).show();
                    } else {
                        //noinspection ConstantConditions
                        String filename = inputText.toString();
                        new Thread(() -> ServiceHelper.startBackgroundServiceIfNotAlreadyRunning(activity, filename,
                                filterQuery, logLevel)).start();
                        dialog.dismiss();
                        activity.finish();
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which, inputText, isChecked) -> {
                    WidgetHelper.updateWidgets(activity);
                    dialog.dismiss();
                    activity.finish();
                })
                .setNeutralButton(R.string.text_filter_ellipsis, null)
                .create();
        alertDialog.setOnShowListener(dialog -> {
            AlertDialog dialog1 = (AlertDialog) dialog;
            Button filterButton = dialog1.getButton(AlertDialog.BUTTON_NEUTRAL);
            filterButton.setOnClickListener(v -> {
                WidgetHelper.updateWidgets(activity);
                showFilterDialogForRecording(suggestions);
            });
        });
        return alertDialog;
    }

    public void showFilterDialogForRecording(List<String> filterQuerySuggestions) {
        List<CharSequence> logLevelsLocalised = Arrays.asList(getResources().getStringArray(R.array.log_levels));
        int idx = LogViewerPreferences.LOG_LEVEL_VALUES.indexOf(logLevel);
        TextInputDropdownDialogBuilder builder = new TextInputDropdownDialogBuilder(activity, R.string.text_filter_text)
                .setTitle(R.string.filter)
                .setInputText(filterQuery)
                .setDropdownItems(filterQuerySuggestions, true)
                .setAuxiliaryInput(getText(R.string.log_level), null, logLevelsLocalised.get(idx),
                        logLevelsLocalised, true)
                .setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
            if (inputText == null || builder.getAuxiliaryInput() == null) return;
            int logLevelIdx = logLevelsLocalised.indexOf(builder.getAuxiliaryInput().toString().trim());
            if (logLevelIdx == -1) return;
            logLevel = LogViewerPreferences.LOG_LEVEL_VALUES.get(logLevelIdx);
            filterQuery = inputText.toString();
        }).show();
    }
}
