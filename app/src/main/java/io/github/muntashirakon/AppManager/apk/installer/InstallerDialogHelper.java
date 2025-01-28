// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;

import com.google.android.material.textview.MaterialTextView;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.dialog.DialogTitleBuilder;

public final class InstallerDialogHelper {
    public interface OnClickButtonsListener {
        void triggerInstall();

        void triggerCancel();
    }

    private final Context mContext;
    private final InstallerDialogFragment mFragment;
    private final DialogTitleBuilder mTitleBuilder;
    private final FragmentContainerView mFragmentContainer;
    private final MaterialTextView mMessage;
    private final LinearLayoutCompat mLayout;
    private final int mFragmentId = R.id.fragment_container_view_tag;
    private final AlertDialog mDialog;
    private final Button mPositiveBtn;
    private final Button mNegativeBtn;
    private final Button mNeutralBtn;

    public InstallerDialogHelper(@NonNull InstallerDialogFragment fragment, AlertDialog dialog) {
        mContext = fragment.requireContext();
        mFragment = fragment;
        mDialog = dialog;
        View view = mFragment.getDialogView();
        mTitleBuilder = mFragment.getTitleBuilder();
        mFragmentContainer = view.findViewById(mFragmentId);
        mMessage = view.findViewById(R.id.message);
        mLayout = view.findViewById(R.id.layout);
        mPositiveBtn = mDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        mNegativeBtn = mDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        mNeutralBtn = mDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
    }

    public void initProgress(View.OnClickListener cancelListener) {
        // Title section
        mTitleBuilder.setTitle(R.string._undefined)
                .setStartIcon(R.drawable.ic_get_app)
                .setSubtitle(null)
                .setEndIcon(null, null);
        // Buttons
        mPositiveBtn.setVisibility(View.GONE);
        mNeutralBtn.setVisibility(View.GONE);
        mNegativeBtn.setVisibility(View.VISIBLE);
        mNegativeBtn.setText(R.string.cancel);
        mNegativeBtn.setOnClickListener(cancelListener);
        // Body
        View v = View.inflate(mContext, R.layout.dialog_progress2, null);
        TextView tv = v.findViewById(android.R.id.text1);
        tv.setText(R.string.staging_apk_files);
        mLayout.setVisibility(View.VISIBLE);
        mLayout.removeAllViews();
        mLayout.addView(v);
        mMessage.setVisibility(View.GONE);
        mFragmentContainer.setVisibility(View.GONE);
    }

    public void showParseFailedDialog(View.OnClickListener closeListener) {
        // Title section
        mTitleBuilder.setTitle(R.string._undefined)
                .setStartIcon(R.drawable.ic_get_app)
                .setSubtitle(null)
                .setEndIcon(null, null);
        // Buttons
        mPositiveBtn.setVisibility(View.GONE);
        mNeutralBtn.setVisibility(View.GONE);
        mNegativeBtn.setVisibility(View.VISIBLE);
        mNegativeBtn.setText(R.string.close);
        mNegativeBtn.setOnClickListener(closeListener);
        // Body
        mLayout.setVisibility(View.GONE);
        mMessage.setVisibility(View.VISIBLE);
        mMessage.setText(R.string.failed_to_fetch_package_info);
        mFragmentContainer.setVisibility(View.GONE);
    }

    public void onParseSuccess(CharSequence title, CharSequence subtitle, Drawable icon,
                               @Nullable View.OnClickListener optionsClickListener) {
        mTitleBuilder.setTitle(title)
                .setStartIcon(icon)
                .setSubtitle(subtitle);
        if (optionsClickListener != null) {
            mTitleBuilder.setEndIcon(R.drawable.ic_settings, optionsClickListener)
                    .setEndIconContentDescription(R.string.installer_options);
        } else mTitleBuilder.setEndIcon(null, null);
    }

