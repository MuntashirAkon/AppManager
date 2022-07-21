// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.changelog;

import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;


public class ChangelogRecyclerAdapter extends RecyclerView.Adapter<ChangelogRecyclerAdapter.ViewHolder> {
    private final List<ChangelogItem> adapterList = new ArrayList<>();

    public ChangelogRecyclerAdapter() {
    }

    public void setAdapterList(@NonNull List<ChangelogItem> list) {
        synchronized (adapterList) {
            adapterList.clear();
            adapterList.addAll(list);
            notifyDataSetChanged();
        }
    }

    @ChangelogItem.ChangelogType
    @Override
    public int getItemViewType(int position) {
        synchronized (adapterList) {
            return adapterList.get(position).type;
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, @ChangelogItem.ChangelogType int viewType) {
        View v;
        if (viewType == ChangelogItem.HEADER) {
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_changelog_header, parent, false);
        } else {
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_changelog_item, parent, false);
        }
        return new ViewHolder(v, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChangelogItem changelogItem;
        synchronized (adapterList) {
            changelogItem = adapterList.get(position);
        }
        switch (changelogItem.type) {
            case ChangelogItem.HEADER:
                holder.title.setText(changelogItem.getChangeText());
                holder.subtitle.setText(((ChangelogHeader) changelogItem).getReleaseDate());
                break;
            case ChangelogItem.FIX:
                holder.tag.setVisibility(View.VISIBLE);
                holder.tag.setText(R.string.changelog_type_fix);
                holder.tag.setChipBackgroundColorResource(R.color.orange);
                holder.subtitle.setText(changelogItem.getChangeText());
                break;
            case ChangelogItem.IMPROVE:
                holder.tag.setVisibility(View.VISIBLE);
                holder.tag.setText(R.string.changelog_type_improve);
                holder.tag.setChipBackgroundColorResource(R.color.purple);
                holder.subtitle.setText(changelogItem.getChangeText());
                break;
            case ChangelogItem.NEW:
                holder.tag.setVisibility(View.VISIBLE);
                holder.tag.setText(R.string.changelog_type_new);
                holder.tag.setChipBackgroundColorResource(R.color.stopped);
                holder.subtitle.setText(changelogItem.getChangeText());
                break;
            default:
            case ChangelogItem.NOTE:
                holder.tag.setVisibility(View.GONE);
                holder.subtitle.setText(changelogItem.getChangeText());
                break;
        }
    }

    @Override
    public int getItemCount() {
        synchronized (adapterList) {
            return adapterList.size();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final Chip tag;
        public final TextView title;
        public final TextView subtitle;

        public ViewHolder(@NonNull View itemView, @ChangelogItem.ChangelogType int viewType) {
            super(itemView);
            tag = itemView.findViewById(R.id.item_tag);
            title = itemView.findViewById(R.id.item_title);
            subtitle = itemView.findViewById(R.id.item_subtitle);
            subtitle.setMovementMethod(LinkMovementMethod.getInstance());
            if (viewType == ChangelogItem.HEADER) {
                title.setMovementMethod(LinkMovementMethod.getInstance());
            }
        }
    }
}
