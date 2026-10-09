// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner.vt;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.entity.VtFile;
import io.github.muntashirakon.AppManager.db.entity.VtScanAttempt;
import io.github.muntashirakon.AppManager.progress.NotificationProgressHandler;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.io.Path;

public class VtScanService extends Service {
    public static final String ACTION_RUN = BuildConfig.APPLICATION_ID + ".action.VT_RUN";
    public static final String ACTION_CANCEL = BuildConfig.APPLICATION_ID + ".action.VT_CANCEL";
    public static final String EXTRA_ATTEMPT_ID = BuildConfig.APPLICATION_ID + ".extra.VT_ATTEMPT_ID";
    private static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.VT_SCAN";
    private static final String RESULT_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.VT_SCAN_RESULTS";
    private static final int NOTIFICATION_ID = 0x5654;
    private static final long POLL_DELAY_MS = 30_000L;
    private static final int MAX_POLLS = 20;
    private static final int MAX_CONCURRENT_SCANS = 3;

    private final ExecutorService mDispatcher = Executors.newSingleThreadExecutor();
    private final ExecutorService mScanExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_SCANS);
    private final Map<Long, Future<?>> mRunningScans = new ConcurrentHashMap<>();
    private volatile boolean mStopping;
    @Nullable
    private VtScanRepository mRepository;
    @Nullable
    private NotificationProgressHandler mProgressHandler;

    @NonNull
    public static Intent getIntent(@NonNull Context context) {
        return new Intent(context, VtScanService.class).setAction(ACTION_RUN);
    }

    public static void start(@NonNull Context context) {
        ContextCompat.startForegroundService(context, getIntent(context));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mRepository = new VtScanRepository();
        NotificationUtils.getNewNotificationManager(this, CHANNEL_ID, "VirusTotal",
                NotificationManagerCompat.IMPORTANCE_LOW);
        NotificationUtils.getNewNotificationManager(this, RESULT_CHANNEL_ID, "VirusTotal results",
                NotificationManagerCompat.IMPORTANCE_DEFAULT);
        mProgressHandler = new NotificationProgressHandler(this,
                new NotificationProgressHandler.NotificationManagerInfo(CHANNEL_ID, "VirusTotal",
                        NotificationManagerCompat.IMPORTANCE_LOW),
                new NotificationProgressHandler.NotificationManagerInfo(RESULT_CHANNEL_ID, "VirusTotal results",
                        NotificationManagerCompat.IMPORTANCE_DEFAULT), null);
        startForeground();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            cancelAttempt(intent.getLongExtra(EXTRA_ATTEMPT_ID, 0));
        }
        scheduleDispatch();
        return START_STICKY;
    }

    private void scheduleDispatch() {
        if (!mStopping) {
            mDispatcher.submit(this::dispatch);
        }
    }

    private void dispatch() {
        if (mStopping || mRepository == null) {
            return;
        }
        List<VtScanAttempt> attempts = mRepository.getActiveAttempts();
        boolean hasRunnableAttempt = false;
        for (VtScanAttempt attempt : attempts) {
            if (VtScanAttempt.STATUS_PENDING_CONSENT.equals(attempt.status)) {
                continue;
            }
            hasRunnableAttempt = true;
            if (mRunningScans.containsKey(attempt.id)) {
                continue;
            }
            FutureTask<Void> future = new FutureTask<>(() -> {
                ScanProgress progress = new ScanProgress(attempt);
                try {
                    execute(attempt, progress);
                } finally {
                    progress.detach();
                    mRunningScans.remove(attempt.id);
                    scheduleDispatch();
                }
                return null;
            });
            if (mRunningScans.putIfAbsent(attempt.id, future) == null) {
                mScanExecutor.execute(future);
            }
        }
        if (!hasRunnableAttempt && mRunningScans.isEmpty()) {
            stopSelf();
        }
    }

    private void cancelAttempt(long attemptId) {
        if (attemptId == 0) return;
        if (mRepository != null) {
            mRepository.cancelAttempt(attemptId);
        }
        Future<?> future = mRunningScans.get(attemptId);
        if (future != null) {
            future.cancel(true);
        }
    }

    private void execute(@NonNull VtScanAttempt attempt, @NonNull ScanProgress progress) {
        VirusTotal virusTotal = VirusTotal.getInstance();
        if (virusTotal == null) {
            fail(attempt.id, "Unavailable", "VirusTotal is disabled or no API key is configured.", progress);
            return;
        }
        try {
            VtScanRepository repository = requireRepository();
            VtFile file = repository.getFile(attempt.fileId);
            if (file == null) {
                throw new IOException("VirusTotal file record no longer exists.");
            }
            progress.setTitle(file.displayName == null ? file.sha256 : file.displayName);
            String analysisId = attempt.analysisId;
            VtFileReport report;
            if (VtScanAttempt.STATUS_ANALYSING.equals(attempt.status) && analysisId != null) {
                report = pollAnalysis(virusTotal, file.sha256, analysisId, attempt.id, progress);
            } else if (VtScanAttempt.TYPE_RESCAN.equals(attempt.type)) {
                repository.setAttemptStatus(attempt.id, VtScanAttempt.STATUS_ANALYSING);
                progress.update(getString(R.string.vt_checking));
                VirusTotal.ResponseV3<String> response = virusTotal.requestRescan(file.sha256);
                if (!response.isSuccessful()) throw error(response);
                analysisId = response.response;
                repository.setAnalysisId(attempt.id, analysisId);
                report = pollAnalysis(virusTotal, file.sha256, analysisId, attempt.id, progress);
            } else if (VtScanAttempt.TYPE_UPLOAD.equals(attempt.type)) {
                report = uploadAndPoll(virusTotal, file, attempt, progress);
                VtScanAttempt updated = repository.getAttempt(attempt.id);
                analysisId = updated == null ? null : updated.analysisId;
            } else {
                repository.setAttemptStatus(attempt.id, VtScanAttempt.STATUS_LOOKING_UP);
                progress.update(getString(R.string.vt_checking));
                VirusTotal.ResponseV3<VtFileReport> response = virusTotal.fetchFileReport(file.sha256);
                if (response.isSuccessful() && response.response.hasReport()) {
                    report = response.response;
                } else if (VtScanAttempt.TYPE_LOOKUP.equals(attempt.type)
                        && response.error != null && "NotFoundError".equals(response.error.code)) {
                    report = uploadAndPoll(virusTotal, file, attempt, progress);
                    VtScanAttempt updated = repository.getAttempt(attempt.id);
                    analysisId = updated == null ? null : updated.analysisId;
                } else {
                    throw error(response);
                }
            }
            repository.completeAttempt(attempt.id, report, analysisId);
            progress.complete(getString(R.string.vt_success, report.getPositives(), report.getTotal()),
                    file.displayName == null ? file.sha256 : file.displayName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            VtScanAttempt current = requireRepository().getAttempt(attempt.id);
            if (current != null && VtScanAttempt.STATUS_CANCELLED.equals(current.status)) {
                progress.cancelled();
            } else if (!mStopping) {
                fail(attempt.id, "Interrupted", "Scan interrupted.", progress);
            }
        } catch (Throwable e) {
            VtScanAttempt current = requireRepository().getAttempt(attempt.id);
            if (current != null && VtScanAttempt.STATUS_CANCELLED.equals(current.status)) {
                progress.cancelled();
            } else if (!mStopping) {
                fail(attempt.id, e.getClass().getSimpleName(), e.getMessage(), progress);
            }
        }
    }

    @NonNull
    private VtFileReport uploadAndPoll(@NonNull VirusTotal virusTotal, @NonNull VtFile file,
                                       @NonNull VtScanAttempt attempt, @NonNull ScanProgress progress)
            throws IOException, InterruptedException {
        VtScanRepository repository = requireRepository();
        Path path = repository.resolveSource(file.id);
        if (path.length() > 650_000_000L) throw new IOException("File is larger than 650 MB.");
        repository.setAttemptStatus(attempt.id, VtScanAttempt.STATUS_UPLOADING);
        progress.update(getString(R.string.vt_uploading));
        VirusTotal.ResponseV3<String> response;
        try (InputStream inputStream = path.openInputStream()) {
            response = path.length() > 32_000_000L
                    ? virusTotal.uploadLargeFile(path.getName(), inputStream)
                    : virusTotal.uploadFile(path.getName(), inputStream);
        }
        if (!response.isSuccessful()) throw error(response);
        String analysisId = response.response;
        repository.setAnalysisId(attempt.id, analysisId);
        return pollAnalysis(virusTotal, file.sha256, analysisId, attempt.id, progress);
    }

    @NonNull
    private VtFileReport pollAnalysis(@NonNull VirusTotal virusTotal, @NonNull String fileId,
                                      @NonNull String analysisId, long attemptId,
                                      @NonNull ScanProgress progress)
            throws IOException, InterruptedException {
        requireRepository().setAttemptStatus(attemptId, VtScanAttempt.STATUS_ANALYSING);
        for (int i = 0; i < MAX_POLLS; ++i) {
            VirusTotal.ResponseV3<VtAnalysis> analysis = virusTotal.fetchAnalysis(analysisId);
            if (analysis.isSuccessful() && analysis.response.isCompleted()) {
                return pollReport(virusTotal, fileId, attemptId, progress);
            }
            if (!analysis.isSuccessful() && !analysis.shouldRetry()) throw error(analysis);
            progress.update(getString(R.string.vt_checking), i + 1, MAX_POLLS);
            waitForNextPoll();
        }
        throw new IOException("VirusTotal analysis timed out.");
    }

    @NonNull
    private VtFileReport pollReport(@NonNull VirusTotal virusTotal, @NonNull String fileId,
                                    long attemptId, @NonNull ScanProgress progress)
            throws IOException, InterruptedException {
        requireRepository().setAttemptStatus(attemptId, VtScanAttempt.STATUS_LOOKING_UP);
        for (int i = 0; i < MAX_POLLS; ++i) {
            VirusTotal.ResponseV3<VtFileReport> response = virusTotal.fetchFileReport(fileId);
            if (response.isSuccessful() && response.response.hasReport()) return response.response;
            if (!response.shouldRetry() && !response.isSuccessful()) throw error(response);
            progress.update(getString(R.string.vt_checking), i + 1, MAX_POLLS);
            waitForNextPoll();
        }
        throw new IOException("VirusTotal report timed out.");
    }

    private void waitForNextPoll() throws InterruptedException {
        Thread.sleep(POLL_DELAY_MS);
    }

    @NonNull
    private IOException error(@NonNull VirusTotal.ResponseV3<?> response) {
        if (response.error == null) return new IOException("VirusTotal request failed.");
        String message = response.error.message != null ? response.error.message : response.error.toString();
        return new IOException(response.error.code + ": " + message);
    }

    private void fail(long attemptId, @NonNull String code, @Nullable String message,
                      @NonNull ScanProgress progress) {
        if (mRepository != null) mRepository.failAttempt(attemptId, code, message);
        progress.failed(message == null ? code : message);
    }

    @NonNull
    private VtScanRepository requireRepository() {
        if (mRepository == null) {
            throw new IllegalStateException("Service is not initialized.");
        }
        return mRepository;
    }

    private void startForeground() {
        ForegroundService.start(this, NOTIFICATION_ID, new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_default_notification)
                        .setContentTitle("VirusTotal")
                        .setContentText(getString(R.string.operation_running))
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .setSilent(true)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .build(),
                ForegroundService.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        | ForegroundService.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    }

    private class ScanProgress {
        private final VtScanAttempt mAttempt;
        private final ProgressHandler mHandler;
        private final AtomicReference<CharSequence> mText = new AtomicReference<>(getString(R.string.vt_queued));
        private String mTitle;

        ScanProgress(@NonNull VtScanAttempt attempt) {
            mAttempt = attempt;
            NotificationProgressHandler root = mProgressHandler;
            if (root == null) {
                throw new IllegalStateException("Progress handler is not initialized.");
            }
            mHandler = root.newSubProgressHandler();
            mHandler.setProgressTextInterface(handler -> mText.get());
            VtFile file = mRepository == null ? null : mRepository.getFile(attempt.fileId);
            mTitle = file == null || file.displayName == null ? "VirusTotal scan #" + attempt.id : file.displayName;
            NotificationProgressHandler.NotificationInfo info = createInfo(mText.get());
            ThreadUtils.postOnMainThread(() -> {
                mHandler.onAttach(null, info);
                mHandler.onProgressStart(-1, 0, null);
            });
        }

        void setTitle(@NonNull String title) {
            mTitle = title;
        }

        void update(@NonNull CharSequence text) {
            update(text, -1, 0);
        }

        void update(@NonNull CharSequence text, int current, int max) {
            mText.set(text);
            if (max < 0) {
                mHandler.postUpdate(-1, 0);
            } else mHandler.postUpdate(max, current);
        }

        void complete(@NonNull String body, @NonNull String resultText) {
            mText.set(body);
            postResult(isNotificationAllowed() ? createInfo(body).setBody(resultText) : null);
        }

        void failed(@NonNull String body) {
            mText.set(getString(R.string.vt_failed));
            postResult(isNotificationAllowed() ? createInfo(getString(R.string.vt_failed)).setBody(body) : null);
        }

        void cancelled() {
            mText.set(getString(R.string.vt_cancel_scan));
            postResult(isNotificationAllowed() ? createInfo(getString(R.string.vt_cancel_scan)) : null);
        }

        void detach() {
            ThreadUtils.postOnMainThread(() -> mHandler.onDetach(null));
        }

        private void postResult(@Nullable NotificationProgressHandler.NotificationInfo info) {
            ThreadUtils.postOnMainThread(() -> mHandler.onResult(info));
        }

        @NonNull
        private NotificationProgressHandler.NotificationInfo createInfo(@NonNull CharSequence body) {
            Intent openIntent = new Intent(VtScanService.this, VtHistoryActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent open = PendingIntentCompat.getActivity(VtScanService.this,
                    (int) mAttempt.id, openIntent, PendingIntent.FLAG_UPDATE_CURRENT, false);
            Intent cancelIntent = new Intent(VtScanService.this, VtScanService.class)
                    .setAction(ACTION_CANCEL).putExtra(EXTRA_ATTEMPT_ID, mAttempt.id);
            PendingIntent cancel = PendingIntentCompat.getService(VtScanService.this,
                    (int) mAttempt.id, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT, false);
            return new NotificationProgressHandler.NotificationInfo()
                    .setTitle(mTitle).setOperationName("VirusTotal").setBody(body)
                    .setDefaultAction(open).setAutoCancel(false)
                    .addAction(R.drawable.ic_default_notification, getString(R.string.vt_cancel_scan), cancel);
        }

        private boolean isNotificationAllowed() {
            return !VtHistoryActivity.isVisible();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        mStopping = true;
        for (Future<?> future : mRunningScans.values()) future.cancel(true);
        mDispatcher.shutdownNow();
        mScanExecutor.shutdownNow();
        if (mRepository != null) mRepository.close();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
