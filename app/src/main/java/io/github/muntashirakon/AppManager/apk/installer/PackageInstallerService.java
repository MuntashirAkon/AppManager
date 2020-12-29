/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.apk.installer;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.main.MainActivity;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class PackageInstallerService extends ForegroundService {
    public static final String EXTRA_APK_FILE_KEY = "EXTRA_APK_FILE_KEY";
    public static final String EXTRA_APP_LABEL = "EXTRA_APP_LABEL";
    public static final String EXTRA_USER_ID = "EXTRA_USER_ID";
    public static final String EXTRA_CLOSE_APK_FILE = "EXTRA_CLOSE_APK_FILE";
    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel.INSTALL";
    public static final int NOTIFICATION_ID = 3;

    public PackageInstallerService() {
        super("PackageInstallerService");
    }

    private final CountDownLatch installWatcher = new CountDownLatch(1);
    private final CountDownLatch interactionWatcher = new CountDownLatch(1);

    private boolean closeApkFile = false;
    private String appLabel;
    private String packageName;
    private int sessionId = -1;
    private ApkFile apkFile;
    private NotificationCompat.Builder builder;
    private NotificationManagerCompat notificationManager;
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            if (intent.getAction() == null) return;
            int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
            Log.d("PIS", "Action: " + intent.getAction());
            Log.d("PIS", "Session ID: " + sessionId);
            switch (intent.getAction()) {
                case AMPackageInstaller.ACTION_INSTALL_STARTED:
                    // Session successfully created
                    String packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
                    if (PackageInstallerService.this.packageName.equals(packageName)) {
                        PackageInstallerService.this.sessionId = sessionId;
                    }
                    break;
                case AMPackageInstaller.ACTION_INSTALL_INTERACTION_BEGIN:
                    // A install prompt is being shown to the user
                    if (PackageInstallerService.this.sessionId == sessionId) {
                        // Run indefinitely until user finally decided to do something about it
                    }
                    break;
                case AMPackageInstaller.ACTION_INSTALL_INTERACTION_END:
                    // The install prompt is hidden by the user, either by clicking cancel or install,
                    // or just clicking on some place else (latter is our main focus)
                    if (PackageInstallerService.this.sessionId == sessionId) {
                        // The user interaction is done, it doesn't take more than 1 minute now
                        interactionWatcher.countDown();
                    }
                    break;
                case AMPackageInstaller.ACTION_INSTALL_COMPLETED:
                    // Either it failed to create a session or the installation was completed,
                    // regardless of the status: success or failure
                    int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, AMPackageInstaller.STATUS_FAILURE_INVALID);
                    // No need to check package name since it's been checked before
                    if (status == AMPackageInstaller.STATUS_FAILURE_SESSION_CREATE
                            || (sessionId != -1 && PackageInstallerService.this.sessionId == sessionId)) {
                        sendNotification(status, intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME));
                        if (closeApkFile && apkFile != null) {
                            apkFile.close();
                        }
                        interactionWatcher.countDown();
                        installWatcher.countDown();
                    }
                    break;
            }
        }
    };
    private AMPackageInstallerBroadcastReceiver piReceiver;

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        notificationManager = NotificationUtils.getNewNotificationManager(this, CHANNEL_ID,
                "Install Progress", NotificationManagerCompat.IMPORTANCE_LOW);
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);
        builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(null)
                .setContentText(getString(R.string.install_in_progress))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setSubText(getText(R.string.package_installer))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(0, 0, true)
                .setContentIntent(pendingIntent);
        startForeground(NOTIFICATION_ID, builder.build());
        // Add receivers
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(AMPackageInstaller.ACTION_INSTALL_COMPLETED);
        intentFilter.addAction(AMPackageInstaller.ACTION_INSTALL_STARTED);
        intentFilter.addAction(AMPackageInstaller.ACTION_INSTALL_INTERACTION_BEGIN);
        intentFilter.addAction(AMPackageInstaller.ACTION_INSTALL_INTERACTION_END);
        registerReceiver(broadcastReceiver, intentFilter);
        piReceiver = new AMPackageInstallerBroadcastReceiver();
        registerReceiver(piReceiver, new IntentFilter(AMPackageInstallerBroadcastReceiver.ACTION_PI_RECEIVER));
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) return;
        int apkFileKey = intent.getIntExtra(EXTRA_APK_FILE_KEY, -1);
        if (apkFileKey == -1) return;
        apkFile = ApkFile.getInstance(apkFileKey);
        packageName = apkFile.getPackageName();
        piReceiver.setPackageName(packageName);
        appLabel = intent.getStringExtra(EXTRA_APP_LABEL);
        piReceiver.setAppLabel(appLabel);
        closeApkFile = intent.getBooleanExtra(EXTRA_CLOSE_APK_FILE, false);
        // Set package name in the ongoing notification
        builder.setContentTitle(appLabel);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
        // Start writing Obb files
        new Thread(() -> {
            if (apkFile.hasObb()) {
                boolean tmpCloseApkFile = closeApkFile;
                // Disable closing apk file in case the install is finished already.
                closeApkFile = false;
                if (!apkFile.extractObb()) {  // FIXME: Extract OBB for user handle
                    new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(this,
                            R.string.failed_to_extract_obb_files, Toast.LENGTH_LONG).show());
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(this,
                            R.string.obb_files_extracted_successfully, Toast.LENGTH_LONG).show());
                }
                if (installWatcher.getCount() != 0) {
                    // Reset close apk file if the install isn't completed
                    closeApkFile = tmpCloseApkFile;
                } else {
                    // Install completed, close apk file if requested
                    if (tmpCloseApkFile) apkFile.close();
                }
            }
        }).start();
        int userHandle = intent.getIntExtra(EXTRA_USER_ID, Users.getCurrentUserHandle());
        // Install package
        PackageInstallerCompat.getInstance(userHandle).install(apkFile);
        // Wait for user interaction (if needed)
        try {
            // Wait for user interaction
            interactionWatcher.await();
            // Wait for the install to complete
            installWatcher.await(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Log.e("PIS", "Installation interrupted.", e);
        }
        // Remove session if exist
        try {
            getPackageManager().getPackageInstaller().abandonSession(sessionId);
        } catch (SecurityException ignore) {
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(broadcastReceiver);
        unregisterReceiver(piReceiver);
        super.onDestroy();
    }

    private void sendNotification(int status, String blockingPackage) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        NotificationCompat.Builder builder = NotificationUtils.getHighPriorityNotificationBuilder(this);
        builder.setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setTicker(appLabel)
                .setContentTitle(appLabel)
                .setSubText(getText(R.string.package_installer))
                .setContentText(getStringFromStatus(status, blockingPackage));
        if (intent != null) {
            builder.setContentIntent(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT));
        }
        NotificationUtils.displayHighPriorityNotification(this, builder.build());
    }

    @NonNull
    private String getStringFromStatus(int status, String blockingPackage) {
        switch (status) {
            case AMPackageInstaller.STATUS_SUCCESS:
                return getString(R.string.package_name_is_installed_successfully, appLabel);
            case AMPackageInstaller.STATUS_FAILURE_ABORTED:
                return getString(R.string.installer_error_aborted);
            case AMPackageInstaller.STATUS_FAILURE_BLOCKED:
                String blocker = getString(R.string.installer_error_blocked_device);
                if (blockingPackage != null) {
                    blocker = PackageUtils.getPackageLabel(getPackageManager(), blockingPackage);
                }
                return getString(R.string.installer_error_blocked, blocker);
            case AMPackageInstaller.STATUS_FAILURE_CONFLICT:
                return getString(R.string.installer_error_conflict);
            case AMPackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                return getString(R.string.installer_error_incompatible);
            case AMPackageInstaller.STATUS_FAILURE_INVALID:
                return getString(R.string.installer_error_bad_apks);
            case AMPackageInstaller.STATUS_FAILURE_STORAGE:
                return getString(R.string.installer_error_storage);
            case AMPackageInstaller.STATUS_FAILURE_SECURITY:
                return getString(R.string.installer_error_security);
            case AMPackageInstaller.STATUS_FAILURE_SESSION_CREATE:
                return getString(R.string.installer_error_session_create);
            case AMPackageInstaller.STATUS_FAILURE_SESSION_WRITE:
                return getString(R.string.installer_error_session_write);
            case AMPackageInstaller.STATUS_FAILURE_SESSION_COMMIT:
                return getString(R.string.installer_error_session_commit);
            case AMPackageInstaller.STATUS_FAILURE_SESSION_ABANDON:
                return getString(R.string.installer_error_session_abandon);
            case AMPackageInstaller.STATUS_FAILURE_INCOMPATIBLE_ROM:
                return getString(R.string.installer_error_lidl_rom);
        }
        return getString(R.string.installer_error_generic);
    }
}
