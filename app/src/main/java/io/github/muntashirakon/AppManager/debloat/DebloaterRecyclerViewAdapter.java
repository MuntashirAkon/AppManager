// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.content.pm.ApplicationInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.textview.MaterialTextView;

import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.imagecache.ImageLoader;
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
    private final Object mLock = new Object();
    private final ImageLoader mImageLoader = new ImageLoader();

    public DebloaterRecyclerViewAdapter(FragmentActivity activity) {
        highlightColor = ColorCodes.getListItemSelectionColor(activity);
        removalSafeColor = ColorCodes.getRemovalSafeIndicatorColor(activity);
        removalReplaceColor = ColorCodes.getRemovalReplaceIndicatorColor(activity);
        removalCautionColor = ColorCodes.getRemovalCautionIndicatorColor(activity);
        removalUnsafeColor = ColorCodes.getRemovalUnsafeIndicatorColor(activity);
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
        if (applicationInfo != null) {
            holder.labelView.setVisibility(View.VISIBLE);
            holder.labelView.setText(applicationInfo.loadLabel(holder.itemView.getContext().getPackageManager()));
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

    }

    @Override
    protected void deselect(int position) {

    }

    @Override
    protected boolean isSelected(int position) {
        return false;
    }

    @Override
    protected int getSelectedItemCount() {
        return 0;
    }

    @Override
    protected int getTotalItemCount() {
        return 0;
    }

    public static class ViewHolder extends MultiSelectionView.ViewHolder {
        public final MaterialDivider removalIndicator;
        public final AppCompatImageView imageView;
        public final MaterialTextView listTypeView;
        public final MaterialTextView labelView;
        public final MaterialTextView packageNameView;
        public final MaterialTextView descriptionView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            removalIndicator = itemView.findViewById(R.id.divider);
            imageView = itemView.findViewById(R.id.icon);
            listTypeView = itemView.findViewById(R.id.list_type);
            labelView = itemView.findViewById(R.id.label);
            packageNameView = itemView.findViewById(R.id.package_name);
            descriptionView = itemView.findViewById(R.id.apk_description);
        }
    }
}
