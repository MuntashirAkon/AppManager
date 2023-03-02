// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.helper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Prefs;

// Copyright 2012 Nolan Lawson
public class PreferenceHelper {
    private static final String WIDGET_EXISTS_PREFIX = "widget_";

    public static boolean getWidgetExistsPreference(Context context, int appWidgetId) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String widgetExists = WIDGET_EXISTS_PREFIX.concat(Integer.toString(appWidgetId));
        return sharedPrefs.getBoolean(widgetExists, false);
    }

    public static void setWidgetExistsPreference(Context context, @NonNull int[] appWidgetIds) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Editor editor = sharedPrefs.edit();
        for (int appWidgetId : appWidgetIds) {
            String widgetExists = WIDGET_EXISTS_PREFIX.concat(Integer.toString(appWidgetId));
            editor.putBoolean(widgetExists, true);
        }
        editor.apply();
    }

    @NonNull
    public static List<Integer> getBuffers() {
        return getBuffers(Prefs.LogViewer.getBuffers());
    }

    @NonNull
    public static List<Integer> getBuffers(@LogcatHelper.LogBufferId int buffers) {
        List<Integer> separatedBuffers = new ArrayList<>();
        if ((buffers & LogcatHelper.LOG_ID_MAIN) != 0) {
            separatedBuffers.add(LogcatHelper.LOG_ID_MAIN);
        }
        if ((buffers & LogcatHelper.LOG_ID_RADIO) != 0) {
            separatedBuffers.add(LogcatHelper.LOG_ID_RADIO);
        }
        if ((buffers & LogcatHelper.LOG_ID_EVENTS) != 0) {
            separatedBuffers.add(LogcatHelper.LOG_ID_EVENTS);
        }
        if ((buffers & LogcatHelper.LOG_ID_SYSTEM) != 0) {
            separatedBuffers.add(LogcatHelper.LOG_ID_SYSTEM);
        }
        if ((buffers & LogcatHelper.LOG_ID_CRASH) != 0) {
            separatedBuffers.add(LogcatHelper.LOG_ID_CRASH);
        }
        return separatedBuffers;
    }

    public static boolean getIncludeDeviceInfoPreference(Context context) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPrefs.getBoolean(context.getString(R.string.pref_include_device_info), true);
    }

    public static void setIncludeDeviceInfoPreference(Context context, boolean value) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Editor editor = sharedPrefs.edit();
        editor.putBoolean(context.getString(R.string.pref_include_device_info), value);
        editor.apply();
    }

    public static boolean getIncludeDmesgPreference(Context context) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPrefs.getBoolean(context.getString(R.string.pref_include_dmesg), true);
    }

    public static void setIncludeDmesgPreference(Context context, boolean value) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Editor editor = sharedPrefs.edit();
        editor.putBoolean(context.getString(R.string.pref_include_dmesg), value);
        editor.apply();
    }
}
