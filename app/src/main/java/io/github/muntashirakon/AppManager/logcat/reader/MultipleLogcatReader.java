// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.reader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import io.github.muntashirakon.AppManager.logcat.helper.LogcatHelper;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

/**
 * Combines multiple buffered readers into a single reader that merges all input synchronously.
 */
// Copyright 2012 Nolan Lawson
public class MultipleLogcatReader extends AbsLogcatReader {
    public static final String TAG = MultipleLogcatReader.class.getSimpleName();

    private static final String DUMMY_NULL = "";  // Stop marker
    private final List<ReaderThread> mReaderThreads = new LinkedList<>();
    private final BlockingQueue<String> mQueue = new ArrayBlockingQueue<>(1);

    public MultipleLogcatReader(boolean recordingMode, Map<Integer, String> lastLines) throws IOException {
        super(recordingMode);
        // Read from all three buffers all at once
        for (Entry<Integer, String> entry : lastLines.entrySet()) {
            Integer buffers = entry.getKey();
            String lastLine = entry.getValue();
            ReaderThread readerThread = new ReaderThread(buffers, lastLine);
            readerThread.start();
            mReaderThreads.add(readerThread);
        }
    }

    public String readLine() throws IOException {
        try {
            String value = mQueue.take();
            if (!value.equals(DUMMY_NULL)) {
                return value;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, e);
        }
        return null;
    }


    @Override
    public boolean readyToRecord() {
        for (ReaderThread thread : mReaderThreads) {
            if (!thread.mReader.readyToRecord()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void killQuietly() {
        for (ReaderThread thread : mReaderThreads) {
            thread.mKilled = true;
        }
        // Kill all threads in the background
        ThreadUtils.postOnBackgroundThread(() -> {
            for (ReaderThread thread : mReaderThreads) {
                thread.mReader.killQuietly();
            }
            mQueue.offer(DUMMY_NULL);
        });
    }

    @Override
    public List<Process> getProcesses() {
        List<Process> result = new ArrayList<>();
        for (ReaderThread thread : mReaderThreads) {
            result.addAll(thread.mReader.getProcesses());
        }
        return result;
    }

    private class ReaderThread extends Thread {
        private final SingleLogcatReader mReader;
        private boolean mKilled;

        public ReaderThread(@LogcatHelper.LogBufferId int logBuffer, String lastLine) throws IOException {
            mReader = new SingleLogcatReader(recordingMode, logBuffer, lastLine);
        }

        @Override
        public void run() {
            String line;
            try {
                while (!mKilled && (line = mReader.readLine()) != null && !mKilled) {
                    mQueue.put(line);
                }
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, e);
            }
            Log.w(TAG, "Thread died");
        }
    }
}
