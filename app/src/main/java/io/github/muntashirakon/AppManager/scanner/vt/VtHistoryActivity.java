// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner.vt;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getColoredText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getPrimaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

import android.content.ContentResolver;
import android.content.Intent;
import android.app.Application;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.widget.PopupMenu;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.entity.VtFile;
import io.github.muntashirakon.AppManager.db.entity.VtScanAttempt;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.scanner.VirusTotalDialog;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.lifecycle.SingleLiveEvent;
import io.github.muntashirakon.widget.RecyclerView;
import io.github.muntashirakon.util.UiUtils;

public class VtHistoryActivity extends BaseActivity {
    private static volatile boolean sVisible;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private VtHistoryViewModel mViewModel;
    private LinearProgressIndicator mProgress;
    private VtHistoryAdapter mAdapter;
    private boolean mResumed;
    private final Set<Long> mConsentDialogsShown = new HashSet<>();

    private final ActivityResultLauncher<String[]> mFilePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onFilePicked);

    private final Runnable mRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (mResumed) {
                loadData(false);
                mHandler.postDelayed(this, 2_000L);
            }
        }
    };

    public static boolean isVisible() {
        return sVisible;
    }

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        if (!FeatureController.isVirusTotalEnabled()) {
            finish();
            return;
        }
        setContentView(R.layout.activity_virus_total);
        setSupportActionBar(findViewById(R.id.toolbar));
        mViewModel = new ViewModelProvider(this).get(VtHistoryViewModel.class);
        mProgress = findViewById(R.id.progress_linear);
        RecyclerView listView = findViewById(android.R.id.list);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.setEmptyView(findViewById(android.R.id.empty));
        UiUtils.applyWindowInsetsAsPaddingNoTop(listView);
        mAdapter = new VtHistoryAdapter(this);
        listView.setAdapter(mAdapter);
        FloatingActionButton fab = findViewById(R.id.add_file);
        UiUtils.applyWindowInsetsAsMargin(fab);
        fab.setOnClickListener(v -> mFilePicker.launch(new String[]{"*/*"}));
        mViewModel.getHistoryLiveData().observe(this, history -> {
            mProgress.hide();
            mAdapter.submitList(history);
            showPendingConsentDialog(history);
        });
        mViewModel.getReportLiveData().observe(this, report -> {
            if (report != null) {
                displayReport(report);
            }
        });
        mViewModel.getStartScanLiveData().observe(this, start -> {
            if (Boolean.TRUE.equals(start)) {
                VtScanService.start(this);
            }
        });
        mViewModel.getErrorLiveData().observe(this, error -> {
            mProgress.hide();
            if (error != null) {
                UIUtils.displayShortToast(error);
            }
        });
        loadData(true);
        handleIncomingIntent(getIntent());
    }

    private void showPendingConsentDialog(@NonNull List<VtHistoryItem> history) {
        for (VtHistoryItem item : history) {
            if (item.attempt == null
                    || !VtScanAttempt.STATUS_PENDING_CONSENT.equals(item.attempt.status)
                    || !mConsentDialogsShown.add(item.attempt.id)) continue;
            mHandler.post(() -> requestUploadConsent(item.attempt,
                    item.file.displayName == null ? item.file.sha256 : item.file.displayName));
            break;
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(@Nullable Intent intent) {
        if (intent == null || (!Intent.ACTION_VIEW.equals(intent.getAction())
                && !Intent.ACTION_SEND.equals(intent.getAction()))) return;
        Uri uri = IntentCompat.getDataUri(intent);
        if (uri == null) {
            return;
        }
        int readPermission = intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme()) && readPermission != 0) {
            try {
                getContentResolver().takePersistableUriPermission(uri, readPermission);
            } catch (SecurityException | IllegalArgumentException ignored) {
                // URI is not persistable, but it's okay
            }
        }
        mProgress.show();
        mViewModel.observeUri(uri, getContentResolver().getType(uri),
                Intent.ACTION_SEND.equals(intent.getAction())
                        ? VtScanRepository.SOURCE_SHARE_INTENT : VtScanRepository.SOURCE_VIEW_INTENT);
    }

    private void onFilePicked(@Nullable Uri uri) {
        if (uri == null || mViewModel == null) {
            return;
        }
        mProgress.show();
        mViewModel.observeUri(uri, getContentResolver().getType(uri),
                VtScanRepository.SOURCE_FILE_PICKER);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sVisible = true;
        mResumed = true;
        mHandler.removeCallbacks(mRefreshRunnable);
        mHandler.post(mRefreshRunnable);
    }

    @Override
    protected void onPause() {
        sVisible = false;
        mResumed = false;
        mHandler.removeCallbacks(mRefreshRunnable);
        super.onPause();
    }

    private void loadData(boolean showProgress) {
        if (showProgress) {
            mProgress.show();
        }
        mViewModel.loadHistory();
    }

    private static boolean isActive(@NonNull String status) {
        return VtScanAttempt.STATUS_PENDING_CONSENT.equals(status)
                || VtScanAttempt.STATUS_QUEUED.equals(status)
                || VtScanAttempt.STATUS_LOOKING_UP.equals(status)
                || VtScanAttempt.STATUS_UPLOADING.equals(status)
                || VtScanAttempt.STATUS_ANALYSING.equals(status);
    }

    @NonNull
    private CharSequence getLocalizedStatus(@NonNull String status) {
        switch (status) {
            case VtScanAttempt.STATUS_PENDING_CONSENT:
                return getText(R.string.vt_pending_consent);
            case VtScanAttempt.STATUS_QUEUED:
                return getText(R.string.vt_queued);
            case VtScanAttempt.STATUS_LOOKING_UP:
            case VtScanAttempt.STATUS_ANALYSING:
                return getText(R.string.vt_checking);
            case VtScanAttempt.STATUS_UPLOADING:
                return getText(R.string.vt_uploading);
            case VtScanAttempt.STATUS_FAILED:
                return getText(R.string.vt_failed);
            case VtScanAttempt.STATUS_CANCELLED:
                return getText(R.string.vt_cancelled);
            case VtScanAttempt.STATUS_COMPLETED:
                return getText(R.string.done);
            default:
                return status;
        }
    }

    void openReport(long fileId) {
        mViewModel.loadReport(fileId, 0, true);
    }

    void openAttemptReport(long attemptId) {
        mViewModel.loadReport(0, attemptId, false);
    }

    private void displayReport(@NonNull VtFileReport report) {
        int positives = report.getPositives();
        CharSequence title = getColoredText(getString(R.string.vt_success, positives,
                report.getTotal()), getReportColor(positives));
        CharSequence subtitle = getString(R.string.vt_scan_date,
                DateUtils.formatDateTime(this, report.scanDate));
        ArrayList<Spannable> items = new ArrayList<>();
        int unsafeColor = ColorCodes.getVirusTotalExtremelyUnsafeIndicatorColor(this);
        int safeColor = ColorCodes.getVirusTotalSafeIndicatorColor(this);
        for (VtAvEngineResult item : report.results) {
            SpannableStringBuilder line = new SpannableStringBuilder();
            int color = item.category >= VtAvEngineResult.CAT_SUSPICIOUS ? unsafeColor
                    : item.category >= VtAvEngineResult.CAT_UNDETECTED ? safeColor
                    : ColorCodes.getListItemDefaultIndicatorColor(this);
            line.append(getColoredText(getPrimaryText(this, item.engineName), color));
            if (item.engineVersion != null) {
                line.append(getSmallerText(" (" + item.engineVersion + ")"));
            }
            if (item.result != null) {
                line.append("\n").append(item.result);
            }
            items.add(line);
        }
        Spanned message = UiUtils.getOrderedList(items);
        // TODO: 10/5/26 This is a repeat of ScannerFragment: need to merge them
        VirusTotalDialog dialog = VirusTotalDialog.getInstance(title, subtitle, message, report.permalink);
        dialog.show(getSupportFragmentManager(), VirusTotalDialog.TAG);
    }

    private int getReportColor(int positives) {
        if (positives <= 3) {
            return ColorCodes.getVirusTotalSafeIndicatorColor(this);
        }
        if (positives <= 12) {
            return ColorCodes.getVirusTotalUnsafeIndicatorColor(this);
        }
        return ColorCodes.getVirusTotalExtremelyUnsafeIndicatorColor(this);
    }

    void startAttempt(long fileId, @NonNull String type) {
        mViewModel.startAttempt(fileId, type);
    }

    void cancelAttempt(long attemptId) {
        mViewModel.cancelAttempt(attemptId);
    }

    void requestUploadConsent(@NonNull VtScanAttempt attempt, @NonNull String fileName) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.scan_in_vt)
                .setMessage(getString(R.string.vt_confirm_uploading_file) + "\n\n" + fileName)
                .setNegativeButton(R.string.no, (dialog, which) -> mViewModel.rejectUpload(attempt.id))
                .setPositiveButton(R.string.vt_confirm_upload_and_scan,
                        (dialog, which) -> mViewModel.approveUpload(attempt.id))
                .show();
    }

    void deleteAttempt(long attemptId) {
        mViewModel.deleteAttempt(attemptId);
    }

    void deleteFile(long fileId) {
        mViewModel.deleteFile(fileId);
    }

    void setRead(@NonNull VtHistoryItem item, boolean read) {
        mViewModel.setRead(item, read);
    }

    private void clearHistory() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_history)
                .setMessage(R.string.are_you_sure)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes, (d, w) -> mViewModel.clearHistory()).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_virus_total_actions, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_refresh) {
            loadData(true);
            return true;
        } else if (item.getItemId() == R.id.action_clear_history) {
            clearHistory();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        sVisible = false;
        mResumed = false;
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    static class VtHistoryItem {
        final VtFile file;
        @Nullable
        final VtScanAttempt attempt;
        final boolean latest;

        VtHistoryItem(VtFile file, @Nullable VtScanAttempt attempt, boolean latest) {
            this.file = file;
            this.attempt = attempt;
            this.latest = latest;
        }
    }

    public static class VtHistoryViewModel extends AndroidViewModel {
        private final VtScanRepository mRepository = new VtScanRepository();
        private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
        private final MutableLiveData<List<VtHistoryItem>> mHistoryLiveData = new MutableLiveData<>();
        private final SingleLiveEvent<VtFileReport> mReportLiveData = new SingleLiveEvent<>();
        private final SingleLiveEvent<Boolean> mStartScanLiveData = new SingleLiveEvent<>();
        private final SingleLiveEvent<String> mErrorLiveData = new SingleLiveEvent<>();

        public VtHistoryViewModel(@NonNull Application application) {
            super(application);
        }

        LiveData<List<VtHistoryItem>> getHistoryLiveData() {
            return mHistoryLiveData;
        }

        LiveData<VtFileReport> getReportLiveData() {
            return mReportLiveData;
        }

        LiveData<Boolean> getStartScanLiveData() {
            return mStartScanLiveData;
        }

        LiveData<String> getErrorLiveData() {
            return mErrorLiveData;
        }

        void observeUri(@NonNull Uri uri, @Nullable String mimeType,
                        @VtScanRepository.FileSource @NonNull String sourceType) {
            mExecutor.execute(() -> {
                try {
                    VtScanRepository.ObservedFile file = mRepository.observeUri(uri,
                            mimeType, sourceType);
                    VtScanAttempt attempt = mRepository.createInitialAttempt(file,
                            Prefs.VirusTotal.promptBeforeUpload());
                    if (attempt != null && VtScanAttempt.STATUS_QUEUED.equals(attempt.status)) {
                        mStartScanLiveData.postValue(true);
                    }
                    loadHistoryInternal();
                } catch (Throwable e) {
                    mErrorLiveData.postValue(e.getMessage() == null
                            ? getApplication().getString(R.string.failed) : e.getMessage());
                }
            });
        }

        void loadHistory() {
            mExecutor.execute(this::loadHistoryInternal);
        }

        @WorkerThread
        private void loadHistoryInternal() {
            List<VtFile> files = mRepository.getFiles();
            List<VtScanAttempt> activeAttempts = mRepository.getActiveAttempts();
            Map<Long, VtFile> fileMap = new HashMap<>();
            for (VtFile file : files) {
                fileMap.put(file.id, file);
            }
            Set<Long> activeFileIds = new HashSet<>();
            List<VtHistoryItem> result = new ArrayList<>();
            for (VtScanAttempt attempt : activeAttempts) {
                VtFile file = fileMap.get(attempt.fileId);
                if (file != null) {
                    activeFileIds.add(file.id);
                    result.add(new VtHistoryItem(file, attempt, false));
                }
            }
            for (VtFile file : files) {
                if (!activeFileIds.contains(file.id)) {
                    result.add(new VtHistoryItem(file, null, true));
                }
                boolean latestAttemptSkipped = false;
                for (VtScanAttempt attempt : mRepository.getAttempts(file.id)) {
                    if (isActive(attempt.status)) {
                        continue;
                    }
                    if (!activeFileIds.contains(file.id) && !latestAttemptSkipped
                            && VtScanAttempt.STATUS_COMPLETED.equals(attempt.status)
                            && Objects.equals(file.latestReportJson, attempt.reportJson)) {
                        latestAttemptSkipped = true;
                        continue;
                    }
                    result.add(new VtHistoryItem(file, attempt, false));
                }
            }
            mHistoryLiveData.postValue(result);
        }

        void loadReport(long fileId, long attemptId, boolean latest) {
            mExecutor.execute(() -> {
                VtFile file = null;
                VtScanAttempt attempt = attemptId == 0 ? null : mRepository.getAttempt(attemptId);
                if (attempt != null) {
                    mRepository.markAttemptRead(attemptId);
                    file = mRepository.getFile(attempt.fileId);
                } else if (latest) {
                    mRepository.markLatestRead(fileId);
                    file = mRepository.getFile(fileId);
                }
                String json = attempt == null ? file == null ? null : file.latestReportJson : attempt.reportJson;
                if (json == null) {
                    mErrorLiveData.postValue(getApplication().getString(R.string.vt_report_unavailable));
                    return;
                }
                try {
                    mReportLiveData.postValue(new VtFileReport(new org.json.JSONObject(json)));
                } catch (JSONException e) {
                    mErrorLiveData.postValue(getApplication().getString(R.string.vt_report_unavailable));
                }
            });
        }

        void startAttempt(long fileId, @NonNull String type) {
            mExecutor.execute(() -> {
                VtScanAttempt attempt = mRepository.retryAttempt(fileId, type);
                if (VtScanAttempt.STATUS_QUEUED.equals(attempt.status)) {
                    mStartScanLiveData.postValue(true);
                }
                loadHistoryInternal();
            });
        }

        void cancelAttempt(long attemptId) {
            mExecutor.execute(() -> {
                mRepository.cancelAttempt(attemptId);
                getApplication().startService(new Intent(getApplication(), VtScanService.class)
                        .setAction(VtScanService.ACTION_CANCEL)
                        .putExtra(VtScanService.EXTRA_ATTEMPT_ID, attemptId));
                loadHistoryInternal();
            });
        }

        void approveUpload(long attemptId) {
            mExecutor.execute(() -> {
                mRepository.approveUpload(attemptId);
                mStartScanLiveData.postValue(true);
                loadHistoryInternal();
            });
        }

        void rejectUpload(long attemptId) {
            mExecutor.execute(() -> {
                mRepository.rejectUpload(attemptId);
                loadHistoryInternal();
            });
        }

        void deleteAttempt(long attemptId) {
            mExecutor.execute(() -> {
                mRepository.deleteAttempt(attemptId);
                loadHistoryInternal();
            });
        }

        void deleteFile(long fileId) {
            mExecutor.execute(() -> {
                mRepository.deleteFile(fileId);
                loadHistoryInternal();
            });
        }

        void clearHistory() {
            mExecutor.execute(() -> {
                mRepository.clearHistory();
                loadHistoryInternal();
            });
        }

        void setRead(@NonNull VtHistoryItem item, boolean wasUnread) {
            mExecutor.execute(() -> {
                if (item.latest) {
                    if (wasUnread) {
                        mRepository.markLatestRead(item.file.id);
                    } else mRepository.markLatestUnread(item.file.id);
                } else if (wasUnread) {
                    mRepository.markAttemptRead(item.attempt.id);
                } else {
                    mRepository.markAttemptUnread(item.attempt.id);
                }
                loadHistoryInternal();
            });
        }

        @Override
        protected void onCleared() {
            mExecutor.shutdownNow();
            mRepository.close();
            super.onCleared();
        }
    }

    static class VtHistoryAdapter extends RecyclerView.ListAdapter<VtHistoryItem, VtHistoryAdapter.ViewHolder> {
        private final VtHistoryActivity mActivity;
        private final int mSuccessColor;
        private final int mFailureColor;
        private static final DiffUtil.ItemCallback<VtHistoryItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<VtHistoryItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull VtHistoryItem a, @NonNull VtHistoryItem b) {
                return a.file.id == b.file.id && Objects.equals(a.attempt == null ? null : a.attempt.id,
                        b.attempt == null ? null : b.attempt.id) && a.latest == b.latest;
            }

            @Override
            public boolean areContentsTheSame(@NonNull VtHistoryItem a, @NonNull VtHistoryItem b) {
                return a.file.updatedAt == b.file.updatedAt
                        && a.file.latestReadAt == b.file.latestReadAt
                        && Objects.equals(a.attempt == null ? null : a.attempt.status,
                        b.attempt == null ? null : b.attempt.status)
                        && (a.attempt == null ? 0 : a.attempt.updatedAt)
                        == (b.attempt == null ? 0 : b.attempt.updatedAt)
                        && (a.attempt == null ? 0 : a.attempt.readAt)
                        == (b.attempt == null ? 0 : b.attempt.readAt);
            }
        };

        VtHistoryAdapter(@NonNull VtHistoryActivity activity) {
            super(DIFF_CALLBACK);
            mActivity = activity;
            mSuccessColor = ColorCodes.getSuccessColor(activity);
            mFailureColor = ColorCodes.getFailureColor(activity);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_vt_history, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            VtHistoryItem item = getItem(position);
            VtScanAttempt attempt = item.attempt;
            boolean active = attempt != null && isActive(attempt.status);
            boolean hasReport = item.latest ? item.file.latestReportJson != null
                    : attempt != null && attempt.reportJson != null;
            boolean unread = item.latest ? hasReport && item.file.latestReadAt == 0
                    : hasReport && attempt.readAt == 0;
            holder.name.setText(item.file.displayName == null ? item.file.sha256 : item.file.displayName);
            holder.hash.setText(item.file.sha256);
            if (active) {
                holder.status.setText(mActivity.getLocalizedStatus(attempt.status));
            } else if (hasReport) {
                Integer detectedValue = item.latest ? item.file.latestDetected : attempt.detected;
                Integer totalValue = item.latest ? item.file.latestTotal : attempt.total;
                int detected = detectedValue == null ? 0 : detectedValue;
                int total = totalValue == null ? 0 : totalValue;
                holder.status.setText(mActivity.getString(R.string.vt_success, detected, total));
            } else {
                if (attempt == null) {
                    holder.status.setText(R.string.vt_report_unavailable);
                } else holder.status.setText(mActivity.getLocalizedStatus(attempt.status));
            }
            holder.name.setTypeface(unread ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            if (unread) {
                Integer detectedValue = item.latest ? item.file.latestDetected : attempt.detected;
                boolean unsafe = detectedValue != null && detectedValue > 0;
                holder.card.setStrokeColor(unsafe ? mFailureColor : mSuccessColor);
            } else if (unread && attempt != null && VtScanAttempt.STATUS_FAILED.equals(attempt.status)) {
                holder.card.setStrokeColor(mFailureColor);
            } else {
                holder.card.setStrokeColor(ColorCodes.getListItemDefaultIndicatorColor(mActivity));
            }
            holder.itemView.setOnClickListener(v -> {
                if (active && VtScanAttempt.STATUS_PENDING_CONSENT.equals(attempt.status)) {
                    mActivity.requestUploadConsent(attempt,
                            item.file.displayName == null ? item.file.sha256 : item.file.displayName);
                    return;
                }
                if (!hasReport) {
                    return;
                }
                if (item.latest) {
                    mActivity.openReport(item.file.id);
                } else mActivity.openAttemptReport(attempt.id);
            });
            holder.more.setOnClickListener(v -> showMenu(holder.more, item, hasReport, active, unread));
        }

        private void showMenu(@NonNull View anchor, @NonNull VtHistoryItem item, boolean hasReport,
                              boolean active, boolean unread) {
            PopupMenu popupMenu = new PopupMenu(mActivity, anchor);
            popupMenu.setForceShowIcon(true);
            Menu menu = popupMenu.getMenu();
            if (hasReport) {
                menu.add(R.string.vt_view_report).setOnMenuItemClickListener(i -> {
                    if (item.latest) mActivity.openReport(item.file.id);
                    else mActivity.openAttemptReport(item.attempt.id);
                    return true;
                });
                menu.add(unread ? R.string.mark_as_read : R.string.mark_as_unread)
                        .setOnMenuItemClickListener(i -> {
                            mActivity.setRead(item, unread);
                            return true;
                        });
            }
            if (active && VtScanAttempt.STATUS_PENDING_CONSENT.equals(item.attempt.status)) {
                menu.add(R.string.vt_confirm_upload_and_scan).setOnMenuItemClickListener(i -> {
                    mActivity.requestUploadConsent(item.attempt,
                            item.file.displayName == null ? item.file.sha256 : item.file.displayName);
                    return true;
                });
                menu.add(R.string.vt_cancel_scan).setOnMenuItemClickListener(i -> {
                    mActivity.cancelAttempt(item.attempt.id);
                    return true;
                });
            } else if (active) {
                menu.add(R.string.vt_cancel_scan).setOnMenuItemClickListener(i -> {
                    mActivity.cancelAttempt(item.attempt.id);
                    return true;
                });
            } else if (item.latest) {
                menu.add(R.string.vt_update_report).setOnMenuItemClickListener(i -> {
                    mActivity.startAttempt(item.file.id, VtScanAttempt.TYPE_UPDATE);
                    return true;
                });
                if (hasReport) menu.add(R.string.vt_rescan).setOnMenuItemClickListener(i -> {
                    mActivity.startAttempt(item.file.id, VtScanAttempt.TYPE_RESCAN);
                    return true;
                });
                menu.add(R.string.delete).setOnMenuItemClickListener(i -> {
                    mActivity.deleteFile(item.file.id);
                    return true;
                });
            } else {
                menu.add(R.string.vt_retry).setOnMenuItemClickListener(i -> {
                    mActivity.startAttempt(item.file.id, item.attempt.type);
                    return true;
                });
                menu.add(R.string.delete).setOnMenuItemClickListener(i -> {
                    mActivity.deleteAttempt(item.attempt.id);
                    return true;
                });
            }
            popupMenu.show();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final TextView name, status, hash;
            final View more;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                card = (MaterialCardView) itemView;
                name = itemView.findViewById(R.id.name);
                status = itemView.findViewById(R.id.status);
                hash = itemView.findViewById(R.id.hash);
                more = itemView.findViewById(R.id.more);
            }
        }
    }
}
