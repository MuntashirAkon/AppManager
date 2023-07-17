// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class Pithus {
    private static final String BASE_URL = "https://beta.pithus.org/report";

    @WorkerThread
    @Nullable
    public static String resolveReport(@NonNull String sha256Sum) throws IOException {
        URL url = new URL(BASE_URL + File.separator + sha256Sum);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestMethod("GET");
        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            return url.toString();
        }
        return null;
    }
}
