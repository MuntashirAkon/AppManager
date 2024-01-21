// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto;

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
import androidx.core.app.PendingIntentCompat;
import androidx.core.content.ContextCompat;

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

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.io.Path;

// Copyright 2018 jensstein
public class OpenPGPCrypto implements Crypto {
    public static final String TAG = "OpenPGPCrypto";

    public static final String ACTION_OPEN_PGP_INTERACTION_BEGIN = BuildConfig.APPLICATION_ID + ".action.OPEN_PGP_INTERACTION_BEGIN";
    public static final String ACTION_OPEN_PGP_INTERACTION_END = BuildConfig.APPLICATION_ID + ".action.OPEN_PGP_INTERACTION_END";

    public static final String GPG_EXT = ".gpg";

    private OpenPgpServiceConnection mService;
    private boolean mSuccessFlag;
    private boolean mErrorFlag;
    private Path[] mFiles;
    @NonNull
    private final List<Path> mNewFiles = new ArrayList<>();
    private InputStream mIs;
    private OutputStream mOs;
    @NonNull
    private final long[] mKeyIds;
    private final String mProvider;
    private Intent mLastIntent;
    private int mLastMode;
    private final Context mContext;
    private final Handler mHandler;
    private boolean mIsFileMode;  // Whether to en/decrypt a file than an stream
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
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
                            doAction(mLastIntent, mLastMode, false);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }).start();
                    break;
            }
        }
    };

    @AnyThread
    public OpenPGPCrypto(@NonNull Context context, @NonNull String keyIdsStr) throws CryptoException {
        mContext = context;
        try {
            String[] keyIds = keyIdsStr.split(",");
            mKeyIds = new long[keyIds.length];
            for (int i = 0; i < keyIds.length; ++i) mKeyIds[i] = Long.parseLong(keyIds[i]);
        } catch (NumberFormatException e) {
            throw new CryptoException(e);
        }
        mProvider = Prefs.Encryption.getOpenPgpProvider();
        mHandler = new Handler(Looper.getMainLooper());
        bind();
    }

    @Override
    public void close() {
        // Unbind service
        if (mService != null) mService.unbindFromService();
        // Unregister receiver
        mContext.unregisterReceiver(mReceiver);
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
        intent.putExtra(OpenPgpApi.EXTRA_KEY_IDS, mKeyIds);
        handleFiles(intent, Cipher.ENCRYPT_MODE, filesList);
    }

    @Override
    public void encrypt(@NonNull InputStream unencryptedStream, @NonNull OutputStream encryptedStream)
            throws IOException {
        Intent intent = new Intent(OpenPgpApi.ACTION_ENCRYPT);
        intent.putExtra(OpenPgpApi.EXTRA_KEY_IDS, mKeyIds);
        handleStreams(intent, Cipher.ENCRYPT_MODE, unencryptedStream, encryptedStream);
    }

    @WorkerThread
    private void handleFiles(Intent intent, int mode, @NonNull Path[] filesList) throws IOException {
        mIsFileMode = true;
        waitForServiceBound();
        mIs = null;
        mOs = null;
        mFiles = filesList;
        mNewFiles.clear();
        mLastIntent = intent;
        mLastMode = mode;
        doAction(intent, mode, true);
    }

    @WorkerThread
    private void handleStreams(Intent intent, int mode, @NonNull InputStream is, @NonNull OutputStream os)
            throws IOException {
        mIsFileMode = false;
        waitForServiceBound();
        mIs = is;
        mOs = os;
        mFiles = new Path[0];
        mLastIntent = intent;
        mLastMode = mode;
        doAction(intent, mode, true);
    }

    @WorkerThread
    private void doAction(Intent intent, int mode, boolean waitForResult) throws IOException {
        if (mIsFileMode) {
            doActionForFiles(intent, mode, waitForResult);
        } else {
            doActionForStream(intent, waitForResult);
        }
    }

    @WorkerThread
    private void doActionForFiles(Intent intent, int mode, boolean waitForResult) throws IOException {
        mErrorFlag = false;
        // `files` is never null here
        if (mFiles.length == 0) {
            Log.d(TAG, "No files to de/encrypt");
            return;
        }
        for (Path inputPath : mFiles) {
            Path parent = inputPath.getParent();
            if (parent == null) {
                throw new IOException("Parent of " + inputPath + " cannot be null.");
            }
            String outputFilename;
            if (mode == Cipher.DECRYPT_MODE) {
                outputFilename = inputPath.getName().substring(0, inputPath.getName().lastIndexOf(GPG_EXT));
            } else outputFilename = inputPath.getName() + GPG_EXT;
            Path outputPath = parent.createNewFile(outputFilename, null);
            mNewFiles.add(outputPath);
            Log.i(TAG, "Input: %s\nOutput: %s", inputPath, outputPath);
            InputStream is = inputPath.openInputStream();
            OutputStream os = outputPath.openOutputStream();
            OpenPgpApi api = new OpenPgpApi(mContext, mService.getService());
            Intent result = api.executeApi(intent, is, os);
            mHandler.post(() -> handleResult(result));
            if (waitForResult) waitForResult();
            if (mErrorFlag) {
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
        mErrorFlag = false;
        OpenPgpApi api = new OpenPgpApi(mContext, mService.getService());
        Intent result = api.executeApi(intent, mIs, mOs);
        mHandler.post(() -> handleResult(result));
        if (waitForResult) waitForResult();
        if (mErrorFlag) {
            throw new IOException("Error occurred during en/decryption process");
        }
    }

    @NonNull
    @Override
    public Path[] getNewFiles() {
        return mNewFiles.toArray(new Path[0]);
    }

    private void bind() {
        mService = new OpenPgpServiceConnection(mContext, mProvider,
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
        mService.bindToService();
        // Start broadcast receiver
        IntentFilter filter = new IntentFilter(ACTION_OPEN_PGP_INTERACTION_BEGIN);
        filter.addAction(ACTION_OPEN_PGP_INTERACTION_END);
        ContextCompat.registerReceiver(mContext, mReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @WorkerThread
    private void waitForServiceBound() throws IOException {
        int i = 0;
        while (mService.getService() == null) {
            if (i % 20 == 0) {
                Log.i(TAG, "Waiting for openpgp-api service to be bound");
            }
            SystemClock.sleep(100);
            if (i > 1000)
                break;
            i++;
        }
        if (mService.getService() == null) {
            throw new IOException("OpenPGPService could not be bound.");
        }
    }

    @WorkerThread
    private void waitForResult() {
        int i = 0;
        while (!mSuccessFlag && !mErrorFlag) {
            if (i % 200 == 0) Log.i(TAG, "Waiting for user interaction");
            SystemClock.sleep(100);
            if (i > 1000)
                break;
            i++;
        }
    }

    @UiThread
    private void handleResult(@NonNull Intent result) {
        mSuccessFlag = false;
        switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
            case OpenPgpApi.RESULT_CODE_SUCCESS:
                Log.i(TAG, "en/decryption successful.");
                mSuccessFlag = true;
                break;
            case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED: {
                Log.i(TAG, "User interaction required. Sending intent...");
                Intent broadcastIntent = new Intent(OpenPGPCrypto.ACTION_OPEN_PGP_INTERACTION_BEGIN);
                mContext.sendBroadcast(broadcastIntent);
                // Intent wrapper
                Intent intent = new Intent(mContext, OpenPGPCryptoActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(OpenPgpApi.RESULT_INTENT, IntentCompat.getParcelableExtra(result, OpenPgpApi.RESULT_INTENT, PendingIntent.class));
                String openPGP = "Open PGP";
                // We don't need a DELETE intent since the time will be expired anyway
                NotificationCompat.Builder builder = NotificationUtils.getHighPriorityNotificationBuilder(mContext)
                        .setAutoCancel(true)
                        .setDefaults(Notification.DEFAULT_ALL)
                        .setWhen(System.currentTimeMillis())
                        .setSmallIcon(R.drawable.ic_default_notification)
                        .setTicker(openPGP)
                        .setContentTitle(openPGP)
                        .setSubText(openPGP)
                        .setContentText(mContext.getString(R.string.allow_open_pgp_operation));
                builder.setContentIntent(PendingIntentCompat.getActivity(mContext, 0, intent,
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT, false));
                NotificationUtils.displayHighPriorityNotification(mContext, builder.build());
                break;
            }
            case OpenPgpApi.RESULT_CODE_ERROR:
                mErrorFlag = true;
                OpenPgpError error = IntentCompat.getParcelableExtra(result, OpenPgpApi.RESULT_ERROR, OpenPgpError.class);
                if (error != null) {
                    Log.e(TAG, "handleResult: (%d) %s", error.getErrorId(), error.getMessage());
                } else Log.e(TAG, "handleResult: Error occurred during en/decryption process");
                break;
        }
    }
}
