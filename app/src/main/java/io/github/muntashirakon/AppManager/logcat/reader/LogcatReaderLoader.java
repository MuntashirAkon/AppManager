// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.reader;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.logcat.helper.LogcatHelper;
import io.github.muntashirakon.AppManager.logcat.helper.PreferenceHelper;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Copyright 2012 Nolan Lawson
public class LogcatReaderLoader implements Parcelable {
    private final Map<Integer, String> lastLines;
    private final boolean recordingMode;
    private final boolean multiple;

    private LogcatReaderLoader(@LogcatHelper.LogBufferId @NonNull List<Integer> buffers, boolean recordingMode) {
        this.recordingMode = recordingMode;
        this.multiple = buffers.size() > 1;
        this.lastLines = new HashMap<>();
        for (Integer buffer : buffers) {
            // No need to grab the last line if this isn't recording mode
            String lastLine = recordingMode ? LogcatHelper.getLastLogLine(buffer) : null;
            lastLines.put(buffer, lastLine);
        }
    }

    @NonNull
    public static LogcatReaderLoader create(boolean recordingMode) {
        List<Integer> buffers = PreferenceHelper.getBuffers();
        return new LogcatReaderLoader(buffers, recordingMode);
    }

    public LogcatReader loadReader() throws IOException {
        LogcatReader reader;
        if (!multiple) {
            // single reader
            Integer buffers = lastLines.keySet().iterator().next();
            String lastLine = lastLines.values().iterator().next();
            reader = new SingleLogcatReader(recordingMode, buffers, lastLine);
        } else {
            // multiple reader
            reader = new MultipleLogcatReader(recordingMode, lastLines);
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
        dest.writeInt(recordingMode ? 1 : 0);
        dest.writeInt(multiple ? 1 : 0);
        writeParcelableMap(dest, lastLines);
    }

    private LogcatReaderLoader(@NonNull Parcel in) {
        this.recordingMode = in.readInt() == 1;
        this.multiple = in.readInt() == 1;
        this.lastLines = readParcelableMap(in);
    }

    public static void writeParcelableMap(@NonNull Parcel parcel, @NonNull Map<Integer, String> map) {
        parcel.writeInt(map.size());
        for (Map.Entry<Integer, String> e : map.entrySet()) {
            parcel.writeInt(e.getKey());
            parcel.writeString(e.getValue());
        }
    }

    @NonNull
    public static Map<Integer, String> readParcelableMap(@NonNull Parcel parcel) {
        int size = parcel.readInt();
        Map<Integer, String> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            map.put(parcel.readInt(), parcel.readString());
        }
        return map;
    }
}
