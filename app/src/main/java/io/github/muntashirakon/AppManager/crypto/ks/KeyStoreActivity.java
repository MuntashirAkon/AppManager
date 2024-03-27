// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.life.BuildExpiryChecker;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;

public class KeyStoreActivity extends AppCompatActivity {
    public static final String EXTRA_ALIAS = "key";
    public static final String EXTRA_KS = "ks";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(Prefs.Appearance.getTransparentAppTheme());
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        if (Boolean.TRUE.equals(BuildExpiryChecker.buildExpired())) {
            // Build has expired
            BuildExpiryChecker.getBuildExpiredDialog(this).show();
            return;
        }
        if (getIntent() != null) {
            onNewIntent(getIntent());
        } else finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String alias = intent.getStringExtra(EXTRA_ALIAS);
        if (alias != null) {
            displayInputKeyStoreAliasPassword(alias);
            return;
        }
        if (intent.hasExtra(EXTRA_KS)) {
            AlertDialog ksDialog;
            if (KeyStoreManager.hasKeyStore()) {
                // We have a keystore but not a working password, input a password (probably due to system restore)
                ksDialog = KeyStoreManager.inputKeyStorePassword(this, this::finish);
            } else {
                // We neither have a KeyStore nor a password. Create a password (not necessarily a keystore)
                ksDialog = KeyStoreManager.generateAndDisplayKeyStorePassword(this, this::finish);
            }
            ksDialog.show();
            return;
        }
        finish();
    }

    /**
     * @deprecated Kept for migratory purposes only, deprecated since v2.6.3. To be removed in v3.0.0.
     */
    @Deprecated
    private void displayInputKeyStoreAliasPassword(@NonNull String alias) {
        new TextInputDialogBuilder(this, getString(R.string.input_keystore_alias_pass, alias))
                .setTitle(getString(R.string.input_keystore_alias_pass, alias))
                .setHelperText(getString(R.string.input_keystore_alias_pass_description, alias))
                .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) ->
                        savePass(KeyStoreManager.getPrefAlias(alias), inputText)
                )
                .setCancelable(false)
                .setOnDismissListener(dialog -> finish())
                .show();
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
        KeyStoreManager.savePass(this, prefKey, password);
        Utils.clearChars(password);
        sendBroadcast(new Intent(KeyStoreManager.ACTION_KS_INTERACTION_END));
    }
}
