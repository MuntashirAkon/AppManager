// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filter.FilterListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.dao.LogFilterDao;
import io.github.muntashirakon.AppManager.db.entity.LogFilter;
import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.logcat.helper.BuildHelper;
import io.github.muntashirakon.AppManager.logcat.helper.PreferenceHelper;
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper;
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper;
import io.github.muntashirakon.AppManager.logcat.struct.LogLine;
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria;
import io.github.muntashirakon.AppManager.logcat.struct.SendLogDetails;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.settings.LogViewerPreferences;
import io.github.muntashirakon.AppManager.settings.SettingsActivity;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.BetterActivityResult;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.AppManager.utils.StoragePermission;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.UiThreadHandler;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDropdownDialogBuilder;
import io.github.muntashirakon.io.Path;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;

import static io.github.muntashirakon.AppManager.logcat.LogViewerRecyclerAdapter.ViewHolder.CONTEXT_MENU_COPY_ID;
import static io.github.muntashirakon.AppManager.logcat.LogViewerRecyclerAdapter.ViewHolder.CONTEXT_MENU_FILTER_ID;

// Copyright 2012 Nolan Lawson
// Copyright 2021 Muntashir Al-Islam
public class LogViewerActivity extends BaseActivity implements FilterListener, SearchView.OnQueryTextListener,
        LogViewerRecyclerAdapter.ViewHolder.OnClickListener, LogViewerViewModel.LogLinesAvailableInterface,
        SearchView.OnSuggestionListener {
    public static final String TAG = LogViewerActivity.class.getSimpleName();

    public static final String ACTION_LAUNCH = BuildConfig.APPLICATION_ID + ".action.LAUNCH";
    public static final String EXTRA_FILTER = "filter";
    public static final String EXTRA_LEVEL = "level";

    // how often to check to see if we've gone over the max size
    private static final int UPDATE_CHECK_INTERVAL = 200;

    // how many suggestions to keep in the autosuggestions text
    private static final int MAX_NUM_SUGGESTIONS = 1000;

    private static final String INTENT_FILENAME = "filename";

    private RecyclerView mRecyclerView;
    private LinearProgressIndicator mProgressIndicator;
    private ExtendedFloatingActionButton mStopRecordingFab;
    private LogViewerRecyclerAdapter mLogListAdapter;

    private String mSearchingString;

    private boolean mAutoscrollToBottom = true;

    private boolean mDynamicallyEnteringSearchText;
    private boolean mPartialSelectMode;
    private final List<LogLine> mPartiallySelectedLogLines = new ArrayList<>(2);

    private final Set<String> mSearchSuggestionsSet = new HashSet<>();
    private CursorAdapter mSearchSuggestionsAdapter;

    private String mCurrentlyOpenLog = null;

    private Handler mHandler;
    private SearchView mSearchView;
    private LogViewerViewModel mViewModel;

    private int mCounter = 0;

    private final MultithreadedExecutor executor = MultithreadedExecutor.getNewInstance();
    private final BetterActivityResult<Intent, ActivityResult> activityLauncher =
            BetterActivityResult.registerActivityForResult(this);
    private final StoragePermission storagePermission = StoragePermission.init(this);
    private final BetterActivityResult<String, Uri> saveLauncher = BetterActivityResult
            .registerForActivityResult(this, new ActivityResultContracts.CreateDocument());

    public static void startChooser(Context context, String subject, String body,
                                    SendLogDetails.AttachmentType attachmentType, Path attachment) {
        Intent actionSendIntent = new Intent(Intent.ACTION_SEND);

        actionSendIntent.setType(attachmentType.getMimeType());
        actionSendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        if (!body.isEmpty()) {
            actionSendIntent.putExtra(Intent.EXTRA_TEXT, body);
        }
        if (attachment != null) {
            actionSendIntent.putExtra(Intent.EXTRA_STREAM, FmProvider.getContentUri(attachment))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        try {
            context.startActivity(Intent.createChooser(actionSendIntent, context.getResources().getText(R.string.send_log_title))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_logcat);
        setSupportActionBar(findViewById(R.id.toolbar));
        mViewModel = new ViewModelProvider(this).get(LogViewerViewModel.class);
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mStopRecordingFab = findViewById(R.id.fab);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            mSearchView = UIUtils.setupSearchView(actionBar, this);
            mSearchView.setOnSuggestionListener(this);
        }

        mSearchSuggestionsAdapter = new SimpleCursorAdapter(this, R.layout.item_checked_text_view, null,
                new String[]{"suggestion"}, new int[]{android.R.id.text1},
                CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
        mSearchView.setSuggestionsAdapter(mSearchSuggestionsAdapter);

        // Set removal of sensitive info
        LogLine.omitSensitiveInfo = AppPref.getBoolean(AppPref.PrefKey.PREF_LOG_VIEWER_OMIT_SENSITIVE_INFO_BOOL);

        storagePermission.request(granted -> {
            if (granted) {
                handleShortcuts(getIntent().getStringExtra("shortcut_action"));
            }
        });

        mHandler = new Handler(Looper.getMainLooper());

        mStopRecordingFab.setOnClickListener(v -> {
            // Stop recording
            ServiceHelper.stopBackgroundServiceIfRunning(LogViewerActivity.this);
        });

        mRecyclerView = findViewById(R.id.list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setItemAnimator(null);
        new FastScrollerBuilder(mRecyclerView).useMd2Style().build();

        // Set collapsed mode
        mViewModel.setCollapsedMode(!AppPref.getBoolean(AppPref.PrefKey.PREF_LOG_VIEWER_EXPAND_BY_DEFAULT_BOOL));

        setUpLogViewerAdapter();
        // Grant read logs permission if not already
        mViewModel.grantReadLogsPermission();
        // It doesn't matter whether the permission has been granted or not, we can start logging
        mViewModel.setLogLinesAvailableInterface(this);
        mViewModel.observeLoggingFinished().observe(this, finished -> {
            if (finished) {
                mProgressIndicator.hide();
                if (mViewModel.isLogcatPaused()) {
                    mViewModel.resumeLogcat();
                }
                if (mOnFinishedRunnable != null) {
                    mOnFinishedRunnable.run();
                }
            }
        });
        mViewModel.observeLoadingProgress().observe(this, percentage ->
                mProgressIndicator.setProgressCompat(percentage, true));
        mViewModel.observeTruncatedLines().observe(this, maxDisplayedLines -> UIUtils.displayLongToast(
                getResources().getQuantityString(R.plurals.toast_log_truncated, maxDisplayedLines, maxDisplayedLines)));

        startLog();
    }

    private void handleShortcuts(String action) {
        if (action == null) return;
        if ("record".equals(action)) {
            String logFilename = SaveLogHelper.createLogFilename();
            executor.submit(() -> {
                // Start recording logs
                ServiceHelper.startBackgroundServiceIfNotAlreadyRunning(this, logFilename, "",
                        AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_DEFAULT_LOG_LEVEL_INT));
                runOnUiThread(this::finish);
            });
        }
    }

    @WorkerThread
    private void addFiltersToSuggestions() {
        for (LogFilter logFilter : AppManager.getAppsDb().logFilterDao().getAll()) {
            addToAutocompleteSuggestions(logFilter.name);
        }
    }

    private void startLog() {
        String filename = getIntent().getStringExtra(INTENT_FILENAME);
        if (filename == null) {
            startMainLog();
        } else {
            openLogFile(filename);
        }
        doAfterInitialMessage(getIntent());
    }

    private void doAfterInitialMessage(Intent intent) {
        // Handle an intent that was sent from an external application
        if (intent != null && ACTION_LAUNCH.equals(intent.getAction())) {
            String filter = intent.getStringExtra(EXTRA_FILTER);
            String level = intent.getStringExtra(EXTRA_LEVEL);
            if (!TextUtils.isEmpty(filter)) {
                setSearchText(filter);
            }
            if (!TextUtils.isEmpty(level)) {
                int logLevelLimit = LogLine.convertCharToLogLevel(level.charAt(0));
                if (logLevelLimit == -1) {
                    String invalidLevel = getString(R.string.toast_invalid_level, level);
                    Toast.makeText(this, invalidLevel, Toast.LENGTH_LONG).show();
                } else {
                    mLogListAdapter.setLogLevelLimit(logLevelLimit);
                    logLevelChanged();
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mLogListAdapter != null && mLogListAdapter.getItemCount() > 0) {
            // Scroll to bottom, since for some reason it always scrolls to the top, which is annoying
            scrollToBottom();
        }
        if (mStopRecordingFab != null) {
            boolean recordingInProgress = ServiceHelper.checkIfServiceIsRunning(getApplicationContext(), LogcatRecordingService.class);
            mStopRecordingFab.setVisibility(recordingInProgress ? View.VISIBLE : View.GONE);
        }
    }

    private void restartMainLog() {
        mLogListAdapter.clear();
        startMainLog();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        doAfterInitialMessage(intent);
        // Launched from the widget or notification
        if (intent != null && !ACTION_LAUNCH.equals(intent.getAction()) && intent.hasExtra(INTENT_FILENAME)) {
            String filename = intent.getStringExtra(INTENT_FILENAME);
            openLogFile(filename);
        }
    }

    private void startMainLog() {
        Runnable mainLogRunnable = () -> {
            // PreExecute begin
            resetDisplayedLog(null);

            mProgressIndicator.hide();
            mProgressIndicator.setIndeterminate(true);
            mProgressIndicator.show();
            // PreExecute end
            if (mLogListAdapter != null) {
                mLogListAdapter.clear();
            }
            mViewModel.startLogcat();
        };

        if (!mViewModel.isLogcatKilled()) {
            // Restart
            // Do only after current log is depleted, to avoid splicing the streams together
            mViewModel.resumeLogcat();
            setOnFinished(mainLogRunnable);
            mViewModel.killLogcatReader();
        } else {
            // No main log currently running; just start up the main log now
            mainLogRunnable.run();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        cancelPartialSelect();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void populateSuggestionsAdapter(String query) {
        final MatrixCursor c = new MatrixCursor(new String[]{BaseColumns._ID, "suggestion"});
        List<String> suggestionsForQuery = getSuggestionsForQuery(query);
        for (int i = 0, suggestionsForQuerySize = suggestionsForQuery.size(); i < suggestionsForQuerySize; i++) {
            String suggestion = suggestionsForQuery.get(i);
            c.addRow(new Object[]{i, suggestion});
        }
        mSearchSuggestionsAdapter.changeCursor(c);
    }

    private List<String> getSuggestionsForQuery(String query) {
        List<String> suggestions = new ArrayList<>(mSearchSuggestionsSet);
        Collections.sort(suggestions, String.CASE_INSENSITIVE_ORDER);
        List<String> actualSuggestions = new ArrayList<>();
        if (query != null) {
            for (String suggestion : suggestions) {
                if (suggestion.toLowerCase(Locale.getDefault()).startsWith(query.toLowerCase(Locale.getDefault()))) {
                    actualSuggestions.add(suggestion);
                }
            }
        }
        return actualSuggestions;
    }

    @Override
    public void onBackPressed() {
        if (!mSearchView.isIconified()) {
            mSearchView.setIconified(true);
        } else if (mCurrentlyOpenLog != null) {
            startMainLog();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean showingMainLog = !mViewModel.isLogcatKilled();
        MenuItem item = menu.findItem(R.id.menu_expand_all);
        if (mViewModel.isCollapsedMode()) {
            item.setIcon(R.drawable.ic_expand_more_white_24dp);
            item.setTitle(R.string.expand_all);
        } else {
            item.setIcon(R.drawable.ic_expand_less_white_24dp);
            item.setTitle(R.string.collapse_all);
        }

        MenuItem clear = menu.findItem(R.id.menu_clear);
        MenuItem pause = menu.findItem(R.id.menu_play_pause);
        clear.setVisible(mCurrentlyOpenLog == null);
        pause.setVisible(mCurrentlyOpenLog == null);

        MenuItem saveLogMenuItem = menu.findItem(R.id.menu_save_log);
        MenuItem saveAsLogMenuItem = menu.findItem(R.id.menu_save_as_log);

        saveLogMenuItem.setEnabled(showingMainLog);
        saveLogMenuItem.setVisible(showingMainLog);

        saveAsLogMenuItem.setEnabled(!showingMainLog);
        saveAsLogMenuItem.setVisible(!showingMainLog);

        boolean recordingInProgress = ServiceHelper.checkIfServiceIsRunning(getApplicationContext(), LogcatRecordingService.class);

        MenuItem recordMenuItem = menu.findItem(R.id.menu_record_log);

        recordMenuItem.setEnabled(!recordingInProgress);
        recordMenuItem.setVisible(!recordingInProgress);

        MenuItem crazyLoggerMenuItem = menu.findItem(R.id.menu_crazy_logger_service);
        crazyLoggerMenuItem.setEnabled(BuildConfig.DEBUG);
        crazyLoggerMenuItem.setVisible(BuildConfig.DEBUG);

        MenuItem partialSelectMenuItem = menu.findItem(R.id.menu_partial_select);
        partialSelectMenuItem.setEnabled(!mPartialSelectMode);
        partialSelectMenuItem.setVisible(!mPartialSelectMode);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_logcat_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_play_pause) {
            pauseOrResume(item);
            return true;
        } else if (itemId == R.id.menu_expand_all) {
            expandOrCollapse();
            return true;
        } else if (itemId == R.id.menu_clear) {
            if (mLogListAdapter != null) {
                mLogListAdapter.clear();
            }
            Snackbar.make(mRecyclerView, R.string.log_cleared, Snackbar.LENGTH_LONG)
                    .setAction(R.string.undo, v -> startMainLog())
                    .show();
            return true;
        } else if (itemId == R.id.menu_log_level) {
            CharSequence[] logLevelsLocalised = getResources().getStringArray(R.array.log_levels);
            int currentLogLevelIdx = LogViewerPreferences.LOG_LEVEL_VALUES.indexOf(mLogListAdapter.getLogLevelLimit());
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.log_level)
                    .setSingleChoiceItems(logLevelsLocalised, currentLogLevelIdx, (dialog, which) -> {
                        mLogListAdapter.setLogLevelLimit(LogViewerPreferences.LOG_LEVEL_VALUES.get(which));
                        logLevelChanged();
                    })
                    .setNegativeButton(R.string.close, null)
                    .show();
            return true;
        } else if (itemId == R.id.menu_open_log) {
            storagePermission.request(granted -> {
                if (granted) {
                    displayOpenLogFileDialog();
                }
            });
            return true;
        } else if (itemId == R.id.menu_save_log || itemId == R.id.menu_save_as_log) {
            storagePermission.request(granted -> {
                if (granted) displaySaveLogDialog();
            });
            return true;
        } else if (itemId == R.id.menu_record_log) {
            storagePermission.request(granted -> {
                if (granted) {
                    showRecordLogDialog();
                }
            });
            return true;
        } else if (itemId == R.id.menu_send_log_zip) {
            storagePermission.request(granted -> {
                if (granted) displaySaveDebugLogsDialog(true);
            });
            return true;
        } else if (itemId == R.id.menu_save_log_zip) {
            storagePermission.request(granted -> {
                if (granted) displaySaveDebugLogsDialog(false);
            });
            return true;
        } else if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (itemId == R.id.menu_delete_saved_log) {
            storagePermission.request(granted -> {
                if (granted) displayDeleteSavedLogsDialog();
            });
            return true;
        } else if (itemId == R.id.menu_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra(SettingsActivity.EXTRA_KEY, "log_viewer_prefs");
            activityLauncher.launch(intent, result -> {
                // Preferences may have changed
                mViewModel.setCollapsedMode(!AppPref.getBoolean(AppPref.PrefKey.PREF_LOG_VIEWER_EXPAND_BY_DEFAULT_BOOL));
                if (result.getResultCode() == RESULT_FIRST_USER) {
                    Intent data = result.getData();
                    if (data != null && data.getBooleanExtra("bufferChanged", false)
                            && mCurrentlyOpenLog == null) {
                        // Log buffer changed, so update list
                        restartMainLog();
                    }
                }
                mLogListAdapter.notifyDataSetChanged();
                updateUiForFilename();
            });
            return true;
        } else if (itemId == R.id.menu_crazy_logger_service) {
            ServiceHelper.startOrStopCrazyLogger(this);
            return true;
        } else if (itemId == R.id.menu_partial_select) {
            startPartialSelectMode();
            return true;
        } else if (itemId == R.id.menu_filters) {
            showFiltersDialog();
            return true;
        }
        return false;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item, LogLine logLine) {
        if (logLine != null) {
            switch (item.getItemId()) {
                case CONTEXT_MENU_COPY_ID:
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText(null, logLine.getOriginalLine()));
                    Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
                    return true;
                case CONTEXT_MENU_FILTER_ID:
                    if (logLine.getProcessId() == -1) {
                        // invalid line
                        return false;
                    }
                    showSearchByDialog(logLine);
                    return true;
            }
        }
        return false;
    }

    @Override
    public void onClick(final View itemView, final LogLine logLine) {
        if (mPartialSelectMode) {
            logLine.setHighlighted(true);
            mPartiallySelectedLogLines.add(logLine);

            mLogListAdapter.notifyItemChanged(mRecyclerView.getChildAdapterPosition(itemView));

            if (mPartiallySelectedLogLines.size() == 2) {
                storagePermission.request(granted -> {
                    if (granted) completePartialSelect();
                });
            }
        } else {
            logLine.setExpanded(!logLine.isExpanded());
            mLogListAdapter.notifyItemChanged(mRecyclerView.getChildAdapterPosition(itemView));
        }
    }

    @Override
    public void onNewLogsAvailable(@NonNull List<LogLine> newLogLines) {
        if (!mViewModel.isLogcatKilled()) {
            mProgressIndicator.hide();
            for (LogLine logLine : newLogLines) {
                mLogListAdapter.addWithFilter(logLine, mSearchingString, false);
                addToAutocompleteSuggestions(logLine);
            }
            mLogListAdapter.notifyDataSetChanged();

            // How many logs to keep in memory, to avoid OutOfMemoryError
            int maxNumLogLines = AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_DISPLAY_LIMIT_INT);

            // Check to see if the list needs to be truncated to avoid OutOfMemoryError
            ++mCounter;
            if (mCounter % UPDATE_CHECK_INTERVAL == 0 && mLogListAdapter.getRealSize() > maxNumLogLines) {
                int numItemsToRemove = mLogListAdapter.getRealSize() - maxNumLogLines;
                mLogListAdapter.removeFirst(numItemsToRemove);
                Log.d(TAG, "Truncating " + numItemsToRemove + " lines from log list to avoid out of memory errors");
            }

            if (mAutoscrollToBottom) {
                scrollToBottom();
            }
        } else {
            // File mode
            mProgressIndicator.hide();

            for (LogLine logLine : newLogLines) {
                mLogListAdapter.addWithFilter(logLine, "", false);
                addToAutocompleteSuggestions(logLine);
            }
            mLogListAdapter.notifyDataSetChanged();

            // Scroll to bottom
            scrollToBottom();
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (!mDynamicallyEnteringSearchText) {
            search(newText);
            populateSuggestionsAdapter(newText);
        }
        mDynamicallyEnteringSearchText = false;
        return false;
    }

    @Override
    public boolean onSuggestionSelect(int position) {
        return false;
    }

    @Override
    public boolean onSuggestionClick(int position) {
        List<String> suggestions = getSuggestionsForQuery(mSearchingString);
        if (!suggestions.isEmpty()) {
            mSearchView.setQuery(suggestions.get(position), true);
        }
        return false;
    }

    private void showSearchByDialog(final LogLine logLine) {
        View view = getLayoutInflater().inflate(R.layout.dialog_searchby, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.filter_choice)
                .setIcon(R.drawable.ic_search)
                .setView(view)
                .setNegativeButton(R.string.close, null)
                .show();

        TextInputLayout tag = view.findViewById(R.id.search_by_tag);
        TextInputLayout pid = view.findViewById(R.id.search_by_pid);

        TextView tagText = tag.getEditText();
        TextView pidText = pid.getEditText();

        Objects.requireNonNull(tagText).setText(logLine.getTag());
        Objects.requireNonNull(pidText).setText(String.valueOf(logLine.getProcessId()));

        tag.setEndIconOnClickListener(v -> {
            String tagQuery = (logLine.getTag().contains(" ")) ? ('"' + logLine.getTag() + '"') : logLine.getTag();
            setSearchText(SearchCriteria.TAG_KEYWORD + tagQuery);
            dialog.dismiss();
        });

        pid.setEndIconOnClickListener(v -> {
            setSearchText(SearchCriteria.PID_KEYWORD + logLine.getProcessId());
            dialog.dismiss();
        });
    }

    private void showRecordLogDialog() {
        String[] suggestions = mSearchSuggestionsSet.toArray(new String[0]);
        DialogFragment dialog = RecordLogDialogFragment.getInstance(suggestions, () -> {
            if (mStopRecordingFab != null) {
                mStopRecordingFab.setVisibility(View.VISIBLE);
            }
        });
        dialog.show(getSupportFragmentManager(), RecordLogDialogFragment.TAG);
    }

    private void showFiltersDialog() {
        executor.submit(() -> {
            final List<LogFilter> filters = AppManager.getAppsDb().logFilterDao().getAll();
            Collections.sort(filters);
            mHandler.post(() -> {
                final LogFilterAdapter logFilterAdapter = new LogFilterAdapter(LogViewerActivity.this, filters);
                ListView view = new ListView(LogViewerActivity.this);
                view.setAdapter(logFilterAdapter);
                view.setDivider(null);
                view.setDividerHeight(0);
                View footer = getLayoutInflater().inflate(R.layout.header_logcat_add_filter, view, false);
                view.addFooterView(footer);

                footer.setOnClickListener(v -> new TextInputDropdownDialogBuilder(this, R.string.text_filter_text)
                        .setTitle(R.string.add_filter)
                        .setDropdownItems(new ArrayList<>(mSearchSuggestionsSet), -1, true)
                        .setPositiveButton(android.R.string.ok, (dialog1, which, inputText, isChecked) ->
                                handleNewFilterText(inputText == null ? "" : inputText.toString(), logFilterAdapter))
                        .setNegativeButton(R.string.cancel, null)
                        .show());

                AlertDialog alertDialog = new MaterialAlertDialogBuilder(LogViewerActivity.this)
                        .setTitle(R.string.saved_filters)
                        .setView(view)
                        .setNegativeButton(R.string.ok, null)
                        .show();

                logFilterAdapter.setOnItemClickListener((parent, view1, position, logFilter) -> {
                    setSearchText(logFilter.name);
                    alertDialog.dismiss();
                });
            });
        });
    }

    protected void handleNewFilterText(String text, final LogFilterAdapter logFilterAdapter) {
        final String trimmed = text.trim();
        if (!TextUtils.isEmpty(trimmed)) {
            executor.submit(() -> {
                LogFilterDao dao = AppManager.getAppsDb().logFilterDao();
                long id = dao.insert(trimmed);
                LogFilter logFilter = dao.get(id);
                mHandler.post(() -> {
                    if (logFilter != null) {
                        logFilterAdapter.add(logFilter);
                        logFilterAdapter.sort(LogFilter.COMPARATOR);
                        logFilterAdapter.notifyDataSetChanged();

                        addToAutocompleteSuggestions(trimmed);
                    }
                });
            });
        }
    }

    private void startPartialSelectMode() {
        boolean hideHelp = PreferenceHelper.getHidePartialSelectHelpPreference(this);

        if (hideHelp) {
            mPartialSelectMode = true;
            mPartiallySelectedLogLines.clear();
            Toast.makeText(this, R.string.toast_started_select_partial, Toast.LENGTH_SHORT).show();
        } else {
            View helpView = View.inflate(this, R.layout.dialog_partial_save_help, null);
            CheckBox checkBox = helpView.findViewById(android.R.id.checkbox);

            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.menu_title_partial_select)
                    .setView(helpView)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        mPartialSelectMode = true;
                        mPartiallySelectedLogLines.clear();
                        Toast.makeText(LogViewerActivity.this, R.string.toast_started_select_partial, Toast.LENGTH_SHORT).show();
                        if (checkBox.isChecked()) {
                            // hide this help dialog in the future
                            PreferenceHelper.setHidePartialSelectHelpPreference(LogViewerActivity.this, true);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }

    private void expandOrCollapse() {
        int oldFirstVisibleItem = ((LinearLayoutManager) mRecyclerView.getLayoutManager()).findFirstVisibleItemPosition();
        mViewModel.setCollapsedMode(!mViewModel.isCollapsedMode());
        mLogListAdapter.setCollapseMode(mViewModel.isCollapsedMode());
        mLogListAdapter.notifyDataSetChanged();
        // Ensure that we either stay autoscrolling at the bottom of the list...
        if (mAutoscrollToBottom) {
            scrollToBottom();
            // ... or that whatever was the previous first visible item is still the current first
            // visible item after expanding/collapsing
        } else if (oldFirstVisibleItem != -1) {
            mRecyclerView.scrollToPosition(oldFirstVisibleItem);
        }
        supportInvalidateOptionsMenu();
    }

    private void displayDeleteSavedLogsDialog() {
        List<Path> logFiles = SaveLogHelper.getLogFiles();
        if (logFiles.isEmpty()) {
            Toast.makeText(this, R.string.no_saved_logs, Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence[] filenameArray = SaveLogHelper.getFormattedFilenames(this, logFiles);
        new SearchableMultiChoiceDialogBuilder<>(this, logFiles, filenameArray)
                .setTitle(R.string.manage_saved_logs)
                .setPositiveButton(R.string.delete, (dialog, which, selectedFiles) -> {
                    final int deleteCount = selectedFiles.size();
                    if (deleteCount == 0) return;
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.delete_saved_log)
                            .setCancelable(true)
                            .setMessage(getResources().getQuantityString(R.plurals.file_deletion_confirmation,
                                    deleteCount, deleteCount))
                            .setPositiveButton(android.R.string.ok, (dialog1, which1) -> {
                                for (Path selectedFile : selectedFiles) {
                                    SaveLogHelper.deleteLogIfExists(selectedFile.getName());
                                }
                                UIUtils.displayShortToast(R.string.deleted_successfully);
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void displaySaveDebugLogsDialog(boolean share) {
        View includeDeviceInfoView = getLayoutInflater().inflate(R.layout.dialog_send_log, null, false);
        final CheckBox includeDeviceInfoCheckBox = includeDeviceInfoView.findViewById(android.R.id.checkbox);

        // allow user to choose whether or not to include device info in report, use preferences for persistence
        includeDeviceInfoCheckBox.setChecked(PreferenceHelper.getIncludeDeviceInfoPreference(this));
        includeDeviceInfoCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> PreferenceHelper.setIncludeDeviceInfoPreference(LogViewerActivity.this, isChecked));

        final CheckBox includeDmesgCheckBox = includeDeviceInfoView.findViewById(R.id.checkbox_dmesg);

        // allow user to choose whether or not to include device info in report, use preferences for persistence
        includeDmesgCheckBox.setChecked(PreferenceHelper.getIncludeDmesgPreference(this));
        includeDmesgCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> PreferenceHelper.setIncludeDmesgPreference(LogViewerActivity.this, isChecked));

        new MaterialAlertDialogBuilder(LogViewerActivity.this)
                .setTitle(share ? R.string.share : R.string.save_log_zip)
                .setView(includeDeviceInfoView)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    boolean includeDeviceInfo = includeDeviceInfoCheckBox.isChecked();
                    boolean includeDmesg = includeDmesgCheckBox.isChecked();
                    if (share) {
                        sendLogToTargetApp(includeDeviceInfo, includeDmesg);
                    } else {
                        saveLogToTargetApp(includeDeviceInfo, includeDmesg);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    protected void sendLogToTargetApp(final boolean includeDeviceInfo, final boolean includeDmesg) {
        AlertDialog dialog;
        if (mCurrentlyOpenLog == null || includeDeviceInfo || includeDmesg) {
            dialog = UIUtils.getProgressDialog(LogViewerActivity.this, R.string.dialog_compiling_log);
            dialog.setCanceledOnTouchOutside(false);
            dialog.setCancelable(false);
        } else dialog = null;
        executor.submit(() -> {
            SendLogDetails sendLogDetails = new SendLogDetails();
            List<Path> files = saveLogDetails(includeDeviceInfo, includeDmesg);
            sendLogDetails.setBody("");
            sendLogDetails.setSubject(getString(R.string.subject_log_report));
            // either zip up multiple files or just attach the one file
            switch (files.size()) {
                case 0: // no attachments
                    sendLogDetails.setAttachmentType(SendLogDetails.AttachmentType.None);
                    break;
                case 1: // one plaintext file attachment
                    sendLogDetails.setAttachmentType(SendLogDetails.AttachmentType.Text);
                    sendLogDetails.setAttachment(files.get(0));
                    break;
                default: // 2 files - need to zip them up
                    try {
                        Path zipFile = SaveLogHelper.saveTemporaryZipFile(SaveLogHelper.createZipFilename(true), files);
                        sendLogDetails.setSubject(zipFile.getName());
                        sendLogDetails.setAttachmentType(SendLogDetails.AttachmentType.Zip);
                        sendLogDetails.setAttachment(zipFile);
                    } catch (Exception e) {
                        Log.e(TAG, e);
                        runOnUiThread(() -> UIUtils.displayLongToast(R.string.failed));
                        return;
                    }
                    break;
            }
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                startChooser(LogViewerActivity.this, sendLogDetails.getSubject(), sendLogDetails.getBody(),
                        sendLogDetails.getAttachmentType(), sendLogDetails.getAttachment());
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }
            });
        });

    }

    @UiThread
    private void saveLogToTargetApp(final boolean includeDeviceInfo, final boolean includeDmesg) {
        AlertDialog dialog;
        if (mCurrentlyOpenLog == null || includeDeviceInfo || includeDmesg) {
            dialog = UIUtils.getProgressDialog(LogViewerActivity.this, R.string.dialog_compiling_log);
            dialog.setCanceledOnTouchOutside(false);
            dialog.setCancelable(false);
        } else dialog = null;
        executor.submit(() -> {
            List<Path> files = saveLogDetails(includeDeviceInfo, includeDmesg);
            if (isDestroyed()) return;
            runOnUiThread(() -> {
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }
                saveLauncher.launch(SaveLogHelper.createZipFilename(true), uri -> {
                    if (uri == null) return;
                    executor.submit(() -> {
                        try {
                            SaveLogHelper.saveZipFileAndThrow(this, uri, files);
                            UiThreadHandler.run(() -> UIUtils.displayShortToast(R.string.saved_successfully));
                        } catch (IOException e) {
                            UiThreadHandler.run(() -> UIUtils.displayShortToast(R.string.saving_failed));
                        }
                    });
                });
            });
        });

    }

    @NonNull
    @WorkerThread
    private List<Path> saveLogDetails(boolean includeDeviceInfo, boolean includeDmesg) {
        List<Path> files = new ArrayList<>();
        SaveLogHelper.cleanTemp();

        if (mCurrentlyOpenLog != null) { // Use saved log file
            try {
                files.add(SaveLogHelper.getFile(mCurrentlyOpenLog));
            } catch (IOException e) {
                Log.e(TAG, e);
            }
        } else { // Create a temp file to hold the current, unsaved log
            Path tempLogFile = SaveLogHelper.saveTemporaryFile(SaveLogHelper.TEMP_LOG_FILENAME, null,
                    getCurrentLogAsListOfStrings());
            files.add(tempLogFile);
        }

        if (includeDeviceInfo) {
            String deviceInfo = BuildHelper.getBuildInformationAsString();
            Path tempFile = SaveLogHelper.saveTemporaryFile(SaveLogHelper.TEMP_DEVICE_INFO_FILENAME, deviceInfo, null);
            files.add(tempFile);
        }

        if (includeDmesg) {
            Path tempDmsgFile = SaveLogHelper.saveTemporaryFile(SaveLogHelper.TEMP_DMESG_FILENAME, null,
                    Runner.runCommand("dmesg").getOutputAsList());
            files.add(tempDmsgFile);
        }
        return files;
    }

    @NonNull
    private List<String> getCurrentLogAsListOfStrings() {
        List<String> result = new ArrayList<>(mLogListAdapter.getItemCount());
        for (int i = 0; i < mLogListAdapter.getItemCount(); i++) {
            result.add(mLogListAdapter.getItem(i).getOriginalLine());
        }
        return result;
    }

    private void displaySaveLogDialog() {
        new TextInputDialogBuilder(this, R.string.filename)
                .setTitle(R.string.save_log)
                .setInputText(SaveLogHelper.createLogFilename())
                .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                    if (SaveLogHelper.isInvalidFilename(inputText)) {
                        Toast.makeText(LogViewerActivity.this, R.string.enter_good_filename, Toast.LENGTH_SHORT).show();
                    } else {
                        @SuppressWarnings("ConstantConditions")
                        String filename = inputText.toString();
                        saveLog(filename);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void savePartialLog(final String filename, LogLine first, LogLine last) {
        final List<String> logLines = new ArrayList<>(mLogListAdapter.getItemCount());

        // Filter based on first and last
        boolean started = false;
        boolean foundLast = false;
        for (int i = 0; i < mLogListAdapter.getItemCount(); i++) {
            LogLine logLine = mLogListAdapter.getItem(i);
            if (logLine == first) {
                started = true;
            }
            if (started) {
                logLines.add(logLine.getOriginalLine());
            }
            if (logLine == last) {
                foundLast = true;
                break;
            }
        }

        if (!foundLast || logLines.isEmpty()) {
            Toast.makeText(this, R.string.toast_invalid_selection, Toast.LENGTH_LONG).show();
            cancelPartialSelect();
            return;
        }

        executor.submit(() -> {
            SaveLogHelper.deleteLogIfExists(filename);
            final boolean saved = SaveLogHelper.saveLog(logLines, filename);
            UiThreadHandler.run(() -> {
                if (saved) {
                    UIUtils.displayShortToast(R.string.saved_successfully);
                    openLogFile(filename);
                } else {
                    UIUtils.displayLongToast(R.string.unable_to_save_log);
                }
                cancelPartialSelect();
            });
        });
    }

    private void saveLog(final String filename) {
        final List<String> logLines = getCurrentLogAsListOfStrings();

        executor.submit(() -> {
            SaveLogHelper.deleteLogIfExists(filename);
            final boolean saved = SaveLogHelper.saveLog(logLines, filename);
            UiThreadHandler.run(() -> {
                if (saved) {
                    UIUtils.displayShortToast(R.string.log_saved);
                    openLogFile(filename);
                } else {
                    UIUtils.displayLongToast(R.string.unable_to_save_log);
                }
            });
        });

    }

    private void displayOpenLogFileDialog() {
        List<Path> logFiles = SaveLogHelper.getLogFiles();
        if (logFiles.isEmpty()) {
            Toast.makeText(this, R.string.no_saved_logs, Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.open)
                .setItems(SaveLogHelper.getFormattedFilenames(this, logFiles), (dialog, which) ->
                        openLogFile(logFiles.get(which).getName()))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void openLogFile(final String filename) {
        Runnable openLogfileRunnable = () -> {
            resetDisplayedLog(filename);

            mProgressIndicator.hide();
            mProgressIndicator.setIndeterminate(false);
            mProgressIndicator.show();

            mViewModel.openLogsFromFile(filename);
        };

        // if the main log task is running, we can only run AFTER it's been canceled
        if (!mViewModel.isLogcatKilled()) {
            setOnFinished(openLogfileRunnable);
            mViewModel.resumeLogcat();
            mViewModel.killLogcatReader();
        } else {
            // main log not running; just open in this thread
            openLogfileRunnable.run();
        }
    }

    public void resetDisplayedLog(String filename) {
        mLogListAdapter.clear();
        mCurrentlyOpenLog = filename;
        mViewModel.setCollapsedMode(!AppPref.getBoolean(AppPref.PrefKey.PREF_LOG_VIEWER_EXPAND_BY_DEFAULT_BOOL));
        // Populate suggestions with existing filters (if any)
        executor.submit(this::addFiltersToSuggestions);
        updateUiForFilename();
        resetFilter();
    }

    private void updateUiForFilename() {
        boolean logFileMode = mCurrentlyOpenLog != null;

        //noinspection ConstantConditions
        getSupportActionBar().setSubtitle(logFileMode ? mCurrentlyOpenLog : "");
        getSupportActionBar().setDisplayHomeAsUpEnabled(logFileMode);
        supportInvalidateOptionsMenu();
    }

    private void resetFilter() {
        mLogListAdapter.setLogLevelLimit(AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_DEFAULT_LOG_LEVEL_INT));
        logLevelChanged();
    }

    private void setUpLogViewerAdapter() {
        mLogListAdapter = new LogViewerRecyclerAdapter();
        mLogListAdapter.setClickListener(this);
        mRecyclerView.setAdapter(mLogListAdapter);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                // Update what the first viewable item is
                final LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null) {
                    // Stop autoscroll if the bottom of the list isn't visible anymore
                    mAutoscrollToBottom = layoutManager.findLastCompletelyVisibleItemPosition() ==
                            (mLogListAdapter.getItemCount() - 1);
                }
            }
        });
        mRecyclerView.setHasFixedSize(true);
    }

    private void completePartialSelect() {
        new TextInputDialogBuilder(this, R.string.filename)
                .setTitle(R.string.save_log)
                .setInputText(SaveLogHelper.createLogFilename())
                .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                    if (SaveLogHelper.isInvalidFilename(inputText)) {
                        cancelPartialSelect();
                        Toast.makeText(LogViewerActivity.this, R.string.enter_good_filename, Toast.LENGTH_SHORT).show();
                    } else {
                        @SuppressWarnings("ConstantConditions")
                        String filename = inputText.toString();
                        if (mPartiallySelectedLogLines.size() == 2)
                            savePartialLog(filename, mPartiallySelectedLogLines.get(0), mPartiallySelectedLogLines.get(1));
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which, inputText, isChecked) -> cancelPartialSelect())
                .show();
    }

    private void cancelPartialSelect() {
        mPartialSelectMode = false;
        boolean changed = false;
        for (LogLine logLine : mPartiallySelectedLogLines) {
            if (logLine.isHighlighted()) {
                logLine.setHighlighted(false);
                changed = true;
            }
        }
        mPartiallySelectedLogLines.clear();
        if (changed) {
            mHandler.post(mLogListAdapter::notifyDataSetChanged);
        }
    }

    private void setSearchText(String text) {
        // Sets the search text without invoking autosuggestions, which are really only useful when typing
        mDynamicallyEnteringSearchText = true;
        search(text);
        mSearchView.setIconified(false);
        mSearchView.setQuery(mSearchingString, true);
        mSearchView.clearFocus();
    }

    private void search(String filterText) {
        Filter filter = mLogListAdapter.getFilter();
        filter.filter(filterText, this);
        mSearchingString = filterText;
        if (!TextUtils.isEmpty(mSearchingString)) {
            mDynamicallyEnteringSearchText = true;
        }
    }

    private void pauseOrResume(MenuItem item) {
        if (mViewModel.isLogcatPaused()) {
            mViewModel.resumeLogcat();
            item.setIcon(R.drawable.ic_pause_white_24dp);
        } else {
            mViewModel.pauseLogcat();
            item.setIcon(R.drawable.ic_play_arrow_white_24dp);
        }
    }

    @Override
    public void onFilterComplete(int count) {
        // always scroll to the bottom when searching
        mRecyclerView.scrollToPosition(count - 1);
    }

    private void logLevelChanged() {
        search(mSearchingString);
    }

    private void addToAutocompleteSuggestions(@NonNull LogLine logLine) {
        if (logLine.getTag() == null) return;
        String tag = logLine.getTag().trim();
        if (!TextUtils.isEmpty(tag)) {
            addToAutocompleteSuggestions(tag);
        }
    }

    private void addToAutocompleteSuggestions(String trimmed) {
        if (mSearchSuggestionsSet.size() < MAX_NUM_SUGGESTIONS
                && !mSearchSuggestionsSet.contains(trimmed)) {
            mSearchSuggestionsSet.add(trimmed);
            populateSuggestionsAdapter(mSearchingString);
            //searchSuggestionsAdapter.add(trimmed);
        }
    }

    private void scrollToBottom() {
        mRecyclerView.scrollToPosition(mLogListAdapter.getItemCount() - 1);
    }

    private Runnable mOnFinishedRunnable;

    private void setOnFinished(Runnable onFinished) {
        this.mOnFinishedRunnable = onFinished;
    }
}
