// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getBitmapFromDrawable;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getDimmedBitmap;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.behavior.FreezeUnfreeze;
import io.github.muntashirakon.AppManager.fm.FmActivity;
import io.github.muntashirakon.AppManager.fm.OpenWithActivity;
import io.github.muntashirakon.AppManager.profiles.AppsProfileActivity;

public final class LauncherShortcuts {
    public static void fm_createForFolder(@NonNull Context context, @NonNull String filename, @NonNull Uri uri) {
        Drawable icon = Objects.requireNonNull(ContextCompat.getDrawable(context, R.drawable.ic_folder));
        Intent intent = new Intent(context, FmActivity.class);
        intent.setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        LauncherIconCreator.createLauncherIcon(context, filename, icon, intent);
    }

    public static void fm_createForFile(@NonNull Context context, @NonNull String filename, @Nullable Bitmap icon,
                                        @NonNull Uri uri, @Nullable String customMimeType) {
        if (icon == null) {
            icon = getBitmapFromDrawable(Objects.requireNonNull(ContextCompat.getDrawable(context, R.drawable.ic_file)));
        }
        Intent intent = new Intent(context, OpenWithActivity.class);
        if (customMimeType != null) {
            intent.setDataAndType(uri, customMimeType);
        } else {
            intent.setData(uri);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        LauncherIconCreator.createLauncherIcon(context, filename, icon, intent);
    }

    public static void createFreezeUnfreezeShortcut(@NonNull Context context, @NonNull CharSequence appLabel,
                                                    @NonNull Bitmap icon, @NonNull String mPackageName,
                                                    @UserIdInt int userId, int flags, boolean isFrozen) {
        FreezeUnfreeze.ShortcutInfo shortcutInfo = new FreezeUnfreeze.ShortcutInfo(mPackageName, userId, flags);
        Intent shortcutIntent = FreezeUnfreeze.getShortcutIntent(context, shortcutInfo);
        shortcutInfo.setLabel(appLabel.toString());
        shortcutInfo.setIcon(isFrozen ? getDimmedBitmap(icon) : icon);
        LauncherIconCreator.createLauncherIcon(context, shortcutInfo.shortcutId, shortcutInfo.getLabel(),
                shortcutInfo.getIcon(), shortcutIntent);
    }

    public static void createForProfile(@NonNull Context context,
                                        @NonNull String profileName,
                                        @AppsProfileActivity.ShortcutType String shortcutType,
                                        CharSequence readableShortcutType) {
        Drawable icon = Objects.requireNonNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground));
        String shortcutName = profileName + " - " + readableShortcutType;
        Intent intent = AppsProfileActivity.getShortcutIntent(context, profileName, shortcutType, null);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        LauncherIconCreator.createLauncherIcon(context, shortcutName, icon, intent);
    }
}
