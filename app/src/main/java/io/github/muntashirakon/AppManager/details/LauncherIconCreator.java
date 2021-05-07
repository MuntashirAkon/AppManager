/*
 * Copyright (C) 2020 Muntashir Al-Islam
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

// This is a modified version of LauncherIconCreator.java taken
// from https://github.com/butzist/ActivityLauncher/commit/dfb7fe271dae9379b5453bbb6e88f30a1adc94a9
// and was authored by Adam M. Szalkowski with ISC License.
// All derivative works are licensed under GPLv3.0.

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

public class LauncherIconCreator {
    /**
     * Create launcher icon.
     *
     * @param context                  Activity context
     * @param packageItemInfo          App package name
     * @param name             Name/Label of the app
     * @param icon             App icon
     */
    public static void createLauncherIcon(@NonNull Context context, @NonNull ActivityInfo packageItemInfo,
                                          @NonNull String name, @NonNull Drawable icon) {
        createLauncherIcon(context, name, icon, getIntent(packageItemInfo));
    }

    /**
     * Create launcher icon.
     *
     * @param context                  Activity context
     * @param name             Name/Label of the app
     * @param icon             App icon
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
