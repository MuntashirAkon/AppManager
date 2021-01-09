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

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;

import java.io.File;
import java.util.concurrent.CountDownLatch;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.TAG;

public abstract class AMPackageInstaller {
    public static final String ACTION_INSTALL_STARTED = BuildConfig.APPLICATION_ID + ".action.INSTALL_STARTED";
    public static final String ACTION_INSTALL_COMPLETED = BuildConfig.APPLICATION_ID + ".action.INSTALL_COMPLETED";
    // For rootless installer to prevent PackageInstallerService from hanging
    public static final String ACTION_INSTALL_INTERACTION_BEGIN = BuildConfig.APPLICATION_ID + ".action.INSTALL_INTERACTION_BEGIN";
    public static final String ACTION_INSTALL_INTERACTION_END = BuildConfig.APPLICATION_ID + ".action.INSTALL_INTERACTION_END";

    /**
     * See {@link PackageInstaller#STATUS_SUCCESS}
     */
    public static final int STATUS_SUCCESS = PackageInstaller.STATUS_SUCCESS;
    /**
     * See {@link PackageInstaller#STATUS_FAILURE_ABORTED}
     */
    public static final int STATUS_FAILURE_ABORTED = PackageInstaller.STATUS_FAILURE_ABORTED;
    /**
     * See {@link PackageInstaller#STATUS_FAILURE_BLOCKED}
     */
    public static final int STATUS_FAILURE_BLOCKED = PackageInstaller.STATUS_FAILURE_BLOCKED;
    /**
     * See {@link PackageInstaller#STATUS_FAILURE_CONFLICT}
     */
    public static final int STATUS_FAILURE_CONFLICT = PackageInstaller.STATUS_FAILURE_CONFLICT;
    /**
     * See {@link PackageInstaller#STATUS_FAILURE_INCOMPATIBLE}
     */
    public static final int STATUS_FAILURE_INCOMPATIBLE = PackageInstaller.STATUS_FAILURE_INCOMPATIBLE;
    /**
     * See {@link PackageInstaller#STATUS_FAILURE_INVALID}
     */
    public static final int STATUS_FAILURE_INVALID = PackageInstaller.STATUS_FAILURE_INVALID;
    /**
     * See {@link PackageInstaller#STATUS_FAILURE_STORAGE}
     */
    public static final int STATUS_FAILURE_STORAGE = PackageInstaller.STATUS_FAILURE_STORAGE;
    // Custom status
    /**
     * The operation failed because the apk file(s) are not accessible.
     */
    public static final int STATUS_FAILURE_SECURITY = -2;
    /**
     * The operation failed because it failed to create an installer session.
     */
    public static final int STATUS_FAILURE_SESSION_CREATE = -3;
    /**
     * The operation failed because it failed to write apk files to session.
     */
    public static final int STATUS_FAILURE_SESSION_WRITE = -4;
    /**
     * The operation failed because it could not commit the installer session.
     */
    public static final int STATUS_FAILURE_SESSION_COMMIT = -5;
    /**
     * The operation failed because it could not abandon the installer session. This is a redundant
     * failure.
     */
    public static final int STATUS_FAILURE_SESSION_ABANDON = -6;
    /**
     * The operation failed because the current ROM is incompatible with PackageInstaller
     */
    public static final int STATUS_FAILURE_INCOMPATIBLE_ROM = -7;

    @SuppressLint("StaticFieldLeak")
    protected static final Context context = AppManager.getContext();

    protected CountDownLatch installWatcher;
    protected CountDownLatch interactionWatcher;

    protected boolean closeApkFile = false;
    @Nullable
    protected ApkFile apkFile;
    protected String packageName;
    protected CharSequence appLabel;
    protected int sessionId = -1;
    protected int userHandle;
    protected int finalStatus = STATUS_FAILURE_INVALID;
    private PackageInstallerBroadcastReceiver piReceiver;
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            if (intent.getAction() == null) return;
            int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
            Log.d(TAG, "Action: " + intent.getAction());
            Log.d(TAG, "Session ID: " + sessionId);
            switch (intent.getAction()) {
                case ACTION_INSTALL_STARTED:
                    // Session successfully created
                    break;
                case ACTION_INSTALL_INTERACTION_BEGIN:
                    // A install prompt is being shown to the user
                    // Run indefinitely until user finally decided to do something about it
                    break;
                case ACTION_INSTALL_INTERACTION_END:
                    // The install prompt is hidden by the user, either by clicking cancel or install,
                    // or just clicking on some place else (latter is our main focus)
                    if (AMPackageInstaller.this.sessionId == sessionId) {
                        // The user interaction is done, it doesn't take more than 1 minute now
                        interactionWatcher.countDown();
                    }
                    break;
                case ACTION_INSTALL_COMPLETED:
                    // Either it failed to create a session or the installation was completed,
                    // regardless of the status: success or failure
                    AMPackageInstaller.this.finalStatus = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, STATUS_FAILURE_INVALID);
                    // No need to check package name since it's been checked before
                    if (finalStatus == STATUS_FAILURE_SESSION_CREATE || (sessionId != -1 && AMPackageInstaller.this.sessionId == sessionId)) {
                        sendNotification(finalStatus, intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME));
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

