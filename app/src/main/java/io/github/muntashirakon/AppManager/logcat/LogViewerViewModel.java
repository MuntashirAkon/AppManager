// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.Manifest;
import android.app.Application;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.compat.PermissionCompat;
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper;
import io.github.muntashirakon.AppManager.logcat.reader.LogcatReader;
import io.github.muntashirakon.AppManager.logcat.reader.LogcatReaderLoader;
import io.github.muntashirakon.AppManager.logcat.struct.LogLine;
import io.github.muntashirakon.AppManager.logcat.struct.SavedLog;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.AppManager.utils.PermissionUtils;
import io.github.muntashirakon.AppManager.utils.UiThreadHandler;

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
    private LogcatReader mReader;
    @Nullable
    private WeakReference<LogLinesAvailableInterface> logLinesAvailableInterface;

    private final Pattern mFilterPattern;
    private final MutableLiveData<Boolean> mLoggingFinishedLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> mLoadingProgressLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> mTruncatedLinesLiveData = new MutableLiveData<>();
    private final MultithreadedExecutor mExecutor = MultithreadedExecutor.getNewInstance();

    public LogViewerViewModel(@NonNull Application application) {
        super(application);
        mFilterPattern = Pattern.compile(AppPref.getString(AppPref.PrefKey.PREF_LOG_VIEWER_FILTER_PATTERN_STR));
    }

    @Override
    protected void onCleared() {
        killLogcatReaderInternal();
        mExecutor.shutdown();
        if (logLinesAvailableInterface != null) {
            logLinesAvailableInterface.clear();
        }
        super.onCleared();
    }

    @AnyThread
    public void grantReadLogsPermission() {
        if (!PermissionUtils.hasPermission(getApplication(), Manifest.permission.READ_LOGS) && Ops.isPrivileged()) {
            mExecutor.submit(() -> {
                try {
                    PermissionCompat.grantPermission(getApplication().getPackageName(), Manifest.permission.READ_LOGS,
                            UserHandleHidden.myUserId());
                } catch (RemoteException e) {
                    Log.d(TAG, e.toString());
                }
            });
        }
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

    public void setLogLinesAvailableInterface(@Nullable LogLinesAvailableInterface logLinesAvailableInterface) {
        this.logLinesAvailableInterface = new WeakReference<>(logLinesAvailableInterface);
    }

    @AnyThread
    public void startLogcat() {
        mExecutor.submit(() -> {
            mKilled = false;
            try {
                // use "recordingMode" because we want to load all the existing lines at once
                // for a performance boost
                LogcatReaderLoader loader = LogcatReaderLoader.create(true);
                mReader = loader.loadReader();

                int maxLines = AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_DISPLAY_LIMIT_INT);

                String line;
                LinkedList<LogLine> initialLines = new LinkedList<>();
                while ((line = mReader.readLine()) != null && !Thread.currentThread().isInterrupted()) {
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
                        sendNewLogs(initialLines);
                        initialLines.clear();
                    } else {
                        // just proceed as normal
                        sendNewLogs(Collections.singletonList(logLine));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, e);
            } finally {
                killLogcatReaderInternal();
            }
            mLoggingFinishedLiveData.postValue(true);
        });
    }

    private void sendNewLogs(List<LogLine> logLines) {
        if (logLinesAvailableInterface != null) {
            LogLinesAvailableInterface i = logLinesAvailableInterface.get();
            if (i != null) {
                UiThreadHandler.run(() -> i.onNewLogsAvailable(logLines));
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
    public void openLogsFromFile(String filename) {
        mExecutor.submit(() -> {
            // remove any lines at the beginning if necessary
            final int maxLines = AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_DISPLAY_LIMIT_INT);
            SavedLog savedLog;
            try {
                savedLog = SaveLogHelper.openLog(filename, maxLines);
            } catch (IOException e) {
                Log.e(TAG, e);
                sendNewLogs(Collections.emptyList());
                return;
            }
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
            sendNewLogs(logLines);
            if (savedLog.isTruncated()) {
                mTruncatedLinesLiveData.postValue(maxLines);
            }
        });
    }
}
