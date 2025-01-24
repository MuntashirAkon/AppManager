// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.filters.options.FilterOption;

// Copyright 2012 Nolan Lawson
public class FinderFilterAdapter extends RecyclerView.Adapter<FinderFilterAdapter.ViewHolder> {
    private OnClickListener mListener;
    @NonNull
    private final FilterItem mFilterItem;

    public void setOnItemClickListener(OnClickListener listener) {
        mListener = listener;
    }

    public FinderFilterAdapter(@NonNull FilterItem filterItem) {
        mFilterItem = filterItem;
    }

    public void add(@NonNull FilterOption filter) {
        if (mFilterItem.addFilterOption(filter)) {
            notifyItemInserted(mFilterItem.getSize() - 1);
        }
    }

    public void update(int position, @NonNull FilterOption filter) {
        mFilterItem.updateFilterOptionAt(position, filter);
        notifyItemChanged(position);
    }

    public void remove(int position) {
        if (mFilterItem.removeFilterOptionAt(position)) {
            notifyItemRemoved(position);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_title_action, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final FilterOption filterOption = mFilterItem.getFilterOptionAt(position);
        holder.textView.setText(filterOption.type + "_" + filterOption.id);
        // TODO: 14/2/24 Display a localised string
        holder.itemView.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onClick(holder.itemView, position, filterOption);
            }
        });
        holder.actionButton.setOnClickListener(v -> remove(position));
    }

    @Override
    public int getItemCount() {
        return mFilterItem.getSize();
    }

    public interface OnClickListener {
        void onClick(View view, int position, FilterOption filterOption);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        MaterialButton actionButton;

        public ViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.item_title);
            actionButton = itemView.findViewById(R.id.item_action);
        }
    }
}
