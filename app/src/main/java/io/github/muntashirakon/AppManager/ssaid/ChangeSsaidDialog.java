// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ssaid;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandleHidden;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

@RequiresApi(Build.VERSION_CODES.O)
public class ChangeSsaidDialog extends DialogFragment {
    public static final String TAG = ChangeSsaidDialog.class.getSimpleName();

    @NonNull
    public static ChangeSsaidDialog getInstance(@NonNull String packageName, int uid, @Nullable String ssaid) {
        ChangeSsaidDialog dialog = new ChangeSsaidDialog();
        Bundle args = new Bundle();
        args.putString(ARG_PACKAGE_NAME, packageName);
        args.putInt(ARG_UID, uid);
        args.putString(ARG_OPTIONAL_SSAID, ssaid);
        dialog.setArguments(args);
        return dialog;
    }

    public interface SsaidChangedInterface {
        @MainThread
        void onSsaidChanged(String newSsaid, boolean isSuccessful);
    }

    public static final String ARG_PACKAGE_NAME = "pkg";
    public static final String ARG_UID = "uid";
    public static final String ARG_OPTIONAL_SSAID = "ssaid";

    private String mSsaid;
    private String mOldSsaid;
    @Nullable
    private SsaidChangedInterface mSsaidChangedInterface;
    @Nullable
    private Future<?> mSsaidChangedResult;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        FragmentActivity activity = requireActivity();
        mSsaid = requireArguments().getString(ARG_OPTIONAL_SSAID);
        mOldSsaid = mSsaid;
        String packageName = Objects.requireNonNull(requireArguments().getString(ARG_PACKAGE_NAME));
        int uid = requireArguments().getInt(ARG_UID);
        int sizeByte = packageName.equals("android") ? 32 : 8;
        View view = getLayoutInflater().inflate(R.layout.dialog_ssaid_info, null);
        AlertDialog alertDialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.ssaid)
                .setView(view)
                .setPositiveButton(R.string.apply, null)
                .setNegativeButton(R.string.close, null)
                .setNeutralButton(R.string.reset_to_default, null)
                .create();
        TextInputEditText ssaidEditText = view.findViewById(android.R.id.text1);
        TextInputLayout ssaidInputLayout = view.findViewById(R.id.ssaid_layout);
        AtomicReference<Button> applyButton = new AtomicReference<>();
        AtomicReference<Button> resetButton = new AtomicReference<>();

        alertDialog.setOnShowListener(dialog -> {
            applyButton.set(alertDialog.getButton(AlertDialog.BUTTON_POSITIVE));
            resetButton.set(alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL));
            applyButton.get().setVisibility(View.GONE);
            applyButton.get().setOnClickListener(v -> {
                mSsaidChangedResult = ThreadUtils.postOnBackgroundThread(() -> {
                    try {
                        Editable editable = ssaidEditText.getText();
                        if (editable == null) {
                            throw new IOException("Empty SSAID field.");
                        }
                        mSsaid = editable.toString();
                        if (mSsaid.length() != sizeByte * 2) {
                            throw new IOException("Invalid SSAID size " + mSsaid.length());
                        }
                        if (!mSsaid.matches("[0-9A-Fa-f]+")) {
                            throw new IOException("Invalid SSAID " + mSsaid.length());
                        }
                        SsaidSettings ssaidSettings = new SsaidSettings(UserHandleHidden.getUserId(uid));
                        boolean isSuccess = ssaidSettings.setSsaid(packageName, uid, mSsaid);
                        if (isSuccess) {
                            alertDialog.dismiss();
                        }
                        if (mSsaidChangedInterface != null) {
                            ThreadUtils.postOnMainThread(() -> mSsaidChangedInterface.onSsaidChanged(mSsaid, isSuccess));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        if (mSsaidChangedInterface != null) {
                            ThreadUtils.postOnMainThread(() -> mSsaidChangedInterface.onSsaidChanged(mSsaid, false));
                        }
                    }
                });
            });
            resetButton.get().setVisibility(View.GONE);
            resetButton.get().setOnClickListener(v -> {
                mSsaid = mOldSsaid;
                ssaidEditText.setText(mSsaid);
                applyButton.get().performClick();
                resetButton.get().setVisibility(View.GONE);
                applyButton.get().setVisibility(View.GONE);
            });
        });
        ssaidEditText.setText(mSsaid);
        ssaidEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean valid = !s.equals(mSsaid) && s.length() == (2 * sizeByte);
                if (resetButton.get() != null) {
                    resetButton.get().setVisibility(valid && !mOldSsaid.contentEquals(s) ? View.VISIBLE : View.GONE);
                }
                if (applyButton.get() != null) {
                    applyButton.get().setVisibility(valid ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        ssaidInputLayout.setEndIconOnClickListener(v -> {
            mSsaid = SsaidSettings.generateSsaid(packageName);
            ssaidEditText.setText(mSsaid);
            if (!mOldSsaid.equals(mSsaid)) {
                if (resetButton.get() != null) {
                    resetButton.get().setVisibility(View.VISIBLE);
                }
                if (applyButton.get() != null) {
                    applyButton.get().setVisibility(View.VISIBLE);
                }
            }
        });
        ssaidInputLayout.setHelperText(getString(R.string.input_ssaid_instruction, sizeByte, sizeByte * 2));
        ssaidInputLayout.setCounterMaxLength(sizeByte * 2);
        return alertDialog;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        if (mSsaidChangedResult != null) {
            mSsaidChangedResult.cancel(true);
        }
        super.onDismiss(dialog);
    }

    public void setSsaidChangedInterface(@Nullable SsaidChangedInterface ssaidChangedInterface) {
        mSsaidChangedInterface = ssaidChangedInterface;
    }
}
