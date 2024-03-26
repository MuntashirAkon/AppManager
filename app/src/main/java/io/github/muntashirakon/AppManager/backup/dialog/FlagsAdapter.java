// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.resources.MaterialAttributes;

import java.util.List;

import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.utils.UIUtils;

class FlagsAdapter extends RecyclerView.Adapter<FlagsAdapter.ViewHolder> {
    private final int mLayoutId;
    private final List<Integer> mSupportedBackupFlags;
    private final CharSequence[] mSupportedBackupFlagNames;
    @BackupFlags.BackupFlag
    private final int mDisabledFlags;

    @BackupFlags.BackupFlag
    private int mSelectedFlags;

    @SuppressLint("RestrictedApi")
    public FlagsAdapter(@NonNull Context context, @BackupFlags.BackupFlag int flags,
                        @BackupFlags.BackupFlag int supportedFlags) {
        this(context, flags, supportedFlags, 0);
    }

    @SuppressLint("RestrictedApi")
    public FlagsAdapter(@NonNull Context context, @BackupFlags.BackupFlag int flags,
                        @BackupFlags.BackupFlag int supportedFlags, @BackupFlags.BackupFlag int disabledFlags) {
        mLayoutId = MaterialAttributes.resolveInteger(context, androidx.appcompat.R.attr.multiChoiceItemLayout,
                com.google.android.material.R.layout.mtrl_alert_select_dialog_multichoice);
        // We list |supportedFlags| and select |flags| by default
        mSupportedBackupFlags = BackupFlags.getBackupFlagsAsArray(supportedFlags);
        mSupportedBackupFlagNames = BackupFlags.getFormattedFlagNames(context, mSupportedBackupFlags);
        mSelectedFlags = flags;
        mDisabledFlags = disabledFlags;
        notifyItemRangeInserted(0, mSupportedBackupFlags.size());
    }

    public int getSelectedFlags() {
        return mSelectedFlags;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(mLayoutId, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int flag = mSupportedBackupFlags.get(position);
        boolean isSelected = (mSelectedFlags & flag) != 0;
        boolean isDisabled = (mDisabledFlags & flag) != 0;
        holder.item.setChecked(isSelected);
        holder.item.setEnabled(!isDisabled);
        holder.item.setText(mSupportedBackupFlagNames[position]);
        holder.item.setOnClickListener(v -> {
            if (isSelected) {
                // Now unselected
                mSelectedFlags &= ~flag;
            } else {
                // Now selected
                mSelectedFlags |= flag;
            }
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return mSupportedBackupFlags.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckedTextView item;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            item = itemView.findViewById(android.R.id.text1);
            // textAppearanceBodyLarge
            item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            item.setTextColor(UIUtils.getTextColorSecondary(item.getContext()));
        }
    }
}
