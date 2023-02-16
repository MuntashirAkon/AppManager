// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.os.Bundle;

import io.github.muntashirakon.AppManager.BaseActivity;

// Copyright 2012 Nolan Lawson
// Copyright 2021 Muntashir Al-Islam
public class RecordLogDialogActivity extends BaseActivity {
    public static final String EXTRA_QUERY_SUGGESTIONS = "suggestions";

    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        RecordLogDialogFragment dialog;
        dialog = RecordLogDialogFragment.getInstance(getIntent().getStringArrayExtra(EXTRA_QUERY_SUGGESTIONS), null);
        dialog.show(getSupportFragmentManager(), RecordLogDialogFragment.TAG);
        dialog.setOnDismissListener(v -> finish());
    }

}
