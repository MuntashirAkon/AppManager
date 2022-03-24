// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.helper;

import android.content.Context;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.backup.BackupFiles;
import io.github.muntashirakon.AppManager.logcat.struct.SavedLog;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.Path;

// Copyright 2012 Nolan Lawson
public class SaveLogHelper {
    public static final String TAG = SaveLogHelper.class.getSimpleName();

    public static final String TEMP_DEVICE_INFO_FILENAME = "device_info.txt";
    public static final String TEMP_LOG_FILENAME = "logcat.log";
    public static final String TEMP_DMESG_FILENAME = "dmesg.txt";
    public static final String SAVED_LOGS_DIR = "saved_logs";
    private static final String TEMP_ZIP_FILENAME = "logs";
    private static final int BUFFER = 0x1000; // 4K

    @Nullable
    public static Path saveTemporaryFile(String filename, CharSequence text, List<String> lines) {
        try {
            Path tempFile = getTempDirectory().createNewFile(filename, null);
            try (PrintStream out = new PrintStream(new BufferedOutputStream(tempFile.openOutputStream(), BUFFER))) {
                if (text != null) { // one big string
                    out.print(text);
                } else { // multiple lines separated by newline
                    for (CharSequence line : lines) {
                        out.println(line);
                    }
                }
                Log.d(TAG, "Saved temp file: " + tempFile);
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
            getSavedLogsDirectory().findFile(filename).delete();
        } catch (IOException ignore) {
        }
    }

    @NonNull
    public static CharSequence[] getFormattedFilenames(@NonNull Context context, @NonNull List<Path> files) {
        CharSequence[] fileNames = new CharSequence[files.size()];
        DateFormat dateFormat = DateFormat.getDateTimeInstance();
        for (int i = 0; i < files.size(); ++i) {
            fileNames[i] = new SpannableStringBuilder(UIUtils.getPrimaryText(context, files.get(i).getName()))
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
    public static SavedLog openLog(@NonNull String filename, int maxLines) throws IOException {
        Path logFile = getSavedLogsDirectory().findFile(filename);
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
        return saveLog(null, logString, filename);
    }

    public static synchronized boolean saveLog(List<String> logLines, String filename) {
        return saveLog(logLines, null, filename);
    }

    private static boolean saveLog(List<String> logLines, CharSequence logString, String filename) {
        try {
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
        } catch (IOException e) {
            Log.e(TAG, e);
            return false;
        }
        return true;
    }

    @NonNull
    private static Path getTempDirectory() throws IOException {
        return BackupFiles.getTemporaryDirectory();
    }

    @NonNull
    private static Path getSavedLogsDirectory() throws IOException {
        return getAMDirectory().findOrCreateDirectory(SAVED_LOGS_DIR);
    }

    @NonNull
    private static Path getAMDirectory() {
        Path amDir = AppPref.getAppManagerDirectory();
        if (!amDir.exists()) {
            amDir.mkdir();
        }
        return amDir;
    }

    @NonNull
    public static Path saveTemporaryZipFile(String filename, @NonNull List<Path> files) throws IOException {
        Path zipFile = getTempDirectory().createNewFile(filename, null);
        try (ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(zipFile.openOutputStream(), BUFFER))) {
            for (Path file : files) {
                try (BufferedInputStream input = new BufferedInputStream(file.openInputStream(), BUFFER)) {
                    ZipEntry entry = new ZipEntry(file.getName());
                    output.putNextEntry(entry);
                    FileUtils.copy(input, output);
                }
            }
        }
        return zipFile;
    }

    public static void saveZipFileAndThrow(@NonNull Context context, @NonNull Uri uri, @NonNull List<Path> files)
            throws IOException {
        OutputStream os = context.getContentResolver().openOutputStream(uri);
        if (os == null) throw new IOException("Could not open uri.");
        try (ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(os, BUFFER))) {
            for (Path file : files) {
                try (BufferedInputStream input = new BufferedInputStream(file.openInputStream(), BUFFER)) {
                    ZipEntry entry = new ZipEntry(file.getName());
                    output.putNextEntry(entry);
                    FileUtils.copy(input, output);
                }
            }
        }
    }

    @NonNull
    public static String createZipFilename(boolean withDate) {
        return createLogFilename(TEMP_ZIP_FILENAME, ".zip", withDate);
    }

    @NonNull
    public static String createLogFilename() {
        return createLogFilename(null, ".log", true);
    }

    @NonNull
    private static String createLogFilename(@Nullable String prefix, @NonNull String extension, boolean withDate) {
        if (withDate) {
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

            return (prefix == null ? "" : prefix + "-") + year + "-" + month + "-" + day + "-" + hour + "-" + minute
                    + "-" + second + extension;
        } else return prefix + extension;
    }

    public static boolean isInvalidFilename(CharSequence filename) {
        String filenameAsString;
        return TextUtils.isEmpty(filename)
                || (filenameAsString = filename.toString()).contains("/")
                || filenameAsString.contains(":")
                || filenameAsString.contains(" ")
                || !filenameAsString.endsWith(".log");
    }

    public static void cleanTemp() {
        try {
            Path[] files = getTempDirectory().listFiles();
            for (Path file : files) {
                file.delete();
            }
        } catch (Throwable ignore) {
        }
    }
}