    @CallSuper
    public boolean install(@NonNull ApkFile apkFile) {
        this.apkFile = apkFile;
        this.packageName = apkFile.getPackageName();
        initBroadcastReceiver();
        new Thread(() -> copyObb(apkFile)).start();
        return false;
    }

    @CallSuper
    public boolean install(@NonNull File[] apkFiles, String packageName) {
        this.apkFile = null;
        this.packageName = packageName;
        initBroadcastReceiver();
        return false;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected abstract boolean openSession();

    protected abstract boolean abandon();

    protected abstract boolean commit();

    protected abstract void copyObb(@NonNull ApkFile apkFile);

    protected void unregisterReceiver() {
        if (piReceiver != null) context.unregisterReceiver(piReceiver);
        context.unregisterReceiver(broadcastReceiver);
    }

    private void initBroadcastReceiver() {
        installWatcher = new CountDownLatch(1);
        interactionWatcher = new CountDownLatch(1);
        piReceiver = new PackageInstallerBroadcastReceiver();
        piReceiver.setAppLabel(appLabel);
        piReceiver.setPackageName(packageName);
        context.registerReceiver(piReceiver, new IntentFilter(PackageInstallerBroadcastReceiver.ACTION_PI_RECEIVER));
        // Add receivers
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_INSTALL_COMPLETED);
        intentFilter.addAction(ACTION_INSTALL_STARTED);
        intentFilter.addAction(ACTION_INSTALL_INTERACTION_BEGIN);
        intentFilter.addAction(ACTION_INSTALL_INTERACTION_END);
        context.registerReceiver(broadcastReceiver, intentFilter);
    }

    private void sendNotification(int status, String blockingPackage) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        NotificationCompat.Builder builder = NotificationUtils.getHighPriorityNotificationBuilder(context);
        builder.setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setTicker(appLabel)
                .setContentTitle(appLabel)
                .setSubText(context.getText(R.string.package_installer))
                .setContentText(getStringFromStatus(status, blockingPackage));
        if (intent != null) {
            builder.setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT));
        }
        NotificationUtils.displayHighPriorityNotification(context, builder.build());
    }

    @NonNull
    private String getStringFromStatus(int status, String blockingPackage) {
        switch (status) {
            case STATUS_SUCCESS:
                return context.getString(R.string.package_name_is_installed_successfully, appLabel);
            case STATUS_FAILURE_ABORTED:
                return getString(R.string.installer_error_aborted);
            case STATUS_FAILURE_BLOCKED:
                String blocker = getString(R.string.installer_error_blocked_device);
                if (blockingPackage != null) {
                    blocker = PackageUtils.getPackageLabel(context.getPackageManager(), blockingPackage);
                }
                return context.getString(R.string.installer_error_blocked, blocker);
            case STATUS_FAILURE_CONFLICT:
                return getString(R.string.installer_error_conflict);
            case STATUS_FAILURE_INCOMPATIBLE:
                return getString(R.string.installer_error_incompatible);
            case STATUS_FAILURE_INVALID:
                return getString(R.string.installer_error_bad_apks);
            case STATUS_FAILURE_STORAGE:
                return getString(R.string.installer_error_storage);
            case STATUS_FAILURE_SECURITY:
                return getString(R.string.installer_error_security);
            case STATUS_FAILURE_SESSION_CREATE:
                return getString(R.string.installer_error_session_create);
            case STATUS_FAILURE_SESSION_WRITE:
                return getString(R.string.installer_error_session_write);
            case STATUS_FAILURE_SESSION_COMMIT:
                return getString(R.string.installer_error_session_commit);
            case STATUS_FAILURE_SESSION_ABANDON:
                return getString(R.string.installer_error_session_abandon);
            case STATUS_FAILURE_INCOMPATIBLE_ROM:
                return getString(R.string.installer_error_lidl_rom);
        }
        return getString(R.string.installer_error_generic);
    }

    private String getString(@StringRes int stringRes) {
        return context.getString(stringRes);
    }

    static void sendStartedBroadcast(String packageName, int sessionId) {
        Intent broadcastIntent = new Intent(ACTION_INSTALL_STARTED);
        broadcastIntent.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName);
        broadcastIntent.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
        context.sendBroadcast(broadcastIntent);
    }

    static void sendCompletedBroadcast(String packageName, int status, int sessionId) {
        Intent broadcastIntent = new Intent(ACTION_INSTALL_COMPLETED);
        broadcastIntent.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName);
        broadcastIntent.putExtra(PackageInstaller.EXTRA_STATUS, status);
        broadcastIntent.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
        context.sendBroadcast(broadcastIntent);
    }
}
