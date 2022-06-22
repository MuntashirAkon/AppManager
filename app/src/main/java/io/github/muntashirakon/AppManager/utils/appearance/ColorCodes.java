// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils.appearance;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import io.github.muntashirakon.AppManager.R;

public final class ColorCodes {
    @ColorInt
    public static int getListItemSelectionColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.highlight);
    }

    @ColorInt
    public static int getQueryStringHighlightColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.red);
    }

    public static int getSuccessColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.stopped);
    }

    public static int getFailureColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.electric_red);
    }

    public static int getAppDisabledIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.disabled_user);
    }

    public static int getAppForceStoppedIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.stopped);
    }

    public static int getAppKeystoreIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.tracker);
    }

    public static int getAppNoBatteryOptimizationIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.red_orange);
    }

    public static int getAppSsaidIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.tracker);
    }

    public static int getAppPlayAppSigningIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.disabled_user);
    }

    public static int getAppSuspendedIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.stopped);
    }

    public static int getAppHiddenIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.disabled_user);
    }

    public static int getAppUninstalledIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.red);
    }

    public static int getBackupLatestIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.stopped);
    }

    public static int getBackupOutdatedIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.orange);
    }

    public static int getBackupUninstalledIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.red);
    }

    public static int getComponentRunningIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.running);
    }

    public static int getComponentTrackerIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.tracker);
    }

    public static int getComponentTrackerBlockedIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.stopped);
    }

    public static int getComponentBlockedIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.red);
    }

    public static int getComponentExternallyBlockedIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.disabled_user);
    }

    public static int getPermissionDangerousIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.red);
    }

    public static int getScannerTrackerIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.electric_red);
    }

    public static int getScannerNoTrackerIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.stopped);
    }

    public static int getVirusTotalSafeIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.stopped);
    }

    public static int getVirusTotalUnsafeIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.tracker);
    }

    public static int getVirusTotalExtremelyUnsafeIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.electric_red);
    }

    public static int getWhatsNewPlusIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.stopped);
    }

    public static int getWhatsNewMinusIndicatorColor(@NonNull Context context) {
        return ContextCompat.getColor(context, R.color.electric_red);
    }
}
