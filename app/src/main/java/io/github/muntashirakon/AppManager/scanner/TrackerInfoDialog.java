// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.lifecycle.ViewModelProvider;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.dialog.BottomSheetAlertDialogFragment;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.MaterialAlertView;

public class TrackerInfoDialog extends BottomSheetAlertDialogFragment {
    public static final String TAG = TrackerInfoDialog.class.getSimpleName();

    private static final String ARG_HAS_SECOND_DEGREE = "sec_deg";

    @NonNull
    public static TrackerInfoDialog getInstance(@NonNull CharSequence subtitle, @NonNull CharSequence message,
                                                boolean hasSecondDegree) {
        TrackerInfoDialog dialog = new TrackerInfoDialog();
        Bundle args = getArgs(null, subtitle, message);
        args.putBoolean(ARG_HAS_SECOND_DEGREE, hasSecondDegree);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onBodyInitialized(@NonNull View bodyView, @Nullable Bundle savedInstanceState) {
        super.onBodyInitialized(bodyView, savedInstanceState);
        ScannerViewModel viewModel = new ViewModelProvider(requireActivity()).get(ScannerViewModel.class);
        String packageName = viewModel.getPackageName();
        boolean hasSecondDegree = requireArguments().getBoolean(ARG_HAS_SECOND_DEGREE, false);

        setTitle(R.string.tracker_details);
        if (packageName != null) {
            setEndIcon(R.drawable.ic_exodusprivacy, R.string.exodus_link, v -> {
                Uri exodus_link = Uri.parse(String.format(
                        "https://reports.exodus-privacy.eu.org/en/reports/%s/latest/", packageName));
                Intent intent = new Intent(Intent.ACTION_VIEW, exodus_link);
                if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                    startActivity(intent);
                }
            });
        }
        setMessageIsSelectable(true);
        setMessageMovementMethod(LinkMovementMethod.getInstance());
        if (hasSecondDegree) {
            MaterialAlertView alertView = new MaterialAlertView(bodyView.getContext());
            alertView.setAlertType(MaterialAlertView.ALERT_TYPE_INFO);
            alertView.setText(R.string.second_degree_tracker_note);
            alertView.setMovementMethod(LinkMovementMethod.getInstance());
            LinearLayoutCompat.LayoutParams layoutParams = new LinearLayoutCompat.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.bottomMargin = layoutParams.topMargin = UiUtils.dpToPx(bodyView.getContext(), 8);
            layoutParams.leftMargin = layoutParams.rightMargin = UiUtils.dpToPx(bodyView.getContext(), 16);
            prependView(alertView, layoutParams);
        }
    }
}
