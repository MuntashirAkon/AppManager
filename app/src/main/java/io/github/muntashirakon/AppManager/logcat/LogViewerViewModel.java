// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.LogFilter;
import io.github.muntashirakon.AppManager.logcat.helper.BuildHelper;
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper;
import io.github.muntashirakon.AppManager.logcat.reader.LogcatReader;
import io.github.muntashirakon.AppManager.logcat.reader.LogcatReaderLoader;
import io.github.muntashirakon.AppManager.logcat.struct.LogLine;
import io.github.muntashirakon.AppManager.logcat.struct.SavedLog;
import io.github.muntashirakon.AppManager.logcat.struct.SendLogDetails;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

// Copyright 2022 Muntashir Al-Islam
public class LogViewerViewModel extends AndroidViewModel {
    public static final String TAG = LogViewerViewModel.class.getSimpleName();

    public interface LogLinesAvailableInterface {
        @UiThread
        void onNewLogsAvailable(@NonNull List<LogLine> logLines);
    }

    private final Object mLock = new Object();

    private volatile boolean mPaused;
    private volatile boolean mKilled = true;
    private volatile boolean mCollapsedMode;
    private volatile int mLogLevel;
    private volatile LogcatReader mReader;

    private final Pattern mFilterPattern;
    private final MutableLiveData<Boolean> mExpandLogsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mLoggingFinishedLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> mLoadingProgressLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> mTruncatedLinesLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> mLogLevelLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<LogFilter>> mLogFiltersLiveData = new MutableLiveData<>();
    private final MutableLiveData<Path> mLogSavedLiveData = new MutableLiveData<>();
    private final MutableLiveData<SendLogDetails> mLogToBeSentLiveData = new MutableLiveData<>();
    private final MultithreadedExecutor mExecutor = MultithreadedExecutor.getNewInstance();

    public LogViewerViewModel(@NonNull Application application) {
        super(application);
        mFilterPattern = Pattern.compile(Prefs.LogViewer.getFilterPattern());
    }

    @Override
    protected void onCleared() {
        killLogcatReaderInternal();
        mExecutor.shutdown();
        super.onCleared();
    }

    public LiveData<Boolean> observeLoggingFinished() {
        return mLoggingFinishedLiveData;
    }

    public LiveData<Integer> observeLoadingProgress() {
        return mLoadingProgressLiveData;
    }

    public LiveData<Integer> observeTruncatedLines() {
        return mTruncatedLinesLiveData;
    }

    public LiveData<List<LogFilter>> getLogFilters() {
        return mLogFiltersLiveData;
    }

    public LiveData<Path> observeLogSaved() {
        return mLogSavedLiveData;
    }

    public MutableLiveData<Integer> observeLogLevelLiveData() {
        return mLogLevelLiveData;
    }

    public LiveData<SendLogDetails> getLogsToBeSent() {
        return mLogToBeSentLiveData;
    }

    public LiveData<Boolean> getExpandLogsLiveData() {
        return mExpandLogsLiveData;
    }

    @AnyThread
    public void startLogcat(@Nullable WeakReference<LogLinesAvailableInterface> logLinesAvailableInterface) {
        mExecutor.submit(() -> {
            mKilled = false;
            try {
                mReader = LogcatReaderLoader.create(true).loadReader();

                int maxLines = Prefs.LogViewer.getDisplayLimit();

                String line;
                LinkedList<LogLine> initialLines = new LinkedList<>();
                while ((line = mReader.readLine()) != null && !ThreadUtils.isInterrupted()) {
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
                            // Logcat is ready
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
                        sendNewLogs(initialLines, logLinesAvailableInterface);
                        initialLines.clear();
                    } else {
                        // just proceed as normal
                        sendNewLogs(Collections.singletonList(logLine), logLinesAvailableInterface);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, e);
            } finally {
                if (logLinesAvailableInterface != null) {
                    logLinesAvailableInterface.clear();
                }
                killLogcatReaderInternal();
            }
            mLoggingFinishedLiveData.postValue(true);
        });
    }

