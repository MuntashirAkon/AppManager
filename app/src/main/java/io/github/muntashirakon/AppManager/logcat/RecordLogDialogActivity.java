// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.os.Bundle;

import io.github.muntashirakon.AppManager.BaseActivity;

// Copyright 2012 Nolan Lawson
public class RecordLogDialogActivity extends BaseActivity {
    public static final String EXTRA_QUERY_SUGGESTIONS = "suggestions";

    @Override
    protected boolean displaySplashScreen() {
        return false;
    }

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        final String[] suggestions = (getIntent() != null && getIntent().hasExtra(EXTRA_QUERY_SUGGESTIONS))
                ? getIntent().getStringArrayExtra(EXTRA_QUERY_SUGGESTIONS) : new String[]{};

        RecordLogDialogFragment dialogFragment = new RecordLogDialogFragment();
        Bundle args = new Bundle();
        args.putStringArray(RecordLogDialogFragment.QUERY_SUGGESTIONS, suggestions);
        dialogFragment.setArguments(args);
        dialogFragment.show(getSupportFragmentManager(), RecordLogDialogFragment.TAG);
    }

}
