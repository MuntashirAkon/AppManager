// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner.vt;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.db.entity.VtFile;
import io.github.muntashirakon.AppManager.db.entity.VtScanAttempt;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.io.Path;

/**
 * Shared entry point for foreground callers that want to submit a file to VirusTotal.
 */
public final class VtScanCoordinator implements AutoCloseable {
    public interface VtScanCallback {
        void onConsentRequired(@NonNull VtScanAttempt attempt, @NonNull VtFile file);

        void onQueued(@NonNull VtScanAttempt attempt);

        void onCompleted(@NonNull VtFileReport report);

        void onFailed(@Nullable String message);
    }

    private final Context mContext;
    private final VtScanRepository mRepository = new VtScanRepository();
    private final ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();

    public VtScanCoordinator(@NonNull Context context) {
        mContext = context.getApplicationContext();
    }

    public void scan(@NonNull Path path, @NonNull String sourceUri, @Nullable String displayName,
                     @Nullable String mimeType, @Nullable String sourceType,
                     @NonNull VtScanCallback callback) {
        mExecutor.execute(() -> {
            try {
                VtScanRepository.ObservedFile observed = mRepository.observePath(path, sourceUri,
                        displayName, mimeType, sourceType);
                VtScanAttempt attempt = mRepository.createInitialAttempt(observed,
                        Prefs.VirusTotal.promptBeforeUpload());
                if (attempt == null) {
                    VtFile file = mRepository.getFile(observed.file.id);
                    if (file != null && file.latestReportJson != null) {
                        callback.onCompleted(new VtFileReport(new JSONObject(file.latestReportJson)));
                    } else callback.onFailed("VirusTotal report unavailable.");
                    return;
                }
                if (VtScanAttempt.STATUS_PENDING_CONSENT.equals(attempt.status)) {
                    callback.onConsentRequired(attempt, observed.file);
                } else {
                    callback.onQueued(attempt);
                    VtScanService.start(mContext);
                }
                await(attempt.id, callback);
            } catch (Throwable e) {
                callback.onFailed(e.getMessage());
            }
        });
    }

    public void approve(@NonNull VtScanAttempt attempt, @NonNull VtScanCallback callback) {
        mExecutor.execute(() -> {
            mRepository.approveUpload(attempt.id);
            callback.onQueued(attempt);
            VtScanService.start(mContext);
        });
    }

    public void reject(@NonNull VtScanAttempt attempt, @NonNull VtScanCallback callback) {
        mExecutor.execute(() -> {
            mRepository.rejectUpload(attempt.id);
            callback.onFailed("Upload cancelled.");
        });
    }

    private void await(long attemptId, @NonNull VtScanCallback callback) {
        mExecutor.schedule(() -> poll(attemptId, callback), 500, TimeUnit.MILLISECONDS);
    }

    private void poll(long attemptId, @NonNull VtScanCallback callback) {
        VtScanAttempt attempt = mRepository.getAttempt(attemptId);
        if (attempt == null) {
            callback.onFailed("VirusTotal scan was removed.");
        } else if (VtScanAttempt.STATUS_COMPLETED.equals(attempt.status)) {
            try {
                callback.onCompleted(new VtFileReport(new JSONObject(attempt.reportJson)));
            } catch (Throwable e) {
                callback.onFailed(e.getMessage());
            }
        } else if (VtScanAttempt.STATUS_FAILED.equals(attempt.status)
                || VtScanAttempt.STATUS_CANCELLED.equals(attempt.status)) {
            callback.onFailed(attempt.errorMessage);
        } else if (!mExecutor.isShutdown()) {
            // Schedule one poll after the previous one finishes
            mExecutor.schedule(() -> poll(attemptId, callback), 500, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void close() {
        mExecutor.shutdownNow();
        mRepository.close();
    }
}
