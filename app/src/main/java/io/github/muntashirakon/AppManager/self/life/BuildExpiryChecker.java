// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self.life;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;

public final class BuildExpiryChecker {
    @IntDef({BUILD_TYPE_DEBUG, BUILD_TYPE_ALPHA, BUILD_TYPE_BETA, BUILD_TYPE_RC, BUILD_TYPE_STABLE})
    @Retention(RetentionPolicy.SOURCE)
    private @interface BuildType {}

    private static final int BUILD_TYPE_DEBUG = 0;
    private static final int BUILD_TYPE_ALPHA = 1;
    private static final int BUILD_TYPE_BETA = 2;
    private static final int BUILD_TYPE_RC = 3;
    private static final int BUILD_TYPE_STABLE = 4;

    private static final long[] TIME_SPAN_MILLIS = new long[]{
            2L * 30 * 24 * 60 * 60_000, // 2 months
            6L * 30 * 24 * 60 * 60_000, // 6 months
            6L * 30 * 24 * 60 * 60_000, // 6 months
            6L * 30 * 24 * 60 * 60_000, // 6 months
            18L * 30 * 24 * 60 * 60_000, // 18 months
    };

    private static final long[] WARNING_PERIOD_MILLIS = new long[]{
            14L * 24 * 60 * 60_000, // 2 weeks
            30L * 24 * 60 * 60_000, // 1 month
            30L * 24 * 60 * 60_000, // 1 month
            30L * 24 * 60 * 60_000, // 1 month
            3L * 30 * 24 * 60 * 60_000, // 3 months
    };

    @NonNull
    public static AlertDialog getBuildExpiredDialog(@NonNull FragmentActivity activity) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.app_manager_build_expired)
                .setMessage(R.string.app_manager_build_expired_message)
                .setCancelable(false)
                .setPositiveButton(R.string.update, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setData(getUpdateUri());
                    activity.startActivity(intent);
                    activity.finishAndRemoveTask();
                })
                .setNeutralButton(R.string.uninstall, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_DELETE);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID));
                    activity.startActivity(intent);
                    activity.finishAndRemoveTask();
                });
        if (getBuildType() == BUILD_TYPE_STABLE) {
            builder.setNeutralButton(R.string.action_continue, null);
        }
        return builder.create();
    }

    @Nullable
    public static Boolean buildExpired() {
        int buildType = getBuildType();
        long timeSpan = getCurrentTime() - getBuildTime();
        long realTimeSpan = TIME_SPAN_MILLIS[buildType];
        if (timeSpan <= realTimeSpan) {
            // Build hasn't yet expired
            return false;
        }
        // Build has expired
        long warningPeriod = WARNING_PERIOD_MILLIS[buildType];
        if (timeSpan <= realTimeSpan + warningPeriod) {
            // Build has expired but in warning period
            return null;
        }
        // Build has completely expired and should stop working
        return true;
    }

    private static long getCurrentTime() {
        // Ideally, we should fetch this from an SNTP server such as time.android.com
        return System.currentTimeMillis();
    }

    private static long getBuildTime() {
        return BuildConfig.BUILD_TIME_MILLIS;
    }

    private static Uri getUpdateUri() {
        if (getBuildType() == BUILD_TYPE_DEBUG) {
            return Uri.parse("https://github.com/MuntashirAkon/AppManager/actions/workflows/debug_build.yml");
        }
        // TODO: 3/12/22 For Stable builds, check F-Droid too
        return Uri.parse("https://github.com/MuntashirAkon/AppManager/releases");
    }

    @BuildType
    private static int getBuildType() {
        if (BuildConfig.DEBUG) {
            return BUILD_TYPE_DEBUG;
        }
        String[] versionParts = BuildConfig.VERSION_NAME.split("-");
        if (versionParts.length == 1) {
            return BUILD_TYPE_STABLE;
        }
        String lastPart = versionParts[versionParts.length - 1];
        switch (lastPart.substring(0, lastPart.length() - 2).toLowerCase(Locale.ROOT)) {
            case "alpha":
                return BUILD_TYPE_ALPHA;
            case "beta":
                return BUILD_TYPE_BETA;
            case "rc":
                return BUILD_TYPE_RC;
            default:
                throw new IllegalStateException("Invalid App Manager version");
        }
    }
}
