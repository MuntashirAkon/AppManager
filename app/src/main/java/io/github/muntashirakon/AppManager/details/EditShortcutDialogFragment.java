/*
 * Copyright (C) 2020 Muntashir Al-Islam
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

// This is a modified version of ShortcutEditDialogFragment.java taken
// from https://github.com/butzist/ActivityLauncher/commit/dfb7fe271dae9379b5453bbb6e88f30a1adc94a9
// and was authored by Adam M. Szalkowski with ISC License.
// All derivative works are licensed under GPLv3.0.

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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import io.github.muntashirakon.AppManager.R;

public class EditShortcutDialogFragment extends DialogFragment {
    static final String ARG_ACTIVITY_INFO = "activityInfo";
    static final String TAG = "EditShortcutDialogFragment";

    private ActivityInfo mActivityInfo;
    private PackageManager mPackageManager;
    private EditText text_name;
    private EditText text_icon;
    private ImageView image_icon;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final FragmentActivity activity = requireActivity();
        mActivityInfo = requireArguments().getParcelable(ARG_ACTIVITY_INFO);
        mPackageManager = activity.getPackageManager();
        LayoutInflater inflater = LayoutInflater.from(activity);
        if (inflater == null) return super.onCreateDialog(savedInstanceState);
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.dialog_shortcut, null);
        final String activityName = (String) mActivityInfo.loadLabel(mPackageManager);
        text_name = view.findViewById(R.id.shortcut_name);
        text_name.setText(activityName);
        EditText text_package = view.findViewById(R.id.package_name);
        text_package.setText(mActivityInfo.packageName);
        EditText text_class = view.findViewById(R.id.class_name);
        text_class.setText(mActivityInfo.name);
        text_icon = view.findViewById(R.id.insert_icon);
        ComponentName activityComponent = new ComponentName(mActivityInfo.packageName, mActivityInfo.name);
        try {
            String activityIconResourceName = mPackageManager.getResourcesForActivity(activityComponent)
                    .getResourceName(mActivityInfo.getIconResource());
            text_icon.setText(activityIconResourceName);
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {}

        text_icon.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {
                image_icon.setImageDrawable(getIcon(s.toString()));
            }
        });

        image_icon = view.findViewById(R.id.insert_icon_btn);
        image_icon.setImageDrawable(mActivityInfo.loadIcon(mPackageManager));
        image_icon.setOnClickListener(v -> {
            IconPickerDialogFragment dialog = new IconPickerDialogFragment();
            dialog.attachIconPickerListener(icon -> {
                text_icon.setText(icon);
                image_icon.setImageDrawable(getIcon(icon));
            });
            dialog.show(getParentFragmentManager(), IconPickerDialogFragment.TAG);
        });

        return new MaterialAlertDialogBuilder(activity)
                .setTitle(mActivityInfo.loadLabel(mPackageManager))
                .setView(view)
                .setIcon(mActivityInfo.loadIcon(mPackageManager))
                .setPositiveButton(R.string.create_shortcut, (dialog, which) -> {
                    String newActivityName = text_name.getText().toString();
                    if (newActivityName.length() == 0) newActivityName = activityName;

                    Drawable icon = null;
                    try {
                        final String iconResourceString = text_icon.getText().toString();
                        final String pack = iconResourceString.substring(0, iconResourceString.indexOf(':'));
                        final String type = iconResourceString.substring(iconResourceString.indexOf(':') + 1, iconResourceString.indexOf('/'));
                        final String name = iconResourceString.substring(iconResourceString.indexOf('/') + 1);

                        Resources resources = mPackageManager.getResourcesForApplication(pack);
                        int iconResource = resources.getIdentifier(name, type, pack);
                        if (iconResource != 0) {
                            icon = ResourcesCompat.getDrawable(resources, iconResource, activity.getTheme());
                        } else {
                            Toast.makeText(activity, R.string.error_invalid_icon_resource, Toast.LENGTH_LONG).show();
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        Toast.makeText(activity, R.string.error_invalid_icon_resource, Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(getActivity(), R.string.error_invalid_icon_format, Toast.LENGTH_LONG).show();
                    }
                    if (icon == null) {
                        icon = mPackageManager.getDefaultActivityIcon();
                    }
                    LauncherIconCreator.createLauncherIcon(activity, mActivityInfo, newActivityName, icon);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    if (getDialog() != null) getDialog().cancel();
                }).create();
    }

    private Drawable getIcon(String icon_resource_string) {
        try {
            String pack = icon_resource_string.substring(0, icon_resource_string.indexOf(':'));
            String type = icon_resource_string.substring(icon_resource_string.indexOf(':') + 1, icon_resource_string.indexOf('/'));
            String name = icon_resource_string.substring(icon_resource_string.indexOf('/') + 1);
            Resources res = mPackageManager.getResourcesForApplication(pack);
            return ResourcesCompat.getDrawable(res, res.getIdentifier(name, type, pack),
                    getActivity() == null ? null : getActivity().getTheme());
        } catch (Exception e) {
            return mPackageManager.getDefaultActivityIcon();
        }

    }
}
