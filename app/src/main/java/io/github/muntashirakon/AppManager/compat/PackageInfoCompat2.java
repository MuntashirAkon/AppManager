// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.content.pm.PackageInfo;
import android.content.pm.PackageInfoHidden;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

import dev.rikka.tools.refine.Refine;
import io.github.muntashirakon.AppManager.utils.ExUtils;

public class PackageInfoCompat2 {
    @Nullable
    public static String getOverlayTarget(@NonNull PackageInfo packageInfo) {
        return Refine.<PackageInfoHidden>unsafeCast(packageInfo).overlayTarget;
    }

    @Nullable
    public static String getTargetOverlayableName(@NonNull PackageInfo packageInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return Refine.<PackageInfoHidden>unsafeCast(packageInfo).targetOverlayableName;
        }
        return null;
    }

    @Nullable
    public static String getOverlayCategory(@NonNull PackageInfo packageInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Refine.<PackageInfoHidden>unsafeCast(packageInfo).overlayCategory;
        }
        return null;
    }

    public static int getOverlayPriority(@NonNull PackageInfo packageInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Refine.<PackageInfoHidden>unsafeCast(packageInfo).overlayPriority;
        }
        return 0; // MAX priority
    }

    public static boolean isStaticOverlayPackage(@NonNull PackageInfo packageInfo) {
        PackageInfoHidden info = Refine.unsafeCast(packageInfo);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return info.isStaticOverlayPackage();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Optional.ofNullable(ExUtils.exceptionAsNull(() -> info.isStaticOverlay))
                    .orElse((info.overlayFlags & PackageInfoHidden.FLAG_OVERLAY_STATIC) != 0);
        }
        // Static is by default
        return true;
    }
}
