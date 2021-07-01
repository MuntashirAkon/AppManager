// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.view.LayoutInflater;
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
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.dao.LogFilterDao;
import io.github.muntashirakon.AppManager.db.entity.LogFilter;
import io.github.muntashirakon.AppManager.logcat.helper.BuildHelper;
import io.github.muntashirakon.AppManager.logcat.helper.PreferenceHelper;
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper;
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper;
import io.github.muntashirakon.AppManager.logcat.reader.LogcatReader;
import io.github.muntashirakon.AppManager.logcat.reader.LogcatReaderLoader;
import io.github.muntashirakon.AppManager.logcat.struct.LogLine;
import io.github.muntashirakon.AppManager.logcat.struct.SavedLog;
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria;
import io.github.muntashirakon.AppManager.logcat.struct.SendLogDetails;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.PermissionCompat;
import io.github.muntashirakon.AppManager.settings.LogViewerPreferences;
import io.github.muntashirakon.AppManager.settings.SettingsActivity;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.types.TextInputDialogBuilder;
import io.github.muntashirakon.AppManager.types.TextInputDropdownDialogBuilder;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.BetterActivityResult;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.AppManager.utils.PermissionUtils;
import io.github.muntashirakon.AppManager.utils.StoragePermission;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.UiThreadHandler;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;

import static io.github.muntashirakon.AppManager.logcat.LogViewerRecyclerAdapter.ViewHolder.CONTEXT_MENU_COPY_ID;
import static io.github.muntashirakon.AppManager.logcat.LogViewerRecyclerAdapter.ViewHolder.CONTEXT_MENU_FILTER_ID;