    @AnyThread
    public void restartLogcat() {
        mExecutor.submit(() -> {
            synchronized (mLock) {
                // Pause -> reload reader -> resume
                mPaused = true;
                try {
                    mReader = LogcatReaderLoader.create(true).loadReader();
                } catch (Exception e) {
                    // Errors do not matter
                    Log.e(TAG, e);
                } finally {
                    mPaused = false;
                    mLock.notify();
                }
            }
        });
    }

    private static void sendNewLogs(@NonNull List<LogLine> logLines, @Nullable WeakReference<LogLinesAvailableInterface> logLinesAvailableInterface) {
        if (logLinesAvailableInterface != null) {
            LogLinesAvailableInterface i = logLinesAvailableInterface.get();
            List<LogLine> logLines1 = new ArrayList<>(logLines);
            if (i != null) {
                ThreadUtils.postOnMainThread(() -> i.onNewLogsAvailable(logLines1));
            }
        }
    }

    @AnyThread
    public void pauseLogcat() {
        mExecutor.submit(() -> {
            synchronized (mLock) {
                mPaused = true;
            }
        });
    }

    @AnyThread
    public void resumeLogcat() {
        mExecutor.submit(() -> {
            synchronized (mLock) {
                mPaused = false;
                mLock.notify();
            }
        });
    }

    public boolean isLogcatPaused() {
        return mPaused;
    }

    public boolean isLogcatKilled() {
        return mKilled;
    }

    public boolean isCollapsedMode() {
        return mCollapsedMode;
    }

    public void setCollapsedMode(boolean collapsedMode) {
        mCollapsedMode = collapsedMode;
        mExpandLogsLiveData.postValue(collapsedMode);
    }

    public int getLogLevel() {
        return mLogLevel;
    }

    public void setLogLevel(int logLevel) {
        mLogLevel = logLevel;
        mLogLevelLiveData.postValue(mLogLevel);
    }

    @AnyThread
    public void killLogcatReader() {
        mExecutor.submit(this::killLogcatReaderInternal);
    }

    @WorkerThread
    private void killLogcatReaderInternal() {
        if (!mKilled) {
            synchronized (mLock) {
                if (!mKilled && mReader != null) {
                    mReader.killQuietly();
                    mKilled = true;
                }
            }
        }
    }

    @AnyThread
    public void openLogsFromFile(Uri filename, @Nullable WeakReference<LogLinesAvailableInterface> logLinesAvailableInterface) {
        mExecutor.submit(() -> {
            // remove any lines at the beginning if necessary
            final int maxLines = Prefs.LogViewer.getDisplayLimit();
            SavedLog savedLog;
            savedLog = SaveLogHelper.openLog(filename, maxLines);
            List<String> lines = savedLog.getLogLines();
            List<LogLine> logLines = new ArrayList<>();
            for (int lineNumber = 0, linesSize = lines.size(); lineNumber < linesSize; lineNumber++) {
                String line = lines.get(lineNumber);
                LogLine logLine = LogLine.newLogLine(line, !mCollapsedMode, mFilterPattern);
                if (logLine != null) {
                    logLines.add(logLine);
                }
                mLoadingProgressLiveData.postValue(lineNumber * 100 / linesSize);
            }
            sendNewLogs(logLines, logLinesAvailableInterface);
            if (savedLog.isTruncated()) {
                mTruncatedLinesLiveData.postValue(maxLines);
            }
        });
    }

    @AnyThread
    public void loadFilters() {
        mExecutor.submit(() -> {
            final List<LogFilter> filters = AppsDb.getInstance().logFilterDao().getAll();
            Collections.sort(filters);
            mLogFiltersLiveData.postValue(filters);
        });
    }

    @AnyThread
    public void saveLogs(String filename, @NonNull List<String> logLines) {
        mExecutor.submit(() -> {
            SaveLogHelper.deleteLogIfExists(filename);
            mLogSavedLiveData.postValue(SaveLogHelper.saveLog(logLines, filename));
        });
    }

