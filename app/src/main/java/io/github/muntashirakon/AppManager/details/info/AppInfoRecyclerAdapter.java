// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.UIUtils;

import static io.github.muntashirakon.AppManager.details.info.ListItem.LIST_ITEM_FLAG_MONOSPACE;
import static io.github.muntashirakon.AppManager.details.info.ListItem.LIST_ITEM_FLAG_SELECTABLE;
import static io.github.muntashirakon.AppManager.details.info.ListItem.LIST_ITEM_GROUP_BEGIN;
import static io.github.muntashirakon.AppManager.details.info.ListItem.LIST_ITEM_GROUP_END;
import static io.github.muntashirakon.AppManager.details.info.ListItem.LIST_ITEM_INLINE;
import static io.github.muntashirakon.AppManager.details.info.ListItem.LIST_ITEM_REGULAR;

class AppInfoRecyclerAdapter extends RecyclerView.Adapter<AppInfoRecyclerAdapter.ViewHolder> {
    private final List<ListItem> adapterList;
    private final int accentColor;
    private final int paddingMedium;
    private final int paddingSmall;
    private final int paddingVerySmall;

    AppInfoRecyclerAdapter(Context context) {
        adapterList = new ArrayList<>();
        accentColor = UIUtils.getAccentColor(context);
        paddingVerySmall = context.getResources().getDimensionPixelOffset(R.dimen.padding_very_small);
        paddingSmall = context.getResources().getDimensionPixelOffset(R.dimen.padding_small);
        paddingMedium = context.getResources().getDimensionPixelOffset(R.dimen.padding_medium);
    }

    void setAdapterList(@NonNull List<ListItem> list) {
        adapterList.clear();
        adapterList.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    @ListItem.ListItemType
    public int getItemViewType(int position) {
        return adapterList.get(position).type;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, @ListItem.ListItemType int viewType) {
        final View view;
        switch (viewType) {
            case LIST_ITEM_GROUP_BEGIN:
            case LIST_ITEM_REGULAR:
            default:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_icon_title_subtitle, parent, false);
                break;
            case LIST_ITEM_GROUP_END:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_divider_horizontal, parent, false);
                break;
            case LIST_ITEM_INLINE:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_title_subtitle_inline, parent, false);
                break;
        }
        return new ViewHolder(view, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ListItem listItem = adapterList.get(position);
        holder.itemView.setClickable(false);
        holder.itemView.setFocusable(false);
        switch (listItem.type) {
            case LIST_ITEM_GROUP_BEGIN:
                holder.title.setText(listItem.title);
                holder.title.setTextColor(accentColor);
                LinearLayoutCompat item_layout = holder.itemView.findViewById(R.id.item_layout);
                item_layout.setPadding(paddingMedium, paddingSmall, paddingMedium, paddingVerySmall);
                break;
            case LIST_ITEM_GROUP_END:
                break;
            case LIST_ITEM_INLINE:
                holder.title.setText(listItem.title);
                holder.subtitle.setText(listItem.subtitle);
                holder.subtitle.setTextIsSelectable((listItem.flags & LIST_ITEM_FLAG_SELECTABLE) != 0);
                if ((listItem.flags & LIST_ITEM_FLAG_MONOSPACE) != 0)
                    holder.subtitle.setTypeface(Typeface.MONOSPACE);
                else holder.subtitle.setTypeface(Typeface.DEFAULT);
                break;
            case LIST_ITEM_REGULAR:
                holder.title.setText(listItem.title);
                holder.subtitle.setText(listItem.subtitle);
                holder.subtitle.setTextIsSelectable((listItem.flags & LIST_ITEM_FLAG_SELECTABLE) != 0);
                if ((listItem.flags & LIST_ITEM_FLAG_MONOSPACE) != 0)
                    holder.subtitle.setTypeface(Typeface.MONOSPACE);
                else holder.subtitle.setTypeface(Typeface.DEFAULT);
                if (listItem.actionIcon != 0) {
                    holder.actionIcon.setIconResource(listItem.actionIcon);
                }
                if (listItem.actionListener != null) {
                    holder.actionIcon.setVisibility(View.VISIBLE);
                    holder.actionIcon.setOnClickListener(listItem.actionListener);
                } else holder.actionIcon.setVisibility(View.GONE);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return adapterList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView subtitle;
        MaterialButton actionIcon;

        public ViewHolder(@NonNull View itemView, @ListItem.ListItemType int viewType) {
            super(itemView);
            if (viewType != LIST_ITEM_GROUP_END) {
                itemView.findViewById(R.id.item_icon).setVisibility(View.GONE);
            }
            switch (viewType) {
                case LIST_ITEM_GROUP_BEGIN:
                    title = itemView.findViewById(R.id.item_title);
                    itemView.findViewById(R.id.item_subtitle).setVisibility(View.GONE);
                    itemView.findViewById(R.id.item_open).setVisibility(View.GONE);
                    break;
                case LIST_ITEM_REGULAR:
                    title = itemView.findViewById(R.id.item_title);
                    subtitle = itemView.findViewById(R.id.item_subtitle);
                    actionIcon = itemView.findViewById(R.id.item_open);
                    break;
                case LIST_ITEM_GROUP_END:
                default:
                    break;
                case LIST_ITEM_INLINE:
                    title = itemView.findViewById(R.id.item_title);
                    subtitle = itemView.findViewById(R.id.item_subtitle);
                    break;
            }
        }
    }
}