// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.helper;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.core.app.PendingIntentCompat;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logcat.LogcatRecordingService;
import io.github.muntashirakon.AppManager.logcat.RecordingWidgetProvider;
import io.github.muntashirakon.AppManager.logs.Log;

// Copyright 2012 Nolan Lawson
public class WidgetHelper {
    public static void updateWidgets(Context context) {
        int[] appWidgetIds = findAppWidgetIds(context);
        updateWidgets(context, appWidgetIds);
    }

    /**
     * manually tell us if the service is running or not
     */
    public static void updateWidgets(Context context, boolean serviceRunning) {
        int[] appWidgetIds = findAppWidgetIds(context);
        updateWidgets(context, appWidgetIds, serviceRunning);
    }

    public static void updateWidgets(Context context, int[] appWidgetIds) {
        boolean serviceRunning = ServiceHelper.checkIfServiceIsRunning(context, LogcatRecordingService.class);
        updateWidgets(context, appWidgetIds, serviceRunning);
    }

    public static void updateWidgets(Context context, @NonNull int[] appWidgetIds, boolean serviceRunning) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int appWidgetId : appWidgetIds) {
            if (!PreferenceHelper.getWidgetExistsPreference(context, appWidgetId)) {
                // android has a bug that sometimes keeps stale app widget ids around
                Log.d("WidgetHelper", "Found stale app widget id %d; skipping...", appWidgetId);
                continue;
            }
            updateWidget(context, manager, appWidgetId, serviceRunning);
        }
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId, boolean serviceRunning) {
        RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.widget_recording);
        // change the subtext depending on whether the service is running or not
        CharSequence subtext = context.getText(serviceRunning ? R.string.widget_recording_in_progress :
                R.string.widget_start_recording);
        updateViews.setTextViewText(R.id.widget_subtext, subtext);
        PendingIntent pendingIntent = getPendingIntent(context, appWidgetId);
        updateViews.setOnClickPendingIntent(R.id.clickable_linear_layout, pendingIntent);
        manager.updateAppWidget(appWidgetId, updateViews);
    }

    private static PendingIntent getPendingIntent(Context context, int appWidgetId) {
        Intent intent = new Intent(context, RecordingWidgetProvider.class);
        intent.setAction(RecordingWidgetProvider.ACTION_RECORD_OR_STOP);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        // gotta make this unique for this appwidgetid - otherwise, the PendingIntents conflict
        // it seems to be a quasi-bug in Android
        Uri data = Uri.withAppendedPath(Uri.parse(RecordingWidgetProvider.URI_SCHEME + "://widget/id/#"), String.valueOf(appWidgetId));
        intent.setData(data);
        return PendingIntentCompat.getBroadcast(context, 0 /* no requestCode */, intent,
                PendingIntent.FLAG_UPDATE_CURRENT, false);
    }

    private static int[] findAppWidgetIds(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName widget = new ComponentName(context, RecordingWidgetProvider.class);
        return manager.getAppWidgetIds(widget);
    }
}
