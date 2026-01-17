// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricPrompt;
import androidx.biometric.BiometricPrompt.AuthenticationResult;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.github.muntashirakon.AppManager.compat.BiometricAuthenticatorsCompat;
import io.github.muntashirakon.AppManager.crypto.auth.AuthManager;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreActivity;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.self.filecache.InternalCacheCleanerService;
import io.github.muntashirakon.AppManager.self.life.BuildExpiryChecker;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.settings.SecurityAndOpsViewModel;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public abstract class BaseActivity extends PerProcessActivity {
    public static final String TAG = BaseActivity.class.getSimpleName();

    public static final HashMap<String, Boolean> ASKED_PERMISSIONS = new HashMap<String, Boolean>() {{
        // (permission, required) pairs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            put(Manifest.permission.POST_NOTIFICATIONS, false);
        }
    }};

    public static final String EXTRA_AUTH = "auth";

    @Nullable
    private AlertDialog mAlertDialog;
    @Nullable
    private SecurityAndOpsViewModel mViewModel;
    private boolean mDisplayLoader = true;
    private BiometricPrompt mBiometricPrompt;
    @Nullable
    private Bundle mSavedInstanceState;

    private final ActivityResultLauncher<Intent> mKeyStoreActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                // Need authentication and/or verify mode of operation
                ensureSecurityAndModeOfOp();
            });
    private final ActivityResultLauncher<String[]> mPermissionCheckActivity = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissionStatusMap -> {
                // Run authentication
                doAuthenticate(mSavedInstanceState);
                mSavedInstanceState = null;
            });

    @Override
    protected final void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Ops.isAuthenticated()) {
            Log.d(TAG, "Already authenticated.");
            onAuthenticated(savedInstanceState);
            initPermissionChecks(false);
            return;
        }
        if (Boolean.TRUE.equals(BuildExpiryChecker.buildExpired())) {
            // Build has expired
            BuildExpiryChecker.getBuildExpiredDialog(this, (dialog, which) -> doAuthenticate(savedInstanceState)).show();
            return;
        }
        // Init permission checks
        mSavedInstanceState = savedInstanceState;
        if (!initPermissionChecks(true)) {
            mSavedInstanceState = null;
            // Run authentication
            doAuthenticate(savedInstanceState);
        }
    }

    protected abstract void onAuthenticated(@Nullable Bundle savedInstanceState);

    @CallSuper
    @Override
    protected void onStart() {
        super.onStart();
        if (mViewModel != null && mViewModel.isAuthenticating() && mAlertDialog != null) {
            if (mDisplayLoader) {
                mAlertDialog.show();
            } else {
                mAlertDialog.hide();
            }
        }
    }

    @CallSuper
    @Override
    protected void onStop() {
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
        }
        super.onStop();
    }

    private void doAuthenticate(@Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(this).get(SecurityAndOpsViewModel.class);
        mBiometricPrompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        finishAndRemoveTask();
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        handleMigrationAndModeOfOp();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                    }
                });
        mAlertDialog = UIUtils.getProgressDialog(this, getString(R.string.initializing), true);
        Log.d(TAG, "Waiting to be authenticated.");
        mViewModel.authenticationStatus().observe(this, status -> {
            switch (status) {
                case Ops.STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING:
                    Log.d(TAG, "Try auto-connecting to wireless debugging.");
                    mDisplayLoader = false;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        mViewModel.autoConnectWirelessDebugging();
                        return;
                    } // fall-through
                case Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED:
                    Log.d(TAG, "Display wireless debugging chooser (pair or connect)");
                    mDisplayLoader = false;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Ops.connectWirelessDebugging(this, mViewModel);
                        return;
                    } // fall-through
                case Ops.STATUS_ADB_CONNECT_REQUIRED:
                    Log.d(TAG, "Display connect dialog.");
                    mDisplayLoader = false;
                    Ops.connectAdbInput(this, mViewModel);
                    return;
                case Ops.STATUS_ADB_PAIRING_REQUIRED:
                    Log.d(TAG, "Display pairing dialog.");
                    mDisplayLoader = false;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Ops.pairAdbInput(this, mViewModel);
                        return;
                    } // fall-through
                case Ops.STATUS_FAILURE_ADB_NEED_MORE_PERMS:
                    Ops.displayIncompleteUsbDebuggingMessage(this);
                case Ops.STATUS_SUCCESS:
                case Ops.STATUS_FAILURE:
                    Log.d(TAG, "Authentication completed.");
                    mViewModel.setAuthenticating(false);
                    if (mAlertDialog != null) mAlertDialog.dismiss();
                    Ops.setAuthenticated(this, true);
                    onAuthenticated(savedInstanceState);
                    InternalCacheCleanerService.scheduleAlarm(getApplicationContext());
            }
        });
        if (!mViewModel.isAuthenticating()) {
            mViewModel.setAuthenticating(true);
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
    }

    private void ensureSecurityAndModeOfOp() {
        if (!Prefs.Privacy.isScreenLockEnabled()) {
            // No security enabled
            handleMigrationAndModeOfOp();
            return;
        }
        if (getIntent().hasExtra(EXTRA_AUTH)) {
            Log.i(TAG, "Screen lock-bypass enabled.");
            // Check for auth
            String auth = getIntent().getStringExtra(EXTRA_AUTH);
            if (AuthManager.getKey().equals(auth)) {
                // Auth successful
                handleMigrationAndModeOfOp();
                return;
            } // else // Invalid authorization key, fallback to security
        }
        Log.i(TAG, "Screen lock enabled.");
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (keyguardManager.isKeyguardSecure()) {
            // Screen lock enabled
            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.unlock_app_manager))
                    .setAllowedAuthenticators(new BiometricAuthenticatorsCompat.Builder().allowEverything(true).build())
                    .build();
            mBiometricPrompt.authenticate(promptInfo);
        } else {
            // Screen lock disabled
            UIUtils.displayLongToast(R.string.screen_lock_not_enabled);
            finishAndRemoveTask();
        }
    }

    private void handleMigrationAndModeOfOp() {
        // Authentication was successful
        Log.d(TAG, "Authenticated");
        // Set mode of operation
        if (mViewModel != null) {
            mViewModel.setModeOfOps();
        }
    }

    private boolean initPermissionChecks(boolean checkAll) {
        List<String> permissionsToBeAsked = new ArrayList<>(ASKED_PERMISSIONS.size());
        for (String permission : ASKED_PERMISSIONS.keySet()) {
            boolean required = Boolean.TRUE.equals(ASKED_PERMISSIONS.get(permission));
            if (!SelfPermissions.checkSelfPermission(permission) && (required || checkAll)) {
                permissionsToBeAsked.add(permission);
            }
        }
        if (!permissionsToBeAsked.isEmpty()) {
            // Ask required permissions
            mPermissionCheckActivity.launch(permissionsToBeAsked.toArray(new String[0]));
            return true;
        }
        return false;
    }
}
