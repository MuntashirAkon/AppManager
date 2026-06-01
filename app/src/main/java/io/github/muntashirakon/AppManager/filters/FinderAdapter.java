// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.DiffUtil;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textview.MaterialTextView;

import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.widget.RecyclerView;

public class FinderAdapter extends RecyclerView.ListAdapter<FilterItem.FilteredItemInfo<FilterableAppInfo>, FinderAdapter.ViewHolder> {
    private static final DiffUtil.ItemCallback<FilterItem.FilteredItemInfo<FilterableAppInfo>> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<FilterItem.FilteredItemInfo<FilterableAppInfo>>() {
                @Override
                public boolean areItemsTheSame(@NonNull FilterItem.FilteredItemInfo<FilterableAppInfo> oldItem,
                                               @NonNull FilterItem.FilteredItemInfo<FilterableAppInfo> newItem) {
                    return Objects.equals(oldItem.info.getPackageName(), newItem.info.getPackageName());
                }

                @Override
                public boolean areContentsTheSame(@NonNull FilterItem.FilteredItemInfo<FilterableAppInfo> oldItem,
                                                  @NonNull FilterItem.FilteredItemInfo<FilterableAppInfo> newItem) {
                    return Objects.equals(oldItem.info.getAppLabel(), newItem.info.getAppLabel())
                            && Objects.equals(oldItem.info.getApplicationInfo(), newItem.info.getApplicationInfo());
                }
            };

    public FinderAdapter() {
        super(DIFF_CALLBACK);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_finder, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FilterItem.FilteredItemInfo<FilterableAppInfo> itemInfo = getItem(position);
        FilterableAppInfo appInfo = itemInfo.info;
        holder.icon.setImageDrawable(null);
        ImageLoader.getInstance().displayImage(appInfo.getPackageName(), appInfo.getApplicationInfo(), holder.icon);
        holder.label.setText(appInfo.getAppLabel());
        holder.pkg.setText(appInfo.getPackageName());
        // TODO: 8/2/24 Add highlighted extras
        holder.item1.setVisibility(View.GONE);
        holder.item2.setVisibility(View.GONE);
        holder.item3.setVisibility(View.GONE);
        holder.toggleBtn.setVisibility(View.GONE);
        holder.itemView.setStrokeColor(Color.TRANSPARENT);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public MaterialCardView itemView;
        public AppCompatImageView icon;
        public MaterialTextView label;
        public MaterialTextView pkg;
        public MaterialTextView item1;
        public MaterialTextView item2;
        public MaterialTextView item3;
        public MaterialSwitch toggleBtn;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = (MaterialCardView) itemView;
            icon = itemView.findViewById(R.id.icon);
            label = itemView.findViewById(R.id.label);
            pkg = itemView.findViewById(R.id.package_name);
            item1 = itemView.findViewById(R.id.item1);
            item2 = itemView.findViewById(R.id.item2);
            item3 = itemView.findViewById(R.id.item3);
            toggleBtn = itemView.findViewById(R.id.toggle_button);
        }
    }
}
