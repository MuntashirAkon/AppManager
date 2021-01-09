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

import android.app.PendingIntent;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.main.MainActivity;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

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

    private NotificationCompat.Builder builder;
    private NotificationManagerCompat notificationManager;

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
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) return;
        int apkFileKey = intent.getIntExtra(EXTRA_APK_FILE_KEY, -1);
        if (apkFileKey == -1) return;
        String appLabel = intent.getStringExtra(EXTRA_APP_LABEL);
        // Set package name in the ongoing notification
        builder.setContentTitle(appLabel);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
        int userHandle = intent.getIntExtra(EXTRA_USER_ID, Users.getCurrentUserHandle());
        // Install package
        PackageInstallerCompat pi = PackageInstallerCompat.getNewInstance(userHandle);
        pi.setAppLabel(appLabel);
        pi.setCloseApkFile(intent.getBooleanExtra(EXTRA_CLOSE_APK_FILE, false));
        pi.install(ApkFile.getInstance(apkFileKey));
    }
}
