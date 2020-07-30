// This is a modified version of LauncherIconCreator.java taken
// from https://github.com/butzist/ActivityLauncher/commit/dfb7fe271dae9379b5453bbb6e88f30a1adc94a9
// and was authored by Adam M. Szalkowski with ISC License.
// All derivative works are licensed under GPLv3.0.

package io.github.muntashirakon.AppManager.utils;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import java.util.Objects;

import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.R;

public class LauncherIconCreator {

    private static Intent getActivityIntent(String packageName, String activityName) {
        Intent intent = new Intent();
        intent.setClassName(packageName, activityName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    /**
     * Create launcher icon.
     * @param context Activity context
     * @param activityInfo App package name
     * @param activityName Name/Label of the app
     * @param activityIcon App icon
     * @param activityIconResourceName App icon resource name
     */
    public static void createLauncherIcon(Context context, ActivityInfo activityInfo,
                                          String activityName, Drawable activityIcon,
                                          @Nullable String activityIconResourceName) {
        Intent activityIntent = getActivityIntent(activityInfo.packageName, activityInfo.name);
        if (activityIconResourceName != null) {
            final String pack = activityIconResourceName.substring(0, activityIconResourceName.indexOf(':'));
            if (!pack.equals(activityInfo.packageName)) {  // Icon is not from the same package
                activityIconResourceName = null;
            }
        }
        if (activityIconResourceName == null) Log.d("Launcher", "Empty resource");
        if (Build.VERSION.SDK_INT >= 26) {
            doCreateShortcut(context, activityName, activityIcon, activityIntent);
        } else {
            doCreateShortcut(context, activityName, activityIntent, activityIconResourceName);
        }
    }

    /**
     * Launch activity.
     * @param context Activity context
     * @param activityInfo Activity info
     */
    public static void launchActivity(Context context, ActivityInfo activityInfo) {
        Intent intent = getActivityIntent(activityInfo.packageName, activityInfo.name);
        Toast.makeText(context, context.getString(R.string.starting_activity, activityInfo.name), Toast.LENGTH_LONG).show();
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, context.getText(R.string.error).toString() + ": " + e.toString(), Toast.LENGTH_LONG).show();
        }
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

    @TargetApi(14)
    private static void doCreateShortcut(Context context, String appName, Intent intent, String iconResourceName) {
        Intent shortcutIntent = new Intent();
        shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, intent);
        shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, appName);
        if (iconResourceName != null) {
            Intent.ShortcutIconResource ir = new Intent.ShortcutIconResource();
            if (intent.getComponent() == null) {
                ir.packageName = intent.getPackage();
            } else {
                ir.packageName = intent.getComponent().getPackageName();
            }
            ir.resourceName = iconResourceName;
            shortcutIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, ir);
        }
        shortcutIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
        context.sendBroadcast(shortcutIntent);
    }

    @TargetApi(26)
    private static void doCreateShortcut(Context context, String appName, Drawable draw, Intent intent) {
        ShortcutManager shortcutManager = Objects.requireNonNull(context.getSystemService(ShortcutManager.class));

        if (shortcutManager.isRequestPinShortcutSupported()) {
            Bitmap bitmap = getBitmapFromDrawable(draw);
            intent.setAction(Intent.ACTION_CREATE_SHORTCUT);

            ShortcutInfo shortcutInfo = new ShortcutInfo.Builder(context, appName)
                    .setShortLabel(appName)
                    .setLongLabel(appName)
                    .setIcon(Icon.createWithBitmap(bitmap))
                    .setIntent(intent)
                    .build();

            shortcutManager.requestPinShortcut(shortcutInfo, null);
        } else {
            new AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.error_creating_shortcut))
                    .setMessage(context.getString(R.string.error_verbose_pin_shortcut))
                    .setPositiveButton(context.getString(android.R.string.ok), (dialog, which) -> {
                        // Just close dialog don't do anything
                        dialog.cancel();
                    })
                    .show();
        }
    }
}
