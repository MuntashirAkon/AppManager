// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.fm.FmUtils;
import io.github.muntashirakon.widget.TextInputTextView;

public class ChangeFileModeDialogFragment extends DialogFragment {
    public static final String TAG = ChangeFileModeDialogFragment.class.getSimpleName();

    public interface OnChangeFileModeInterface {
        void onChangeMode(int mode, boolean recursive);
    }

    private static final String ARG_MODE = "mode";
    private static final String ARG_DISPLAY_RECURSIVE = "recursive";

    @NonNull
    public static ChangeFileModeDialogFragment getInstance(int mode, boolean recursive,
                                                           @Nullable OnChangeFileModeInterface changeFileModeInterface) {
        ChangeFileModeDialogFragment fragment = new ChangeFileModeDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_MODE, mode);
        args.putBoolean(ARG_DISPLAY_RECURSIVE, recursive);
        fragment.setArguments(args);
        fragment.setOnChangeFileModeInterface(changeFileModeInterface);
        return fragment;
    }

    @Nullable
    private OnChangeFileModeInterface mOnChangeFileModeInterface;
    private View mDialogView;
    private MaterialCheckBox mOwnerRead;
    private MaterialCheckBox mOwnerWrite;
    private MaterialCheckBox mOwnerExec;
    private MaterialCheckBox mGroupRead;
    private MaterialCheckBox mGroupWrite;
    private MaterialCheckBox mGroupExec;
    private MaterialCheckBox mOthersRead;
    private MaterialCheckBox mOthersWrite;
    private MaterialCheckBox mOthersExec;
    private MaterialSwitch mUidBit;
    private MaterialSwitch mGidBit;
    private MaterialSwitch mStickyBit;
    private TextInputTextView mPreview;
    private MaterialCheckBox mRecursive;
    private int mOldMode;
    private int mMode;

    @SuppressWarnings("OctalInteger")
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mMode = mOldMode = requireArguments().getInt(ARG_MODE);
        boolean displayRecursive = requireArguments().getBoolean(ARG_DISPLAY_RECURSIVE);
        mDialogView = View.inflate(requireActivity(), R.layout.dialog_change_file_mode, null);
        mOwnerRead = mDialogView.findViewById(R.id.user_read);
        mOwnerRead.setChecked((mOldMode & 0400) != 0);
        mOwnerRead.setOnCheckedChangeListener((buttonView, isChecked) -> updateModePreview(0400, isChecked));
        mOwnerWrite = mDialogView.findViewById(R.id.user_write);
        mOwnerWrite.setChecked((mOldMode & 0200) != 0);
        mOwnerWrite.setOnCheckedChangeListener((buttonView, isChecked) -> updateModePreview(0200, isChecked));
        mOwnerExec = mDialogView.findViewById(R.id.user_exec);
        mOwnerExec.setChecked((mOldMode & 0100) != 0);
        mOwnerExec.setOnCheckedChangeListener((buttonView, isChecked) -> updateModePreview(0100, isChecked));
        mGroupRead = mDialogView.findViewById(R.id.group_read);
        mGroupRead.setChecked((mOldMode & 040) != 0);
        mGroupRead.setOnCheckedChangeListener((buttonView, isChecked) -> updateModePreview(040, isChecked));
        mGroupWrite = mDialogView.findViewById(R.id.group_write);
        mGroupWrite.setChecked((mOldMode & 020) != 0);
        mGroupWrite.setOnCheckedChangeListener((buttonView, isChecked) -> updateModePreview(020, isChecked));
        mGroupExec = mDialogView.findViewById(R.id.group_exec);
        mGroupExec.setChecked((mOldMode & 010) != 0);
        mGroupExec.setOnCheckedChangeListener((buttonView, isChecked) -> updateModePreview(010, isChecked));
        mOthersRead = mDialogView.findViewById(R.id.others_read);
        mOthersRead.setChecked((mOldMode & 04) != 0);
        mOthersRead.setOnCheckedChangeListener((buttonView, isChecked) -> updateModePreview(04, isChecked));
        mOthersWrite = mDialogView.findViewById(R.id.others_write);
        mOthersWrite.setChecked((mOldMode & 02) != 0);
        mOthersWrite.setOnCheckedChangeListener((buttonView, isChecked) -> updateModePreview(02, isChecked));
        mOthersExec = mDialogView.findViewById(R.id.others_exec);
        mOthersExec.setChecked((mOldMode & 01) != 0);
        mOthersExec.setOnCheckedChangeListener((buttonView, isChecked) -> updateModePreview(01, isChecked));
        mUidBit = mDialogView.findViewById(R.id.uid_bit);
        mUidBit.setChecked((mOldMode & 04000) != 0);
        mUidBit.setOnCheckedChangeListener((buttonView, isChecked) -> updateModePreview(04000, isChecked));
        mGidBit = mDialogView.findViewById(R.id.gid_bit);
        mGidBit.setChecked((mOldMode & 02000) != 0);
        mGidBit.setOnCheckedChangeListener((buttonView, isChecked) -> updateModePreview(02000, isChecked));
        mStickyBit = mDialogView.findViewById(R.id.sticky_bit);
        mStickyBit.setChecked((mOldMode & 01000) != 0);
        mStickyBit.setOnCheckedChangeListener((buttonView, isChecked) -> updateModePreview(01000, isChecked));
        mPreview = mDialogView.findViewById(R.id.preview);
        mPreview.setText(FmUtils.getFormattedMode(mOldMode));
        mRecursive = mDialogView.findViewById(R.id.checkbox);
        mRecursive.setVisibility(displayRecursive ? View.VISIBLE : View.GONE);
        return new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.change_mode)
                .setView(mDialogView)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    if (mOnChangeFileModeInterface != null) {
                        mOnChangeFileModeInterface.onChangeMode(mMode, mRecursive.isChecked());
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

    public void setOnChangeFileModeInterface(@Nullable OnChangeFileModeInterface changeFileModeInterface) {
        mOnChangeFileModeInterface = changeFileModeInterface;
    }

    private void updateModePreview(int mode, boolean enabled) {
        if (enabled) {
            mMode |= mode;
        } else mMode &= ~mode;
        mPreview.setText(FmUtils.getFormattedMode(mMode));
    }
}
