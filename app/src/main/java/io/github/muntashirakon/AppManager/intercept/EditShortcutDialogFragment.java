// SPDX-License-Identifier: ISC AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.intercept;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ComponentName;
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
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.IconPickerDialogFragment;

// Copyright 2017 Adam M. Szalkowski
public class EditShortcutDialogFragment extends DialogFragment {
    public static final String TAG = EditShortcutDialogFragment.class.getSimpleName();
    public static final String EXTRA_SHORTCUT_NAME = "shortcut_name";
    public static final String EXTRA_COMPONENT_NAME = "component_name";

    @NonNull
    public static EditShortcutDialogFragment getInstance(@NonNull String shortcutName, @Nullable ComponentName componentName) {
        EditShortcutDialogFragment fragment = new EditShortcutDialogFragment();
        Bundle args = new Bundle();
        args.putString(EXTRA_SHORTCUT_NAME, shortcutName);
        if (componentName != null) {
            args.putParcelable(EXTRA_COMPONENT_NAME, componentName);
        }
        fragment.setArguments(args);
        return fragment;
    }

    public interface CreateShortcutInterface {
        void onCreateShortcut(@NonNull String shortcutName, @NonNull Drawable drawable);
    }

    @Nullable
    private ComponentName mComponentName;
    private PackageManager mPackageManager;
    private EditText textName;
    private EditText textIcon;
    private ImageView imageIcon;
    @Nullable
    private CreateShortcutInterface createShortcutInterface;

    public void setOnCreateShortcut(@Nullable CreateShortcutInterface createShortcutInterface) {
        this.createShortcutInterface = createShortcutInterface;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final FragmentActivity activity = requireActivity();
        mComponentName = requireArguments().getParcelable(EXTRA_COMPONENT_NAME);
        String shortcutName = requireArguments().getString(EXTRA_SHORTCUT_NAME);
        mPackageManager = activity.getPackageManager();
        LayoutInflater inflater = LayoutInflater.from(activity);
        if (inflater == null) return super.onCreateDialog(savedInstanceState);
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.dialog_shortcut, null);
        textName = view.findViewById(R.id.shortcut_name);
        textName.setText(shortcutName);
        textIcon = view.findViewById(R.id.insert_icon);
        textIcon.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {
                imageIcon.setImageDrawable(getIcon(s.toString()));
            }
        });

        imageIcon = view.findViewById(R.id.insert_icon_btn);
        imageIcon.setOnClickListener(v -> {
            IconPickerDialogFragment dialog = new IconPickerDialogFragment();
            dialog.attachIconPickerListener(icon -> {
                textIcon.setText(icon.name);
                imageIcon.setImageDrawable(icon.loadIcon(mPackageManager));
            });
            dialog.show(getParentFragmentManager(), IconPickerDialogFragment.TAG);
        });

        return new MaterialAlertDialogBuilder(activity)
                .setTitle(shortcutName)
                .setView(view)
                .setPositiveButton(R.string.create_shortcut, (dialog, which) -> {
                    String newShortcutName = textName.getText().toString();
                    if (newShortcutName.length() == 0) newShortcutName = shortcutName;

                    Drawable icon = null;
                    try {
                        final String iconResourceString = textIcon.getText().toString();
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
                    if (createShortcutInterface != null) {
                        createShortcutInterface.onCreateShortcut(newShortcutName, icon);
                    }
                })
                .setNegativeButton(R.string.cancel, null).create();
    }

    private Drawable getIcon(String iconResourceString) {
        try {
            String pack = iconResourceString.substring(0, iconResourceString.indexOf(':'));
            String type = iconResourceString.substring(iconResourceString.indexOf(':') + 1, iconResourceString.indexOf('/'));
            String name = iconResourceString.substring(iconResourceString.indexOf('/') + 1);
            Resources res = mPackageManager.getResourcesForApplication(pack);
            return ResourcesCompat.getDrawable(res, res.getIdentifier(name, type, pack),
                    getActivity() == null ? null : getActivity().getTheme());
        } catch (Exception e) {
            return mPackageManager.getDefaultActivityIcon();
        }

    }
}
