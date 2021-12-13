// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.menu.MenuBuilder;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.misc.AMExceptionHandler;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public class SplashActivity extends AppCompatActivity {
    private static final String IS_AUTHENTICATING = "is_authenticating";

    private final ActivityResultLauncher<Intent> mAuthActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Success
                    handleSecurityAndModeOfOp();
                } else {
                    // Authentication failed
                    finishAndRemoveTask();
                }
            });
    private final CountDownLatch mWaitForKS = new CountDownLatch(1);
    @Nullable
    private TextView mStateNameView;
    private boolean mIsAuthenticating = false;

    @Override
    protected final void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mIsAuthenticating = savedInstanceState.getBoolean(IS_AUTHENTICATING, false);
        }
        Thread.setDefaultUncaughtExceptionHandler(new AMExceptionHandler(this));
        AppCompatDelegate.setDefaultNightMode(AppPref.getInt(AppPref.PrefKey.PREF_APP_THEME_INT));
        getWindow().getDecorView().setLayoutDirection(AppPref.getInt(AppPref.PrefKey.PREF_LAYOUT_ORIENTATION_INT));
        setContentView(R.layout.activity_authentication);
        ((TextView) findViewById(R.id.version)).setText(String.format(Locale.ROOT, "%s (%d)",
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        mStateNameView = findViewById(R.id.state_name);
        if (AppManager.isAuthenticated()) {
            // Already authenticated
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        // We would still have to ensure if KeyStore has been initialised
        ensureKeyStore();
        if (mIsAuthenticating) {
            // Authentication is currently running, do nothing
            return;
        }
        // Need authentication and/or verify mode of operation
        mIsAuthenticating = true;
        ensureSecurityAndModeOfOp();
    }

    @CallSuper
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (mIsAuthenticating) {
            outState.putBoolean(IS_AUTHENTICATING, true);
        }
        super.onSaveInstanceState(outState);
    }

    @CallSuper
    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }


    private void ensureKeyStore() {
        AlertDialog ksDialog;
        // Handle KeyStore
        if (KeyStoreManager.hasKeyStorePassword()) {
            if (Utils.isAppInstalled() || Utils.isAppUpdated()) {
                // We already have a working keystore password
                try {
                    char[] password = KeyStoreManager.getInstance().getAmKeyStorePassword();
                    ksDialog = KeyStoreManager.displayKeyStorePassword(this, password, mWaitForKS::countDown);
                } catch (Exception e) {
                    e.printStackTrace();
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
    }

    private void ensureSecurityAndModeOfOp() {
        if (!AppPref.getBoolean(AppPref.PrefKey.PREF_ENABLE_SCREEN_LOCK_BOOL)) {
            // No security enabled
            handleSecurityAndModeOfOp();
            return;
        }
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (keyguardManager.isKeyguardSecure()) {
            // Screen lock enabled
            Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(getString(R.string.unlock_app_manager), null);
            mAuthActivity.launch(intent);
        } else {
            // Screen lock disabled
            UIUtils.displayLongToast(R.string.screen_lock_not_enabled);
            finishAndRemoveTask();
        }
    }

    private void handleSecurityAndModeOfOp() {
        // Authentication was successful
        AppManager.setIsAuthenticated(true);
        // Set mode of operation
        new Thread(() -> {
            try {
                try {
                    mWaitForKS.await();
                } catch (InterruptedException ignore) {
                }
                runOnUiThread(() -> {
                    if (mStateNameView != null) {
                        mStateNameView.setText(R.string.initializing);
                    }
                });
                if (Utils.isAppInstalled() || Utils.isAppUpdated()) {
                    // This works because this could be the first activity the user can see
                    try {
                        KeyStoreManager.migrateKeyStore();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                RunnerUtils.setModeOfOps(this, false);
            } finally {
                // We're authenticated and mode of operation is chosen
                runOnUiThread(() -> {
                    mIsAuthenticating = false;
                    // No saved instance state is passed here
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                });
            }
        }).start();
    }
}
