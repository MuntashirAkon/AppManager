// This is a modified version of ShortcutEditDialogFragment.java taken
// from https://github.com/butzist/ActivityLauncher/commit/dfb7fe271dae9379b5453bbb6e88f30a1adc94a9
// and was authored by Adam M. Szalkowski with ISC License.
// All derivative works are licensed under GPLv3.0.

package io.github.muntashirakon.AppManager;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import io.github.muntashirakon.AppManager.utils.LauncherIconCreator;

public class EditShortcutDialogFragment extends DialogFragment {
    static final String ARG_ACTIVITY_INFO = "activityInfo";
    static final String TAG = "EditShortcutDialogFragment";

    private ActivityInfo mActivityInfo;
    private PackageManager mPackageManager;
    private EditText text_name;
    private EditText text_package;
    private EditText text_class;
    private EditText text_icon;
    private ImageView image_icon;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        if (getArguments() == null) return  builder.create();
        mActivityInfo = getArguments().getParcelable(ARG_ACTIVITY_INFO);

        if (getActivity() == null) return  builder.create();
        mPackageManager = getActivity().getPackageManager();

        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater == null) return builder.create();
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.dialog_shortcut, null);

        final String activityName = (String) mActivityInfo.loadLabel(mPackageManager);
        text_name = view.findViewById(R.id.shortcut_name);
        text_name.setText(activityName);
        text_package = view.findViewById(R.id.package_name);
        text_package.setText(mActivityInfo.packageName);
        text_class = view.findViewById(R.id.class_name);
        text_class.setText(mActivityInfo.name);
        text_icon = view.findViewById(R.id.insert_icon);
        ComponentName activity = new ComponentName(mActivityInfo.packageName, mActivityInfo.name);
        final String[] activityIconResourceName = new String[1];
        try {
            activityIconResourceName[0] = mPackageManager.getResourcesForActivity(activity).getResourceName(mActivityInfo.getIconResource());
            text_icon.setText(activityIconResourceName[0]);
        } catch (PackageManager.NameNotFoundException ignored) {}

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
        image_icon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IconPickerDialogFragment dialog = new IconPickerDialogFragment();
                dialog.attachIconPickerListener(new IconPickerDialogFragment.IconPickerListener() {
                    @Override
                    public void iconPicked(String icon) {
                        text_icon.setText(icon);
                        image_icon.setImageDrawable(getIcon(icon));
                    }
                });
                if (getFragmentManager() != null)
                    dialog.show(getFragmentManager(), IconPickerDialogFragment.TAG);
            }
        });

        builder.setTitle(mActivityInfo.loadLabel(mPackageManager))
                .setView(view)
                .setIcon(mActivityInfo.loadIcon(mPackageManager))
                .setPositiveButton(R.string.create_shortcut, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newActivityName = text_name.getText().toString();
                        if (newActivityName.length() == 0) newActivityName = activityName;

                        activityIconResourceName[0] = text_icon.getText().toString();
                        Drawable icon;
                        try {
                            final String icon_resource_string = activityIconResourceName[0];
                            final String pack = icon_resource_string.substring(0, icon_resource_string.indexOf(':'));
                            final String type = icon_resource_string.substring(icon_resource_string.indexOf(':') + 1, icon_resource_string.indexOf('/'));
                            final String name = icon_resource_string.substring(icon_resource_string.indexOf('/') + 1);

                            Resources resources = mPackageManager.getResourcesForApplication(pack);
                            int icon_resource = resources.getIdentifier(name, type, pack);
                            if (icon_resource != 0) {
                                icon = resources.getDrawable(icon_resource);
                            } else {
                                icon = mPackageManager.getDefaultActivityIcon();
                                Toast.makeText(getActivity(), R.string.error_invalid_icon_resource, Toast.LENGTH_LONG).show();
                            }
                        } catch (PackageManager.NameNotFoundException e) {
                            icon = mPackageManager.getDefaultActivityIcon();
                            Toast.makeText(getActivity(), R.string.error_invalid_icon_resource, Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            icon = mPackageManager.getDefaultActivityIcon();
                            Toast.makeText(getActivity(), R.string.error_invalid_icon_format, Toast.LENGTH_LONG).show();
                        }

                        LauncherIconCreator.createLauncherIcon(getActivity(), mActivityInfo, newActivityName, icon, activityIconResourceName[0]);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (getDialog() != null) getDialog().cancel();
                    }
                });

        return builder.create();
    }

    private Drawable getIcon(String icon_resource_string) {
        try {
            String pack = icon_resource_string.substring(0, icon_resource_string.indexOf(':'));
            String type = icon_resource_string.substring(icon_resource_string.indexOf(':') + 1, icon_resource_string.indexOf('/'));
            String name = icon_resource_string.substring(icon_resource_string.indexOf('/') + 1);
            Resources res = mPackageManager.getResourcesForApplication(pack);
            return res.getDrawable(res.getIdentifier(name, type, pack));
        } catch (Exception e) {
            return mPackageManager.getDefaultActivityIcon();
        }

    }
}
