// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class ActivityLauncherShortcutActivity extends BaseActivity {
    public static final String EXTRA_PKG = BuildConfig.APPLICATION_ID + ".intent.EXTRA.shortcut.pkg";
    public static final String EXTRA_CLS = BuildConfig.APPLICATION_ID + ".intent.EXTRA.shortcut.cls";
    public static final String EXTRA_USR = BuildConfig.APPLICATION_ID + ".intent.EXTRA.shortcut.usr";

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        Intent intent = getIntent();
        if (!intent.hasExtra(EXTRA_PKG) || !intent.hasExtra(EXTRA_CLS)) {
            // Invalid intent
            finishAndRemoveTask();
            return;
        }
        intent.setClassName(intent.getStringExtra(EXTRA_PKG), intent.getStringExtra(EXTRA_CLS));
        int userId = intent.getIntExtra(EXTRA_USR, UserHandleHidden.myUserId());
        intent.removeExtra(EXTRA_PKG);
        intent.removeExtra(EXTRA_CLS);
        try {
            ActivityManagerCompat.startActivity(AppManager.getContext(), intent, userId);
        } catch (RemoteException e) {
            e.printStackTrace();
            UIUtils.displayLongToast("Error: " + e.getMessage());
        }
        finishAndRemoveTask();
    }
}
