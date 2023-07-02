// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.PendingIntentCompat;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

public class AMExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static final String E = "muntashirakon@riseup.net";

    private final Thread.UncaughtExceptionHandler mDefaultExceptionHandler;
    private final Context mContext;

    public AMExceptionHandler(Context context) {
        mDefaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        mContext = context;
    }

    public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
        // Collect info
        StackTraceElement[] arr = e.getStackTrace();
        StringBuilder report = new StringBuilder(e + "\n");
        for (StackTraceElement traceElement : arr) {
            report.append("    at ").append(traceElement.toString()).append("\n");
        }
        Throwable cause = e;
        while((cause = cause.getCause()) != null) {
            report.append(" Caused by: ").append(cause).append("\n");
            arr = cause.getStackTrace();
            for (StackTraceElement stackTraceElement : arr) {
                report.append("   at ").append(stackTraceElement.toString()).append("\n");
            }
        }
        report.append("\nDevice Info:\n");
        report.append(new DeviceInfo(mContext));
        // Send notification
        Intent i = new Intent(Intent.ACTION_SEND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            i.setIdentifier(String.valueOf(System.currentTimeMillis()));
        }
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_EMAIL, new String[]{E});
        i.putExtra(Intent.EXTRA_SUBJECT, "App Manager: Crash report");
        String body = report.toString();
        i.putExtra(Intent.EXTRA_TEXT, body);
        PendingIntent pendingIntent = PendingIntentCompat.getActivity(mContext, 0,
                Intent.createChooser(i, mContext.getText(R.string.send_crash_report)),
                PendingIntent.FLAG_ONE_SHOT, true);
        NotificationCompat.Builder builder = NotificationUtils.getHighPriorityNotificationBuilder(mContext)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setTicker(mContext.getText(R.string.app_name))
                .setContentTitle(mContext.getText(R.string.am_crashed))
                .setContentText(mContext.getText(R.string.tap_to_submit_crash_report))
                .setContentIntent(pendingIntent);
        NotificationUtils.displayHighPriorityNotification(mContext, builder.build());
        // Manage the rests via the default handler
        mDefaultExceptionHandler.uncaughtException(t, e);
    }
}