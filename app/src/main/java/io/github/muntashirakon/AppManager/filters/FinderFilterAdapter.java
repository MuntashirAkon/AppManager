// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.filters.options.FilterOption;

// Copyright 2012 Nolan Lawson
public class FinderFilterAdapter extends RecyclerView.Adapter<FinderFilterAdapter.ViewHolder> {
    private OnClickListener mListener;
    private final List<FilterOption> mItems;

    public void setOnItemClickListener(OnClickListener listener) {
        mListener = listener;
    }

    public FinderFilterAdapter(@NonNull FilterItem filterItem) {
        mItems = filterItem.getOptions();
    }

    public void add(@NonNull FilterOption filter) {
        mItems.add(filter);
        notifyItemInserted(mItems.size() - 1);
    }

    public void update(int position, @NonNull FilterOption filter) {
        mItems.set(position, filter);
        notifyItemChanged(position);
    }

    public void remove(int position) {
        mItems.remove(position);
        notifyItemRemoved(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_title_action, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final FilterOption filterOption = mItems.get(position);
        holder.textView.setText(filterOption.type); // TODO: 14/2/24 Display a localised string
        holder.itemView.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onClick(holder.itemView, position, filterOption);
            }
        });
        holder.actionButton.setOnClickListener(v -> remove(position));
    }

    @Override
    public int getItemCount() {
        return mItems.size();
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
