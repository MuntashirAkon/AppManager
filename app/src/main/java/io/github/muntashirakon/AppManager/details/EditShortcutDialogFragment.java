// SPDX-License-Identifier: ISC AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.ResourceUtil;
import io.github.muntashirakon.dialog.DialogTitleBuilder;

// Copyright 2017 Adam M. Szalkowski
public class EditShortcutDialogFragment extends DialogFragment {
    static final String ARG_ACTIVITY_INFO = "activity_info";
    static final String ARG_SHORTCUT_NAME = "shortcut_name";
    static final String TAG = "EditShortcutDialogFragment";

    private ActivityInfo mActivityInfo;
    private PackageManager mPackageManager;
    private EditText mShortcutNameField;
    private EditText mShortcutIconField;
    private ImageView mShortcutIconSelectionBtn;
    private CharSequence mShortcutName;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final FragmentActivity activity = requireActivity();
        mActivityInfo = requireArguments().getParcelable(ARG_ACTIVITY_INFO);
        mShortcutName = requireArguments().getCharSequence(ARG_SHORTCUT_NAME);
        mPackageManager = activity.getPackageManager();
        LayoutInflater inflater = LayoutInflater.from(activity);
        if (inflater == null) return super.onCreateDialog(savedInstanceState);
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.dialog_shortcut, null);
        if (mShortcutName == null) {
            mShortcutName = mActivityInfo.loadLabel(mPackageManager);
        }
        mShortcutNameField = view.findViewById(R.id.shortcut_name);
        mShortcutNameField.setText(mShortcutName);
        mShortcutIconField = view.findViewById(R.id.insert_icon);
        ComponentName activityComponent = new ComponentName(mActivityInfo.packageName, mActivityInfo.name);
        try {
            String activityIconResourceName = mPackageManager.getResourcesForActivity(activityComponent)
                    .getResourceName(mActivityInfo.getIconResource());
            mShortcutIconField.setText(activityIconResourceName);
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {
        }

        mShortcutIconField.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mShortcutIconSelectionBtn.setImageDrawable(getDrawable(s.toString()));
            }
        });

        Drawable activityIcon = mActivityInfo.loadIcon(mPackageManager);
        mShortcutIconSelectionBtn = view.findViewById(R.id.insert_icon_btn);
        mShortcutIconSelectionBtn.setImageDrawable(activityIcon);
        mShortcutIconSelectionBtn.setOnClickListener(v -> {
            IconPickerDialogFragment dialog = new IconPickerDialogFragment();
            dialog.attachIconPickerListener(icon -> {
                mShortcutIconField.setText(icon.name);
                mShortcutIconSelectionBtn.setImageDrawable(icon.loadIcon(mPackageManager));
            });
            dialog.show(getParentFragmentManager(), IconPickerDialogFragment.TAG);
        });

        return new MaterialAlertDialogBuilder(activity)
                .setCustomTitle(new DialogTitleBuilder(activity)
                        .setTitle(mShortcutName)
                        .setSubtitle(R.string.edit_shortcut)
                        .setStartIcon(activityIcon)
                        .build())
                .setView(view)
                .setPositiveButton(R.string.create_shortcut, (dialog, which) -> {
                    CharSequence newShortcutName = mShortcutNameField.getText();
                    if (newShortcutName.length() == 0) newShortcutName = mShortcutName;

                    Drawable icon = null;
                    try {
                        String iconResourceString = mShortcutIconField.getText().toString();
                        icon = ResourceUtil.getResourceFromName(mPackageManager, iconResourceString).getDrawable(activity.getTheme());
                    } catch (PackageManager.NameNotFoundException | Resources.NotFoundException e) {
                        Toast.makeText(activity, R.string.error_invalid_icon_resource, Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(getActivity(), R.string.error_invalid_icon_format, Toast.LENGTH_LONG).show();
                    }
                    if (icon == null) {
                        icon = mPackageManager.getDefaultActivityIcon();
                    }
                    LauncherIconCreator.createLauncherIcon(activity, mActivityInfo, newShortcutName, icon);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    if (getDialog() != null) getDialog().cancel();
                }).create();
    }

    @NonNull
    public Drawable getDrawable(@NonNull String iconResString) {
        try {
            return ResourceUtil.getResourceFromName(mPackageManager, iconResString).getDrawable(requireActivity().getTheme());
        } catch (PackageManager.NameNotFoundException ignore) {
            return mPackageManager.getDefaultActivityIcon();
        }
    }
}
