// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Filter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;

import java.lang.ref.WeakReference;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logcat.struct.LogLine;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.multiselection.MultiSelectionActionsView;

// Copyright 2022 Muntashir Al-Islam
public class SavedLogViewerFragment extends AbsLogViewerFragment implements LogViewerViewModel.LogLinesAvailableInterface,
        MultiSelectionActionsView.OnItemSelectedListener, LogViewerActivity.SearchingInterface, Filter.FilterListener {
    public static final String TAG = SavedLogViewerFragment.class.getSimpleName();
    public static final String ARG_FILE_URI = "file_uri";

    @NonNull
    public static SavedLogViewerFragment getInstance(@NonNull Uri uri) {
        SavedLogViewerFragment fragment = new SavedLogViewerFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_FILE_URI, uri);
        fragment.setArguments(args);
        return fragment;
    }

    private String mFilename = "";

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Uri uri = BundleCompat.getParcelable(requireArguments(), ARG_FILE_URI, Uri.class);
        if (uri == null) {
            // TODO: 31/5/22 Handle invalid URI
            return;
        }
        mFilename = uri.getLastPathSegment();
        mViewModel.openLogsFromFile(uri, new WeakReference<>(this));
    }

    @Override
    public void onResume() {
        if (mLogListAdapter != null && mLogListAdapter.getItemCount() > 0) {
            // Scroll to bottom
            // TODO: 31/5/22 Is this really required?
            mRecyclerView.scrollToPosition(mLogListAdapter.getItemCount() - 1);
        }
        if (mActivity.getSupportActionBar() != null) {
            mActivity.getSupportActionBar().setSubtitle(mFilename);
        }
        super.onResume();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_saved_log_viewer_actions, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onNewLogsAvailable(@NonNull List<LogLine> logLines) {
        mActivity.hideProgressBar();
        for (LogLine logLine : logLines) {
            mLogListAdapter.addWithFilter(logLine, "", true);
            mActivity.addToAutocompleteSuggestions(logLine);
        }

        mRecyclerView.scrollToPosition(mLogListAdapter.getItemCount() - 1);
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
