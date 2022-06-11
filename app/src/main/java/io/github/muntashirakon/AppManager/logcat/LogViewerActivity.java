// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
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
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.dao.LogFilterDao;
import io.github.muntashirakon.AppManager.db.entity.LogFilter;
import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper;
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper;
import io.github.muntashirakon.AppManager.logcat.struct.LogLine;
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria;
import io.github.muntashirakon.AppManager.logcat.struct.SendLogDetails;
import io.github.muntashirakon.AppManager.settings.SettingsActivity;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.BetterActivityResult;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.AppManager.utils.StoragePermission;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.UiThreadHandler;
import io.github.muntashirakon.dialog.TextInputDropdownDialogBuilder;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.util.UiUtils;

import static io.github.muntashirakon.AppManager.logcat.LogViewerRecyclerAdapter.CONTEXT_MENU_COPY_ID;
import static io.github.muntashirakon.AppManager.logcat.LogViewerRecyclerAdapter.CONTEXT_MENU_FILTER_ID;

// Copyright 2012 Nolan Lawson
// Copyright 2021 Muntashir Al-Islam
public class LogViewerActivity extends BaseActivity implements SearchView.OnQueryTextListener,
        LogViewerRecyclerAdapter.ViewHolder.OnClickListener, SearchView.OnSuggestionListener {
    public static final String TAG = LogViewerActivity.class.getSimpleName();

    public interface SearchingInterface {
        void onQuery(@Nullable String searchTerm);
    }

    public static final String EXTRA_FILTER = "filter";
    public static final String EXTRA_LEVEL = "level";

    // how often to check to see if we've gone over the max size
    public static final int UPDATE_CHECK_INTERVAL = 200;

    // how many suggestions to keep in the autosuggestions text
    private static final int MAX_NUM_SUGGESTIONS = 1000;

    private static final String INTENT_FILENAME = "filename";

    private LinearProgressIndicator mProgressIndicator;
    private ExtendedFloatingActionButton mStopRecordingFab;
    @Nullable
    private AlertDialog mLoadingDialog;
    private SearchView mSearchView;
    private String mSearchQuery;
    private boolean mDynamicallyEnteringSearchQuery;
    private final Set<String> mSearchSuggestionsSet = new HashSet<>();
    private CursorAdapter mSearchSuggestionsAdapter;
    private boolean mLogsToBeShared;
    @Nullable
    private SearchingInterface mSearchingInterface;
    private LogViewerViewModel mViewModel;

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
        UiUtils.applyWindowInsetsAsMargin(mStopRecordingFab);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
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

        if ("record".equals(getIntent().getStringExtra("shortcut_action"))) {
            // Handle shortcut
            storagePermission.request(granted -> {
                if (granted) {
                    startLogRecorder();
                }
            });
            return;
        }

        mStopRecordingFab.setOnClickListener(v -> {
            // Stop log recorder
            ServiceHelper.stopBackgroundServiceIfRunning(LogViewerActivity.this);
        });

        // Set collapsed mode
        mViewModel.setCollapsedMode(!AppPref.getBoolean(AppPref.PrefKey.PREF_LOG_VIEWER_EXPAND_BY_DEFAULT_BOOL));

        // Grant read logs permission if not already
        mViewModel.grantReadLogsPermission();
        // It doesn't matter whether the permission has been granted or not, we can start logging
        mViewModel.observeLoggingFinished().observe(this, finished -> {
            if (finished) {
                mProgressIndicator.hide();
                if (mViewModel.isLogcatPaused()) {
                    mViewModel.resumeLogcat();
                }
            }
        });
        mViewModel.observeLoadingProgress().observe(this, percentage ->
                mProgressIndicator.setProgressCompat(percentage, true));
        mViewModel.observeTruncatedLines().observe(this, maxDisplayedLines -> UIUtils.displayLongToast(
                getResources().getQuantityString(R.plurals.toast_log_truncated, maxDisplayedLines, maxDisplayedLines)));
        mViewModel.getLogFilters().observe(this, this::showFiltersDialog);
        mViewModel.observeLogSaved().observe(this, path -> {
            if (path != null) {
                UIUtils.displayShortToast(R.string.log_saved);
                if (!path.getName().endsWith(".zip")) {
                    openLogFile(path.getName());
                }
            } else {
                UIUtils.displayLongToast(R.string.unable_to_save_log);
            }
        });
        mViewModel.getLogsToBeSent().observe(this, sendLogDetails -> {
            if (mLoadingDialog != null) {
                mLoadingDialog.dismiss();
            }
            if (sendLogDetails == null) {
                UIUtils.displayLongToast(R.string.failed);
                return;
            }
            if (mLogsToBeShared) {
                // Open chooser dialog
                startChooser(this, sendLogDetails.getSubject(), sendLogDetails.getBody(),
                        sendLogDetails.getAttachmentType(), sendLogDetails.getAttachment());
            } else {
                // Open SAF activity
                saveLauncher.launch(sendLogDetails.getAttachment().getName(), uri -> {
                    if (uri == null) return;
                    mViewModel.saveLogs(Paths.get(uri), sendLogDetails);
                });
            }
        });

        startLogging();
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 1) {
            if (!mSearchView.isIconified()) {
                mSearchView.setIconified(true);
            }
            super.onBackPressed();
        } else this.finish();
    }

    public void loadNewFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_layout, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void startLogRecorder() {
        String logFilename = SaveLogHelper.createLogFilename();
        executor.submit(() -> {
            // Start recording logs
            Intent intent = ServiceHelper.getLogcatRecorderServiceIfNotAlreadyRunning(this, logFilename,
                    "", AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_DEFAULT_LOG_LEVEL_INT));
            runOnUiThread(() -> {
                if (intent != null) {
                    ContextCompat.startForegroundService(this, intent);
                }
                finish();
            });
        });
    }

    @WorkerThread
    private void addFiltersToSuggestions() {
        for (LogFilter logFilter : AppManager.getAppsDb().logFilterDao().getAll()) {
            addToAutocompleteSuggestions(logFilter.name);
        }
    }

    private void startLogging() {
        applyFiltersFromIntent(getIntent());
        Uri dataUri = IntentCompat.getDataUri(getIntent());
        String filename = getIntent().getStringExtra(INTENT_FILENAME);
        if (dataUri != null) {
            openLogFile(dataUri);
        } else if (filename != null) {
            openLogFile(filename);
        } else {
            startLiveLogViewer(false);
        }
    }

    private void applyFiltersFromIntent(@Nullable Intent intent) {
        if (intent == null) return;
        String filter = intent.getStringExtra(EXTRA_FILTER);
        String level = intent.getStringExtra(EXTRA_LEVEL);
        if (!TextUtils.isEmpty(filter)) {
            setSearchQuery(filter);
        }
        if (!TextUtils.isEmpty(level)) {
            int logLevelLimit = LogLine.convertCharToLogLevel(level.charAt(0));
            if (logLevelLimit == -1) {
                String invalidLevel = getString(R.string.toast_invalid_level, level);
                Toast.makeText(this, invalidLevel, Toast.LENGTH_LONG).show();
            } else {
                mViewModel.setLogLevel(logLevelLimit);
                search(mSearchQuery);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mStopRecordingFab != null) {
            boolean recordingInProgress = ServiceHelper.checkIfServiceIsRunning(getApplicationContext(), LogcatRecordingService.class);
            mStopRecordingFab.setVisibility(recordingInProgress ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        applyFiltersFromIntent(intent);
        Uri dataUri = IntentCompat.getDataUri(intent);
        String filename = intent.getStringExtra(INTENT_FILENAME);
        if (dataUri != null) {
            openLogFile(dataUri);
        } else if (filename != null) {
            openLogFile(filename);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void startLiveLogViewer(boolean force) {
        if (!mViewModel.isLogcatKilled() && !force) {
            // Logcat already running
            mViewModel.resumeLogcat();
            return;
        }
        // (re)start logcat
        resetDisplay();
        mProgressIndicator.hide();
        mProgressIndicator.setIndeterminate(true);
        mProgressIndicator.show();
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            if (getSupportFragmentManager().findFragmentByTag(LiveLogViewerFragment.TAG) != null) {
                // Fragment already exists, just restart logcat
                mViewModel.restartLogcat();
                return;
            }
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_layout, new LiveLogViewerFragment(), LiveLogViewerFragment.TAG)
                .addToBackStack(null)
                .commit();
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
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
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
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (!mDynamicallyEnteringSearchQuery) {
            search(newText);
            populateSuggestionsAdapter(newText);
        }
        mDynamicallyEnteringSearchQuery = false;
        return false;
    }

    @Override
    public boolean onSuggestionSelect(int position) {
        return false;
    }

    @Override
    public boolean onSuggestionClick(int position) {
        List<String> suggestions = getSuggestionsForQuery(mSearchQuery);
        if (!suggestions.isEmpty()) {
            mSearchView.setQuery(suggestions.get(position), true);
        }
        return false;
    }

    LinearProgressIndicator getProgressBar() {
        return mProgressIndicator;
    }

    void setLogsToBeShared(boolean logsToBeShared, boolean displayLoader) {
        mLogsToBeShared = logsToBeShared;
        if (displayLoader) {
            mLoadingDialog = UIUtils.getProgressDialog(LogViewerActivity.this, R.string.dialog_compiling_log);
            mLoadingDialog.show();
        } else mLoadingDialog = null;
    }

    void addToAutocompleteSuggestions(@NonNull LogLine logLine) {
        if (logLine.getTagName() == null) return;
        String tag = logLine.getTagName().trim();
        if (!TextUtils.isEmpty(tag)) {
            addToAutocompleteSuggestions(tag);
        }
    }

    void displayLogViewerSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra(SettingsActivity.EXTRA_KEY, "log_viewer_prefs");
        activityLauncher.launch(intent, result -> {
            // Preferences may have changed
            mViewModel.setCollapsedMode(!AppPref.getBoolean(AppPref.PrefKey.PREF_LOG_VIEWER_EXPAND_BY_DEFAULT_BOOL));
            if (result.getResultCode() == Activity.RESULT_FIRST_USER) {
                Intent data = result.getData();
                if (data != null && data.getBooleanExtra("bufferChanged", false)) {
                    // Log buffer changed, so update list
                    startLiveLogViewer(true);
                }
            }
        });
    }

    private void showSearchByDialog(final LogLine logLine) {
        View view = View.inflate(this, R.layout.dialog_searchby, null);
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

        Objects.requireNonNull(tagText).setText(logLine.getTagName());
        Objects.requireNonNull(pidText).setText(String.valueOf(logLine.getProcessId()));

        tag.setEndIconOnClickListener(v -> {
            String tagQuery = (logLine.getTagName().contains(" ")) ? ('"' + logLine.getTagName() + '"') : logLine.getTagName();
            setSearchQuery(SearchCriteria.TAG_KEYWORD + tagQuery);
            dialog.dismiss();
        });

        pid.setEndIconOnClickListener(v -> {
            setSearchQuery(SearchCriteria.PID_KEYWORD + logLine.getProcessId());
            dialog.dismiss();
        });
    }

    void showRecordLogDialog() {
        String[] suggestions = mSearchSuggestionsSet.toArray(new String[0]);
        DialogFragment dialog = RecordLogDialogFragment.getInstance(suggestions, () -> {
            if (mStopRecordingFab != null) {
                mStopRecordingFab.setVisibility(View.VISIBLE);
            }
        });
        dialog.show(getSupportFragmentManager(), RecordLogDialogFragment.TAG);
    }

    private void showFiltersDialog(List<LogFilter> filters) {
        LogFilterAdapter logFilterAdapter = new LogFilterAdapter(this, filters);
        ListView view = new ListView(this);
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

        AlertDialog alertDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.saved_filters)
                .setView(view)
                .setNegativeButton(R.string.ok, null)
                .show();

        logFilterAdapter.setOnItemClickListener((parent, view1, position, logFilter) -> {
            setSearchQuery(logFilter.name);
            alertDialog.dismiss();
        });
    }

    protected void handleNewFilterText(String text, final LogFilterAdapter logFilterAdapter) {
        final String trimmed = text.trim();
        if (!TextUtils.isEmpty(trimmed)) {
            executor.submit(() -> {
                LogFilterDao dao = AppManager.getAppsDb().logFilterDao();
                long id = dao.insert(trimmed);
                LogFilter logFilter = dao.get(id);
                UiThreadHandler.run(() -> {
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

    void setSearchingInterface(@Nullable SearchingInterface searchingInterface) {
        mSearchingInterface = searchingInterface;
    }

    void openLogFile(Path logFile) {
        mProgressIndicator.hide();
        mProgressIndicator.setIndeterminate(false);
        mProgressIndicator.show();

        loadNewFragment(SavedLogViewerFragment.getInstance(logFile.getUri()));
    }

    private void openLogFile(String filename) {
        try {
            Path logFile = SaveLogHelper.getFile(filename);
            mProgressIndicator.hide();
            mProgressIndicator.setIndeterminate(false);
            mProgressIndicator.show();

            loadNewFragment(SavedLogViewerFragment.getInstance(logFile.getUri()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openLogFile(Uri logFile) {
        mProgressIndicator.hide();
        mProgressIndicator.setIndeterminate(false);
        mProgressIndicator.show();

        loadNewFragment(SavedLogViewerFragment.getInstance(logFile));
    }

    private void resetDisplay() {
        mViewModel.setCollapsedMode(!AppPref.getBoolean(AppPref.PrefKey.PREF_LOG_VIEWER_EXPAND_BY_DEFAULT_BOOL));
        // Populate suggestions with existing filters (if any)
        executor.submit(this::addFiltersToSuggestions);
        resetFilter();
    }

    private void resetFilter() {
        mViewModel.setLogLevel(AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_DEFAULT_LOG_LEVEL_INT));
        search(mSearchQuery);
    }

    private void setSearchQuery(String text) {
        // Sets the search text without invoking autosuggestions, which are really only useful when typing
        mDynamicallyEnteringSearchQuery = true;
        search(text);
        mSearchView.setIconified(false);
        mSearchView.setQuery(mSearchQuery, true);
        mSearchView.clearFocus();
    }

    String getSearchQuery() {
        return mSearchQuery;
    }

    void search(String filterText) {
        if (mSearchingInterface != null) {
            mSearchingInterface.onQuery(filterText);
        }
        mSearchQuery = filterText;
        if (!TextUtils.isEmpty(mSearchQuery)) {
            mDynamicallyEnteringSearchQuery = true;
        }
    }

    private void addToAutocompleteSuggestions(String trimmed) {
        if (mSearchSuggestionsSet.size() < MAX_NUM_SUGGESTIONS && !mSearchSuggestionsSet.contains(trimmed)) {
            mSearchSuggestionsSet.add(trimmed);
            populateSuggestionsAdapter(mSearchQuery);
        }
    }
}
