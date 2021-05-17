// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;

import androidx.activity.result.ActivityResult;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.menu.MenuBuilder;

import io.github.muntashirakon.AppManager.crypto.AuthenticationActivity;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.AMExceptionHandler;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.BetterActivityResult;

public abstract class BaseActivity extends AppCompatActivity {
    private final BetterActivityResult<Intent, ActivityResult> authActivity = BetterActivityResult.registerActivityForResult(this);

    @CallSuper
    @Override
    protected final void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new AMExceptionHandler(this));
        AppCompatDelegate.setDefaultNightMode(AppPref.getInt(AppPref.PrefKey.PREF_APP_THEME_INT));
        getWindow().getDecorView().setLayoutDirection(AppPref.getInt(AppPref.PrefKey.PREF_LAYOUT_ORIENTATION_INT));
        if (!AppManager.isAuthenticated()) {
            try {
                authActivity.launch(new Intent(this, AuthenticationActivity.class), result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        onAuthenticated(savedInstanceState);
                    } else {
                        finishAndRemoveTask();
                    }
                });
            } catch (Throwable th) {
                Log.e("BaseActivity", th);
                finishAndRemoveTask();
            }
        } else onAuthenticated(savedInstanceState);
    }

    protected abstract void onAuthenticated(@Nullable Bundle savedInstanceState);

    @CallSuper
    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }
}
