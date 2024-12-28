// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.format.Formatter;
import android.widget.RemoteViews;

import androidx.collection.SparseArrayCompat;
import androidx.core.app.PendingIntentCompat;

import com.google.android.material.color.MaterialColors;

import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;
import io.github.muntashirakon.util.UiUtils;

public class DataUsageAppWidget extends AppWidgetProvider {
    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        if (!FeatureController.isUsageAccessEnabled() || !SelfPermissions.checkUsageStatsPermission()) {
            return;
        }
        // Fetch colors
        context = AppearanceUtils.getThemedWidgetContext(context, false);
        // Fetch data
        UsageUtils.TimeInterval interval = UsageUtils.getTimeInterval(UsageUtils.USAGE_TODAY);
        SparseArrayCompat<AppUsageStatsManager.DataUsage> mobileData = AppUsageStatsManager.getMobileData(interval);
        SparseArrayCompat<AppUsageStatsManager.DataUsage> wifiData = AppUsageStatsManager.getWifiData(interval);
        long mobileTx = 0;
        long mobileRx = 0;
        long wifiTx = 0;
        long wifiRx = 0;
        AppUsageStatsManager.DataUsage usage;
        for (int i = 0; i < mobileData.size(); ++i) {
            usage = Objects.requireNonNull(mobileData.valueAt(i));
            mobileRx += usage.getRx();
            mobileTx += usage.getTx();
        }
        for (int i = 0; i < wifiData.size(); ++i) {
            usage = Objects.requireNonNull(wifiData.valueAt(i));
            wifiRx += usage.getRx();
            wifiTx += usage.getTx();
        }
        long totalTx = mobileTx + wifiTx;
        long totalRx = mobileRx + wifiRx;
        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.app_widget_data_usage_small);
        // Set data usage
        views.setTextViewText(R.id.data_usage, String.format("↑ %1$s ↓ %2$s",
                Formatter.formatShortFileSize(context, totalTx),
                Formatter.formatShortFileSize(context, totalRx)));
        // Set colors
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean isNight = UiUtils.isDarkMode(context);
            int colorSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, "colorSurface");
            int colorSurfaceInverse = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceInverse, "colorSurfaceInverse");
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
        Intent appWidgetIntent = new Intent(context, DataUsageAppWidget.class)
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
}