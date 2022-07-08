// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.dialog.BottomSheetAlertDialogFragment;

public class VirusTotalDialog extends BottomSheetAlertDialogFragment {
    public static final String TAG = VirusTotalDialog.class.getSimpleName();

    private static final String ARG_PERMALINK = "permalink";

    @NonNull
    public static VirusTotalDialog getInstance(@NonNull CharSequence title, @NonNull CharSequence subtitle,
                                               @NonNull CharSequence message, @Nullable String permalink) {
        VirusTotalDialog dialog = new VirusTotalDialog();
        Bundle args = getArgs(title, subtitle, message);
        args.putString(ARG_PERMALINK, permalink);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onBodyInitialized(@NonNull View bodyView, @Nullable Bundle savedInstanceState) {
        super.onBodyInitialized(bodyView, savedInstanceState);
        String permalink = requireArguments().getString(ARG_PERMALINK);
        if (permalink != null) {
            setEndIcon(R.drawable.ic_vt, R.string.vt_permalink, v -> {
                Uri vtPermalink = Uri.parse(permalink);
                Intent linkIntent = new Intent(Intent.ACTION_VIEW, vtPermalink);
                if (linkIntent.resolveActivity(requireContext().getPackageManager()) != null) {
                    startActivity(linkIntent);
                }
            });
        }
        setMessageIsSelectable(true);
    }
}
