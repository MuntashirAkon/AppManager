// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.explorer;

import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatTextView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.fm.FileType;
import io.github.muntashirakon.widget.MultiSelectionView;

public class AppExplorerAdapter extends MultiSelectionView.Adapter<AppExplorerAdapter.ViewHolder> {
    private final List<AdapterItem> adapterList = new ArrayList<>();
    private final AppExplorerActivity activity;

    public AppExplorerAdapter(AppExplorerActivity activity) {
        this.activity = activity;
    }

    public void setFmList(List<AdapterItem> list) {
        adapterList.clear();
        adapterList.addAll(list);
        notifySelectionChange();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AdapterItem item = adapterList.get(position);
        holder.title.setText(item.name);
        // Set icon
        if (item.type == FileType.DIRECTORY) {
            holder.icon.setImageResource(R.drawable.ic_folder_white_24dp);
            holder.itemView.setOnClickListener(v -> activity.loadNewFragment(
                    AppExplorerFragment.getNewInstance(item.fullName, item.depth + 1)));
        } else {
            holder.icon.setImageResource(R.drawable.ic_insert_drive_file_white_24dp);
            holder.subtitle.setText(Formatter.formatFileSize(activity, item.zipEntry.getSize()));
            holder.itemView.setOnClickListener(v -> {
                // TODO: 9/10/21
            });
        }
        // Set background colors
        holder.itemView.setBackgroundResource(position % 2 == 0 ? R.drawable.item_semi_transparent : R.drawable.item_transparent);
        // Set selections
        holder.icon.setOnClickListener(v -> toggleSelection(position));
        super.onBindViewHolder(holder, position);
    }

    @Override
    public long getItemId(int position) {
        return adapterList.get(position).hashCode();
    }

    @Override
    public int getItemCount() {
        return adapterList.size();
    }

    @Override
    protected void select(int position) {
        // TODO: 4/7/21
    }

    @Override
    protected void deselect(int position) {
        // TODO: 4/7/21
    }

    @Override
    protected boolean isSelected(int position) {
        // TODO: 4/7/21
        return false;
    }

    @Override
    protected void cancelSelection() {
        // TODO: 4/7/21
    }

    @Override
    protected int getSelectedItemCount() {
        // TODO: 4/7/21
        return 0;
    }

    @Override
    protected int getTotalItemCount() {
        return adapterList.size();
    }

    protected static class ViewHolder extends MultiSelectionView.ViewHolder {
        final AppCompatImageView icon;
        final MaterialButton action;
        final AppCompatTextView title;
        final AppCompatTextView subtitle;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.item_icon);
            action = itemView.findViewById(R.id.item_open);
            title = itemView.findViewById(R.id.item_title);
            subtitle = itemView.findViewById(R.id.item_subtitle);
            action.setIconResource(R.drawable.ic_more_vert_black_24dp);
        }
    }
}
