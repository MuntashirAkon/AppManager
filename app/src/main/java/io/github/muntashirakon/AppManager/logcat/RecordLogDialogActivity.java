/*
 * Copyright (c) 2021 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.logcat;

import android.os.Bundle;

import io.github.muntashirakon.AppManager.BaseActivity;

public class RecordLogDialogActivity extends BaseActivity {
    public static final String EXTRA_QUERY_SUGGESTIONS = "suggestions";

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
