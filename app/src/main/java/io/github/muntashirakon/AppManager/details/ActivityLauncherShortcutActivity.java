// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.Manifest;
import android.annotation.UserIdInt;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.behavior.FreezeUnfreeze;
import io.github.muntashirakon.AppManager.apk.behavior.FreezeUnfreezeService;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class ActivityLauncherShortcutActivity extends BaseActivity {
    private static final String EXTRA_PKG = BuildConfig.APPLICATION_ID + ".intent.EXTRA.shortcut.pkg";
    private static final String EXTRA_CLS = BuildConfig.APPLICATION_ID + ".intent.EXTRA.shortcut.cls";
    private static final String EXTRA_AST = BuildConfig.APPLICATION_ID + ".intent.EXTRA.shortcut.ast";
    private static final String EXTRA_USR = BuildConfig.APPLICATION_ID + ".intent.EXTRA.shortcut.usr";

    @NonNull
    public static Intent getShortcutIntent(@NonNull Context context, @NonNull String pkg, @NonNull String clazz,
                                           @UserIdInt int userId, boolean launchViaAssist) {
        return new Intent()
                .setClass(context, ActivityLauncherShortcutActivity.class)
                .putExtra(EXTRA_PKG, pkg)
                .putExtra(EXTRA_CLS, clazz)
                .putExtra(EXTRA_USR, userId)
                .putExtra(EXTRA_AST, launchViaAssist)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    private Intent mIntent;
    private String mPackageName;
    private ComponentName mComponentName;
    private int mUserId;
    private boolean mCanLaunchViaAssist;
    private boolean mIsLaunchViaAssist;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        Intent intent = getIntent();
        if (!Intent.ACTION_CREATE_SHORTCUT.equals(intent.getAction()) || !intent.hasExtra(EXTRA_PKG) || !intent.hasExtra(EXTRA_CLS)) {
            // Invalid intent
            finishActivity(0);
            return;
        }
        unfreezeAndLaunchActivity(intent);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        unfreezeAndLaunchActivity(intent);
    }

    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    private void unfreezeAndLaunchActivity(@NonNull Intent intent) {
        mIntent = new Intent(intent);
        mPackageName = Objects.requireNonNull(mIntent.getStringExtra(EXTRA_PKG));
        String className = Objects.requireNonNull(mIntent.getStringExtra(EXTRA_CLS));
        mComponentName = new ComponentName(mPackageName, className);
        mIntent.setAction(null);
        mIntent.setComponent(mComponentName);
        mUserId = mIntent.getIntExtra(EXTRA_USR, UserHandleHidden.myUserId());
        mCanLaunchViaAssist = SelfPermissions.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS);
        mIsLaunchViaAssist = mIntent.getBooleanExtra(EXTRA_AST, false) && mCanLaunchViaAssist;
        mIntent.removeExtra(EXTRA_PKG);
        mIntent.removeExtra(EXTRA_CLS);
        mIntent.removeExtra(EXTRA_AST);
        mIntent.removeExtra(EXTRA_USR);
        // Check for frozen
        ApplicationInfo info = ExUtils.exceptionAsNull(() -> PackageManagerCompat.getApplicationInfo(mPackageName, 0, mUserId));
        if (info != null && FreezeUtils.isFrozen(info)) {
            // Ask to unfreeze
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.title_shortcut_for_frozen_app)
                    .setMessage(R.string.message_shortcut_for_frozen_app)
                    .setCancelable(false)
                    .setPositiveButton(R.string.yes, (dialog, which) -> ThreadUtils.postOnBackgroundThread(() -> {
                        try {
                            FreezeUtils.unfreeze(mPackageName, mUserId);
                            ThreadUtils.postOnMainThread(() -> {
                                Intent service = new Intent(FreezeUnfreeze.getShortcutIntent(this, mPackageName, mUserId, 0))
                                        .setClassName(this, FreezeUnfreezeService.class.getName());
                                ContextCompat.startForegroundService(this, service);
                                launchActivity();
                            });
                        } catch (Throwable e) {
                            ThreadUtils.postOnMainThread(() -> {
                                UIUtils.displayShortToast(R.string.failed);
                                finishActivity(0);
                            });
                        }
                    }))
                    .setNegativeButton(R.string.cancel, (dialog, which) -> finishActivity(0))
                    .show();
        } else {
            // Try launching it anyway (we don't care about failure)
            launchActivity();
        }
    }

    private void launchActivity() {
        if (mIsLaunchViaAssist && !SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.START_ANY_ACTIVITY)) {
            launchActivityViaAssist();
        } else {
            try {
                finishActivity(0);
                ActivityManagerCompat.startActivity(mIntent, mUserId);
            } catch (Throwable e) {
                e.printStackTrace();
                UIUtils.displayLongToast("Error: " + e.getMessage());
                // Try assist instead
                if (mCanLaunchViaAssist) {
                    launchActivityViaAssist();
                } else finishActivity(0);
            }
        }
    }

    private void launchActivityViaAssist() {
        boolean launched = ActivityManagerCompat.startActivityViaAssist(ContextUtils.getContext(), mComponentName, () -> {
            CountDownLatch waitForInteraction = new CountDownLatch(1);
            ThreadUtils.postOnMainThread(() -> new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.launch_activity_dialog_title)
                    .setMessage(R.string.launch_activity_dialog_message)
                    .setCancelable(false)
                    .setOnDismissListener((dialog) -> {
                        waitForInteraction.countDown();
                        finishActivity(0);
                    })
                    .setNegativeButton(R.string.close, null)
                    .show());
            try {
                waitForInteraction.await(10, TimeUnit.MINUTES);
            } catch (InterruptedException ignore) {
            }
        });
        if (launched) {
            finishActivity(0);
        }
    }
}
