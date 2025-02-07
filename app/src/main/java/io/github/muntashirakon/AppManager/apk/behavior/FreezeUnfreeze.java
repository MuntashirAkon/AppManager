// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandleHidden;
import android.text.SpannableStringBuilder;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.PendingIntentCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;

public final class FreezeUnfreeze {
    @IntDef(flag = true, value = {
            FLAG_ON_UNFREEZE_OPEN_APP,
            FLAG_ON_OPEN_APP_NO_TASK,
            FLAG_FREEZE_ON_PHONE_LOCKED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FreezeFlags {
    }

    public static final int FLAG_ON_UNFREEZE_OPEN_APP = 1 << 0;
    public static final int FLAG_ON_OPEN_APP_NO_TASK = 1 << 1;
    public static final int FLAG_FREEZE_ON_PHONE_LOCKED = 1 << 2;

    public static final int PRIVATE_FLAG_FREEZE_FORCE = 1 << 0;

    private static final String EXTRA_PACKAGE_NAME = "pkg";
    private static final String EXTRA_USER_ID = "user";
    private static final String EXTRA_FLAGS = "flags";
    private static final String EXTRA_FORCE_FREEZE = "force";

    @NonNull
    public static Intent getShortcutIntent(@NonNull Context context, @NonNull FreezeUnfreezeShortcutInfo shortcutInfo) {
        Intent intent = new Intent(context, FreezeUnfreezeActivity.class);
        intent.putExtra(EXTRA_PACKAGE_NAME, shortcutInfo.packageName);
        intent.putExtra(EXTRA_USER_ID, shortcutInfo.userId);
        intent.putExtra(EXTRA_FLAGS, shortcutInfo.flags);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    @Nullable
    public static FreezeUnfreezeShortcutInfo getShortcutInfo(@NonNull Intent intent) {
        String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        if (packageName == null) {
            return null;
        }
        int userId = intent.getIntExtra(EXTRA_USER_ID, UserHandleHidden.myUserId());
        int flags = intent.getIntExtra(EXTRA_FLAGS, 0);
        boolean force = intent.getBooleanExtra(EXTRA_FORCE_FREEZE, false);
        FreezeUnfreezeShortcutInfo shortcutInfo = new FreezeUnfreezeShortcutInfo(packageName, userId, flags);
        if (force) {
            shortcutInfo.addPrivateFlags(PRIVATE_FLAG_FREEZE_FORCE);
        }
        return shortcutInfo;
    }

    private static final Integer[] FREEZING_METHODS = new Integer[]{
            FreezeUtils.FREEZE_SUSPEND,
            FreezeUtils.FREEZE_ADV_SUSPEND,
            FreezeUtils.FREEZE_DISABLE,
            FreezeUtils.FREEZE_HIDE
    };

    private static final Integer[] FREEZING_METHOD_TITLES = new Integer[]{
            R.string.suspend_app,
            R.string.advanced_suspend_app,
            R.string.disable,
            R.string.hide_app
    };

    private static final Integer[] FREEZING_METHOD_DESCRIPTIONS = new Integer[]{
            R.string.suspend_app_description,
            R.string.advanced_suspend_app_description,
            R.string.disable_app_description,
            R.string.hide_app_description
    };

    @NonNull
    public static SearchableSingleChoiceDialogBuilder<Integer> getFreezeDialog(
            @NonNull Context context,
            @FreezeUtils.FreezeType int selectedType) {
        CharSequence[] itemDescription = new CharSequence[FREEZING_METHODS.length];
        for (int i = 0; i < FREEZING_METHODS.length; ++i) {
            itemDescription[i] = new SpannableStringBuilder()
                    .append(context.getString(FREEZING_METHOD_TITLES[i]))
                    .append("\n")
                    .append(UIUtils.getSmallerText(context.getString(FREEZING_METHOD_DESCRIPTIONS[i])));
        }
        return new SearchableSingleChoiceDialogBuilder<>(context, FREEZING_METHODS, itemDescription)
                .setSelectionIndex(ArrayUtils.indexOf(FREEZING_METHODS, selectedType));
    }

    static void launchApp(@NonNull FragmentActivity activity, @NonNull FreezeUnfreezeShortcutInfo shortcutInfo) {
        Intent launchIntent = PackageManagerCompat.getLaunchIntentForPackage(shortcutInfo.packageName, shortcutInfo.userId);
        if (launchIntent == null) {
            // No launch intent found
            return;
        }
        // launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        if ((shortcutInfo.flags & FLAG_ON_OPEN_APP_NO_TASK) != 0) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        }
        try {
            activity.startActivity(launchIntent);
            Intent intent = getShortcutIntent(activity, shortcutInfo);
            intent.putExtra(EXTRA_FORCE_FREEZE, true);
            PendingIntent pendingIntent = PendingIntentCompat.getActivity(activity, 0, intent,
                    PendingIntent.FLAG_ONE_SHOT, false);
            // There's a small chance that the notification by shortcutInfo.hasCode() already exists, in that case,
            // find the next one. This will cause trouble with dismissing the notification, but this is a viable
            // trade-off.
            String notificationTag = String.valueOf(shortcutInfo.hashCode());
            NotificationUtils.displayFreezeUnfreezeNotification(activity, notificationTag, builder -> builder
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.drawable.ic_default_notification)
                    .setTicker(activity.getText(R.string.freeze))
                    .setContentTitle(shortcutInfo.getName())
                    .setContentText(activity.getString(R.string.tap_to_freeze_app))
                    .setContentIntent(pendingIntent)
                    .build());
            if ((shortcutInfo.flags & FLAG_FREEZE_ON_PHONE_LOCKED) != 0) {
                Intent service = new Intent(intent)
                        .setClassName(activity, FreezeUnfreezeService.class.getName());
                ContextCompat.startForegroundService(activity, service);
            }
        } catch (Throwable th) {
            UIUtils.displayLongToast(th.getLocalizedMessage());
        }
    }
}
