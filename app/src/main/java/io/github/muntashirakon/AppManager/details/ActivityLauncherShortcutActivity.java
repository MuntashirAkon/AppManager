// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.Manifest;
import android.annotation.UserIdInt;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class ActivityLauncherShortcutActivity extends BaseActivity {
    private static final String EXTRA_PKG = BuildConfig.APPLICATION_ID + ".intent.EXTRA.shortcut.pkg";
    private static final String EXTRA_CLS = BuildConfig.APPLICATION_ID + ".intent.EXTRA.shortcut.cls";
    private static final String EXTRA_AST = BuildConfig.APPLICATION_ID + ".intent.EXTRA.shortcut.ast";
    private static final String EXTRA_USR = BuildConfig.APPLICATION_ID + ".intent.EXTRA.shortcut.usr";

    public static Intent getShortcutIntent(@NonNull Context context, @NonNull String pkg, @NonNull String clazz,
                                           @UserIdInt int userId, boolean launchViaAssist) {
        return new Intent()
                .setClass(context, ActivityLauncherShortcutActivity.class)
                .putExtra(ActivityLauncherShortcutActivity.EXTRA_PKG, pkg)
                .putExtra(ActivityLauncherShortcutActivity.EXTRA_CLS, clazz)
                .putExtra(ActivityLauncherShortcutActivity.EXTRA_USR, userId)
                .putExtra(ActivityLauncherShortcutActivity.EXTRA_AST, launchViaAssist)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        Intent intent = getIntent();
        if (!Intent.ACTION_CREATE_SHORTCUT.equals(intent.getAction()) || !intent.hasExtra(EXTRA_PKG) || !intent.hasExtra(EXTRA_CLS)) {
            // Invalid intent
            finish();
            return;
        }
        intent.setAction(null);
        intent.setClassName(intent.getStringExtra(EXTRA_PKG), intent.getStringExtra(EXTRA_CLS));
        int currentUserId = UserHandleHidden.myUserId();
        int userId = intent.getIntExtra(EXTRA_USR, currentUserId);
        boolean canLaunchViaAssist = SelfPermissions.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS);
        boolean launchViaAssist = intent.getBooleanExtra(EXTRA_AST, false) && canLaunchViaAssist;
        intent.removeExtra(EXTRA_PKG);
        intent.removeExtra(EXTRA_CLS);
        intent.removeExtra(EXTRA_AST);
        intent.removeExtra(EXTRA_USR);
        if (launchViaAssist && !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.START_ANY_ACTIVITY)) {
            launchViaAssist(intent.getComponent());
        } else {
            try {
                ActivityManagerCompat.startActivity(intent, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
                UIUtils.displayLongToast("Error: " + e.getMessage());
                // Try assist instead
                if (canLaunchViaAssist) {
                    launchViaAssist(intent.getComponent());
                }
            }
            finish();
        }
    }

    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    private void launchViaAssist(@NonNull ComponentName cn) {
        ActivityManagerCompat.startActivityViaAssist(ContextUtils.getContext(), cn, () -> {
            CountDownLatch waitForInteraction = new CountDownLatch(1);
            ThreadUtils.postOnMainThread(() -> new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.launch_activity_dialog_title)
                    .setMessage(R.string.launch_activity_dialog_message)
                    .setCancelable(false)
                    .setOnDismissListener((dialog) -> {
                        waitForInteraction.countDown();
                        finish();
                    })
                    .setNegativeButton(R.string.close, null)
                    .show());
            try {
                waitForInteraction.await(10, TimeUnit.MINUTES);
            } catch (InterruptedException ignore) {
            }
        });
    }
}
