// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.helper;

import android.content.Context;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;

import io.github.muntashirakon.AppManager.logcat.struct.SavedLog;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

// Copyright 2012 Nolan Lawson
public class SaveLogHelper {
    public static final String TAG = SaveLogHelper.class.getSimpleName();

    public static final String DEVICE_INFO_FILENAME = "device_info.txt";
    public static final String LOG_FILENAME = "logcat.am.log";
    public static final String DMESG_FILENAME = "dmesg.txt";
    public static final String SAVED_LOGS_DIR = "saved_logs";
    private static final int BUFFER = 0x1000; // 4K

    @Nullable
    public static Path saveTemporaryFile(String extension, CharSequence text, Collection<String> lines) {
        try {
            Path tempFile = Paths.get(FileCache.getGlobalFileCache().createCachedFile(extension));
            try (PrintStream out = new PrintStream(new BufferedOutputStream(tempFile.openOutputStream(), BUFFER))) {
                if (text != null) { // one big string
                    out.print(text);
                } else { // multiple lines separated by newline
                    for (CharSequence line : lines) {
                        out.println(line);
                    }
                }
                Log.d(TAG, "Saved temp file: %s", tempFile);
                return tempFile;
            }
        } catch (IOException e) {
            Log.e(TAG, e);
            return null;
        }
    }

    @NonNull
    public static Path getFile(@NonNull String filename) throws IOException {
        return getSavedLogsDirectory().findFile(filename);
    }

    public static void deleteLogIfExists(@Nullable String filename) {
        if (filename == null) return;
        try {
            getFile(filename).delete();
        } catch (IOException ignore) {
        }
    }

    @NonNull
    public static CharSequence[] getFormattedFilenames(@NonNull Context context, @NonNull List<Path> files) {
        CharSequence[] fileNames = new CharSequence[files.size()];
        DateFormat dateFormat = DateFormat.getDateTimeInstance();
        for (int i = 0; i < files.size(); ++i) {
            fileNames[i] = new SpannableStringBuilder(files.get(i).getName())
                    .append("\n").append(UIUtils.getSmallerText(UIUtils.getSecondaryText(context,
                            dateFormat.format(new Date(files.get(i).lastModified())))));
        }
        return fileNames;
    }

    @NonNull
    public static List<Path> getLogFiles() {
        try {
            Path[] filesArray = getSavedLogsDirectory().listFiles();
            List<Path> files = new ArrayList<>(Arrays.asList(filesArray));
            Collections.sort(files, (o1, o2) -> Long.compare(o2.lastModified(), o1.lastModified()));
            return files;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    @NonNull
    public static SavedLog openLog(@NonNull Uri fileUri, int maxLines) {
        Path logFile = Paths.get(fileUri);
        LinkedList<String> logLines = new LinkedList<>();
        boolean truncated = false;
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(logFile.openInputStream()), BUFFER)) {
            while (bufferedReader.ready()) {
                logLines.add(bufferedReader.readLine());
                if (logLines.size() > maxLines) {
                    logLines.removeFirst();
                    truncated = true;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, e);
        }
        return new SavedLog(logLines, truncated);
    }

    public static synchronized boolean saveLog(CharSequence logString, String filename) {
        try {
            saveLog(null, logString, filename);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Nullable
    public static synchronized Path saveLog(List<String> logLines, String filename) {
        try {
            return saveLog(logLines, null, filename);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @NonNull
    private static Path saveLog(List<String> logLines, CharSequence logString, String filename) throws IOException {
        Path newFile = getSavedLogsDirectory().createNewFile(filename, null);
        try (PrintStream out = new PrintStream(new BufferedOutputStream(newFile.openOutputStream(), BUFFER))) {
            // Save a log as either a list of strings
            if (logLines != null) {
                for (CharSequence line : logLines) {
                    out.println(line);
                }
            } else if (logString != null) {
                out.print(logString);
            }
        }
        return newFile;
    }

    @NonNull
    private static Path getSavedLogsDirectory() throws IOException {
        Path amDir = Prefs.Storage.getAppManagerDirectory();
        if (!amDir.exists()) {
            amDir.mkdir();
        }
        return amDir.findOrCreateDirectory(SAVED_LOGS_DIR);
    }

    @NonNull
    public static String createLogFilename() {
        Date date = new Date();
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTime(date);

        DecimalFormat twoDigitDecimalFormat = new DecimalFormat("00");
        DecimalFormat fourDigitDecimalFormat = new DecimalFormat("0000");

        String year = fourDigitDecimalFormat.format(calendar.get(Calendar.YEAR));
        String month = twoDigitDecimalFormat.format(calendar.get(Calendar.MONTH) + 1);
        String day = twoDigitDecimalFormat.format(calendar.get(Calendar.DAY_OF_MONTH));
        String hour = twoDigitDecimalFormat.format(calendar.get(Calendar.HOUR_OF_DAY));
        String minute = twoDigitDecimalFormat.format(calendar.get(Calendar.MINUTE));
        String second = twoDigitDecimalFormat.format(calendar.get(Calendar.SECOND));

        return year + "-" + month + "-" + day + "-" + hour + "-" + minute
                + "-" + second + ".am.log";
    }

    @Contract("null -> true")
    public static boolean isInvalidFilename(@Nullable CharSequence filename) {
        String filenameAsString;
        return TextUtils.isEmpty(filename)
                || (filenameAsString = filename.toString()).contains("/")
                || filenameAsString.contains(":")
                || filenameAsString.contains(" ")
                || !filenameAsString.endsWith(".log");
    }

}
