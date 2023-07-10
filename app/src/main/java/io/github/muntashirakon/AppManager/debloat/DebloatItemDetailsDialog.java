// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.textview.MaterialTextView;

import java.util.Arrays;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.FlowLayout;
import io.github.muntashirakon.widget.MaterialAlertView;


public class DebloatItemDetailsDialog extends CapsuleBottomSheetDialogFragment {
    public static final String TAG = DebloatItemDetailsDialog.class.getSimpleName();

    public static final String ARG_PACKAGE_NAME = "pkg";

    @NonNull
    public static DebloatItemDetailsDialog getInstance(@NonNull String packageName) {
        DebloatItemDetailsDialog fragment = new DebloatItemDetailsDialog();
        Bundle args = new Bundle();
        args.putString(ARG_PACKAGE_NAME, packageName);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public View initRootView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_debloat_item_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String packageName = requireArguments().getString(ARG_PACKAGE_NAME);
        if (packageName == null) {
            dismiss();
            return;
        }
        DebloaterViewModel viewModel = new ViewModelProvider(requireActivity()).get(DebloaterViewModel.class);
        DebloatObject debloatObject = viewModel.findDebloatObject(packageName);
        if (debloatObject == null) {
            dismiss();
            return;
        }
        ImageView appIconView = view.findViewById(R.id.icon);
        MaterialButton openAppInfoButton = view.findViewById(R.id.info);
        TextView appLabelView = view.findViewById(R.id.name);
        TextView packageNameView = view.findViewById(R.id.package_name);
        FlowLayout flowLayout = view.findViewById(R.id.tag_cloud);
        MaterialAlertView warningView = view.findViewById(R.id.alert_text);
        MaterialTextView descriptionView = view.findViewById(R.id.apk_description);

        Drawable icon = debloatObject.getIcon();
        appIconView.setImageDrawable(icon != null ? icon : requireActivity().getPackageManager().getDefaultActivityIcon());
        int[] users = debloatObject.getUsers();
        if (users != null && users.length > 0) {
            openAppInfoButton.setOnClickListener(v -> {
                Intent appDetailsIntent = AppDetailsActivity.getIntent(requireContext(), debloatObject.packageName,
                        users[0]);
                startActivity(appDetailsIntent);
                dismiss();
            });
        } else {
            openAppInfoButton.setVisibility(View.GONE);
        }
        CharSequence label = debloatObject.getLabel();
        appLabelView.setText(label != null ? label : debloatObject.packageName);
        packageNameView.setText(debloatObject.packageName);
        String warning = debloatObject.getWarning();
        if (warning != null) {
            warningView.setText(warning);
            if (debloatObject.getRemoval() != DebloatObject.REMOVAL_CAUTION) {
                warningView.setAlertType(MaterialAlertView.ALERT_TYPE_INFO);
            }
        } else warningView.setVisibility(View.GONE);
        descriptionView.setText(getDescription(debloatObject.getDescription(), debloatObject.getWebRefs()));
        // Add tags
        int removalColor;
        @StringRes
        int removalRes;
        switch (debloatObject.getRemoval()) {
            case DebloatObject.REMOVAL_SAFE:
                removalColor = ColorCodes.getRemovalSafeIndicatorColor(requireContext());
                removalRes = R.string.debloat_removal_safe_short_description;
                break;
            default:
            case DebloatObject.REMOVAL_CAUTION:
                removalColor = ColorCodes.getRemovalCautionIndicatorColor(requireContext());
                removalRes = R.string.debloat_removal_caution_short_description;
                break;
            case DebloatObject.REMOVAL_REPLACE:
                removalColor = ColorCodes.getRemovalReplaceIndicatorColor(requireContext());
                removalRes = R.string.debloat_removal_replace_short_description;
                break;
        }
        addTag(flowLayout, debloatObject.type);
        addTag(flowLayout, removalRes, removalColor);
    }

    @NonNull
    private CharSequence getDescription(@NonNull String description, @Nullable String[] refSites) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(description.trim());
        if (refSites == null || refSites.length == 0) {
            return sb;
        }
        // Add references
        return sb.append(UIUtils.getBoldString("\n\nReferences\n"))
                .append(UiUtils.getOrderedList(Arrays.asList(refSites)));
    }

    private void addTag(@NonNull ViewGroup parent, @StringRes int titleRes, @ColorInt int textColor) {
        Chip chip = (Chip) LayoutInflater.from(requireContext()).inflate(R.layout.item_chip, parent, false);
        chip.setText(titleRes);
        if (textColor >= 0) {
            chip.setTextColor(textColor);
        }
        parent.addView(chip);
    }

    private void addTag(@NonNull ViewGroup parent, @NonNull CharSequence title) {
        Chip chip = (Chip) LayoutInflater.from(requireContext()).inflate(R.layout.item_chip, parent, false);
        chip.setText(title);
        parent.addView(chip);
    }
}
