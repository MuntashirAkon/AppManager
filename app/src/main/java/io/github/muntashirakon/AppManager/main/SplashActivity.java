// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.splashscreen.SplashScreen;
import androidx.lifecycle.ViewModelProvider;

import java.util.Locale;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreActivity;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.AMExceptionHandler;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.SecurityAndOpsViewModel;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class SplashActivity extends AppCompatActivity {
    public static final String TAG = SplashActivity.class.getSimpleName();

    @Nullable
    private TextView mStateNameView;
    private SecurityAndOpsViewModel mViewModel;

    private final ActivityResultLauncher<Intent> mKeyStoreActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                // Need authentication and/or verify mode of operation
                ensureSecurityAndModeOfOp();
            });
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

    @Override
    protected final void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SplashScreen.installSplashScreen(this);
        Thread.setDefaultUncaughtExceptionHandler(new AMExceptionHandler(this));
        AppCompatDelegate.setDefaultNightMode(AppPref.getInt(AppPref.PrefKey.PREF_APP_THEME_INT));
        getWindow().getDecorView().setLayoutDirection(AppPref.getInt(AppPref.PrefKey.PREF_LAYOUT_ORIENTATION_INT));
        setContentView(R.layout.activity_authentication);
        ((TextView) findViewById(R.id.version)).setText(String.format(Locale.ROOT, "%s (%d)",
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        mStateNameView = findViewById(R.id.state_name);
        if (Ops.isAuthenticated()) {
            Log.d(TAG, "Already authenticated.");
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        // Run authentication
        mViewModel = new ViewModelProvider(this).get(SecurityAndOpsViewModel.class);
        Log.d(TAG, "Waiting to be authenticated.");
        mViewModel.authenticationStatus().observe(this, status -> {
            switch (status) {
                case Ops.STATUS_SUCCESS:
                case Ops.STATUS_FAILED:
                    Log.d(TAG, "Authentication completed.");
                    mViewModel.setAuthenticating(false);
                    Ops.setAuthenticated(true);
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                    return;
                case Ops.STATUS_DISPLAY_WIRELESS_DEBUGGING:
                    Log.d(TAG, "Request wireless debugging.");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        mViewModel.autoConnectAdb(Ops.STATUS_DISPLAY_PAIRING);
                        return;
                    } // fall-through
                case Ops.STATUS_DISPLAY_PAIRING:
                    Log.d(TAG, "Display pairing dialog.");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Ops.connectWirelessDebugging(this, mViewModel);
                        return;
                    } // fall-through
                case Ops.STATUS_DISPLAY_CONNECT:
                    Log.d(TAG, "Display connect dialog.");
                    Ops.connectAdbInput(this, mViewModel);
            }
        });
        if (!mViewModel.isAuthenticating()) {
            mViewModel.setAuthenticating(true);
            authenticate();
        }
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

    private void authenticate() {
        // Check KeyStore
        if (KeyStoreManager.hasKeyStorePassword()) {
            // We already have a working keystore password.
            // Only need authentication and/or verify mode of operation.
            ensureSecurityAndModeOfOp();
            return;
        }
        Intent keyStoreIntent = new Intent(this, KeyStoreActivity.class)
                .putExtra(KeyStoreActivity.EXTRA_KS, true);
        mKeyStoreActivity.launch(keyStoreIntent);
    }

    private void ensureSecurityAndModeOfOp() {
        if (!AppPref.getBoolean(AppPref.PrefKey.PREF_ENABLE_SCREEN_LOCK_BOOL)) {
            // No security enabled
            handleSecurityAndModeOfOp();
            return;
        }
        Log.d(TAG, "Security enabled.");
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
        Log.d(TAG, "Authenticated");
        if (mStateNameView != null) {
            mStateNameView.setText(R.string.initializing);
        }
        // Set mode of operation
        mViewModel.setModeOfOps();
    }
}
