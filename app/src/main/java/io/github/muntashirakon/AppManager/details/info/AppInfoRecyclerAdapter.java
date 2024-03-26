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
import com.google.android.material.divider.MaterialDivider;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.util.AdapterUtils;

import static io.github.muntashirakon.AppManager.details.info.ListItem.LIST_ITEM_GROUP_BEGIN;
import static io.github.muntashirakon.AppManager.details.info.ListItem.LIST_ITEM_INLINE;
import static io.github.muntashirakon.AppManager.details.info.ListItem.LIST_ITEM_REGULAR;
import static io.github.muntashirakon.AppManager.details.info.ListItem.LIST_ITEM_REGULAR_ACTION;

class AppInfoRecyclerAdapter extends RecyclerView.Adapter<AppInfoRecyclerAdapter.ViewHolder> {
    private final Context mContext;
    private final List<ListItem> mAdapterList;

    AppInfoRecyclerAdapter(Context context) {
        mContext = context;
        mAdapterList = new ArrayList<>();
    }

    void setAdapterList(@NonNull List<ListItem> list) {
        AdapterUtils.notifyDataSetChanged(this, mAdapterList, list);
    }

    @Override
    @ListItem.ListItemType
    public int getItemViewType(int position) {
        return mAdapterList.get(position).type;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, @ListItem.ListItemType int viewType) {
        final View view;
        switch (viewType) {
            case LIST_ITEM_GROUP_BEGIN:
                view = LayoutInflater.from(parent.getContext()).inflate(io.github.muntashirakon.ui.R.layout.m3_preference_category, parent, false);
                break;
            default:
            case LIST_ITEM_REGULAR:
                view = LayoutInflater.from(parent.getContext()).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false);
                break;
            case LIST_ITEM_REGULAR_ACTION: {
                view = LayoutInflater.from(parent.getContext()).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false);
                View action = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_right_standalone_action, parent, false);
                LinearLayoutCompat layoutCompat = view.findViewById(android.R.id.widget_frame);
                layoutCompat.addView(action);
                break;
            }
            case LIST_ITEM_INLINE: {
                view = LayoutInflater.from(parent.getContext()).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false);
                View action = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_right_summary, parent, false);
                LinearLayoutCompat layoutCompat = view.findViewById(android.R.id.widget_frame);
                layoutCompat.addView(action);
                break;
            }
        }
        return new ViewHolder(view, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ListItem listItem = mAdapterList.get(position);
        // Set title
        holder.title.setText(listItem.getTitle());
        if (listItem.type == LIST_ITEM_GROUP_BEGIN) {
            return;
        }
        // Set common properties
        holder.subtitle.setText(listItem.getSubtitle());
        holder.subtitle.setTextIsSelectable(listItem.isSelectable());
        holder.subtitle.setTypeface(listItem.isMonospace() ? Typeface.MONOSPACE : Typeface.DEFAULT);
        if (listItem.type == LIST_ITEM_INLINE) {
            return;
        }
        if (listItem.type == LIST_ITEM_REGULAR_ACTION) {
            holder.actionDivider.setVisibility(listItem.getOnActionClickListener() != null ? View.VISIBLE : View.GONE);
            if (listItem.getActionIconRes() != 0) {
                holder.actionIcon.setIconResource(listItem.getActionIconRes());
            }
            if (listItem.getActionContentDescription() != null) {
                holder.actionIcon.setContentDescription(listItem.getActionContentDescription());
            } else if (listItem.getActionContentDescriptionRes() != 0) {
                holder.actionIcon.setContentDescription(mContext.getString(listItem.getActionContentDescriptionRes()));
            }
            if (listItem.getOnActionClickListener() != null) {
                holder.actionIcon.setVisibility(View.VISIBLE);
                holder.actionIcon.setOnClickListener(listItem.getOnActionClickListener());
            } else holder.actionIcon.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return mAdapterList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView subtitle;
        MaterialButton actionIcon;
        MaterialDivider actionDivider;

        public ViewHolder(@NonNull View itemView, @ListItem.ListItemType int viewType) {
            super(itemView);
            itemView.findViewById(R.id.icon_frame).setVisibility(View.GONE);
            switch (viewType) {
                case LIST_ITEM_GROUP_BEGIN: {
                    title = itemView.findViewById(android.R.id.title);
                    itemView.findViewById(android.R.id.summary).setVisibility(View.GONE);
                    break;
                }
                case LIST_ITEM_REGULAR:
                case LIST_ITEM_REGULAR_ACTION:
                    title = itemView.findViewById(android.R.id.title);
                    subtitle = itemView.findViewById(android.R.id.summary);
                    actionDivider = itemView.findViewById(R.id.divider);
                    actionIcon = itemView.findViewById(android.R.id.button1);
                    break;
                default:
                    break;
                case LIST_ITEM_INLINE:
                    title = itemView.findViewById(android.R.id.title);
                    subtitle = itemView.findViewById(android.R.id.text1);
                    itemView.findViewById(android.R.id.summary).setVisibility(View.GONE);
                    break;
            }
        }
    }
}