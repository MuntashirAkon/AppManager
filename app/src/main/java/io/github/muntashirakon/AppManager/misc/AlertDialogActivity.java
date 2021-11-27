// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class AlertDialogActivity extends BaseActivity {
    @Override
    protected boolean displaySplashScreen() {
        return false;
    }

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        if (getIntent() == null) {
            finish();
            return;
        }
        onNewIntent(getIntent());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.clear();
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Check for failed batch ops
        ArrayList<String> failedPackages = intent.getStringArrayListExtra(BatchOpsService.EXTRA_FAILED_PKG);
        ArrayList<Integer> userHandles = intent.getIntegerArrayListExtra(BatchOpsService.EXTRA_OP_USERS);
        String failureMessage = intent.getStringExtra(BatchOpsService.EXTRA_FAILURE_MESSAGE);
        int op = intent.getIntExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_NONE);
        Bundle args = intent.getBundleExtra(BatchOpsService.EXTRA_OP_EXTRA_ARGS);
        ArrayList<String> packageLabels = PackageUtils.packagesToAppLabels(getPackageManager(), failedPackages, userHandles);
        // Failed
        if (failedPackages != null) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(failureMessage)
                    .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                            packageLabels), null)
                    .setNegativeButton(R.string.ok, null)
                    .setPositiveButton(R.string.try_again, (dialog, which) -> {
                        Intent BatchOpsIntent = new Intent(this, BatchOpsService.class);
                        BatchOpsIntent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, failedPackages);
                        BatchOpsIntent.putIntegerArrayListExtra(BatchOpsService.EXTRA_OP_USERS, userHandles);
                        BatchOpsIntent.putExtra(BatchOpsService.EXTRA_OP, op);
                        BatchOpsIntent.putExtra(BatchOpsService.EXTRA_OP_EXTRA_ARGS, args);
                        ContextCompat.startForegroundService(this, BatchOpsIntent);
                    })
                    .setOnDismissListener(dialog -> finish())
                    .show();
        }
        intent.removeExtra(BatchOpsService.EXTRA_FAILED_PKG);
        intent.removeExtra(BatchOpsService.EXTRA_OP_USERS);
        intent.removeExtra(BatchOpsService.EXTRA_FAILURE_MESSAGE);
    }
}
