// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.util.Size;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.PendingIntentCompat;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.DateUtils;

public class ScreenTimeAppWidget extends AppWidgetProvider {

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId) {
        // Fetch screens time
        int[] userIds = Users.getUsersIds();
        List<PackageUsageInfo> packageUsageInfoList = new ArrayList<>();
        for (int userId : userIds) {
            int _try = 5; // try to get usage stats at most 5 times
            do {
                try {
                    packageUsageInfoList.addAll(AppUsageStatsManager.getInstance(context)
                            .getUsageStats(UsageUtils.USAGE_TODAY, userId));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } while (0 != --_try && packageUsageInfoList.size() == 0);
        }
        Collections.sort(packageUsageInfoList, (o1, o2) -> -Long.compare(o1.screenTime, o2.screenTime));
        long totalScreenTime = 0;
        Set<Integer> users = new HashSet<>(3);
        for (PackageUsageInfo appItem : packageUsageInfoList) {
            totalScreenTime += appItem.screenTime;
            users.add(appItem.userId);
        }
        // Get pending intent
        Intent intent = new Intent(context, AppUsageActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0 /* no requestCode */, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntentCompat.FLAG_IMMUTABLE);
        // Construct the RemoteViews object
        Size appWidgetSize = getAppWidgetSize(context, appWidgetManager, appWidgetId);
        Log.w("WIDGET", "Size: " + appWidgetSize);
        RemoteViews views;
        if (appWidgetSize.getHeight() <= 200) {
            views = new RemoteViews(context.getPackageName(), R.layout.app_widget_screen_time_small);
        } else {
            if (appWidgetSize.getWidth() <= 250) {
                views = new RemoteViews(context.getPackageName(), R.layout.app_widget_screen_time);
            } else {
                views = new RemoteViews(context.getPackageName(), R.layout.app_widget_screen_time_large);
            }
        }
        // Set views
        views.setTextViewText(R.id.screen_time, DateUtils.getFormattedDurationShort(totalScreenTime, false, true, false));
        int len = Math.min(packageUsageInfoList.size(), 3);
        if (len > 0) {
            PackageUsageInfo item1 = packageUsageInfoList.get(0);
            views.setTextViewText(R.id.app1_label, item1.appLabel);
            views.setTextViewText(R.id.app1_time, DateUtils.getFormattedDurationSingle(item1.screenTime, false));
        }
        if (len > 1) {
            PackageUsageInfo item2 = packageUsageInfoList.get(1);
            views.setTextViewText(R.id.app2_label, item2.appLabel);
            views.setTextViewText(R.id.app2_time, DateUtils.getFormattedDurationSingle(item2.screenTime, false));
        }
        if (len == 3) {
            PackageUsageInfo item3 = packageUsageInfoList.get(2);
            views.setTextViewText(R.id.app3_label, item3.appLabel);
            views.setTextViewText(R.id.app3_time, DateUtils.getFormattedDurationSingle(item3.screenTime, false));
        }
        views.setOnClickPendingIntent(android.R.id.background, pendingIntent);
        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        updateAppWidget(context, appWidgetManager, appWidgetId);
    }

    @NonNull
    public static Size getAppWidgetSize(@NonNull Context context, @NonNull AppWidgetManager manager, int appWidgetId) {
        Bundle appWidgetOptions = manager.getAppWidgetOptions(appWidgetId);
        boolean isPortrait = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        int width = appWidgetOptions.getInt(isPortrait ? AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH);
        int height = appWidgetOptions.getInt(isPortrait ? AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        return new Size(width, height);
    }
}