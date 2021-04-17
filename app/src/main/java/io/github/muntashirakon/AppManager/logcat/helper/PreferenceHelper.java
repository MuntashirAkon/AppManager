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

package io.github.muntashirakon.AppManager.logcat.helper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.AppPref;

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

    public static boolean getHidePartialSelectHelpPreference(Context context) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPrefs.getBoolean(context.getText(R.string.pref_hide_partial_select_help).toString(), false);
    }

    public static void setHidePartialSelectHelpPreference(Context context, boolean bool) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Editor editor = sharedPrefs.edit();
        editor.putBoolean(context.getString(R.string.pref_hide_partial_select_help), bool);
        editor.apply();
    }

    @NonNull
    public static List<Integer> getBuffers() {
        @LogcatHelper.LogBufferId int buffers = AppPref.getInt(AppPref.PrefKey.PREF_LOG_VIEWER_BUFFER_INT);
        return getBuffers(buffers);
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
