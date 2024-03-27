// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper;
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper;
import io.github.muntashirakon.AppManager.logcat.helper.WidgetHelper;
import io.github.muntashirakon.AppManager.settings.LogViewerPreferences;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDropdownDialogBuilder;

// Copyright 2012 Nolan Lawson
// Copyright 2021 Muntashir Al-Islam
public class RecordLogDialogFragment extends DialogFragment {
    public static final String TAG = RecordLogDialogFragment.class.getSimpleName();

    private static final String QUERY_SUGGESTIONS = "suggestions";

    public interface OnRecordingServiceStartedListenerInterface {
        void onServiceStarted();
    }

    @NonNull
    public static RecordLogDialogFragment getInstance(@Nullable String[] suggestions,
                                                      @Nullable OnRecordingServiceStartedListenerInterface listener) {
        RecordLogDialogFragment dialogFragment = new RecordLogDialogFragment();
        Bundle args = new Bundle();
        args.putStringArray(QUERY_SUGGESTIONS, suggestions);
        dialogFragment.setArguments(args);
        dialogFragment.mListener = listener;
        return dialogFragment;
    }

    private FragmentActivity mActivity;
    private String mFilterQuery;
    private int mLogLevel;
    @Nullable
    private OnRecordingServiceStartedListenerInterface mListener;
    @Nullable
    private DialogInterface.OnDismissListener mDismissListener;

    public void setOnDismissListener(DialogInterface.OnDismissListener dismissListener) {
        mDismissListener = dismissListener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mActivity = requireActivity();
        String[] suggestions = requireArguments().getStringArray(QUERY_SUGGESTIONS);
        String logFilename = SaveLogHelper.createLogFilename();
        mLogLevel = Prefs.LogViewer.getLogLevel();
        mFilterQuery = "";
        return new TextInputDialogBuilder(mActivity, R.string.enter_filename)
                .setTitle(R.string.record_log)
                .setInputText(logFilename)
                .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                    if (SaveLogHelper.isInvalidFilename(inputText)) {
                        Toast.makeText(mActivity, R.string.enter_good_filename, Toast.LENGTH_SHORT).show();
                    } else {
                        //noinspection ConstantConditions
                        String filename = inputText.toString();
                        Context context = mActivity.getApplicationContext();
                        ThreadUtils.postOnBackgroundThread(() -> {
                            Intent intent = ServiceHelper.getLogcatRecorderServiceIfNotAlreadyRunning(context, filename,
                                    mFilterQuery, mLogLevel);
                            ThreadUtils.postOnMainThread(() -> {
                                if (intent != null) {
                                    ContextCompat.startForegroundService(context, intent);
                                }
                                if (mListener != null && !(mActivity.isFinishing() || mActivity.isDestroyed())) {
                                    mListener.onServiceStarted();
                                }
                            });
                        });
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which, inputText, isChecked) ->
                        WidgetHelper.updateWidgets(mActivity))
                .setNeutralButton(R.string.text_filter_ellipsis, null)
                .setOnShowListener(dialog -> {
                    AlertDialog dialog1 = (AlertDialog) dialog;
                    Button filterButton = dialog1.getButton(AlertDialog.BUTTON_NEUTRAL);
                    filterButton.setOnClickListener(v -> {
                        WidgetHelper.updateWidgets(mActivity);
                        showFilterDialogForRecording(suggestions != null ? Arrays.asList(suggestions) : Collections.emptyList());
                    });
                })
                .create();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (mDismissListener != null) {
            mDismissListener.onDismiss(dialog);
        }
    }

    public void showFilterDialogForRecording(List<String> filterQuerySuggestions) {
        List<CharSequence> logLevelsLocalised = Arrays.asList(getResources().getStringArray(R.array.log_levels));
        int idx = LogViewerPreferences.LOG_LEVEL_VALUES.indexOf(mLogLevel);
        TextInputDropdownDialogBuilder builder = new TextInputDropdownDialogBuilder(mActivity, R.string.text_filter_text)
                .setTitle(R.string.filter)
                .setInputText(mFilterQuery)
                .setDropdownItems(filterQuerySuggestions, -1, true)
                .setAuxiliaryInput(getText(R.string.log_level), null, logLevelsLocalised.get(idx),
                        logLevelsLocalised, true)
                .setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
            if (inputText == null || builder.getAuxiliaryInput() == null) return;
            int logLevelIdx = logLevelsLocalised.indexOf(builder.getAuxiliaryInput().toString().trim());
            if (logLevelIdx == -1) return;
            mLogLevel = LogViewerPreferences.LOG_LEVEL_VALUES.get(logLevelIdx);
            mFilterQuery = inputText.toString();
        }).show();
    }
}