// Copyright 2012 Nolan Lawson
public class LogViewerActivity extends BaseActivity implements FilterListener,
        LogViewerRecyclerAdapter.ViewHolder.OnClickListener {
    public static final String TAG = LogViewerActivity.class.getSimpleName();

    public static final String ACTION_LAUNCH = BuildConfig.APPLICATION_ID + ".action.LAUNCH";
    public static final String EXTRA_FILTER = "filter";
    public static final String EXTRA_LEVEL = "level";

    // how often to check to see if we've gone over the max size
    private static final int UPDATE_CHECK_INTERVAL = 200;

    // how many suggestions to keep in the autosuggestions text
    private static final int MAX_NUM_SUGGESTIONS = 1000;

    private static final String INTENT_FILENAME = "filename";

    private RecyclerView recyclerView;
    private LinearProgressIndicator progressIndicator;
    private FloatingActionButton fab;
    private LogViewerRecyclerAdapter mLogListAdapter;
    private LogReaderAsyncTask mTask;

    private String mSearchingString;

    private boolean mAutoscrollToBottom = true;
    private boolean mCollapsedMode;

    private String mFilterPattern = null;

    private boolean mDynamicallyEnteringSearchText;
    private boolean partialSelectMode;
    private final List<LogLine> partiallySelectedLogLines = new ArrayList<>(2);

    private final Set<String> mSearchSuggestionsSet = new HashSet<>();
    private CursorAdapter mSearchSuggestionsAdapter;

    private String mCurrentlyOpenLog = null;

    private Handler mHandler;
    private SearchView searchView;

    private final MultithreadedExecutor executor = MultithreadedExecutor.getNewInstance();
    private final BetterActivityResult<Intent, ActivityResult> activityLauncher =
            BetterActivityResult.registerActivityForResult(this);
    private final StoragePermission storagePermission = StoragePermission.init(this);
    private final BetterActivityResult<String, Uri> saveLauncher = BetterActivityResult
            .registerForActivityResult(this, new ActivityResultContracts.CreateDocument());

    public static void startChooser(Context context, String subject, String body, SendLogDetails.AttachmentType attachmentType, File attachment) {
        Intent actionSendIntent = new Intent(Intent.ACTION_SEND);

        actionSendIntent.setType(attachmentType.getMimeType());
        actionSendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        if (!body.isEmpty()) {
            actionSendIntent.putExtra(Intent.EXTRA_TEXT, body);
        }
        if (attachment != null) {
            Uri uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", attachment);
            actionSendIntent.putExtra(Intent.EXTRA_STREAM, uri);
        }

        try {
            context.startActivity(Intent.createChooser(actionSendIntent, context.getResources().getText(R.string.send_log_title)));
        } catch (Exception e) {
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_logcat);
        setSupportActionBar(findViewById(R.id.toolbar));
        progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);
        fab = findViewById(R.id.fab);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            searchView = UIUtils.setupSearchView(this, actionBar, new SearchView.OnQueryTextListener() {
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
            });
            searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
                @Override
                public boolean onSuggestionSelect(int position) {
                    return false;
                }

                @Override
                public boolean onSuggestionClick(int position) {
                    List<String> suggestions = getSuggestionsForQuery(mSearchingString);
                    if (!suggestions.isEmpty()) {
                        searchView.setQuery(suggestions.get(position), true);
                    }
                    return false;
                }
            });
        }

        mSearchSuggestionsAdapter = new SimpleCursorAdapter(this, R.layout.item_checked_text_view, null,
                new String[]{"suggestion"}, new int[]{android.R.id.text1},
                CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
        searchView.setSuggestionsAdapter(mSearchSuggestionsAdapter);

        // Set removal of sensitive info
        LogLine.omitSensitiveInfo = AppPref.getBoolean(AppPref.PrefKey.PREF_LOG_VIEWER_OMIT_SENSITIVE_INFO_BOOL);

        storagePermission.request(granted -> {
            if (granted) handleShortcuts(getIntent().getStringExtra("shortcut_action"));
        });

        mHandler = new Handler(Looper.getMainLooper());

        fab.setOnClickListener(v -> {
            // Stop recording
            ServiceHelper.stopBackgroundServiceIfRunning(LogViewerActivity.this);
        });

        recyclerView = findViewById(R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemAnimator(null);
        new FastScrollerBuilder(recyclerView).useMd2Style().build();

        mCollapsedMode = !AppPref.getBoolean(AppPref.PrefKey.PREF_LOG_VIEWER_EXPAND_BY_DEFAULT_BOOL);
        mFilterPattern = AppPref.getString(AppPref.PrefKey.PREF_LOG_VIEWER_FILTER_PATTERN_STR);

        Log.d(TAG, "Initial collapsed mode is " + mCollapsedMode);

        setUpLogViewerAdapter();
        // Grant read logs permission if not already
        if (!PermissionUtils.hasPermission(this, Manifest.permission.READ_LOGS) && LocalServer.isAMServiceAlive()) {
            executor.submit(() -> {
                try {
                    PermissionCompat.grantPermission(getPackageName(), Manifest.permission.READ_LOGS, Users.myUserId());
                } catch (RemoteException e) {
                    Log.d(TAG, e.toString());
                }
            });
        }
        // It doesn't matter whether the permission has been granted or not
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
        for (LogFilter logFilter : AppManager.getDb().logFilterDao().getAll()) {
            addToAutocompleteSuggestions(logFilter.name);
        }
    }

    private void startLog() {
        Intent intent = getIntent();
        if (intent == null || !intent.hasExtra(INTENT_FILENAME)) {
            startMainLog();
        } else {
            String filename = intent.getStringExtra(INTENT_FILENAME);
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
        boolean recordingInProgress = ServiceHelper.checkIfServiceIsRunning(getApplicationContext(), LogcatRecordingService.class);
        if (fab != null) {
            fab.setVisibility(recordingInProgress ? View.VISIBLE : View.GONE);
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
            if (mLogListAdapter != null) {
                mLogListAdapter.clear();
            }
            mTask = new LogReaderAsyncTask();
            mTask.execute((Void) null);
        };

        if (mTask != null) {
            // Do only after current log is depleted, to avoid splicing the streams together
            // (Don't cross the streams!)
            mTask.unpause();
            mTask.setOnFinished(mainLogRunnable);
            mTask.killReader();
            mTask = null;
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
        if (mTask != null) {
            mTask.killReader();
            mTask.cancel(true);
            mTask = null;
        }
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
        if (!searchView.isIconified()) {
            searchView.setIconified(true);
        } else if (mCurrentlyOpenLog != null) {
            startMainLog();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean showingMainLog = (mTask != null);
        MenuItem item = menu.findItem(R.id.menu_expand_all);
        if (mCollapsedMode) {
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
        partialSelectMenuItem.setEnabled(!partialSelectMode);
        partialSelectMenuItem.setVisible(!partialSelectMode);

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
            pauseOrUnpause(item);
            return true;
        } else if (itemId == R.id.menu_expand_all) {
            expandOrCollapseAll(true);
            return true;
        } else if (itemId == R.id.menu_clear) {
            if (mLogListAdapter != null) {
                mLogListAdapter.clear();
            }
            Snackbar.make(findViewById(android.R.id.content), R.string.log_cleared, Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.undo), v -> startMainLog())
                    .setActionTextColor(ContextCompat.getColor(this, R.color.colorAccent))
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
                if (granted) displayOpenLogFileDialog();
            });
            return true;
        } else if (itemId == R.id.menu_save_log || itemId == R.id.menu_save_as_log) {
            storagePermission.request(granted -> {
                if (granted) displaySaveLogDialog();
            });
            return true;
        } else if (itemId == R.id.menu_record_log) {
            storagePermission.request(granted -> {
                if (granted) showRecordLogDialog();
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
            // TODO: 16/4/21 Navigate to the relevant preferences, set result if changes are made
            Intent intent = new Intent(this, SettingsActivity.class);
            activityLauncher.launch(intent, result -> {
                // Preferences may have changed
                mCollapsedMode = !AppPref.getBoolean(AppPref.PrefKey.PREF_LOG_VIEWER_EXPAND_BY_DEFAULT_BOOL);
                if (result.getResultCode() == RESULT_OK) {
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
        if (partialSelectMode) {
            logLine.setHighlighted(true);
            partiallySelectedLogLines.add(logLine);

            mHandler.post(() -> mLogListAdapter.notifyItemChanged(recyclerView.getChildAdapterPosition(itemView)));

            if (partiallySelectedLogLines.size() == 2) {
                storagePermission.request(granted -> {
                    if (granted) completePartialSelect();
                });
            }
        } else {
            logLine.setExpanded(!logLine.isExpanded());
            mLogListAdapter.notifyItemChanged(recyclerView.getChildAdapterPosition(itemView));
        }
    }

    private void showSearchByDialog(final LogLine logLine) {
        View view = getLayoutInflater().inflate(R.layout.dialog_searchby, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.filter_choice)
                .setIcon(R.drawable.ic_search)
                .setView(view)
                .show();

        LinearLayoutCompat tag = view.findViewById(R.id.dialog_searchby_tag_linear);
        LinearLayoutCompat pid = view.findViewById(R.id.dialog_searchby_pid_linear);

        TextView tagText = view.findViewById(R.id.dialog_searchby_tag_text);
        TextView pidText = view.findViewById(R.id.dialog_searchby_pid_text);

        tagText.setText(logLine.getTag());
        pidText.setText(String.valueOf(logLine.getProcessId()));

        tag.setOnClickListener(v -> {
            String tagQuery = (logLine.getTag().contains(" ")) ? ('"' + logLine.getTag() + '"') : logLine.getTag();
            setSearchText(SearchCriteria.TAG_KEYWORD + tagQuery);
            dialog.dismiss();
            //TODO: put the cursor at the end
            /*searchEditText.setSelection(searchEditText.length());*/
        });

        pid.setOnClickListener(v -> {
            setSearchText(SearchCriteria.PID_KEYWORD + logLine.getProcessId());
            dialog.dismiss();
            //TODO: put the cursor at the end
            /*searchEditText.setSelection(searchEditText.length());*/
        });
    }

    private void showRecordLogDialog() {
        // start up the dialog-like activity
        String[] suggestions = mSearchSuggestionsSet.toArray(new String[0]);

        Intent intent = new Intent(LogViewerActivity.this, RecordLogDialogActivity.class);
        intent.putExtra(RecordLogDialogActivity.EXTRA_QUERY_SUGGESTIONS, suggestions);

        startActivity(intent);
    }

    private void showFiltersDialog() {
        executor.submit(() -> {
            final List<LogFilter> filters = AppManager.getDb().logFilterDao().getAll();
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
                        .setDropdownItems(new ArrayList<>(mSearchSuggestionsSet), true)
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
                LogFilterDao dao = AppManager.getDb().logFilterDao();
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
            partialSelectMode = true;
            partiallySelectedLogLines.clear();
            Toast.makeText(this, R.string.toast_started_select_partial, Toast.LENGTH_SHORT).show();
        } else {
            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            @SuppressLint("InflateParams") View helpView = inflater.inflate(R.layout.dialog_partial_save_help, null);
            // don't show the scroll bar
            helpView.setVerticalScrollBarEnabled(false);
            helpView.setHorizontalScrollBarEnabled(false);
            final CheckBox checkBox = helpView.findViewById(android.R.id.checkbox);

            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.menu_title_partial_select)
                    .setView(helpView)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        partialSelectMode = true;
                        partiallySelectedLogLines.clear();
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

    private void expandOrCollapseAll(boolean change) {
        mCollapsedMode = change != mCollapsedMode;
        int oldFirstVisibleItem = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
        for (LogLine logLine : mLogListAdapter.getTrueValues()) {
            if (logLine != null) {
                logLine.setExpanded(!mCollapsedMode);
            }
        }
        mLogListAdapter.notifyDataSetChanged();
        // Ensure that we either stay autoscrolling at the bottom of the list...
        if (mAutoscrollToBottom) {
            scrollToBottom();
            // ... or that whatever was the previous first visible item is still the current first
            // visible item after expanding/collapsing
        } else if (oldFirstVisibleItem != -1) {
            recyclerView.scrollToPosition(oldFirstVisibleItem);
        }
        supportInvalidateOptionsMenu();
    }

    private void displayDeleteSavedLogsDialog() {
        List<File> logFiles = SaveLogHelper.getLogFiles();
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
                                for (File selectedFile : selectedFiles) {
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
            List<File> files = saveLogDetails(includeDeviceInfo, includeDmesg);
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
                        File zipFile = SaveLogHelper.saveTemporaryZipFile(SaveLogHelper.createZipFilename(true), files);
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
            List<File> files = saveLogDetails(includeDeviceInfo, includeDmesg);
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
                        } catch (IOException | RemoteException e) {
                            UiThreadHandler.run(() -> UIUtils.displayShortToast(R.string.saving_failed));
                        }
                    });
                });
            });
        });

    }

    @NonNull
    @WorkerThread
    private List<File> saveLogDetails(boolean includeDeviceInfo, boolean includeDmesg) {
        List<File> files = new ArrayList<>();
        SaveLogHelper.cleanTemp();

        if (mCurrentlyOpenLog != null) { // Use saved log file
            files.add(SaveLogHelper.getFile(mCurrentlyOpenLog));
        } else { // Create a temp file to hold the current, unsaved log
            File tempLogFile = SaveLogHelper.saveTemporaryFile(SaveLogHelper.TEMP_LOG_FILENAME, null,
                    getCurrentLogAsListOfStrings());
            files.add(tempLogFile);
        }

        if (includeDeviceInfo) {
            String deviceInfo = BuildHelper.getBuildInformationAsString();
            File tempFile = SaveLogHelper.saveTemporaryFile(SaveLogHelper.TEMP_DEVICE_INFO_FILENAME, deviceInfo, null);
            files.add(tempFile);
        }

        if (includeDmesg) {
            File tempDmsgFile = SaveLogHelper.saveTemporaryFile(SaveLogHelper.TEMP_DMESG_FILENAME, null,
                    Runner.runCommand(Runner.getRootInstance(), "dmesg").getOutputAsList());
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
        List<File> logFiles = SaveLogHelper.getLogFiles();
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
        @SuppressLint("StaticFieldLeak") final AsyncTask<Void, Void, List<LogLine>> openFileTask = new AsyncTask<Void, Void, List<LogLine>>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                resetDisplayedLog(filename);

                progressIndicator.hide();
                progressIndicator.setIndeterminate(false);
                progressIndicator.show();
            }

            @Override
            protected List<LogLine> doInBackground(Void... params) {

                // remove any lines at the beginning if necessary
                final int maxLines = AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_DISPLAY_LIMIT_INT);
                SavedLog savedLog = SaveLogHelper.openLog(filename, maxLines);
                List<String> lines = savedLog.getLogLines();
                List<LogLine> logLines = new ArrayList<>();
                for (int lineNumber = 0, linesSize = lines.size(); lineNumber < linesSize; lineNumber++) {
                    String line = lines.get(lineNumber);
                    LogLine logLine = LogLine.newLogLine(line, !mCollapsedMode, mFilterPattern);
                    if (logLine != null) {
                        logLines.add(logLine);
                    }
                    final int finalLineNumber = lineNumber;
                    runOnUiThread(() -> progressIndicator.setProgressCompat(finalLineNumber * 100 / linesSize, true));
                }

                // notify the user if the saved file was truncated
                if (savedLog.isTruncated()) {
                    mHandler.post(() -> {
                        String toastText = getResources().getQuantityString(R.plurals.toast_log_truncated, maxLines, maxLines);
                        Toast.makeText(LogViewerActivity.this, toastText, Toast.LENGTH_LONG).show();
                    });
                }

                return logLines;
            }

            @Override
            protected void onPostExecute(List<LogLine> logLines) {
                super.onPostExecute(logLines);
                progressIndicator.hide();

                for (LogLine logLine : logLines) {
                    mLogListAdapter.addWithFilter(logLine, "", false);
                    addToAutocompleteSuggestions(logLine);

                }
                mLogListAdapter.notifyDataSetChanged();

                // scroll to bottom
                scrollToBottom();
            }
        };

        // if the main log task is running, we can only run AFTER it's been canceled

        if (mTask != null) {
            mTask.setOnFinished(() -> openFileTask.execute((Void) null));
            mTask.unpause();
            mTask.killReader();
            mTask = null;
        } else {
            // main log not running; just open in this thread
            openFileTask.execute((Void) null);
        }
    }

    public void resetDisplayedLog(String filename) {
        mLogListAdapter.clear();
        mCurrentlyOpenLog = filename;
        mCollapsedMode = !AppPref.getBoolean(AppPref.PrefKey.PREF_LOG_VIEWER_EXPAND_BY_DEFAULT_BOOL);
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
        recyclerView.setAdapter(mLogListAdapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
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
        recyclerView.setHasFixedSize(true);
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
                        if (partiallySelectedLogLines.size() == 2)
                            savePartialLog(filename, partiallySelectedLogLines.get(0), partiallySelectedLogLines.get(1));
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which, inputText, isChecked) -> cancelPartialSelect())
                .show();
    }

    private void cancelPartialSelect() {
        partialSelectMode = false;
        boolean changed = false;
        for (LogLine logLine : partiallySelectedLogLines) {
            if (logLine.isHighlighted()) {
                logLine.setHighlighted(false);
                changed = true;
            }
        }
        partiallySelectedLogLines.clear();
        if (changed) {
            mHandler.post(mLogListAdapter::notifyDataSetChanged);
        }
    }

    private void setSearchText(String text) {
        // Sets the search text without invoking autosuggestions, which are really only useful when typing
        mDynamicallyEnteringSearchText = true;
        search(text);
        searchView.setIconified(false);
        searchView.setQuery(mSearchingString, true);
        searchView.clearFocus();
    }

    private void search(String filterText) {
        Filter filter = mLogListAdapter.getFilter();
        filter.filter(filterText, this);
        mSearchingString = filterText;
        if (!TextUtils.isEmpty(mSearchingString)) {
            mDynamicallyEnteringSearchText = true;
        }
    }

    private void pauseOrUnpause(MenuItem item) {
        LogReaderAsyncTask currentTask = mTask;
        if (currentTask != null) {
            if (currentTask.isPaused()) {
                currentTask.unpause();
                item.setIcon(R.drawable.ic_pause_white_24dp);
            } else {
                currentTask.pause();
                item.setIcon(R.drawable.ic_play_arrow_white_24dp);
            }
        }
    }

    @Override
    public void onFilterComplete(int count) {
        // always scroll to the bottom when searching
        recyclerView.scrollToPosition(count - 1);
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
        recyclerView.scrollToPosition(mLogListAdapter.getItemCount() - 1);
    }

    @SuppressLint("StaticFieldLeak")
    private class LogReaderAsyncTask extends AsyncTask<Void, LogLine, Void> {

        private final Object mLock = new Object();
        private int counter = 0;
        private volatile boolean mPaused;
        private boolean mFirstLineReceived;
        private boolean mKilled;
        private LogcatReader mReader;
        private Runnable mOnFinishedRunnable;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            resetDisplayedLog(null);

            progressIndicator.hide();
            progressIndicator.setIndeterminate(true);
            progressIndicator.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                // use "recordingMode" because we want to load all the existing lines at once
                // for a performance boost
                LogcatReaderLoader loader = LogcatReaderLoader.create(true);
                mReader = loader.loadReader();

                int maxLines = AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_DISPLAY_LIMIT_INT);

                String line;
                LinkedList<LogLine> initialLines = new LinkedList<>();
                while ((line = mReader.readLine()) != null && !isCancelled()) {
                    if (mPaused) {
                        synchronized (mLock) {
                            if (mPaused) {
                                mLock.wait();
                            }
                        }
                    }
                    LogLine logLine = LogLine.newLogLine(line, !mCollapsedMode, mFilterPattern);
                    if (logLine == null) {
                        if (mReader.readyToRecord()) {
                            publishProgress();
                        }
                    } else if (!mReader.readyToRecord()) {
                        // "ready to record" in this case means all the initial lines have been flushed from the reader
                        initialLines.add(logLine);
                        if (initialLines.size() > maxLines) {
                            initialLines.removeFirst();
                        }
                    } else if (!initialLines.isEmpty()) {
                        // flush all the initial lines we've loaded
                        initialLines.add(logLine);
                        publishProgress(initialLines.toArray(new LogLine[0]));
                        initialLines.clear();
                    } else {
                        // just proceed as normal
                        publishProgress(logLine);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, e);
            } finally {
                killReader();
            }
            return null;
        }

        void killReader() {
            if (!mKilled) {
                synchronized (mLock) {
                    if (!mKilled && mReader != null) {
                        mReader.killQuietly();
                        mKilled = true;
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            doWhenFinished();
        }

        @Override
        protected void onProgressUpdate(LogLine... values) {
            super.onProgressUpdate(values);

            if (values == null) return;

            if (!mFirstLineReceived) {
                mFirstLineReceived = true;
                progressIndicator.hide();
            }
            for (LogLine logLine : values) {
                mLogListAdapter.addWithFilter(logLine, mSearchingString, false);

                addToAutocompleteSuggestions(logLine);
            }
            mLogListAdapter.notifyDataSetChanged();

            // How many logs to keep in memory, to avoid OutOfMemoryError
            int maxNumLogLines = AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_DISPLAY_LIMIT_INT);

            // check to see if the list needs to be truncated to avoid out of memory errors
            if (++counter % UPDATE_CHECK_INTERVAL == 0
                    && mLogListAdapter.getTrueValues().size() > maxNumLogLines) {
                int numItemsToRemove = mLogListAdapter.getTrueValues().size() - maxNumLogLines;
                mLogListAdapter.removeFirst(numItemsToRemove);
                Log.d(TAG, "Truncating " + numItemsToRemove + " lines from log list to avoid out of memory errors");
            }

            if (mAutoscrollToBottom) {
                scrollToBottom();
            }
        }

        private void doWhenFinished() {
            if (mPaused) {
                unpause();
            }
            if (mOnFinishedRunnable != null) {
                mOnFinishedRunnable.run();
            }
        }

        private void pause() {
            synchronized (mLock) {
                mPaused = true;
            }
        }

        private void unpause() {
            synchronized (mLock) {
                mPaused = false;
                mLock.notify();
            }
        }

        private boolean isPaused() {
            return mPaused;
        }

        private void setOnFinished(Runnable onFinished) {
            this.mOnFinishedRunnable = onFinished;
        }
    }
}
