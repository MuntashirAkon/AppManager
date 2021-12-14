// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.PendingIntentCompat;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;

public class AMExceptionHandler implements Thread.UncaughtExceptionHandler {
    private final Thread.UncaughtExceptionHandler defaultUEH;
    private final Context context;

    public AMExceptionHandler(Context context) {
        this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
        this.context = context;
    }

    public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
        // Collect info
        StackTraceElement[] arr = e.getStackTrace();
        StringBuilder report = new StringBuilder(e.toString() + "\n");
        for (StackTraceElement traceElement : arr) {
            report.append("    at ").append(traceElement.toString()).append("\n");
        }
        Throwable cause = e.getCause();
        if (cause != null) {
            report.append(" Caused by: ").append(cause.toString()).append("\n");
            arr = cause.getStackTrace();
            for (StackTraceElement stackTraceElement : arr) {
                report.append("   at ").append(stackTraceElement.toString()).append("\n");
            }
        }
        report.append("\nDevice Info:\n");
        report.append(new DeviceInfo(context).toString());
        // Send notification
        Intent i = new Intent(Intent.ACTION_SEND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            i.setIdentifier(String.valueOf(System.currentTimeMillis()));
        }
        i.setType("message/rfc822");
        i.putExtra(Intent.EXTRA_EMAIL, new String[]{"muntashirakon@riseup.net"});
        i.putExtra(Intent.EXTRA_SUBJECT, "App Manager: Crash report");
        String body = report.toString();
        i.putExtra(Intent.EXTRA_TEXT, body);
        NotificationCompat.Builder builder = NotificationUtils.getHighPriorityNotificationBuilder(context);
        builder.setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setTicker(context.getText(R.string.app_name))
                .setContentTitle(context.getText(R.string.am_crashed))
                .setContentText(context.getText(R.string.tap_to_submit_crash_report));
        @SuppressLint("WrongConstant")
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0,
                Intent.createChooser(i, context.getText(R.string.send_crash_report)),
                PendingIntent.FLAG_ONE_SHOT | PendingIntentCompat.FLAG_IMMUTABLE);
        builder.setContentIntent(pendingIntent);
        NotificationUtils.displayHighPriorityNotification(context, builder.build());
        //
        defaultUEH.uncaughtException(t, e);
    }
}