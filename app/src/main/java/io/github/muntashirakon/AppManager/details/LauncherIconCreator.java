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
import android.content.pm.PackageItemInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;

public class LauncherIconCreator {
    @NonNull
    private static Intent getActivityIntent(String packageName, String activityName) {
        Intent intent = new Intent();
        intent.setClassName(packageName, activityName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    /**
     * Create launcher icon.
     *
     * @param context                  Activity context
     * @param packageItemInfo          App package name
     * @param activityName             Name/Label of the app
     * @param activityIcon             App icon
     * @param activityIconResourceName App icon resource name
     */
    public static void createLauncherIcon(Context context, @NonNull PackageItemInfo packageItemInfo,
                                          String activityName, Drawable activityIcon,
                                          @Nullable String activityIconResourceName) {
        Intent activityIntent = getActivityIntent(packageItemInfo.packageName, packageItemInfo.name);
        if (activityIconResourceName != null) {
            final String pack = activityIconResourceName.substring(0, activityIconResourceName.indexOf(':'));
            if (!pack.equals(packageItemInfo.packageName)) {  // Icon is not from the same package
                activityIconResourceName = null;
            }
        }
        if (activityIconResourceName == null) Log.d("Launcher", "Empty resource");
        doCreateShortcut(context, activityName, activityIcon, activityIntent);
    }

    /**
     * Create launcher icon.
     *
     * @param context                  Activity context
     * @param packageName              App package name
     * @param activityName             Name/Label of the app
     * @param activityIcon             App icon
     * @param activityIconResourceName App icon resource name
     */
    public static void createLauncherIcon(Context context, @NonNull String packageName,
                                          String activityName, Drawable activityIcon,
                                          @Nullable String activityIconResourceName,
                                          Intent activityIntent) {
        if (activityIconResourceName != null) {
            final String pack = activityIconResourceName.substring(0, activityIconResourceName.indexOf(':'));
            if (!pack.equals(packageName)) {  // Icon is not from the same package
                activityIconResourceName = null;
            }
        }
        if (activityIconResourceName == null) Log.d("Launcher", "Empty resource");
        doCreateShortcut(context, activityName, activityIcon, activityIntent);
    }

    private static Bitmap getBitmapFromDrawable(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        Bitmap bmp = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bmp;
    }

    private static void doCreateShortcut(@NonNull Context context, @NonNull String appName, @NonNull Drawable drawable,
                                         @NonNull Intent intent) {
        Bitmap bitmap = getBitmapFromDrawable(drawable);

        ShortcutInfoCompat shortcutInfo = new ShortcutInfoCompat.Builder(context, appName)
                .setShortLabel(appName)
                .setLongLabel(appName)
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
}
