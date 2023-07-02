// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.UUID;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.IconPickerDialogFragment;
import io.github.muntashirakon.AppManager.utils.ResourceUtil;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.lifecycle.SoftInputLifeCycleObserver;
import io.github.muntashirakon.view.TextInputLayoutCompat;

public class CreateShortcutDialogFragment extends DialogFragment {
    public static final String TAG = CreateShortcutDialogFragment.class.getSimpleName();

    private static final String ARG_SHORTCUT_INFO = "info";

    @NonNull
    public static CreateShortcutDialogFragment getInstance(@NonNull ShortcutInfo shortcutInfo) {
        CreateShortcutDialogFragment dialog = new CreateShortcutDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_SHORTCUT_INFO, shortcutInfo);
        dialog.setArguments(args);
        return dialog;
    }

    private boolean mValidName = true;
    private ShortcutInfo mShortcutInfo;
    private View mDialogView;
    private TextInputEditText mShortcutNameField;
    private TextInputEditText mShortcutIconField;
    private TextInputLayout mShortcutIconLayout;
    private ShapeableImageView mShortcutIconPreview;
    private MaterialTextView mShortcutNamePreview;
    private PackageManager mPm;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mPm = requireActivity().getPackageManager();
        mShortcutInfo = Objects.requireNonNull(BundleCompat.getParcelable(requireArguments(), ARG_SHORTCUT_INFO, ShortcutInfo.class));
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_create_shortcut, null);
        mShortcutNameField = mDialogView.findViewById(R.id.shortcut_name);
        mShortcutIconField = mDialogView.findViewById(R.id.insert_icon);
        mShortcutIconLayout = TextInputLayoutCompat.fromTextInputEditText(mShortcutIconField);
        mShortcutIconPreview = mDialogView.findViewById(R.id.icon);
        mShortcutNamePreview = mDialogView.findViewById(R.id.name);

        mShortcutNameField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!TextUtils.isEmpty(s)) {
                    mValidName = true;
                    mShortcutInfo.setName(s);
                    mShortcutNamePreview.setText(s);
                } else mValidName = false;
            }
        });
        mShortcutIconField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                Drawable drawable = getDrawable(s.toString());
                if (drawable != null) {
                    mShortcutInfo.setIcon(UIUtils.getBitmapFromDrawable(drawable));
                    mShortcutIconPreview.setImageDrawable(drawable);
                }
            }
        });
        mShortcutIconLayout.setEndIconOnClickListener(v -> {
            IconPickerDialogFragment dialog = new IconPickerDialogFragment();
            dialog.attachIconPickerListener(icon -> {
                mShortcutIconField.setText(icon.name);
                Drawable drawable = icon.loadIcon(mPm);
                mShortcutInfo.setIcon(UIUtils.getBitmapFromDrawable(drawable));
                mShortcutIconPreview.setImageDrawable(drawable);
            });
            dialog.show(getParentFragmentManager(), IconPickerDialogFragment.TAG);
        });
        mShortcutNameField.setText(mShortcutInfo.getName());
        mShortcutNamePreview.setText(mShortcutInfo.getName());
        mShortcutIconPreview.setImageBitmap(mShortcutInfo.getIcon());

        return new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.create_shortcut)
                .setView(mDialogView)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    if (mValidName) {
                        requestPinShortcut(mShortcutInfo);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return mDialogView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        getLifecycle().addObserver(new SoftInputLifeCycleObserver(new WeakReference<>(mShortcutNameField)));
    }


    @Nullable
    private Drawable getDrawable(@Nullable String iconResString) {
        if (TextUtils.isEmpty(iconResString)) {
            return null;
        }
        try {
            Drawable drawable = ResourceUtil.getResourceFromName(mPm, iconResString).getDrawable(requireActivity().getTheme());
            if (drawable != null) {
                return drawable;
            }
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignore) {
        }
        return null;
        // return mPm.getDefaultActivityIcon();
    }

    private void requestPinShortcut(@NonNull ShortcutInfo shortcutInfo) {
        Context context = requireContext().getApplicationContext();
        CharSequence name = Objects.requireNonNull(shortcutInfo.getName());
        String shortcutId = shortcutInfo.getId();
        if (shortcutId == null) {
            shortcutId = UUID.randomUUID().toString();
        }
        Intent shortcutIntent = shortcutInfo.toShortcutIntent(context);
        // Set action for shortcut
        shortcutIntent.setAction(Intent.ACTION_CREATE_SHORTCUT);

        ShortcutInfoCompat shortcutInfoCompat = new ShortcutInfoCompat.Builder(context, shortcutId)
                // Enforce shortcut name to be a String
                .setShortLabel(name.toString())
                .setLongLabel(name)
                .setIcon(IconCompat.createWithBitmap(shortcutInfo.getIcon()))
                .setIntent(shortcutIntent)
                .build();

        if (!ShortcutManagerCompat.requestPinShortcut(context, shortcutInfoCompat, null)) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(context.getString(R.string.error_creating_shortcut))
                    .setMessage(context.getString(R.string.error_verbose_pin_shortcut))
                    .setPositiveButton(context.getString(R.string.ok), (dialog, which) -> dialog.cancel())
                    .show();
        }
    }
}
