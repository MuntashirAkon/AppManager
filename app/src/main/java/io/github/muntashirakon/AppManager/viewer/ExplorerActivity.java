// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.fm.FmActivity;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;

public class ExplorerActivity extends BaseActivity {
    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        Uri uri = IntentCompat.getDataUri(getIntent());
        if (uri == null) {
            finish();
            return;
        }
        FmActivity.Options options = new FmActivity.Options(uri, true, true, true);
        Intent intent = new Intent(this, FmActivity.class);
        intent.putExtra(FmActivity.EXTRA_OPTIONS, options);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean getTransparentBackground() {
        return true;
    }
}
