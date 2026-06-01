// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.LogFilter;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

// Copyright 2012 Nolan Lawson
// Copyright 2026 Muntashir Al-Islam
public class LogFilterAdapter extends ListAdapter<LogFilter, LogFilterAdapter.ViewHolder> {
    private OnClickListener mListener;
    private static final DiffUtil.ItemCallback<LogFilter> DIFF_CALLBACK = new DiffUtil.ItemCallback<LogFilter>() {
        @Override
        public boolean areItemsTheSame(@NonNull LogFilter oldItem, @NonNull LogFilter newItem) {
            return Objects.equals(oldItem.id, newItem.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull LogFilter oldItem, @NonNull LogFilter newItem) {
            return Objects.equals(oldItem.name, newItem.name);
        }
    };

    public void setOnItemClickListener(OnClickListener listener) {
        mListener = listener;
    }

    public LogFilterAdapter() {
        super(DIFF_CALLBACK);
    }

    public void add(@NonNull LogFilter filter) {
        List<LogFilter> currentList = new ArrayList<>(getCurrentList());
        currentList.add(filter);
        Collections.sort(currentList, LogFilter.COMPARATOR);
        submitList(currentList);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_title_action, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final LogFilter logFilter = getItem(position);
        holder.textView.setText(logFilter.name);
        holder.itemView.setOnClickListener(v -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (mListener != null && currentPos != RecyclerView.NO_POSITION) {
                mListener.onClick(holder.itemView, currentPos, logFilter);
            }
        });
        holder.actionButton.setOnClickListener(v -> {
            ThreadUtils.postOnBackgroundThread(() -> AppsDb.getInstance().logFilterDao().delete(logFilter));
            List<LogFilter> updatedList = new ArrayList<>(getCurrentList());
            updatedList.remove(logFilter);
            submitList(updatedList);
        });
    }

    public interface OnClickListener {
        void onClick(View view, int position, LogFilter logFilter);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        MaterialButton actionButton;

        public ViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.item_title);
            actionButton = itemView.findViewById(R.id.item_action);
            actionButton.setContentDescription(itemView.getContext().getString(R.string.item_remove));
        }
    }
}
