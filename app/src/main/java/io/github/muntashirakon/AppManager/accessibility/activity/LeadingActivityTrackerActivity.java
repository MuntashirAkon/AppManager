// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.AccessibilityMultiplexer;
import io.github.muntashirakon.AppManager.accessibility.NoRootAccessibilityService;

public class LeadingActivityTrackerActivity extends BaseActivity {
    private final ActivityResultLauncher<Intent> mSettingsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                // Init again
                init();
            });

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        init();
    }

    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    private void init() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.grant_required_permission)
                    .setMessage(R.string.grant_overlay_permission_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        mSettingsLauncher.launch(intent);
                    })
                    .setNegativeButton(R.string.go_back, (dialog, which) -> finish())
                    .show();
            return;
        }
        if (!NoRootAccessibilityService.isAccessibilityEnabled(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.grant_required_permission)
                    .setMessage(R.string.grant_accessibility_permission_for_tracking_window_contents)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok, (dialog, which) ->
                            mSettingsLauncher.launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                    .setNegativeButton(R.string.go_back, (dialog, which) -> finish())
                    .show();
            return;
        }
        AccessibilityMultiplexer.getInstance().enableLeadingActivityTracker(true);
        finish();
    }
}
