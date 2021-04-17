/*
 * Copyright (c) 2021 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.logcat;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.logcat.helper.PreferenceHelper;
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper;
import io.github.muntashirakon.AppManager.logcat.helper.WidgetHelper;

import java.util.Arrays;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.logs.Log;

public class RecordingWidgetProvider extends AppWidgetProvider {
    public static final String TAG = RecordingWidgetProvider.class.getSimpleName();

    public static final String ACTION_RECORD_OR_STOP = BuildConfig.APPLICATION_ID + ".action.RECORD_OR_STOP";

    public static final String URI_SCHEME = "log_viewer_widget";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        Log.d(TAG, "onUpdate() for appWidgetIds " + Arrays.toString(appWidgetIds));

        // track which widgets were created, since there's a bug in the android system that lets
        // stale app widget ids stick around
        PreferenceHelper.setWidgetExistsPreference(context, appWidgetIds);

        WidgetHelper.updateWidgets(context, appWidgetIds);
    }

    @Override
    public void onReceive(@NonNull final Context context, @NonNull Intent intent) {
        super.onReceive(context, intent);
        Log.d(TAG, "onReceive called with intent " + intent);
        if (ACTION_RECORD_OR_STOP.equals(intent.getAction())) {
            // Start or stop recording as necessary
            synchronized (RecordingWidgetProvider.class) {
                boolean alreadyRunning = ServiceHelper.checkIfServiceIsRunning(context, LogcatRecordingService.class);
                if (alreadyRunning) {
                    // stop the current recording process
                    ServiceHelper.stopBackgroundServiceIfRunning(context);
                } else {
                    // start a new recording process
                    Intent targetIntent = new Intent();
                    targetIntent.setClass(context, RecordLogDialogActivity.class);
                    targetIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

                    context.startActivity(targetIntent);
                }
            }
        }
    }
}
