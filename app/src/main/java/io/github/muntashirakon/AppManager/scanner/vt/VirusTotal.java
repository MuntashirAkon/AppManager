// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.scanner.vt;

import android.os.PowerManager;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.CpuUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;

public class VirusTotal {
    public interface FullScanResponseInterface {
        boolean uploadFile();

        void onUploadInitiated();

        void onUploadCompleted(@NonNull String permalink);

        void onReportReceived(@NonNull VtFileReport report);
    }

    public static class ResponseV3<T> {
        @Nullable
        public final T response;
        @Nullable
        public final VtError error;
        public final int httpCode;

        public ResponseV3(@Nullable T response, @Nullable VtError error) {
            // The params are mutually exclusive
            assert (response != null && error == null) || (response == null && error != null);
            this.response = response;
            this.error = error;
            if (error != null) {
                httpCode = error.httpErrorCode;
            } else httpCode = HttpURLConnection.HTTP_OK;
        }

        public boolean shouldRetry() {
            // It should only retry when the quota is exceeded, or the resource is not found, or
            // the resource is not yet available
            if (error == null || error.code == null) {
                return false;
            }
            return (error.code.equals("NotAvailableYet")
                    || error.code.equals("NotFoundError")
                    || error.code.equals("QuotaExceededError"));
        }

        @NonNull
        @Override
        public String toString() {
            return "ResponseV3{" +
                    "response=" + response +
                    ", error=" + error +
                    ", httpCode=" + httpCode +
                    '}';
        }
    }

    protected static final String FORM_DATA_BOUNDARY = "--AppManagerDataBoundary9f3d77ed3a";
    protected static final String API_V3_PREFIX = "https://www.virustotal.com/api/v3";
    protected static final String URL_FILE_UPLOAD = API_V3_PREFIX + "/files";
    protected static final String URL_LARGE_FILE_UPLOAD = API_V3_PREFIX + "/files/upload_url";
    protected static final String URL_FILE_REPORT = API_V3_PREFIX + "/files/";

    @Nullable
    public static VirusTotal getInstance() {
        String apiKey = Prefs.VirusTotal.getApiKey();
        if (FeatureController.isVirusTotalEnabled() && apiKey != null) {
            return new VirusTotal(apiKey);
        }
        return null;
    }

    private final String mApiKey;

    public VirusTotal(@NonNull String apiKey) {
        mApiKey = Objects.requireNonNull(apiKey);
    }

    public void fetchFileReportOrScan(@NonNull Path file,
                                      @NonNull String checksum,
                                      @NonNull FullScanResponseInterface response)
            throws IOException {
        ResponseV3<VtFileReport> responseReport = fetchFileReport(checksum);
        if (responseReport.response != null && responseReport.response.hasReport()) {
            // A report is found
            response.onReportReceived(responseReport.response);
            return;
        }
        // No report found: either failed or still queued
        boolean queued = responseReport.response != null && !responseReport.response.hasReport();
        if (!queued && !responseReport.shouldRetry()) {
            // Retry is not available
            throw new FileNotFoundException("Fetch error: " + responseReport.error);
        }
        // Scan or retry
        VtError error = Objects.requireNonNull(responseReport.error);
        boolean waitFirst = false;
        if ("NotFoundError".equals(error.code)) {
            // Initiate scan
            if (!response.uploadFile()) {
                // Scanning disabled
                throw new FileNotFoundException("File not found in VirusTotal.");
            }
            waitFirst = true;
            PowerManager.WakeLock wakeLock = CpuUtils.getPartialWakeLock("vt_upload");
            wakeLock.acquire();
            try {
                long fileSize = file.length();
                if (fileSize > 650_000_000) {
                    throw new IOException("APK is larger than 650 MB.");
                }
                boolean largeFile = fileSize > 32_000_000L;
                response.onUploadInitiated();
                String filename = file.getName();
                ResponseV3<String> uploadResponse;
                try (InputStream is = file.openInputStream()) {
                    uploadResponse = largeFile
                            ? uploadLargeFile(filename, is)
                            : uploadFile(filename, is);
                }
                if (uploadResponse.response != null) {
                    response.onUploadCompleted(getPermalink(checksum));
                }
            } finally {
                CpuUtils.releaseWakeLock(wakeLock);
            }
        }
        int waitDuration = 60_000;
        while (queued || responseReport.shouldRetry()) {
            if (waitFirst) {
                // Effectively makes it a do-while loop
                waitFirst = false;
            } else {
                responseReport = fetchFileReport(checksum);
                queued = responseReport.response != null && !responseReport.response.hasReport();
            }
            // Wait for result: First wait for 1 minute, then for 30 seconds
            // We won't do it less than 30 seconds since the API has a limit of 4 request/minute
            SystemClock.sleep(waitDuration);
            // TODO: 23/5/22 Wait duration should be according to the fileSize
            waitDuration = 30_000;
        }
        if (responseReport.response != null) {
            response.onReportReceived(responseReport.response);
        } else {
            throw new IOException("Scan error: " + responseReport.error);
        }
    }

    @WorkerThread
    @NonNull
    public ResponseV3<String> uploadFile(@NonNull String filename, @NonNull InputStream is)
            throws IOException {
        return uploadFile(filename, is, null);
    }

