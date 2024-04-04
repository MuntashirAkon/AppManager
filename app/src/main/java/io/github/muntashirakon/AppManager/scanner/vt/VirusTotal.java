// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.scanner.vt;

import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.CpuUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;

public class VirusTotal {
    public interface FullScanResponseInterface {
        boolean scanFile();

        void onScanningInitiated();

        void onScanCompleted(@NonNull VtFileScanMeta meta);

        void onReportReceived(@NonNull VtFileReport report);
    }

    public static final int RESPONSE_FOUND = 1;
    public static final int RESPONSE_NOT_FOUND = 0;
    public static final int RESPONSE_QUEUED = -2;

    protected static final String FORM_DATA_BOUNDARY = "--AppManagerDataBoundary9f3d77ed3a";
    protected static final String API_PREFIX = "https://www.virustotal.com/vtapi/v2";
    protected static final String URL_FILE_SCAN = API_PREFIX + "/file/scan";
    protected static final String URL_FILE_RESCAN = API_PREFIX + "/file/rescan";
    protected static final String URL_FILE_REPORT = API_PREFIX + "/file/report";

    @Nullable
    public static VirusTotal getInstance() {
        String apiKey = Prefs.VirusTotal.getApiKey();
        if (FeatureController.isVirusTotalEnabled() && apiKey != null) {
            return new VirusTotal(apiKey);
        }
        return null;
    }

    private final String mApiKey;
    private final Gson mGson;

    public VirusTotal(@NonNull String apiKey) {
        mApiKey = Objects.requireNonNull(apiKey);
        mGson = new Gson();
    }

    public void fetchReportsOrScan(@NonNull Path file,
                                   @NonNull String checksum,
                                   @NonNull FullScanResponseInterface response)
            throws IOException {
        List<VtFileReport> reports = fetchReports(new String[]{checksum});
        if (reports.isEmpty()) {
            throw new IOException("No report returned.");
        }
        VtFileReport report = reports.get(0);
        int responseCode = report.getResponseCode();
        if (responseCode == VirusTotal.RESPONSE_FOUND) {
            response.onReportReceived(report);
            return;
        }
        // Report not found: request scan or wait if queued
        if (responseCode == VirusTotal.RESPONSE_NOT_FOUND) {
            // Initiate scan
            if (!response.scanFile()) {
                // Scanning disabled
                throw new FileNotFoundException("File not found in VirusTotal.");
            }
            PowerManager.WakeLock wakeLock = CpuUtils.getPartialWakeLock("vt_upload");
            wakeLock.acquire();
            try {
                long fileSize = file.length();
                if (fileSize > 32 * 1024 * 1024) {
                    throw new IOException("APK is larger than 32 MB.");
                }
                response.onScanningInitiated();
                String filename = file.getName();
                VtFileScanMeta scanMeta;
                try (InputStream is = file.openInputStream()) {
                    scanMeta = scan(filename, is);
                }
                response.onScanCompleted(scanMeta);
                responseCode = VirusTotal.RESPONSE_QUEUED;
            } finally {
                CpuUtils.releaseWakeLock(wakeLock);
            }
        } else {
            // Item is queued
            response.onReportReceived(report);
        }
        int waitDuration = 60_000;
        while (responseCode == VirusTotal.RESPONSE_QUEUED) {
            reports = fetchReports(new String[]{checksum});
            if (reports.isEmpty()) throw new IOException("No report returned.");
            report = reports.get(0);
            responseCode = report.getResponseCode();
            // Wait for result: First wait for 1 minute, then for 30 seconds
            // We won't do it less than 30 seconds since the API has a limit of 4 request/minute
            SystemClock.sleep(waitDuration);
            // TODO: 23/5/22 Wait duration should be according to the fileSize
            waitDuration = 30_000;
        }
        if (responseCode == VirusTotal.RESPONSE_FOUND) {
            response.onReportReceived(report);
        } else throw new IOException("Could not generate scan report");
    }

    @NonNull
    public VtFileScanMeta scan(String filename, File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return scan(filename, fis);
        }
    }

    @WorkerThread
    @NonNull
    public VtFileScanMeta scan(String filename, InputStream is) throws IOException {
        URL url = new URL(URL_FILE_SCAN);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setUseCaches(false);
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setDoInput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + FORM_DATA_BOUNDARY);
        // Set headers, form data
        OutputStream outputStream = connection.getOutputStream();
        addHeader(outputStream, "Transfer-Encoding", "chunked");
        addMultipartFormData(outputStream, "apikey", mApiKey);
        addMultipartFormData(outputStream, "file", filename, is);
        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
        outputStream.write(("--" + FORM_DATA_BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        // Response
        String response = getResponse(connection);
        return mGson.fromJson(response, VtFileScanMeta.class);
    }

    @WorkerThread
    @NonNull
    public List<VtFileReport> fetchReports(@NonNull String[] hashes) throws IOException {
        URL url = new URL(URL_FILE_REPORT);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setUseCaches(false);
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setDoInput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + FORM_DATA_BOUNDARY);
        // Set headers, form data
        OutputStream outputStream = connection.getOutputStream();
        addHeader(outputStream, "Transfer-Encoding", "chunked");
        addMultipartFormData(outputStream, "apikey", mApiKey);
        addMultipartFormData(outputStream, "resource", TextUtils.join(",", hashes));
        addMultipartFormData(outputStream, "allinfo", "false");
        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
        outputStream.write(("--" + FORM_DATA_BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
        // Response
        String response = getResponse(connection);
        if (hashes.length > 1) {
            return Arrays.asList(mGson.fromJson(response, VtFileReport[].class));
        }
        return Collections.singletonList(mGson.fromJson(response, VtFileReport.class));
    }

    public static void addHeader(@NonNull OutputStream os, @NonNull String key, String value) throws IOException {
        os.write((key + ": " + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    public static void addMultipartFormData(@NonNull OutputStream os, @NonNull String key, String value) throws IOException {
        os.write(("--" + FORM_DATA_BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(("Content-Disposition: form-data; name=\"" + key + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(("Content-Type: text/plain; charset=UTF-8\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(("\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    public static void addMultipartFormData(@NonNull OutputStream os, @NonNull String key, @NonNull String filename,
                                            @NonNull InputStream is) throws IOException {
        os.write(("--" + FORM_DATA_BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(("Content-Disposition: form-data; name=\"" + key + "\"; filename=\"" + filename + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        os.write(("Content-Type: application/octet-stream\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(("Content-Transfer-Encoding: chunked\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        IoUtils.copy(is, os);
    }

    @WorkerThread
    @NonNull
    public static String getResponse(@NonNull HttpURLConnection connection) throws IOException {
        try {
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                return response.toString();
            }
            if (status == HttpURLConnection.HTTP_NO_CONTENT) {
                throw new IOException("Request rate limit exceeded.");
            }
            if (status == HttpURLConnection.HTTP_FORBIDDEN) {
                throw new IOException("Not enough privileges to make the request.");
            }
            throw new IOException("Bad request.");
        } finally {
            connection.disconnect();
        }
    }
}
