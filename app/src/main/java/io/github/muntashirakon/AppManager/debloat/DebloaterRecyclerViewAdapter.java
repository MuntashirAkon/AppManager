// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.content.pm.ApplicationInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.textview.MaterialTextView;

import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.widget.MultiSelectionView;

public class DebloaterRecyclerViewAdapter extends MultiSelectionView.Adapter<DebloaterRecyclerViewAdapter.ViewHolder> {
    private List<DebloatObject> mAdapterList = Collections.emptyList();

    @ColorInt
    private final int highlightColor;
    @ColorInt
    private final int removalSafeColor;
    @ColorInt
    private final int removalReplaceColor;
    @ColorInt
    private final int removalCautionColor;
    @ColorInt
    private final int removalUnsafeColor;
    @ColorInt
    private final int colorSurface;
    private final Object mLock = new Object();
    private final ImageLoader mImageLoader = new ImageLoader();
    private final DebloaterViewModel mViewModel;

    public DebloaterRecyclerViewAdapter(DebloaterActivity activity) {
        highlightColor = ColorCodes.getListItemSelectionColor(activity);
        removalSafeColor = ColorCodes.getRemovalSafeIndicatorColor(activity);
        removalReplaceColor = ColorCodes.getRemovalReplaceIndicatorColor(activity);
        removalCautionColor = ColorCodes.getRemovalCautionIndicatorColor(activity);
        removalUnsafeColor = ColorCodes.getRemovalUnsafeIndicatorColor(activity);
        colorSurface = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorSurface,
                DebloaterRecyclerViewAdapter.class.getCanonicalName());
        mViewModel = activity.viewModel;
    }

    public void setAdapterList(List<DebloatObject> adapterList) {
        synchronized (mLock) {
            this.mAdapterList = adapterList;
            notifyDataSetChanged();
        }
    }

    @Override
    public int getHighlightColor() {
        return highlightColor;
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
        ApplicationInfo applicationInfo;
        if (debloatObject.getPackageInfo() != null) {
            applicationInfo = debloatObject.getPackageInfo().applicationInfo;
        } else applicationInfo = null;
        mImageLoader.displayImage(debloatObject.packageName, applicationInfo, holder.imageView);
        holder.listTypeView.setText(debloatObject.type);
        holder.packageNameView.setText(debloatObject.packageName);
        holder.descriptionView.setText(debloatObject.description.trim());
        CharSequence label = debloatObject.getLabel();
        if (label != null) {
            holder.labelView.setVisibility(View.VISIBLE);
            holder.labelView.setText(label);
        } else {
            holder.labelView.setVisibility(View.GONE);
        }
        switch (debloatObject.getRemoval()) {
            case DebloatObject.REMOVAL_SAFE:
                holder.removalIndicator.setDividerColor(removalSafeColor);
                break;
            case DebloatObject.REMOVAL_CAUTION:
                holder.removalIndicator.setDividerColor(removalCautionColor);
                break;
            case DebloatObject.REMOVAL_REPLACE:
                holder.removalIndicator.setDividerColor(removalReplaceColor);
                break;
            case DebloatObject.REMOVAL_UNSAFE:
                holder.removalIndicator.setDividerColor(removalUnsafeColor);
                break;
        }
        holder.itemView.setOnLongClickListener(v -> {
            toggleSelection(position);
            return true;
        });
        holder.imageView.setOnClickListener(v -> toggleSelection(position));
        holder.itemView.setOnClickListener(v -> {
            if (isInSelectionMode()) {
                toggleSelection(position);
            }
        });
        holder.itemView.setCardBackgroundColor(colorSurface);
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
        public final MaterialDivider removalIndicator;
        public final AppCompatImageView imageView;
        public final MaterialTextView listTypeView;
        public final MaterialTextView labelView;
        public final MaterialTextView packageNameView;
        public final MaterialTextView descriptionView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = (MaterialCardView) itemView;
            removalIndicator = itemView.findViewById(R.id.divider);
            imageView = itemView.findViewById(R.id.icon);
            listTypeView = itemView.findViewById(R.id.list_type);
            labelView = itemView.findViewById(R.id.label);
            packageNameView = itemView.findViewById(R.id.package_name);
            descriptionView = itemView.findViewById(R.id.apk_description);
        }
    }
}
