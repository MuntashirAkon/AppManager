// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.reader;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.logcat.helper.LogcatHelper;
import io.github.muntashirakon.AppManager.logcat.helper.PreferenceHelper;
import io.github.muntashirakon.util.ParcelUtils;

// Copyright 2012 Nolan Lawson
// Copyright 2021 Muntashir Al-Islam
public class LogcatReaderLoader implements Parcelable {
    private final Map<Integer, String> mLastLines;
    private final boolean mRecordingMode;
    private final boolean mMultipleBuffers;

    private LogcatReaderLoader(@LogcatHelper.LogBufferId @NonNull List<Integer> buffers, boolean recordingMode) {
        this.mRecordingMode = recordingMode;
        this.mMultipleBuffers = buffers.size() > 1;
        this.mLastLines = new HashMap<>();
        for (Integer buffer : buffers) {
            // No need to grab the last line if this isn't recording mode
            String lastLine = recordingMode ? LogcatHelper.getLastLogLine(buffer) : null;
            mLastLines.put(buffer, lastLine);
        }
    }

    @NonNull
    public static LogcatReaderLoader create(boolean recordingMode) {
        List<Integer> buffers = PreferenceHelper.getBuffers();
        return new LogcatReaderLoader(buffers, recordingMode);
    }

    public LogcatReader loadReader() throws IOException {
        LogcatReader reader;
        if (!mMultipleBuffers) {
            // single reader
            Integer buffers = mLastLines.keySet().iterator().next();
            String lastLine = mLastLines.values().iterator().next();
            reader = new SingleLogcatReader(mRecordingMode, buffers, lastLine);
        } else {
            // multiple reader
            reader = new MultipleLogcatReader(mRecordingMode, mLastLines);
        }

        return reader;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<LogcatReaderLoader> CREATOR = new Parcelable.Creator<LogcatReaderLoader>() {
        public LogcatReaderLoader createFromParcel(Parcel in) {
            return new LogcatReaderLoader(in);
        }

        public LogcatReaderLoader[] newArray(int size) {
            return new LogcatReaderLoader[size];
        }
    };

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mRecordingMode ? 1 : 0);
        dest.writeInt(mMultipleBuffers ? 1 : 0);
        ParcelUtils.writeMap(mLastLines, dest);
    }

    private LogcatReaderLoader(@NonNull Parcel in) {
        this.mRecordingMode = in.readInt() == 1;
        this.mMultipleBuffers = in.readInt() == 1;
        this.mLastLines = ParcelUtils.readMap(in, Integer.class.getClassLoader(), String.class.getClassLoader());
    }
}