    @AnyThread
    public void saveLogs(@NonNull Path path, @NonNull SendLogDetails sendLogDetails) {
        mExecutor.submit(() -> {
            if (sendLogDetails.getAttachmentType() == null || sendLogDetails.getAttachment() == null) {
                mLogSavedLiveData.postValue(null);
                return;
            }
            try (OutputStream output = path.openOutputStream()) {
                try (InputStream input = sendLogDetails.getAttachment().openInputStream()) {
                    IoUtils.copy(input, output);
                }
                mLogSavedLiveData.postValue(path);
            } catch (IOException e) {
                mLogSavedLiveData.postValue(null);
                e.printStackTrace();
            }
        });
    }

    @AnyThread
    public void prepareLogsToBeSent(boolean includeDeviceInfo, boolean includeDmesg, @NonNull Collection<String> logLines) {
        mExecutor.submit(() -> {
            SendLogDetails sendLogDetails = new SendLogDetails();
            sendLogDetails.setSubject(getApplication().getString(R.string.subject_log_report));
            // either zip up multiple files or just attach the one file
            String deviceInfo = null;
            if (includeDeviceInfo) {
                deviceInfo = BuildHelper.getBuildInformationAsString();
            }
            String dmesg = null;
            if (includeDmesg) {
                Runner.Result result = Runner.runCommand(new String[]{"dmesg"});
                if (result.isSuccessful()) {
                    dmesg = result.getOutput();
                    if (dmesg.length() == 0) {
                        dmesg = null;
                    }
                }
            }
            int exportCount = 0;
            if (!logLines.isEmpty()) {
                ++exportCount;
            }
            if (deviceInfo != null) {
                ++exportCount;
            }
            if (dmesg != null) {
                ++exportCount;
            }

            if (exportCount == 0) {
                sendLogDetails.setAttachmentType(null);
            } else if (exportCount == 1) {
                Path tempFile;
                if (!logLines.isEmpty()) {
                    tempFile = SaveLogHelper.saveTemporaryFile("log", null, logLines);
                } else if (dmesg != null) {
                    tempFile = SaveLogHelper.saveTemporaryFile("txt", dmesg, null);
                } else { // Device info
                    tempFile = SaveLogHelper.saveTemporaryFile("txt", deviceInfo, null);
                }
                sendLogDetails.setAttachmentType("text/plain");
                sendLogDetails.setAttachment(tempFile);
            } else { // Multiple attachments, make zip first
                try {
                    Path zipFile = Paths.get(FileCache.getGlobalFileCache().createCachedFile("zip"));
                    try (ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(zipFile.openOutputStream(), 0x1000))) {
                        if (!logLines.isEmpty()) {
                            output.putNextEntry(new ZipEntry(SaveLogHelper.LOG_FILENAME));
                            for (String logLine : logLines) {
                                output.write(logLine.getBytes(StandardCharsets.UTF_8));
                                output.write("\n".getBytes(StandardCharsets.UTF_8));
                            }
                        }
                        if (deviceInfo != null) {
                            output.putNextEntry(new ZipEntry(SaveLogHelper.DEVICE_INFO_FILENAME));
                            output.write(deviceInfo.getBytes(StandardCharsets.UTF_8));
                        }
                        if (dmesg != null) {
                            output.putNextEntry(new ZipEntry(SaveLogHelper.DMESG_FILENAME));
                            output.write(dmesg.getBytes(StandardCharsets.UTF_8));
                        }
                    }
                    sendLogDetails.setAttachmentType("application/zip");
                    sendLogDetails.setAttachment(zipFile);
                } catch (Throwable th) {
                    th.printStackTrace();
                    sendLogDetails.setAttachmentType(null);
                }
            }
            mLogToBeSentLiveData.postValue(sendLogDetails);
        });
    }
}
