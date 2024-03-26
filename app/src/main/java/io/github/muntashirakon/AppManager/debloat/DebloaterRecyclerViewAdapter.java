// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getColoredText;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textview.MaterialTextView;

import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.widget.MultiSelectionView;

public class DebloaterRecyclerViewAdapter extends MultiSelectionView.Adapter<DebloaterRecyclerViewAdapter.ViewHolder> {
    private List<DebloatObject> mAdapterList = Collections.emptyList();

    private final FragmentActivity mActivity;
    @ColorInt
    private final int mRemovalSafeColor;
    @ColorInt
    private final int mRemovalReplaceColor;
    @ColorInt
    private final int mRemovalCautionColor;
    @ColorInt
    private final int mColorSurface;
    private final Object mLock = new Object();
    @NonNull
    private final DebloaterViewModel mViewModel;
    @NonNull
    private final Drawable mDefaultIcon;

    public DebloaterRecyclerViewAdapter(DebloaterActivity activity) {
        mActivity = activity;
        mRemovalSafeColor = ColorCodes.getRemovalSafeIndicatorColor(activity);
        mRemovalReplaceColor = ColorCodes.getRemovalReplaceIndicatorColor(activity);
        mRemovalCautionColor = ColorCodes.getRemovalCautionIndicatorColor(activity);
        mColorSurface = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorSurface,
                DebloaterRecyclerViewAdapter.class.getCanonicalName());
        mViewModel = activity.viewModel;
        mDefaultIcon = activity.getPackageManager().getDefaultActivityIcon();
    }

    public void setAdapterList(List<DebloatObject> adapterList) {
        synchronized (mLock) {
            int previousCount = mAdapterList.size();
            mAdapterList = adapterList;
            AdapterUtils.notifyDataSetChanged(this, previousCount, mAdapterList.size());
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_debloater, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DebloatObject debloatObject;
        synchronized (mLock) {
            debloatObject = mAdapterList.get(position);
        }
        Context context = holder.itemView.getContext();
        Drawable icon = debloatObject.getIcon() != null ? debloatObject.getIcon() : mDefaultIcon;
        String warning = debloatObject.getWarning();
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int removalColor;
        @StringRes
        int removalRes;
        switch (debloatObject.getRemoval()) {
            case DebloatObject.REMOVAL_SAFE:
                removalColor = mRemovalSafeColor;
                removalRes = R.string.debloat_removal_safe_short_description;
                break;
            default:
            case DebloatObject.REMOVAL_CAUTION:
                removalColor = mRemovalCautionColor;
                removalRes = R.string.debloat_removal_caution_short_description;
                break;
            case DebloatObject.REMOVAL_REPLACE:
                removalColor = mRemovalReplaceColor;
                removalRes = R.string.debloat_removal_replace_short_description;
                break;
        }
        sb.append(getColoredText(context.getString(removalRes), removalColor));
        if (!TextUtils.isEmpty(warning)) {
            sb.append(" — ").append(warning);
        }
        CharSequence label = debloatObject.getLabel() != null ? debloatObject.getLabel() : debloatObject.packageName;
        holder.iconView.setImageDrawable(icon);
        holder.listTypeView.setText(debloatObject.type);
        holder.packageNameView.setText(debloatObject.packageName);
        holder.descriptionView.setText(sb);
        holder.itemView.setStrokeColor(removalColor);
        holder.labelView.setText(label);
        holder.itemView.setOnLongClickListener(v -> {
            toggleSelection(position);
            return true;
        });
        holder.iconView.setOnClickListener(v -> toggleSelection(position));
        holder.itemView.setOnClickListener(v -> {
            if (isInSelectionMode()) {
                toggleSelection(position);
            } else {
                BloatwareDetailsDialog dialog = BloatwareDetailsDialog.getInstance(debloatObject.packageName);
                dialog.show(mActivity.getSupportFragmentManager(), BloatwareDetailsDialog.TAG);
            }
        });
        super.onBindViewHolder(holder, position);
    }

    @Override
    public long getItemId(int position) {
        synchronized (mLock) {
            return mAdapterList.get(position).packageName.hashCode();
        }
    }

    @Override
    public int getItemCount() {
        synchronized (mLock) {
            return mAdapterList.size();
        }
    }

    @Override
    protected void select(int position) {
        synchronized (mLock) {
            mViewModel.select(mAdapterList.get(position));
        }
    }

    @Override
    protected void deselect(int position) {
        synchronized (mLock) {
            mViewModel.deselect(mAdapterList.get(position));
        }
    }

    @Override
    protected void cancelSelection() {
        super.cancelSelection();
        mViewModel.deselectAll();
    }

    @Override
    protected boolean isSelected(int position) {
        synchronized (mLock) {
            return mViewModel.isSelected(mAdapterList.get(position));
        }
    }

    @Override
    protected int getSelectedItemCount() {
        return mViewModel.getSelectedItemCount();
    }

    @Override
    protected int getTotalItemCount() {
        return mViewModel.getTotalItemCount();
    }

    public static class ViewHolder extends MultiSelectionView.ViewHolder {
        public final MaterialCardView itemView;
        public final AppCompatImageView iconView;
        public final MaterialTextView listTypeView;
        public final MaterialTextView labelView;
        public final MaterialTextView packageNameView;
        public final MaterialTextView descriptionView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = (MaterialCardView) itemView;
            iconView = itemView.findViewById(R.id.icon);
            listTypeView = itemView.findViewById(R.id.list_type);
            labelView = itemView.findViewById(R.id.label);
            packageNameView = itemView.findViewById(R.id.package_name);
            descriptionView = itemView.findViewById(R.id.apk_description);
        }
    }
}
