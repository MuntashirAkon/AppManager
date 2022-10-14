// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;

import org.openintents.openpgp.IOpenPgpService2;
import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.PendingIntentCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.io.Path;

// Copyright 2018 jensstein
public class OpenPGPCrypto implements Crypto {
    public static final String TAG = "OpenPGPCrypto";

    public static final String ACTION_OPEN_PGP_INTERACTION_BEGIN = BuildConfig.APPLICATION_ID + ".action.OPEN_PGP_INTERACTION_BEGIN";
    public static final String ACTION_OPEN_PGP_INTERACTION_END = BuildConfig.APPLICATION_ID + ".action.OPEN_PGP_INTERACTION_END";

    public static final String GPG_EXT = ".gpg";

    private OpenPgpServiceConnection service;
    private boolean successFlag, errorFlag;
    private Path[] files;
    @NonNull
    private final List<Path> newFiles = new ArrayList<>();
    private InputStream is;
    private OutputStream os;
    @NonNull
    private final long[] keyIds;
    private final String provider;
    private Intent lastIntent;
    private int lastMode;
    private final Context context = AppManager.getContext();
    private final Handler handler;
    private boolean isFileMode;  // Whether to en/decrypt a file than an stream
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            if (intent.getAction() == null) return;
            switch (intent.getAction()) {
                case ACTION_OPEN_PGP_INTERACTION_BEGIN:
                    break;
                case ACTION_OPEN_PGP_INTERACTION_END:
                    // TODO: 17/12/21 Handle this better by using CountdownLatch
                    new Thread(() -> {
                        try {
                            doAction(lastIntent, lastMode, false);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }).start();
                    break;
            }
        }
    };

    @AnyThread
    public OpenPGPCrypto(@NonNull String keyIdsStr) throws CryptoException {
        try {
            String[] keyIds = keyIdsStr.split(",");
            this.keyIds = new long[keyIds.length];
            for (int i = 0; i < keyIds.length; ++i) this.keyIds[i] = Long.parseLong(keyIds[i]);
        } catch (NumberFormatException e) {
            throw new CryptoException(e);
        }
        this.provider = (String) AppPref.get(AppPref.PrefKey.PREF_OPEN_PGP_PACKAGE_STR);
        this.handler = new Handler(Looper.getMainLooper());
        bind();
    }

    @Override
    public void close() {
        // Unbind service
        if (service != null) service.unbindFromService();
        // Unregister receiver
        context.unregisterReceiver(receiver);
    }

    @WorkerThread
    @Override
    public void decrypt(@NonNull Path[] files) throws IOException {
        Intent intent = new Intent(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        handleFiles(intent, Cipher.DECRYPT_MODE, files);
    }

    @Override
    public void decrypt(@NonNull InputStream encryptedStream, @NonNull OutputStream unencryptedStream)
            throws IOException {
        Intent intent = new Intent(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        handleStreams(intent, Cipher.DECRYPT_MODE, encryptedStream, unencryptedStream);
    }

    @WorkerThread
    @Override
    public void encrypt(@NonNull Path[] filesList) throws IOException {
        Intent intent = new Intent(OpenPgpApi.ACTION_ENCRYPT);
        intent.putExtra(OpenPgpApi.EXTRA_KEY_IDS, keyIds);
        handleFiles(intent, Cipher.ENCRYPT_MODE, filesList);
    }

    @Override
    public void encrypt(@NonNull InputStream unencryptedStream, @NonNull OutputStream encryptedStream)
            throws IOException {
        Intent intent = new Intent(OpenPgpApi.ACTION_ENCRYPT);
        intent.putExtra(OpenPgpApi.EXTRA_KEY_IDS, keyIds);
        handleStreams(intent, Cipher.ENCRYPT_MODE, unencryptedStream, encryptedStream);
    }

    @WorkerThread
    private void handleFiles(Intent intent, int mode, @NonNull Path[] filesList) throws IOException {
        isFileMode = true;
        waitForServiceBound();
        is = null;
        os = null;
        files = filesList;
        newFiles.clear();
        lastIntent = intent;
        lastMode = mode;
        doAction(intent, mode, true);
    }

    @WorkerThread
    private void handleStreams(Intent intent, int mode, @NonNull InputStream is, @NonNull OutputStream os)
            throws IOException {
        isFileMode = false;
        waitForServiceBound();
        this.is = is;
        this.os = os;
        files = new Path[0];
        lastIntent = intent;
        lastMode = mode;
        doAction(intent, mode, true);
    }

    @WorkerThread
    private void doAction(Intent intent, int mode, boolean waitForResult) throws IOException {
        if (isFileMode) {
            doActionForFiles(intent, mode, waitForResult);
        } else {
            doActionForStream(intent, waitForResult);
        }
    }

    @WorkerThread
    private void doActionForFiles(Intent intent, int mode, boolean waitForResult) throws IOException {
        errorFlag = false;
        // `files` is never null here
        if (files.length == 0) {
            Log.d(TAG, "No files to de/encrypt");
            return;
        }
        for (Path inputPath : files) {
            Path parent = inputPath.getParentFile();
            if (parent == null) {
                throw new IOException("Parent of " + inputPath + " cannot be null.");
            }
            String outputFilename;
            if (mode == Cipher.DECRYPT_MODE) {
                outputFilename = inputPath.getName().substring(0, inputPath.getName().lastIndexOf(GPG_EXT));
            } else outputFilename = inputPath.getName() + GPG_EXT;
            Path outputPath = parent.createNewFile(outputFilename, null);
            newFiles.add(outputPath);
            Log.i(TAG, "Input: " + inputPath + "\nOutput: " + outputPath);
            InputStream is = inputPath.openInputStream();
            OutputStream os = outputPath.openOutputStream();
            OpenPgpApi api = new OpenPgpApi(context, service.getService());
            Intent result = api.executeApi(intent, is, os);
            handler.post(() -> handleResult(result));
            if (waitForResult) waitForResult();
            if (errorFlag) {
                outputPath.delete();
                throw new IOException("Error occurred during en/decryption process");
            }
            // Delete unencrypted file
            if (mode == Cipher.ENCRYPT_MODE) {
                if (!inputPath.delete()) {
                    throw new IOException("Couldn't delete old file " + inputPath);
                }
            }
        }
        // Total success
    }

    @WorkerThread
    private void doActionForStream(Intent intent, boolean waitForResult) throws IOException {
        errorFlag = false;
        OpenPgpApi api = new OpenPgpApi(context, service.getService());
        Intent result = api.executeApi(intent, is, os);
        handler.post(() -> handleResult(result));
        if (waitForResult) waitForResult();
        if (errorFlag) {
            throw new IOException("Error occurred during en/decryption process");
        }
    }

    @NonNull
    @Override
    public Path[] getNewFiles() {
        return newFiles.toArray(new Path[0]);
    }

    private void bind() {
        service = new OpenPgpServiceConnection(context, provider,
                new OpenPgpServiceConnection.OnBound() {
                    @Override
                    public void onBound(IOpenPgpService2 service) {
                        Log.i(OpenPgpApi.TAG, "Service bound.");
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(OpenPgpApi.TAG, "Exception on binding.", e);
                    }
                }
        );
        service.bindToService();
        // Start broadcast receiver
        IntentFilter filter = new IntentFilter(ACTION_OPEN_PGP_INTERACTION_BEGIN);
        filter.addAction(ACTION_OPEN_PGP_INTERACTION_END);
        context.registerReceiver(receiver, filter);
    }

    @WorkerThread
    private void waitForServiceBound() throws IOException {
        int i = 0;
        while (service.getService() == null) {
            if (i % 20 == 0) {
                Log.i(TAG, "Waiting for openpgp-api service to be bound");
            }
            SystemClock.sleep(100);
            if (i > 1000)
                break;
            i++;
        }
        if (service.getService() == null) {
            throw new IOException("OpenPGPService could not be bound.");
        }
    }

    @WorkerThread
    private void waitForResult() {
        int i = 0;
        while (!successFlag && !errorFlag) {
            if (i % 200 == 0) Log.i(TAG, "Waiting for user interaction");
            SystemClock.sleep(100);
            if (i > 1000)
                break;
            i++;
        }
    }

    @SuppressLint("WrongConstant")
    @UiThread
    private void handleResult(@NonNull Intent result) {
        successFlag = false;
        switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
            case OpenPgpApi.RESULT_CODE_SUCCESS:
                Log.i(TAG, "en/decryption successful.");
                successFlag = true;
                break;
            case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED: {
                Log.i(TAG, "User interaction required. Sending intent...");
                Intent broadcastIntent = new Intent(OpenPGPCrypto.ACTION_OPEN_PGP_INTERACTION_BEGIN);
                context.sendBroadcast(broadcastIntent);
                // Intent wrapper
                Intent intent = new Intent(context, OpenPGPCryptoActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(OpenPgpApi.RESULT_INTENT, (PendingIntent) result.getParcelableExtra(OpenPgpApi.RESULT_INTENT));
                String openPGP = "Open PGP";
                // We don't need a DELETE intent since the time will be expired anyway
                NotificationCompat.Builder builder = NotificationUtils.getHighPriorityNotificationBuilder(context)
                        .setAutoCancel(true)
                        .setDefaults(Notification.DEFAULT_ALL)
                        .setWhen(System.currentTimeMillis())
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setTicker(openPGP)
                        .setContentTitle(openPGP)
                        .setSubText(openPGP)
                        .setContentText(context.getString(R.string.allow_open_pgp_operation));
                builder.setContentIntent(PendingIntent.getActivity(context, 0, intent,
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntentCompat.FLAG_IMMUTABLE));
                NotificationUtils.displayHighPriorityNotification(context, builder.build());
                break;
            }
            case OpenPgpApi.RESULT_CODE_ERROR:
                errorFlag = true;
                OpenPgpError error = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
                if (error != null) {
                    Log.e(TAG, "handleResult: (" + error.getErrorId() + ") " + error.getMessage());
                } else Log.e(TAG, "handleResult: Error occurred during en/decryption process");
                break;
        }
    }
}