    public void showWhatsNewDialog(@StringRes int installButtonRes, Fragment fragment,
                                   @NonNull OnClickButtonsListener onClickButtonsListener,
                                   @NonNull View.OnClickListener appInfoButtonListener) {
        // Buttons
        mNeutralBtn.setVisibility(View.VISIBLE);
        mNeutralBtn.setText(R.string.app_info);
        mNeutralBtn.setOnClickListener(appInfoButtonListener);
        mPositiveBtn.setVisibility(View.VISIBLE);
        mPositiveBtn.setText(installButtonRes);
        mPositiveBtn.setOnClickListener(v -> onClickButtonsListener.triggerInstall());
        mNegativeBtn.setVisibility(View.VISIBLE);
        mNegativeBtn.setText(R.string.cancel);
        mNegativeBtn.setOnClickListener(v -> onClickButtonsListener.triggerCancel());
        // Body
        mLayout.setVisibility(View.GONE);
        mMessage.setVisibility(View.GONE);
        mFragmentContainer.setVisibility(View.VISIBLE);
        mFragment.getChildFragmentManager().beginTransaction().replace(mFragmentId, fragment).commit();
    }

    public void showInstallConfirmationDialog(@StringRes int installButtonRes,
                                              @NonNull OnClickButtonsListener onClickButtonsListener,
                                              @NonNull View.OnClickListener appInfoButtonListener) {
        // Buttons
        mNeutralBtn.setVisibility(View.VISIBLE);
        mNeutralBtn.setText(R.string.app_info);
        mNeutralBtn.setOnClickListener(appInfoButtonListener);
        mPositiveBtn.setVisibility(View.VISIBLE);
        mPositiveBtn.setText(installButtonRes);
        mPositiveBtn.setOnClickListener(v -> onClickButtonsListener.triggerInstall());
        mNegativeBtn.setVisibility(View.VISIBLE);
        mNegativeBtn.setText(R.string.cancel);
        mNegativeBtn.setOnClickListener(v -> onClickButtonsListener.triggerCancel());
        // Body
        mLayout.setVisibility(View.GONE);
        mMessage.setVisibility(View.VISIBLE);
        mMessage.setText(R.string.install_app_message);
        mFragmentContainer.setVisibility(View.GONE);
    }
    public void showSessionConfirmationDialog(@StringRes int installButtonRes,
                                              @NonNull OnClickButtonsListener onClickButtonsListener) {
        mTitleBuilder.setTitle(R.string.confirm_installation)
                .setStartIcon(R.drawable.ic_get_app)
                .setSubtitle(null)
                .setEndIcon(null, null);

        // Buttons
        mNeutralBtn.setVisibility(View.GONE);
        mPositiveBtn.setVisibility(View.VISIBLE);
        mPositiveBtn.setText(installButtonRes);
        mPositiveBtn.setOnClickListener(v -> onClickButtonsListener.triggerInstall());
        mNegativeBtn.setVisibility(View.VISIBLE);
        mNegativeBtn.setText(R.string.cancel);
        mNegativeBtn.setOnClickListener(v -> onClickButtonsListener.triggerCancel());
        // Body
        mLayout.setVisibility(View.GONE);
        mMessage.setVisibility(View.VISIBLE);
        mMessage.setText(R.string.install_app_message);
        mFragmentContainer.setVisibility(View.GONE);
    }

    public void showApkChooserDialog(@StringRes int installButtonRes, Fragment fragment,
                                     @NonNull OnClickButtonsListener onClickButtonsListener,
                                     @NonNull View.OnClickListener appInfoButtonListener) {
        // Buttons
        mNeutralBtn.setVisibility(View.VISIBLE);
        mNeutralBtn.setText(R.string.app_info);
        mNeutralBtn.setOnClickListener(appInfoButtonListener);
        mPositiveBtn.setVisibility(View.VISIBLE);
        mPositiveBtn.setText(installButtonRes);
        mPositiveBtn.setOnClickListener(v -> onClickButtonsListener.triggerInstall());
        mNegativeBtn.setVisibility(View.VISIBLE);
        mNegativeBtn.setText(R.string.cancel);
        mNegativeBtn.setOnClickListener(v -> onClickButtonsListener.triggerCancel());
        // Body
        mLayout.setVisibility(View.GONE);
        mMessage.setVisibility(View.GONE);
        mFragmentContainer.setVisibility(View.VISIBLE);
        mFragment.getChildFragmentManager().beginTransaction().replace(mFragmentId, fragment).commit();
    }

