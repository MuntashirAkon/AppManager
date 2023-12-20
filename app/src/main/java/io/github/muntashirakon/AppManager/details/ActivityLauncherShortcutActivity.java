// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class ActivityLauncherShortcutActivity extends BaseActivity {
    public static final String EXTRA_PKG = BuildConfig.APPLICATION_ID + ".intent.EXTRA.shortcut.pkg";
    public static final String EXTRA_CLS = BuildConfig.APPLICATION_ID + ".intent.EXTRA.shortcut.cls";
    public static final String EXTRA_AST = BuildConfig.APPLICATION_ID + ".intent.EXTRA.shortcut.ast";
    public static final String EXTRA_USR = BuildConfig.APPLICATION_ID + ".intent.EXTRA.shortcut.usr";

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        Intent intent = getIntent();
        if (!Intent.ACTION_CREATE_SHORTCUT.equals(intent.getAction()) || !intent.hasExtra(EXTRA_PKG) || !intent.hasExtra(EXTRA_CLS)) {
            // Invalid intent
            finishAndRemoveTask();
            return;
        }
        intent.setAction(null);
        intent.setClassName(intent.getStringExtra(EXTRA_PKG), intent.getStringExtra(EXTRA_CLS));
        int userId = intent.getIntExtra(EXTRA_USR, UserHandleHidden.myUserId());
        boolean launchViaAssist = intent.getBooleanExtra(EXTRA_AST, false);
        intent.removeExtra(EXTRA_PKG);
        intent.removeExtra(EXTRA_CLS);
        intent.removeExtra(EXTRA_AST);
        intent.removeExtra(EXTRA_USR);
        if (launchViaAssist && SelfPermissions.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)) {
            ActivityManagerCompat.startActivityViaAssist(ContextUtils.getContext(), intent.getComponent(), () -> {
                CountDownLatch waitForInteraction = new CountDownLatch(1);
                ThreadUtils.postOnMainThread(() -> new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.launch_activity_dialog_title)
                        .setMessage(R.string.launch_activity_dialog_message)
                        .setCancelable(false)
                        .setOnDismissListener((dialog) -> {
                            waitForInteraction.countDown();
                            finishAndRemoveTask();
                        })
                        .setNegativeButton(R.string.close, null)
                        .show());
                try {
                    waitForInteraction.await(10, TimeUnit.MINUTES);
                } catch (InterruptedException ignore) {
                }
            });
        } else {
            try {
                ActivityManagerCompat.startActivity(intent, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
                UIUtils.displayLongToast("Error: " + e.getMessage());
            }
            finishAndRemoveTask();
        }
    }

    @Override
    public boolean getTransparentBackground() {
        return true;
    }
}
