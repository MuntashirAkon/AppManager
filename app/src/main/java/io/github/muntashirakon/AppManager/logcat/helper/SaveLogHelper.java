// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.helper;

import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
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
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.ProxyFile;
import io.github.muntashirakon.io.ProxyInputStream;
import io.github.muntashirakon.io.ProxyOutputStream;

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
    public static File saveTemporaryFile(String filename, CharSequence text, List<String> lines) {
        File tempFile = new ProxyFile(getTempDirectory(), filename);
        try (PrintStream out = new PrintStream(new BufferedOutputStream(new ProxyOutputStream(tempFile), BUFFER))) {
            if (text != null) { // one big string
                out.print(text);
            } else { // multiple lines separated by newline
                for (CharSequence line : lines) {
                    out.println(line);
                }
            }
            Log.d(TAG, "Saved temp file: " + tempFile);
            return tempFile;
        } catch (FileNotFoundException | RemoteException e) {
            Log.e(TAG, e);
            return null;
        }
    }

    @NonNull
    public static File getFile(@NonNull String filename) {
        return new ProxyFile(getSavedLogsDirectory(), filename);
    }

    public static void deleteLogIfExists(String filename) {
        File file = new ProxyFile(getSavedLogsDirectory(), filename);
        if (file.exists()) {
            file.delete();
        }
    }

    @NonNull
    public static CharSequence[] getFormattedFilenames(@NonNull Context context, @NonNull List<File> files) {
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
    public static List<File> getLogFiles() {
        File logsDirectory = getSavedLogsDirectory();
        File[] filesArray = logsDirectory.listFiles();
        if (filesArray == null) {
            return Collections.emptyList();
        }
        List<File> files = new ArrayList<>(Arrays.asList(filesArray));
        Collections.sort(files, (o1, o2) -> Long.compare(o2.lastModified(), o1.lastModified()));
        return files;
    }

    @NonNull
    public static SavedLog openLog(@NonNull String filename, int maxLines) {
        File logFile = new ProxyFile(getSavedLogsDirectory(), filename);
        LinkedList<String> logLines = new LinkedList<>();
        boolean truncated = false;
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new ProxyInputStream(logFile)), BUFFER)) {
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
        File newFile = new ProxyFile(getSavedLogsDirectory(), filename);
        try {
            if (!newFile.exists()) {
                newFile.createNewFile();
            }
        } catch (IOException e) {
            Log.e(TAG, e);
            return false;
        }
        try (PrintStream out = new PrintStream(new BufferedOutputStream(new ProxyOutputStream(newFile), BUFFER))) {
            // Save a log as either a list of strings
            if (logLines != null) {
                for (CharSequence line : logLines) {
                    out.println(line);
                }
            } else if (logString != null) {
                out.print(logString);
            }
        } catch (FileNotFoundException | RemoteException e) {
            Log.e(TAG, e);
            return false;
        }
        return true;
    }

    @NonNull
    public static File getTempDirectory() {
        File tmpDir = BackupFiles.getTemporaryDirectory();
        if (!tmpDir.exists()) {
            tmpDir.mkdir();
        }
        return tmpDir;
    }

    @NonNull
    private static File getSavedLogsDirectory() {
        File savedLogsDir = new ProxyFile(getAMDirectory(), SAVED_LOGS_DIR);
        if (!savedLogsDir.exists()) {
            savedLogsDir.mkdir();
        }
        return savedLogsDir;
    }

    @NonNull
    private static File getAMDirectory() {
        File amDir = AppPref.getAppManagerDirectory();
        if (!amDir.exists()) {
            amDir.mkdir();
        }
        return amDir;
    }

    @NonNull
    public static File saveTemporaryZipFile(String filename, List<File> files) throws IOException, RemoteException {
        File zipFile = new ProxyFile(getTempDirectory(), filename);
        try (ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(new ProxyOutputStream(zipFile), BUFFER))) {
            for (File file : files) {
                ProxyInputStream fi = new ProxyInputStream(file);
                try (BufferedInputStream input = new BufferedInputStream(fi, BUFFER)) {
                    ZipEntry entry = new ZipEntry(file.getName());
                    output.putNextEntry(entry);
                    IOUtils.copy(input, output);
                }
            }
        }
        return zipFile;
    }

    public static void saveZipFileAndThrow(@NonNull Context context, @NonNull Uri uri, @NonNull List<File> files)
            throws IOException, RemoteException {
        OutputStream os = context.getContentResolver().openOutputStream(uri);
        if (os == null) throw new IOException("Could not open uri.");
        try (ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(os, BUFFER))) {
            for (File file : files) {
                ProxyInputStream fi = new ProxyInputStream(file);
                try (BufferedInputStream input = new BufferedInputStream(fi, BUFFER)) {
                    ZipEntry entry = new ZipEntry(file.getName());
                    output.putNextEntry(entry);
                    IOUtils.copy(input, output);
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
        File[] files = getTempDirectory().listFiles();
        if (files == null) return;
        for (File file : files) {
            file.delete();
        }
    }
}