    public void showDowngradeReinstallWarning(CharSequence msg,
                                              @NonNull OnClickButtonsListener onClickButtonsListener,
                                              @NonNull View.OnClickListener appInfoButtonListener) {
        // Buttons
        mNeutralBtn.setVisibility(View.VISIBLE);
        mNeutralBtn.setText(R.string.app_info);
        mNeutralBtn.setOnClickListener(appInfoButtonListener);
        mPositiveBtn.setVisibility(View.VISIBLE);
        mPositiveBtn.setText(R.string.yes);
        mPositiveBtn.setOnClickListener(v -> onClickButtonsListener.triggerInstall());
        mNegativeBtn.setVisibility(View.VISIBLE);
        mNegativeBtn.setText(R.string.cancel);
        mNegativeBtn.setOnClickListener(v -> onClickButtonsListener.triggerCancel());
        // Body
        mLayout.setVisibility(View.GONE);
        mMessage.setVisibility(View.VISIBLE);
        mMessage.setText(msg);
        mFragmentContainer.setVisibility(View.GONE);
    }

    public void showSignatureMismatchReinstallWarning(CharSequence msg,
                                                      @NonNull OnClickButtonsListener onClickButtonsListener,
                                                      @NonNull View.OnClickListener installOnlyButtonListener,
                                                      boolean isSystem) {
        // Buttons
        mNeutralBtn.setVisibility(View.VISIBLE);
        mNeutralBtn.setText(R.string.only_install);
        mNeutralBtn.setOnClickListener(installOnlyButtonListener);
        mPositiveBtn.setVisibility(isSystem ? View.GONE : View.VISIBLE);
        mPositiveBtn.setText(R.string.yes);
        mPositiveBtn.setOnClickListener(v -> onClickButtonsListener.triggerInstall());
        mNegativeBtn.setVisibility(View.VISIBLE);
        mNegativeBtn.setText(R.string.cancel);
        mNegativeBtn.setOnClickListener(v -> onClickButtonsListener.triggerCancel());
        // Body
        mLayout.setVisibility(View.GONE);
        mMessage.setVisibility(View.VISIBLE);
        mMessage.setText(msg);
        mFragmentContainer.setVisibility(View.GONE);
    }

    public void showInstallProgressDialog(@Nullable View.OnClickListener backgroundButtonListener) {
        // Disable installer options
        mTitleBuilder.setEndIcon(null, null);
        // Buttons
        mNeutralBtn.setVisibility(View.GONE);
        if (backgroundButtonListener != null) {
            mPositiveBtn.setVisibility(View.VISIBLE);
            mPositiveBtn.setText(R.string.background);
            mPositiveBtn.setOnClickListener(backgroundButtonListener);
        } else {
            mPositiveBtn.setVisibility(View.GONE);
        }
        mNegativeBtn.setVisibility(View.GONE);
        // Body
        mLayout.setVisibility(View.VISIBLE);
        View v = View.inflate(mContext, R.layout.dialog_progress2, null);
        TextView tv = v.findViewById(android.R.id.text1);
        tv.setText(R.string.install_in_progress);
        mLayout.removeAllViews();
        mLayout.addView(v);
        mMessage.setVisibility(View.GONE);
        mFragmentContainer.setVisibility(View.GONE);
    }

    public void showInstallFinishedDialog(CharSequence msg, @StringRes int cancelOrNextRes,
                                          @NonNull View.OnClickListener cancelClickListener,
                                          @Nullable View.OnClickListener openButtonClickListener,
                                          @Nullable View.OnClickListener appInfoButtonClickListener) {
        // Buttons
        if (appInfoButtonClickListener != null) {
            mNeutralBtn.setVisibility(View.VISIBLE);
            mNeutralBtn.setText(R.string.app_info);
            mNeutralBtn.setOnClickListener(appInfoButtonClickListener);
        } else mNeutralBtn.setVisibility(View.GONE);
        if (openButtonClickListener != null) {
            mPositiveBtn.setVisibility(View.VISIBLE);
            mPositiveBtn.setText(R.string.open);
            mPositiveBtn.setOnClickListener(openButtonClickListener);
        } else mPositiveBtn.setVisibility(View.GONE);
        mNegativeBtn.setVisibility(View.VISIBLE);
        mNegativeBtn.setText(cancelOrNextRes);
        mNegativeBtn.setOnClickListener(cancelClickListener);
        // Body
        mLayout.setVisibility(View.GONE);
        mMessage.setVisibility(View.VISIBLE);
        mMessage.setText(msg);
        mFragmentContainer.setVisibility(View.GONE);
    }

    public void dismiss() {
        mDialog.dismiss();
    }
}