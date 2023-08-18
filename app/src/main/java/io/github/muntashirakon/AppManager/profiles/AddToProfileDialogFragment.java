// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

import android.app.Dialog;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;

public class AddToProfileDialogFragment extends DialogFragment {
    public static final String TAG = AddToProfileDialogFragment.class.getSimpleName();

    private static final String ARG_PKGS = "pkgs";

    public static AddToProfileDialogFragment getInstance(@NonNull String[] packages) {
        AddToProfileDialogFragment fragment = new AddToProfileDialogFragment();
        Bundle args = new Bundle();
        args.putStringArray(ARG_PKGS, packages);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        String[] packages = requireArguments().getStringArray(ARG_PKGS);
        List<ProfileMetaManager> profiles = ProfileManager.getProfileMetadata();
        List<CharSequence> profileNames = new ArrayList<>(profiles.size());
        for (ProfileMetaManager profileMetaManager : profiles) {
            profileNames.add(new SpannableStringBuilder(profileMetaManager.getProfileName()).append("\n")
                    .append(getSecondaryText(requireContext(), getSmallerText(profileMetaManager
                            .toLocalizedString(requireContext())))));
        }
        AtomicReference<AlertDialog> dialogRef = new AtomicReference<>();
        DialogTitleBuilder titleBuilder = new DialogTitleBuilder(requireContext())
                .setTitle(R.string.add_to_profile)
                .setEndIconContentDescription(R.string.new_profile)
                .setEndIcon(R.drawable.ic_add, v -> new TextInputDialogBuilder(requireContext(), R.string.input_profile_name)
                        .setTitle(R.string.new_profile)
                        .setHelperText(R.string.input_profile_name_description)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.go, (dialog, which, profName, isChecked) -> {
                            if (!TextUtils.isEmpty(profName)) {
                                //noinspection ConstantConditions
                                startActivity(AppsProfileActivity.getNewProfileIntent(requireContext(), profName.toString(),
                                        packages));
                                if (dialogRef.get() != null) {
                                    dialogRef.get().dismiss();
                                }
                            }
                        })
                        .show());
        AlertDialog alertDialog = new SearchableMultiChoiceDialogBuilder<>(requireContext(), profiles, profileNames)
                .setTitle(titleBuilder.build())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.add, (dialog, which, selectedItems) -> {
                    for (ProfileMetaManager metaManager : selectedItems) {
                        try {
                            metaManager.appendPackages(packages);
                            metaManager.writeProfile();
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                    UIUtils.displayShortToast(R.string.done);
                })
                .create();
        dialogRef.set(alertDialog);
        return alertDialog;
    }
}
