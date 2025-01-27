// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.button.MaterialButton;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.activity.LeadingActivityTrackerActivity;
import io.github.muntashirakon.AppManager.editor.CodeEditorActivity;
import io.github.muntashirakon.AppManager.fm.FmActivity;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryActivity;
import io.github.muntashirakon.AppManager.intercept.ActivityInterceptor;
import io.github.muntashirakon.AppManager.logcat.LogViewerActivity;
import io.github.muntashirakon.AppManager.runner.TermActivity;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.sysconfig.SysConfigActivity;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.widget.FlowLayout;

public class LabsActivity extends BaseActivity {
    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_labs);
        setSupportActionBar(findViewById(R.id.toolbar));
        FlowLayout flowLayout = findViewById(R.id.action_container);
        if (FeatureController.isLogViewerEnabled()) {
            addAction(this, flowLayout, R.string.log_viewer, R.drawable.ic_view_list)
                    .setOnClickListener(v -> {
                        Intent intent = new Intent(this, LogViewerActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    });
        }
        addAction(this, flowLayout, R.string.sys_config, R.drawable.ic_hammer_wrench)
                .setOnClickListener(v -> {
                    Intent intent = new Intent(this, SysConfigActivity.class);
                    startActivity(intent);
                });
        addAction(this, flowLayout, R.string.title_terminal_emulator, R.drawable.ic_frost_termux)
                .setOnClickListener(v -> {
                    Intent intent = new Intent(this, TermActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                });
        addAction(this, flowLayout, R.string.files, R.drawable.ic_file_document_multiple)
                .setOnClickListener(v -> {
                    Intent intent = new Intent(this, FmActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                });
        addAction(this, flowLayout, R.string.title_ui_tracker, R.drawable.ic_cursor_default_click)
                .setOnClickListener(v -> {
                    Intent intent = new Intent(this, LeadingActivityTrackerActivity.class);
                    startActivity(intent);
                });
        if (FeatureController.isInterceptorEnabled()) {
            addAction(this, flowLayout, R.string.interceptor, R.drawable.ic_transit_connection)
                    .setOnClickListener(v -> {
                        Intent intent = new Intent(this, ActivityInterceptor.class);
                        startActivity(intent);
                    });
        }
        if (FeatureController.isCodeEditorEnabled()) {
            addAction(this, flowLayout, R.string.title_code_editor, R.drawable.ic_code)
                    .setOnClickListener(v -> {
                        Intent intent = new Intent(this, CodeEditorActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    });
        }
        addAction(this, flowLayout, R.string.op_history, R.drawable.ic_history)
                .setOnClickListener(v -> {
                    Intent intent = new Intent(this, OpHistoryActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @NonNull
    private static MaterialButton addAction(@NonNull Context context, @NonNull ViewGroup layout,
                                            @StringRes int stringResId, @DrawableRes int iconResId) {
        MaterialButton button = (MaterialButton) View.inflate(context, R.layout.item_app_info_action, null);
        button.setBackgroundTintList(ColorStateList.valueOf(ColorCodes.getListItemColor1(context)));
        button.setText(stringResId);
        button.setIconResource(iconResId);
        layout.addView(button);
        return button;
    }
}
