// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.android.internal.util.TextUtils;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.AMExceptionHandler;
import io.github.muntashirakon.AppManager.types.TextInputDialogBuilder;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.Utils;

public class KeyStoreActivity extends AppCompatActivity {
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_ALIAS = "key";

    public static final int TYPE_KS = 1;
    public static final int TYPE_ALIAS = 2;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new AMExceptionHandler(this));
        AppCompatDelegate.setDefaultNightMode(AppPref.getInt(AppPref.PrefKey.PREF_APP_THEME_INT));
        getWindow().getDecorView().setLayoutDirection(AppPref.getInt(AppPref.PrefKey.PREF_LAYOUT_ORIENTATION_INT));
        if (getIntent() != null) onNewIntent(getIntent());
        else finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        int type = intent.getIntExtra(EXTRA_TYPE, TYPE_KS);
        String alias = intent.getStringExtra(EXTRA_ALIAS);
        if (alias == null) {
            finish();
            return;
        }
        AlertDialog ksDialog;
        if (type == TYPE_KS) {
            ksDialog = new TextInputDialogBuilder(this, R.string.input_keystore_pass)
                    .setTitle(R.string.input_keystore_pass)
                    .setHelperText(R.string.input_keystore_pass_description)
                    .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                        if (!TextUtils.isEmpty(inputText)) {
                            // Keystore password can't be null
                            savePass(alias, inputText, false);
                        }
                    })
                    .create();
        } else if (type == TYPE_ALIAS) {
            ksDialog = new TextInputDialogBuilder(this, getString(R.string.input_keystore_alias_pass, alias))
                    .setTitle(getString(R.string.input_keystore_alias_pass, alias))
                    .setHelperText(getString(R.string.input_keystore_alias_pass_description, alias))
                    .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) ->
                            savePass(KeyStoreManager.getPrefAlias(alias), inputText, true)
                    ).create();
        } else {
            finish();
            return;
        }
        ksDialog.setCancelable(false);
        ksDialog.setOnDismissListener(dialog -> finish());
        ksDialog.show();
    }

    private void savePass(@NonNull String prefKey, @Nullable Editable rawPassword, boolean isAlias) {
        char[] password;
        if (TextUtils.isEmpty(rawPassword)) {
            // Only applicable for alias
            if (!isAlias) {
                Log.e(KeyStoreManager.TAG, "Could not set KeyStore password because its empty");
                sendBroadcast(new Intent(KeyStoreManager.ACTION_KS_INTERACTION_END));
                return;
            }
            try {
                password = KeyStoreManager.getInstance().getAmKeyStorePassword();
            } catch (Exception e) {
                Log.e(KeyStoreManager.TAG, "Could not get KeyStore password", e);
                sendBroadcast(new Intent(KeyStoreManager.ACTION_KS_INTERACTION_END));
                return;
            }
        } else {
            password = new char[rawPassword.length()];
            rawPassword.getChars(0, rawPassword.length(), password, 0);
        }
        KeyStoreManager.savePass(prefKey, password);
        Utils.clearChars(password);
        sendBroadcast(new Intent(KeyStoreManager.ACTION_KS_INTERACTION_END));
    }
}
