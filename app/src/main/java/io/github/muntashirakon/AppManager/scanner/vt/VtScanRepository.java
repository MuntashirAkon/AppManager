// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner.vt;

import android.net.Uri;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.List;

import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.dao.VtFileDao;
import io.github.muntashirakon.AppManager.db.dao.VtFileSourceDao;
import io.github.muntashirakon.AppManager.db.dao.VtScanAttemptDao;
import io.github.muntashirakon.AppManager.db.entity.VtFile;
import io.github.muntashirakon.AppManager.db.entity.VtFileSource;
import io.github.muntashirakon.AppManager.db.entity.VtScanAttempt;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class VtScanRepository implements Closeable {
    public static final int MAX_FILES = 100;
    public static final int MAX_ATTEMPTS_PER_FILE = 5;
    public static final int MAX_TERMINAL_ATTEMPTS = 500;

    @StringDef({
            SOURCE_INSTALLED_APK,
            SOURCE_PROCESS_EXECUTABLE,
            SOURCE_FILE_PICKER,
            SOURCE_SHARE_INTENT,
            SOURCE_VIEW_INTENT,
    })
    public @interface FileSource {
    }

    public static final String SOURCE_INSTALLED_APK = "installed_apk";
    public static final String SOURCE_PROCESS_EXECUTABLE = "process_executable";
    public static final String SOURCE_FILE_PICKER = "file_picker";
    public static final String SOURCE_SHARE_INTENT = "share_intent";
    public static final String SOURCE_VIEW_INTENT = "view_intent";

    private final AppsDb mDatabase;
    private final VtFileDao mFileDao;
    private final VtFileSourceDao mSourceDao;
    private final VtScanAttemptDao mAttemptDao;
    private final FileCache mFileCache = new FileCache();

    public VtScanRepository() {
        mDatabase = AppsDb.getInstance();
        mFileDao = mDatabase.vtFileDao();
        mSourceDao = mDatabase.vtFileSourceDao();
        mAttemptDao = mDatabase.vtScanAttemptDao();
    }

    @Override
    public void close() {
        mFileCache.close();
    }

    public static class ObservedFile {
        @NonNull
        public final Path path;
        @NonNull
        public final VtFile file;
        @NonNull
        public final VtFileSource source;

        private ObservedFile(@NonNull Path path, @NonNull VtFile file, @NonNull VtFileSource source) {
            this.path = path;
            this.file = file;
            this.source = source;
        }
    }

    /**
     * Resolves and stages a URI, then observes it by hash. The staged file is temporary
     * and is removed when this repository is closed.
     */
    @WorkerThread
    @NonNull
    public ObservedFile observeUri(@NonNull Uri uri, @Nullable String mimeType, @FileSource @Nullable String sourceType)
            throws IOException {
        Path sourcePath = Paths.get(uri);
        File cachedFile = mFileCache.getCachedFile(sourcePath);
        return observePath(Paths.get(cachedFile), uri.toString(), sourcePath.getName(), mimeType, sourceType);
    }

    @WorkerThread
    @NonNull
    public ObservedFile observePath(@NonNull Path path, @NonNull String sourceUri,
                                @Nullable String displayName, @Nullable String mimeType,
                                @FileSource @Nullable String sourceType)
            throws IOException {
        if (!path.isFile()) {
            throw new IOException("Not a readable file: " + path);
        }
        Pair<String, String>[] digests = DigestUtils.getDigests(path);
        long now = System.currentTimeMillis();
        VtFile observedFile = new VtFile();
        observedFile.md5 = digests[0].second;
        observedFile.sha1 = digests[1].second;
        observedFile.sha256 = digests[2].second;
        observedFile.displayName = displayName != null ? displayName : path.getName();
        observedFile.mimeType = mimeType;
        observedFile.sizeBytes = path.length();
        observedFile.lastAccessedAt = now;
        observedFile.createdAt = now;
        observedFile.updatedAt = now;

        final VtFileSource observedSource = new VtFileSource();
        final VtFile[] persistedFile = new VtFile[]{observedFile};
        mDatabase.runInTransaction(() -> {
            VtFile existing = mFileDao.getBySha256(observedFile.sha256);
            if (existing == null) {
                long id = mFileDao.insert(observedFile);
                if (id == -1) {
                    existing = mFileDao.getBySha256(observedFile.sha256);
                } else {
                    persistedFile[0].id = id;
                    existing = persistedFile[0];
                }
            }
            if (existing == null) {
                throw new IllegalStateException("Unable to persist VirusTotal file identity.");
            }
            if (existing != persistedFile[0]) {
                existing.md5 = persistedFile[0].md5;
                existing.sha1 = persistedFile[0].sha1;
                existing.displayName = persistedFile[0].displayName;
                existing.mimeType = persistedFile[0].mimeType;
                existing.sizeBytes = persistedFile[0].sizeBytes;
                existing.lastAccessedAt = now;
                existing.updatedAt = now;
                mFileDao.update(existing);
                persistedFile[0] = existing;
            }
            observedSource.fileId = persistedFile[0].id;
            observedSource.sourceUri = sourceUri;
            observedSource.displayName = persistedFile[0].displayName;
            observedSource.mimeType = mimeType;
            observedSource.lastSeenAt = now;
            observedSource.isReadable = true;
            observedSource.sourceType = sourceType;
            observedSource.id = mSourceDao.upsert(observedSource);
            pruneLocked();
        });
        return new ObservedFile(path, persistedFile[0], observedSource);
    }

    @WorkerThread
    @Nullable
    public VtScanAttempt createInitialAttempt(@NonNull ObservedFile observedFile,
                                              boolean promptBeforeUpload) {
        VtFile file = observedFile.file;
        if (file.latestReportJson != null) return null;
        VtScanAttempt activeAttempt = mAttemptDao.getActiveForFile(file.id);
        if (activeAttempt != null) return activeAttempt;
        return createAttempt(file.id, VtScanAttempt.TYPE_LOOKUP,
                promptBeforeUpload ? VtScanAttempt.STATUS_PENDING_CONSENT : VtScanAttempt.STATUS_QUEUED);
    }

    @WorkerThread
    @NonNull
    public VtScanAttempt createUpdateAttempt(long fileId) {
        return createAttempt(fileId, VtScanAttempt.TYPE_UPDATE, VtScanAttempt.STATUS_QUEUED);
    }

    @WorkerThread
    @NonNull
    public VtScanAttempt createRescanAttempt(long fileId) {
        return createAttempt(fileId, VtScanAttempt.TYPE_RESCAN, VtScanAttempt.STATUS_QUEUED);
    }

    @WorkerThread
    public void approveUpload(long attemptId) {
        updateAttemptStatus(attemptId, VtScanAttempt.STATUS_QUEUED);
    }

    @WorkerThread
    public void rejectUpload(long attemptId) {
        updateAttemptStatus(attemptId, VtScanAttempt.STATUS_CANCELLED);
    }

    @WorkerThread
    public void cancelAttempt(long attemptId) {
        updateAttemptStatus(attemptId, VtScanAttempt.STATUS_CANCELLED);
    }

    @WorkerThread
    @NonNull
    public VtScanAttempt retryAttempt(long fileId, @NonNull String type) {
        return createAttempt(fileId, type, VtScanAttempt.STATUS_QUEUED);
    }

    @WorkerThread
    @NonNull
    public List<VtFile> getFiles() {
        return mFileDao.getAll();
    }

    @WorkerThread
    @NonNull
    public List<VtScanAttempt> getAttempts(long fileId) {
        return mAttemptDao.getForFile(fileId);
    }

    @WorkerThread
    @NonNull
    public List<VtScanAttempt> getActiveAttempts() {
        return mAttemptDao.getActive();
    }

    @WorkerThread
    public void deleteAttempt(long attemptId) {
        mAttemptDao.delete(attemptId);
    }

    @WorkerThread
    public void deleteFile(long fileId) {
        mFileDao.delete(fileId);
    }

    @WorkerThread
    public void markLatestRead(long fileId) {
        mDatabase.runInTransaction(() -> {
            VtFile file = mFileDao.get(fileId);
            if (file == null) return;
            file.latestReadAt = System.currentTimeMillis();
            file.updatedAt = file.latestReadAt;
            mFileDao.update(file);
        });
    }

    @WorkerThread
    public void markLatestUnread(long fileId) {
        mDatabase.runInTransaction(() -> {
            VtFile file = mFileDao.get(fileId);
            if (file == null) return;
            file.latestReadAt = 0;
            file.updatedAt = System.currentTimeMillis();
            mFileDao.update(file);
        });
    }

    @WorkerThread
    public void markAttemptRead(long attemptId) {
        mDatabase.runInTransaction(() -> {
            VtScanAttempt attempt = mAttemptDao.get(attemptId);
            if (attempt == null) return;
            attempt.readAt = System.currentTimeMillis();
            attempt.updatedAt = attempt.readAt;
            mAttemptDao.update(attempt);
        });
    }

    @WorkerThread
    public void markAttemptUnread(long attemptId) {
        mDatabase.runInTransaction(() -> {
            VtScanAttempt attempt = mAttemptDao.get(attemptId);
            if (attempt == null) return;
            attempt.readAt = 0;
            attempt.updatedAt = System.currentTimeMillis();
            mAttemptDao.update(attempt);
        });
    }

    @WorkerThread
    public void clearHistory() {
        mDatabase.runInTransaction(mFileDao::deleteAll);
    }

    @WorkerThread
    @Nullable
    public VtFile getFile(long fileId) {
        return mFileDao.get(fileId);
    }

    @WorkerThread
    public void setLocalAnalysisJson(long fileId, @NonNull String json) {
        mDatabase.runInTransaction(() -> {
            VtFile file = mFileDao.get(fileId);
            if (file == null) return;
            file.localAnalysisJson = json;
            file.updatedAt = System.currentTimeMillis();
            mFileDao.update(file);
        });
    }

    @WorkerThread
    public void setLocalAnalysisJson(@NonNull String sha256, @NonNull String json) {
        VtFile file = mFileDao.getBySha256(sha256);
        if (file != null) setLocalAnalysisJson(file.id, json);
    }

    @WorkerThread
    @Nullable
    public VtScanAttempt getAttempt(long attemptId) {
        return mAttemptDao.get(attemptId);
    }

    /**
     * Resolves the most recently observed source for an upload.
     */
    @WorkerThread
    @NonNull
    public Path resolveSource(long fileId) throws IOException {
        List<VtFileSource> sources = mSourceDao.getForFile(fileId);
        IOException lastError = null;
        for (VtFileSource source : sources) {
            try {
                Path sourcePath = Paths.get(Uri.parse(source.sourceUri));
                File cachedFile = mFileCache.getCachedFile(sourcePath);
                Path path = Paths.get(cachedFile);
                if (path.isFile()) return path;
            } catch (IOException e) {
                lastError = e;
            }
        }
        throw lastError != null ? lastError : new IOException("No readable source for file " + fileId);
    }

    @WorkerThread
    public void setAttemptStatus(long attemptId, @NonNull String status) {
        mDatabase.runInTransaction(() -> {
            VtScanAttempt attempt = mAttemptDao.get(attemptId);
            if (attempt == null) return;
            long now = System.currentTimeMillis();
            if (attempt.startedAt == 0 && !VtScanAttempt.STATUS_PENDING_CONSENT.equals(status)
                    && !VtScanAttempt.STATUS_QUEUED.equals(status)) {
                attempt.startedAt = now;
            }
            attempt.status = status;
            attempt.updatedAt = now;
            mAttemptDao.update(attempt);
        });
    }

    @WorkerThread
    public void setAnalysisId(long attemptId, @NonNull String analysisId) {
        mDatabase.runInTransaction(() -> {
            VtScanAttempt attempt = mAttemptDao.get(attemptId);
            if (attempt == null) return;
            attempt.analysisId = analysisId;
            attempt.updatedAt = System.currentTimeMillis();
            mAttemptDao.update(attempt);
        });
    }

    @WorkerThread
    public void completeAttempt(long attemptId, @NonNull VtFileReport report,
                                @Nullable String analysisId) {
        mDatabase.runInTransaction(() -> {
            VtScanAttempt attempt = mAttemptDao.get(attemptId);
            VtFile file = attempt == null ? null : mFileDao.get(attempt.fileId);
            if (attempt == null || file == null) return;
            long now = System.currentTimeMillis();
            attempt.status = VtScanAttempt.STATUS_COMPLETED;
            attempt.analysisId = analysisId;
            attempt.permalink = report.permalink;
            attempt.reportJson = report.rawJson;
            attempt.detected = report.getPositives();
            attempt.total = report.getTotal();
            attempt.updatedAt = now;
            attempt.completedAt = now;
            mAttemptDao.update(attempt);
            file.latestReportJson = report.rawJson;
            file.latestDetected = report.getPositives();
            file.latestTotal = report.getTotal();
            file.latestAnalysisId = analysisId;
            file.permalink = report.permalink;
            file.latestAnalysisAt = report.scanDate;
            file.latestReadAt = 0;
            file.updatedAt = now;
            mFileDao.update(file);
            pruneLocked();
        });
    }

    @WorkerThread
    public void failAttempt(long attemptId, @Nullable String errorCode, @Nullable String message) {
        mDatabase.runInTransaction(() -> {
            VtScanAttempt attempt = mAttemptDao.get(attemptId);
            if (attempt == null) return;
            long now = System.currentTimeMillis();
            attempt.status = VtScanAttempt.STATUS_FAILED;
            attempt.errorCode = errorCode;
            attempt.errorMessage = message;
            attempt.updatedAt = now;
            attempt.completedAt = now;
            mAttemptDao.update(attempt);
            pruneLocked();
        });
    }

    @NonNull
    @WorkerThread
    private VtScanAttempt createAttempt(long fileId, @NonNull String type, @NonNull String status) {
        long now = System.currentTimeMillis();
        final VtScanAttempt[] result = new VtScanAttempt[1];
        mDatabase.runInTransaction(() -> {
            if (mFileDao.get(fileId) == null) {
                throw new IllegalArgumentException("Unknown VirusTotal file ID: " + fileId);
            }
            VtScanAttempt activeAttempt = mAttemptDao.getActiveForFile(fileId);
            if (activeAttempt != null) {
                result[0] = activeAttempt;
                return;
            }
            VtScanAttempt attempt = new VtScanAttempt();
            attempt.fileId = fileId;
            attempt.type = type;
            attempt.status = status;
            attempt.createdAt = now;
            attempt.updatedAt = now;
            attempt.id = mAttemptDao.insert(attempt);
            result[0] = attempt;
            pruneLocked();
        });
        return result[0];
    }

    @WorkerThread
    private void updateAttemptStatus(long attemptId, @NonNull String status) {
        mDatabase.runInTransaction(() -> {
            VtScanAttempt attempt = mAttemptDao.get(attemptId);
            if (attempt == null) return;
            long now = System.currentTimeMillis();
            attempt.status = status;
            attempt.updatedAt = now;
            if (VtScanAttempt.STATUS_CANCELLED.equals(status)) {
                attempt.completedAt = now;
            }
            mAttemptDao.update(attempt);
            pruneLocked();
        });
    }

    private void pruneLocked() {
        List<VtFile> files = mFileDao.getAll();
        int filesToRemove = files.size() - MAX_FILES;
        if (filesToRemove > 0) {
            List<Long> ids = mFileDao.getLeastRecentlyAccessedIds(filesToRemove);
            for (Long id : ids) {
                mFileDao.delete(id);
            }
        }
        files = mFileDao.getAll();
        for (VtFile file : files) {
            mAttemptDao.deleteOldTerminalForFile(file.id, MAX_ATTEMPTS_PER_FILE);
        }
        mAttemptDao.deleteOldTerminal(MAX_TERMINAL_ATTEMPTS);
    }
}
