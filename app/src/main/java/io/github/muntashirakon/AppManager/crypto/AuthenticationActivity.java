// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto;

import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.AMExceptionHandler;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class AuthenticationActivity extends AppCompatActivity {
    private AlertDialog alertDialog;
    private final ActivityResultLauncher<Intent> authActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    handleModeOfOps();
                } else {
                    setResult(RESULT_CANCELED);
                    finishAndRemoveTask();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new AMExceptionHandler(this));
        AppCompatDelegate.setDefaultNightMode(AppPref.getInt(AppPref.PrefKey.PREF_APP_THEME_INT));
        getWindow().getDecorView().setLayoutDirection(AppPref.getInt(AppPref.PrefKey.PREF_LAYOUT_ORIENTATION_INT));
        alertDialog = UIUtils.getProgressDialog(this, getString(R.string.initializing));
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if ((boolean) AppPref.get(AppPref.PrefKey.PREF_ENABLE_SCREEN_LOCK_BOOL)) {
            if (keyguardManager.isKeyguardSecure()) {
                Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(getString(R.string.unlock_app_manager), null);
                authActivity.launch(intent);
            } else {
                UIUtils.displayLongToast(R.string.screen_lock_not_enabled);
                setResult(RESULT_CANCELED);
                finishAndRemoveTask();
            }
        } else {
            // No security enabled
            handleModeOfOps();
        }
    }

    private void handleModeOfOps() {
        alertDialog.show();
        // Set mode of operation
        new Thread(() -> {
            try {
                RunnerUtils.setModeOfOps(this);
                AppManager.setIsAuthenticated(true);
                setResult(RESULT_OK);
            } finally {
                runOnUiThread(this::finish);
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        if (alertDialog != null) alertDialog.dismiss();
        super.onDestroy();
    }
}
