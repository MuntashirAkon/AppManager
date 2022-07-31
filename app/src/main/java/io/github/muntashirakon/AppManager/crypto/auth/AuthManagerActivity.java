// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.auth;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;

public class AuthManagerActivity extends BaseActivity {
    private TextInputLayout authKeyLayout;
    private TextInputEditText authKeyField;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_auth_management);
        setSupportActionBar(findViewById(R.id.toolbar));
        findViewById(R.id.progress_linear).setVisibility(View.GONE);
        authKeyLayout = findViewById(R.id.auth_field);
        authKeyField = findViewById(android.R.id.text1);
        authKeyField.setText(AuthManager.getKey());
        authKeyLayout.setEndIconOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.regenerate_auth_key)
                .setMessage(R.string.regenerate_auth_key_warning)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    String authKey = AuthManager.generateKey();
                    AuthManager.setKey(authKey);
                    authKeyField.setText(authKey);
                })
                .show());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
