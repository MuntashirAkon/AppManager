// SPDX-License-Identifier: ISC AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.muntashirakon.AppManager.R;

// Copyright 2017 Adam M. Szalkowski
public class LauncherIconCreator {
    /**
     * Create launcher icon.
     *
     * @param context         Activity context
     * @param packageItemInfo App package name
     * @param name            Name/Label of the app
     * @param icon            App icon
     */
    public static void createLauncherIcon(@NonNull Context context, @NonNull ActivityInfo packageItemInfo,
                                          @NonNull String name, @NonNull Drawable icon) {
        createLauncherIcon(context, name, icon, getIntent(packageItemInfo));
    }

    /**
     * Create launcher icon.
     *
     * @param context Activity context
     * @param name    Name/Label of the app
     * @param icon    App icon
     */
    public static void createLauncherIcon(@NonNull Context context, @NonNull String name, @NonNull Drawable icon,
                                          @NonNull Intent intent) {
        Bitmap bitmap = getBitmapFromDrawable(icon);

        // Set action for shortcut
        intent.setAction(Intent.ACTION_CREATE_SHORTCUT);

        ShortcutInfoCompat shortcutInfo = new ShortcutInfoCompat.Builder(context, name)
                .setShortLabel(name)
                .setLongLabel(name)
                .setIcon(IconCompat.createWithBitmap(bitmap))
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

    private static Bitmap getBitmapFromDrawable(@NonNull Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        Bitmap bmp = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bmp;
    }

    @NonNull
    private static Intent getIntent(@NonNull ActivityInfo itemInfo) {
        Intent intent = new Intent();
        intent.setClassName(itemInfo.packageName, itemInfo.name);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }
}
