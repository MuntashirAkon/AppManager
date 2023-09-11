// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.fm.dialogs.OpenWithDialogFragment;

public class OpenWithActivity extends BaseActivity {
    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        Uri uri = getIntent().getData();
        if (uri == null) {
            finish();
            return;
        }
        OpenWithDialogFragment fragment = OpenWithDialogFragment.getInstance(uri, getIntent().getType(), true);
        fragment.show(getSupportFragmentManager(), OpenWithDialogFragment.TAG);
    }
}
