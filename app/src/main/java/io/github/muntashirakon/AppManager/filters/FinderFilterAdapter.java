// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.filters.options.FilterOption;
import io.github.muntashirakon.util.AdapterUtils;

// Copyright 2012 Nolan Lawson
public class FinderFilterAdapter extends RecyclerView.Adapter<FinderFilterAdapter.ViewHolder> {
    private OnClickListener mClickListener;
    @NonNull
    private final FilterItem mFilterItem;

    public void setOnItemClickListener(OnClickListener listener) {
        mClickListener = listener;
    }

    public FinderFilterAdapter(@NonNull FilterItem filterItem) {
        mFilterItem = filterItem;
    }

    public void add(@NonNull FilterOption filter) {
        int position = mFilterItem.addFilterOption(filter);
        if (position >= 0) {
            notifyItemInserted(position);
        }
    }

    public void update(int position, @NonNull FilterOption filter) {
        mFilterItem.updateFilterOptionAt(position, filter);
        notifyItemChanged(position, AdapterUtils.STUB);
    }

    public void remove(int position, int id) {
        FilterOption filterOption = mFilterItem.getFilterOptionAt(position);
        if (filterOption.id == id && mFilterItem.removeFilterOptionAt(position)) {
            notifyItemRemoved(position);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_icon_title_subtitle, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final FilterOption filterOption = mFilterItem.getFilterOptionAt(position);
        holder.titleView.setText(filterOption.getFullId());
        holder.subtitleView.setText(filterOption.toLocalizedString(holder.itemView.getContext()));
        holder.itemView.setOnClickListener(v -> {
            if (mClickListener != null) {
                mClickListener.onEdit(holder.itemView, holder.getAbsoluteAdapterPosition(), filterOption);
            }
        });
        holder.actionButton.setOnClickListener(v -> {
            if (mClickListener != null) {
                mClickListener.onRemove(holder.itemView, holder.getAbsoluteAdapterPosition(), filterOption);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mFilterItem.getSize();
    }

    public interface OnClickListener {
        void onEdit(View view, int position, FilterOption filterOption);
        void onRemove(View view, int position, FilterOption filterOption);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView titleView;
        TextView subtitleView;
        MaterialButton actionButton;

        public ViewHolder(View itemView) {
            super(itemView);
            itemView.findViewById(R.id.item_icon).setVisibility(View.GONE);
            titleView = itemView.findViewById(R.id.item_title);
            subtitleView = itemView.findViewById(R.id.item_subtitle);
            actionButton = itemView.findViewById(R.id.item_open);
            actionButton.setIcon(ContextCompat.getDrawable(itemView.getContext(), io.github.muntashirakon.ui.R.drawable.ic_clear));
            actionButton.setContentDescription(itemView.getContext().getString(R.string.item_remove));
        }
    }
}
