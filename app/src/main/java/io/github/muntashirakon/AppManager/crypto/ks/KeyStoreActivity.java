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
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;

/**
 * @deprecated Kept for migratory purposes only, deprecated since v2.6.3. To be removed in v3.0.0.
 */
@Deprecated
public class KeyStoreActivity extends AppCompatActivity {
    public static final String EXTRA_ALIAS = "key";

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
        String alias = intent.getStringExtra(EXTRA_ALIAS);
        if (alias == null) {
            finish();
            return;
        }
        AlertDialog ksDialog = new TextInputDialogBuilder(this, getString(R.string.input_keystore_alias_pass, alias))
                .setTitle(getString(R.string.input_keystore_alias_pass, alias))
                .setHelperText(getString(R.string.input_keystore_alias_pass_description, alias))
                .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) ->
                        savePass(KeyStoreManager.getPrefAlias(alias), inputText)
                ).create();
        ksDialog.setCancelable(false);
        ksDialog.setOnDismissListener(dialog -> finish());
        ksDialog.show();
    }

    private void savePass(@NonNull String prefKey, @Nullable Editable rawPassword) {
        char[] password;
        if (TextUtils.isEmpty(rawPassword)) {
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
