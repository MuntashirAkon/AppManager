// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.dialog.BottomSheetAlertDialogFragment;

public class LibraryInfoDialog extends BottomSheetAlertDialogFragment {
    public static final String TAG = LibraryInfoDialog.class.getSimpleName();

    @NonNull
    public static LibraryInfoDialog getInstance(@NonNull CharSequence subtitle, @NonNull CharSequence message) {
        LibraryInfoDialog dialog = new LibraryInfoDialog();
        dialog.setArguments(getArgs(null, subtitle, message));
        return dialog;
    }

    @Override
    public void onBodyInitialized(@NonNull View bodyView, @Nullable Bundle savedInstanceState) {
        super.onBodyInitialized(bodyView, savedInstanceState);
        setTitle(R.string.lib_details);
        setMessageIsSelectable(true);
    }
}
