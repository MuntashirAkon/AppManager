// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.content.Intent;
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

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.widget.MultiSelectionView;

public class FmAdapter extends MultiSelectionView.Adapter<FmAdapter.ViewHolder> {
    private final List<FmItem> adapterList = new ArrayList<>();
    private final FmActivity fmActivity;

    public FmAdapter(FmActivity activity) {
        this.fmActivity = activity;
    }

    public void setFmList(List<FmItem> list) {
        adapterList.clear();
        adapterList.addAll(list);
        notifySelectionChange();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_icon_title_subtitle, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FmItem item = adapterList.get(position);
        holder.title.setText(item.name);
        holder.subtitle.setText(Formatter.formatFileSize(fmActivity, item.path.length()));
        // Set icon
        if (item.type == FileType.DIRECTORY) {
            holder.icon.setImageResource(R.drawable.ic_folder_white_24dp);
            holder.itemView.setOnClickListener(v -> fmActivity.model.loadFiles(item.path));
        } else {
            holder.icon.setImageResource(R.drawable.ic_insert_drive_file_white_24dp);
            holder.itemView.setOnClickListener(v -> {
                if (ApkFile.SUPPORTED_EXTENSIONS.contains(item.extension)) {
                    Intent intent = new Intent(AppManager.getContext(), AppDetailsActivity.class);
                    intent.setData(item.path.getUri());
                    fmActivity.startActivity(intent);
                } else {
                    Intent openFile = new Intent(Intent.ACTION_VIEW);
                    openFile.setDataAndType(FmProvider.getContentUri(item.path), item.path.getType());
                    openFile.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                    if (openFile.resolveActivityInfo(fmActivity.getPackageManager(), 0) != null) {
                        fmActivity.startActivity(openFile);
                    }
                }
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
