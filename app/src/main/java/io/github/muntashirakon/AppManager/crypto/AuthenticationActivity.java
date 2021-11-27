// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto;

import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.AMExceptionHandler;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public class AuthenticationActivity extends AppCompatActivity {
    public static final String TAG = AuthenticationActivity.class.getSimpleName();

    public static final String EXTRA_DISPLAY_SPLASH = "display_splash";

    private final ActivityResultLauncher<Intent> mAuthActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    AppManager.setIsAuthenticated(true);
                    handleModeOfOps();
                } else {
                    setResult(RESULT_CANCELED);
                    finishAndRemoveTask();
                }
            });
    private final CountDownLatch mWaitForKS = new CountDownLatch(1);
    @Nullable
    private TextView mStateNameView;
    @Nullable
    private AlertDialog mAlertDialog;
    private static boolean authenticatorLaunched = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new AMExceptionHandler(this));
        AppCompatDelegate.setDefaultNightMode(AppPref.getInt(AppPref.PrefKey.PREF_APP_THEME_INT));
        getWindow().getDecorView().setLayoutDirection(AppPref.getInt(AppPref.PrefKey.PREF_LAYOUT_ORIENTATION_INT));
        boolean displaySplash = getIntent().getBooleanExtra(EXTRA_DISPLAY_SPLASH, false);
        if (displaySplash) {
            setContentView(R.layout.activity_authentication);
            ((TextView) findViewById(R.id.version)).setText(String.format(Locale.ROOT,
                    "%s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
            mStateNameView = findViewById(R.id.state_name);
        } else {
            setTheme(R.style.AppTheme_TransparentBackground);
            mAlertDialog = UIUtils.getProgressDialog(this, getString(R.string.initializing));
        }
        AlertDialog ksDialog;
        // Handle KeyStore
        if (KeyStoreManager.hasKeyStorePassword()) {
            if (Utils.isAppInstalled() || Utils.isAppUpdated()) {
                // We already have a working keystore password
                try {
                    char[] password = KeyStoreManager.getInstance().getAmKeyStorePassword();
                    ksDialog = KeyStoreManager.displayKeyStorePassword(this, password, mWaitForKS::countDown);
                } catch (Exception e) {
                    Log.e(TAG, e);
                    ksDialog = null;
                }
            } else ksDialog = null;
        } else if (KeyStoreManager.hasKeyStore()) {
            // We have a keystore but not a working password, input a password (probably due to system restore)
            ksDialog = KeyStoreManager.inputKeyStorePassword(this, mWaitForKS::countDown);
        } else {
            // We neither have a KeyStore nor a password. Create a password (not necessarily a keystore)
            ksDialog = KeyStoreManager.generateAndDisplayKeyStorePassword(this, mWaitForKS::countDown);
        }
        if (ksDialog != null) {
            ksDialog.show();
        } else mWaitForKS.countDown();
        // Security
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (!AppManager.isAuthenticated()
                && !authenticatorLaunched
                && (boolean) AppPref.get(AppPref.PrefKey.PREF_ENABLE_SCREEN_LOCK_BOOL)) {
            if (keyguardManager.isKeyguardSecure()) {
                Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(getString(R.string.unlock_app_manager), null);
                authenticatorLaunched = true;
                mAuthActivity.launch(intent);
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
        // Set mode of operation
        new Thread(() -> {
            try {
                try {
                    mWaitForKS.await();
                } catch (InterruptedException ignore) {
                }
                runOnUiThread(() -> {
                    if (mStateNameView != null) mStateNameView.setText(R.string.initializing);
                    if (mAlertDialog != null) mAlertDialog.show();
                });
                if (Utils.isAppInstalled() || Utils.isAppUpdated()) {
                    // This works because this is the first activity the user can see
                    try {
                        KeyStoreManager.migrateKeyStore();
                    } catch (Exception e) {
                        Log.e(TAG, e);
                    }
                }
                RunnerUtils.setModeOfOps(this, false);
            } finally {
                setResult(RESULT_OK);
                runOnUiThread(this::finish);
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        if (mAlertDialog != null) mAlertDialog.dismiss();
        super.onDestroy();
    }
}
