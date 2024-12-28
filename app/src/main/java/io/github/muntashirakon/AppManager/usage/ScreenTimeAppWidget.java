// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.core.app.PendingIntentCompat;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;
import io.github.muntashirakon.util.UiUtils;

public class ScreenTimeAppWidget extends AppWidgetProvider {
    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        if (!FeatureController.isUsageAccessEnabled() || !SelfPermissions.checkUsageStatsPermission()) {
            return;
        }
        // Fetch colors
        context = AppearanceUtils.getThemedWidgetContext(context, false);
        // Fetch screens time
        int[] userIds = Users.getUsersIds();
        List<PackageUsageInfo> packageUsageInfoList = new ArrayList<>();
        AppUsageStatsManager usageStatsManager = AppUsageStatsManager.getInstance();
        for (int userId : userIds) {
            ExUtils.exceptionAsIgnored(() -> packageUsageInfoList.addAll(usageStatsManager
                    .getUsageStats(UsageUtils.USAGE_TODAY, userId)));
        }
        Collections.sort(packageUsageInfoList, (o1, o2) -> -Long.compare(o1.screenTime, o2.screenTime));
        long totalScreenTime = 0;
        for (PackageUsageInfo appItem : packageUsageInfoList) {
            totalScreenTime += appItem.screenTime;
        }
        // Construct the RemoteViews object
        Size appWidgetSize = getAppWidgetSize(context, appWidgetManager, appWidgetId);
        RemoteViews views;
        if (appWidgetSize.getHeight() <= 200) {
            views = new RemoteViews(context.getPackageName(), R.layout.app_widget_screen_time_small);
        } else if (appWidgetSize.getWidth() <= 250) {
            views = new RemoteViews(context.getPackageName(), R.layout.app_widget_screen_time);
        } else {
            views = new RemoteViews(context.getPackageName(), R.layout.app_widget_screen_time_large);
        }
        // Set screen time
        views.setTextViewText(R.id.screen_time, DateUtils.getFormattedDurationShort(totalScreenTime, false, true, false));
        int len = Math.min(packageUsageInfoList.size(), 3);
        // Set visibility
        int app3_visibility = len == 3 ? View.VISIBLE : View.INVISIBLE;
        int app2_visibility = len >= 2 ? View.VISIBLE : View.INVISIBLE;
        int app1_visibility = len >= 1 ? View.VISIBLE : View.INVISIBLE;
        views.setViewVisibility(R.id.app3_circle, app3_visibility);
        views.setViewVisibility(R.id.app3_time, app3_visibility);
        views.setViewVisibility(R.id.app3_label, app3_visibility);
        views.setViewVisibility(R.id.app2_circle, app2_visibility);
        views.setViewVisibility(R.id.app2_time, app2_visibility);
        views.setViewVisibility(R.id.app2_label, app2_visibility);
        views.setViewVisibility(R.id.app1_circle, app1_visibility);
        views.setViewVisibility(R.id.app1_time, app1_visibility);
        views.setViewVisibility(R.id.app1_label, app1_visibility);
        // Set app info
        if (app3_visibility == View.VISIBLE) {
            PackageUsageInfo item3 = packageUsageInfoList.get(2);
            views.setTextViewText(R.id.app3_label, item3.appLabel);
            views.setTextViewText(R.id.app3_time, DateUtils.getFormattedDurationSingle(item3.screenTime, false));
        }
        if (app2_visibility == View.VISIBLE) {
            PackageUsageInfo item2 = packageUsageInfoList.get(1);
            views.setTextViewText(R.id.app2_label, item2.appLabel);
            views.setTextViewText(R.id.app2_time, DateUtils.getFormattedDurationSingle(item2.screenTime, false));
        }
        if (app1_visibility == View.VISIBLE) {
            PackageUsageInfo item1 = packageUsageInfoList.get(0);
            views.setTextViewText(R.id.app1_label, item1.appLabel);
            views.setTextViewText(R.id.app1_time, DateUtils.getFormattedDurationSingle(item1.screenTime, false));
        }
        // Set colors
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean isNight = UiUtils.isDarkMode(context);
            int colorSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, "colorSurface");
            int colorSurfaceInverse = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceInverse, "colorSurfaceInverse");
            ColorStateList color1 = ColorStateList.valueOf(MaterialColors.harmonizeWithPrimary(context, Color.parseColor("#1b1b1b")));
            ColorStateList color2 = ColorStateList.valueOf(MaterialColors.harmonizeWithPrimary(context, Color.parseColor("#565e71")));
            ColorStateList color3 = ColorStateList.valueOf(MaterialColors.harmonizeWithPrimary(context, Color.parseColor("#d4e3ff")));
            views.setColorStateList(R.id.app1_time, "setBackgroundTintList", color1);
            views.setColorStateList(R.id.app1_circle, "setBackgroundTintList", color1);
            views.setColorStateList(R.id.app2_time, "setBackgroundTintList", color2);
            views.setColorStateList(R.id.app2_circle, "setBackgroundTintList", color2);
            views.setColorStateList(R.id.app3_time, "setBackgroundTintList", color3);
            views.setColorStateList(R.id.app3_circle, "setBackgroundTintList", color3);
            if (isNight) {
                views.setColorInt(R.id.widget_background, "setBackgroundColor", colorSurfaceInverse, colorSurface);
            } else views.setColorInt(R.id.widget_background, "setBackgroundColor", colorSurface, colorSurfaceInverse);
        }
        // Get PendingIntent for App Usage page
        Intent appUsageIntent = new Intent(context, AppUsageActivity.class);
        PendingIntent appUsagePendingIntent = PendingIntentCompat.getActivity(context, 0,
                appUsageIntent, PendingIntent.FLAG_UPDATE_CURRENT, false);
        views.setOnClickPendingIntent(R.id.widget_background, appUsagePendingIntent);
        // Get PendingIntent for widget update
        Intent appWidgetIntent = new Intent(context, ScreenTimeAppWidget.class)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
        PendingIntent appWidgetPendingIntent = PendingIntentCompat.getBroadcast(context, 0,
                appWidgetIntent, PendingIntent.FLAG_UPDATE_CURRENT, false);
        views.setOnClickPendingIntent(R.id.screen_time_refresh, appWidgetPendingIntent);
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
    private static Size getAppWidgetSize(@NonNull Context context, @NonNull AppWidgetManager manager, int appWidgetId) {
        Bundle appWidgetOptions = manager.getAppWidgetOptions(appWidgetId);
        boolean isPortrait = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        int width = appWidgetOptions.getInt(isPortrait ? AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH);
        int height = appWidgetOptions.getInt(isPortrait ? AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        return new Size(width, height);
    }
}