    @WorkerThread
    @NonNull
    public ResponseV3<String> uploadFile(@NonNull String filename, @NonNull InputStream is,
                                         @Nullable String password) throws IOException {
        URL url = new URL(URL_FILE_UPLOAD);
        return uploadAnyFile(url, filename, is, password);
    }

    @WorkerThread
    @NonNull
    public ResponseV3<String> uploadLargeFile(@NonNull String filename, @NonNull InputStream is)
            throws IOException {
        return uploadLargeFile(filename, is, null);
    }

    @WorkerThread
    @NonNull
    public ResponseV3<String> uploadLargeFile(@NonNull String filename, @NonNull InputStream is,
                                              @Nullable String password) throws IOException {
        // First retrieve the upload URL
        URL url = new URL(URL_LARGE_FILE_UPLOAD);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setUseCaches(false);
            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            // Set headers
            connection.setRequestProperty("accept", "application/json");
            connection.setRequestProperty("x-apikey", mApiKey);
            // Response
            int status = connection.getResponseCode();
            if (status < 300) {
                // Success
                // Upload the actual file
                URL uploadUrl = getLargeFileUploadUrl(connection);
                return uploadAnyFile(uploadUrl, filename, is, password);
            } else {
                // Failed
                return new ResponseV3<>(null, getErrorResponse(connection));
            }
        } finally {
            connection.disconnect();
        }
    }

    @WorkerThread
    @NonNull
    public ResponseV3<String> uploadAnyFile(@NonNull URL uploadUrl, @NonNull String filename,
                                            @NonNull InputStream is, @Nullable String password)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uploadUrl.openConnection();
        try {
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            // Set headers
            connection.setRequestProperty("accept", "application/json");
            connection.setRequestProperty("x-apikey", mApiKey);
            connection.setRequestProperty("content-type", "multipart/form-data; boundary=" + FORM_DATA_BOUNDARY);
            // Set form data
            OutputStream outputStream = connection.getOutputStream();
            if (password != null) {
                addMultipartFormData(outputStream, "password", password);
            }
            addMultipartFormData(outputStream, "file", filename, is);
            outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
            outputStream.write(("--" + FORM_DATA_BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            // Response
            int status = connection.getResponseCode();
            if (status < 300) {
                // Success
                // Example response: {
                //  "data": {
                //    "type": "analysis",
                //    "id": "base64_hash",
                //    "links": {
                //      "self": "https://www.virustotal.com/api/v3/analyses/base64_hash"
                //    }
                //  }
                //}
                return new ResponseV3<>(getAnalysisId(connection), null);
            } else {
                // Failed
                return new ResponseV3<>(null, getErrorResponse(connection));
            }
        } finally {
            connection.disconnect();
        }
    }

    @WorkerThread
    @NonNull
    public ResponseV3<VtFileReport> fetchFileReport(@NonNull String id) throws IOException {
        URL url = new URL(URL_FILE_REPORT + id);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setUseCaches(false);
            connection.setRequestMethod("GET");
            connection.setDoInput(true);
            // Set headers
            connection.setRequestProperty("accept", "application/json");
            connection.setRequestProperty("x-apikey", mApiKey);
            // Response
            int status = connection.getResponseCode();
            if (status < 300) {
                // Success
                try {
                    JSONObject jsonObject = new JSONObject(getResponseV3(connection));
                    return new ResponseV3<>(new VtFileReport(jsonObject), null);
                } catch (JSONException e) {
                    throw new IOException(e);
                }
            } else {
                // Failed
                return new ResponseV3<>(null, getErrorResponse(connection));
            }
        } finally {
            connection.disconnect();
        }
    }

    @NonNull
    public static String getPermalink(@NonNull String id) {
        return "https://www.virustotal.com/gui/file/" + id;
    }

    @NonNull
    public static String getAnalysisId(@NonNull HttpURLConnection connection) throws IOException {
        // https://docs.virustotal.com/reference/files-scan
        try {
            JSONObject dataObject = new JSONObject(getResponseV3(connection))
                    .getJSONObject("data");
            assert dataObject.getString("type").equals("analysis");
            return dataObject.getString("id");
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    @NonNull
    public static URL getLargeFileUploadUrl(@NonNull HttpURLConnection connection) throws IOException {
        // https://docs.virustotal.com/reference/files-upload-url
        try {
            return new URL(new JSONObject(getResponseV3(connection)).getString("data"));
        } catch (JSONException e) {
            throw new IOException(e);
        }
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
    public static String getResponseV3(@NonNull HttpURLConnection connection) throws IOException {
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

    @WorkerThread
    @NonNull
    public static VtError getErrorResponse(@NonNull HttpURLConnection connection) throws IOException {
        int status = connection.getResponseCode();
        // First try input stream
        String inResponse = ExUtils.exceptionAsNull(() -> getResponseV3(connection));
        if (inResponse != null) {
            return new VtError(status, inResponse);
        }
        // Try error stream
        StringBuilder response;
        InputStream errorStream = connection.getErrorStream();
        if (errorStream == null) {
            response = null;
        } else {
            response = new StringBuilder();
            try {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        errorStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new VtError(status, response != null ? response.toString() : null);
    }
}
