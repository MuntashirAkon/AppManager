// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import static io.github.muntashirakon.AppManager.logcat.LogViewerActivity.UPDATE_CHECK_INTERVAL;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Filter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper;
import io.github.muntashirakon.AppManager.logcat.struct.LogLine;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.multiselection.MultiSelectionActionsView;

// Copyright 2022 Muntashir Al-Islam
public class LiveLogViewerFragment extends AbsLogViewerFragment implements LogViewerViewModel.LogLinesAvailableInterface,
        MultiSelectionActionsView.OnItemSelectedListener, LogViewerActivity.SearchingInterface, Filter.FilterListener {
    public static final String TAG = LiveLogViewerFragment.class.getSimpleName();

    private int mLogCounter = 0;
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mMultiSelectionView.setOnSelectionChangeListener(selectionCount -> {
            if (selectionCount == 1) {
                mViewModel.pauseLogcat();
            } else if (selectionCount == 0) {
                mViewModel.resumeLogcat();
            }
            return false;
        });
        mViewModel.startLogcat(new WeakReference<>(this));
    }

    @Override
    public void onResume() {
        if (mLogListAdapter != null && mLogListAdapter.getItemCount() > 0) {
            // Scroll to bottom
            // TODO: 31/5/22 Is this really required?
            mRecyclerView.scrollToPosition(mLogListAdapter.getItemCount() - 1);
        }
        if (mActivity.getSupportActionBar() != null) {
            mActivity.getSupportActionBar().setSubtitle("");
        }
        super.onResume();

    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.fragment_live_log_viewer_actions, menu);
    }

    @Override
    public void onPrepareMenu(@NonNull Menu menu) {
        super.onPrepareMenu(menu);

        boolean recordingInProgress = ServiceHelper.checkIfServiceIsRunning(requireContext().getApplicationContext(),
                LogcatRecordingService.class);
        MenuItem recordMenuItem = menu.findItem(R.id.action_record);
        recordMenuItem.setEnabled(!recordingInProgress);
        recordMenuItem.setVisible(!recordingInProgress);

        MenuItem crazyLoggerMenuItem = menu.findItem(R.id.action_crazy_logger_service);
        crazyLoggerMenuItem.setEnabled(BuildConfig.DEBUG);
        crazyLoggerMenuItem.setVisible(BuildConfig.DEBUG);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_play_pause) {
            if (mViewModel.isLogcatPaused()) {
                mViewModel.resumeLogcat();
                item.setIcon(R.drawable.ic_pause);
            } else {
                mViewModel.pauseLogcat();
                item.setIcon(R.drawable.ic_play_arrow);
            }
        } else if (id == R.id.action_clear) {
            if (mLogListAdapter != null) {
                mLogListAdapter.clear();
                UIUtils.displayLongToast(R.string.log_cleared);
            }
        } else if (id == R.id.action_record) {
            mStoragePermission.request(granted -> {
                if (granted) {
                    mActivity.showRecordLogDialog();
                }
            });
        } else if (id == R.id.action_crazy_logger_service) {
            ServiceHelper.startOrStopCrazyLogger(mActivity);
        } else return super.onMenuItemSelected(item);
        return true;
    }

    @Override
    public void onNewLogsAvailable(@NonNull List<LogLine> logLines) {
        mActivity.hideProgressBar();
        for (LogLine logLine : logLines) {
            mLogListAdapter.addWithFilter(logLine, mSearchCriteria, true);
            mActivity.addToAutocompleteSuggestions(logLine);
        }

        // How many logs to keep in memory, to avoid OutOfMemoryError
        int maxNumLogLines = Prefs.LogViewer.getDisplayLimit();

        // Check to see if the list needs to be truncated to avoid OutOfMemoryError
        ++mLogCounter;
        if (mLogCounter % UPDATE_CHECK_INTERVAL == 0 && mLogListAdapter.getRealSize() > maxNumLogLines) {
            int numItemsToRemove = mLogListAdapter.getRealSize() - maxNumLogLines;
            mLogListAdapter.removeFirst(numItemsToRemove);
            Log.d(TAG, "Truncating %d lines from log list to avoid out of memory errors", numItemsToRemove);
        }

        if (mAutoscrollToBottom) {
            mRecyclerView.scrollToPosition(mLogListAdapter.getItemCount() - 1);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_save) {
            displaySaveLogDialog(true);
        } else if (id == R.id.action_copy) {
            ThreadUtils.postOnBackgroundThread(() -> {
                String logs = TextUtils.join("\n", getSelectedLogsAsStrings());
                ThreadUtils.postOnMainThread(() -> Utils.copyToClipboard(ContextUtils.getContext(), "Logs", logs));
            });
        } else if (id == R.id.action_export) {
            displaySaveDebugLogsDialog(false, true);
        } else if (id == R.id.action_share) {
            displaySaveDebugLogsDialog(true, true);
        } else return false;
        return true;
    }
}
