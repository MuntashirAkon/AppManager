// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getBitmapFromDrawable;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.UUID;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.ActivityLauncherShortcutActivity;

// Copyright 2017 Adam M. Szalkowski
public class LauncherIconCreator {
    /**
     * Create launcher icon.
     *
     * @param context      Activity context
     * @param activityInfo App package name
     * @param name         Name/Label of the app
     * @param icon         App icon
     */
    public static void createLauncherIcon(@NonNull Context context, @NonNull ActivityInfo activityInfo,
                                          @NonNull CharSequence name, @NonNull Drawable icon) {
        createLauncherIcon(context, name, icon, requireProxy(activityInfo) ? getProxyIntent(activityInfo) : getIntent(activityInfo));
    }

    /**
     * Create launcher icon.
     *
     * @param context Context
     * @param name    Name/Label of the app
     * @param icon    App icon
     * @param intent  Shortcut intent
     */
    public static void createLauncherIcon(@NonNull Context context, @NonNull CharSequence name, @NonNull Drawable icon,
                                          @NonNull Intent intent) {
        createLauncherIcon(context, UUID.randomUUID().toString(), name, icon, intent);
    }

    /**
     * Create launcher icon.
     *
     * @param context Context
     * @param name    Name/Label of the app
     * @param icon    App icon
     * @param intent  Shortcut intent
     */
    public static void createLauncherIcon(@NonNull Context context, @NonNull CharSequence name, @NonNull Bitmap icon,
                                          @NonNull Intent intent) {
        createLauncherIcon(context, UUID.randomUUID().toString(), name, icon, intent);
    }

    /**
     * Create launcher icon.
     *
     * @param context    Context
     * @param shortcutId Shortcut ID
     * @param name       Name/Label of the app
     * @param icon       App icon
     * @param intent     Shortcut intent
     */
    public static void createLauncherIcon(@NonNull Context context, @NonNull String shortcutId,
                                          @NonNull CharSequence name, @NonNull Drawable icon,
                                          @NonNull Intent intent) {
        createLauncherIcon(context, shortcutId, name, getBitmapFromDrawable(icon), intent);
    }

    public static void createLauncherIcon(@NonNull Context context, @NonNull String shortcutId,
                                          @NonNull CharSequence name, @NonNull Bitmap icon,
                                          @NonNull Intent intent) {

        // Set action for shortcut
        intent.setAction(Intent.ACTION_CREATE_SHORTCUT);

        ShortcutInfoCompat shortcutInfo = new ShortcutInfoCompat.Builder(context, shortcutId)
                // Enforce shortcut name to be a String
                .setShortLabel(name.toString())
                .setLongLabel(name)
                .setIcon(IconCompat.createWithBitmap(icon))
                .setIntent(intent)
                .build();

        if (!ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(context.getString(R.string.error_creating_shortcut))
                    .setMessage(context.getString(R.string.error_verbose_pin_shortcut))
                    .setPositiveButton(context.getString(R.string.ok), (dialog, which) -> dialog.cancel())
                    .show();
        }
    }

    @NonNull
    private static Intent getIntent(@NonNull ActivityInfo itemInfo) {
        Intent intent = new Intent();
        intent.setClassName(itemInfo.packageName, itemInfo.name);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    @NonNull
    private static Intent getProxyIntent(@NonNull ActivityInfo itemInfo) {
        Intent intent = new Intent();
        intent.setClass(AppManager.getContext(), ActivityLauncherShortcutActivity.class);
        intent.putExtra(ActivityLauncherShortcutActivity.EXTRA_PKG, itemInfo.packageName);
        intent.putExtra(ActivityLauncherShortcutActivity.EXTRA_CLS, itemInfo.name);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    private static boolean requireProxy(@NonNull ActivityInfo activityInfo) {
        return !BuildConfig.APPLICATION_ID.equals(activityInfo.packageName);
    }
}
