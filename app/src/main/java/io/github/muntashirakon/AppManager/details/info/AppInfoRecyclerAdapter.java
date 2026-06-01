// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.content.Context;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.divider.MaterialDivider;

import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.util.UiUtils;

import static io.github.muntashirakon.AppManager.details.info.ListItem.LIST_ITEM_GROUP_BEGIN;
import static io.github.muntashirakon.AppManager.details.info.ListItem.LIST_ITEM_INLINE;
import static io.github.muntashirakon.AppManager.details.info.ListItem.LIST_ITEM_REGULAR;
import static io.github.muntashirakon.AppManager.details.info.ListItem.LIST_ITEM_REGULAR_ACTION;

class AppInfoRecyclerAdapter extends ListAdapter<ListItem, AppInfoRecyclerAdapter.ViewHolder> {
    private final Context mContext;
    private final float mCornerRadius;
    private final float mCornerRadiusInner;

    private static final DiffUtil.ItemCallback<ListItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<ListItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull ListItem oldItem, @NonNull ListItem newItem) {
            // Check only type and title
            if (oldItem.type != newItem.type) {
                return false;
            }
            return Objects.equals(oldItem.getTitle(), newItem.getTitle());
        }

        @Override
        public boolean areContentsTheSame(@NonNull ListItem oldItem, @NonNull ListItem newItem) {
            // Check text content changes
            if (!Objects.equals(oldItem.getSubtitle(), newItem.getSubtitle())) {
                return false;
            }

            // Check text formatting changes
            if (oldItem.isSelectable() != newItem.isSelectable() || oldItem.isMonospace() != newItem.isMonospace()) {
                return false;
            }

            // Check Action/Button changes (Only applicable for LIST_ITEM_REGULAR_ACTION)
            if (oldItem.type == ListItem.LIST_ITEM_REGULAR_ACTION) {
                if (oldItem.getActionIconRes() != newItem.getActionIconRes()) {
                    return false;
                }
                if (!Objects.equals(oldItem.getActionContentDescription(), newItem.getActionContentDescription())) {
                    return false;
                }
                if (oldItem.getActionContentDescriptionRes() != newItem.getActionContentDescriptionRes()) {
                    return false;
                }
            }

            return true;
        }
    };

    AppInfoRecyclerAdapter(Context context) {
        super(DIFF_CALLBACK);
        mContext = context;
        // Use M3 design for preference cards
        DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
        TypedValue typedValue = new TypedValue();
        if (mContext.getTheme().resolveAttribute(io.github.muntashirakon.ui.R.attr.listItemCornerRadius, typedValue, true)) {
            mCornerRadius = typedValue.getDimension(displayMetrics);
        } else {
            throw new RuntimeException("?attr/listItemCornerRadius not defined.");
        }
        if (mContext.getTheme().resolveAttribute(io.github.muntashirakon.ui.R.attr.listItemCornerRadiusInner, typedValue, true)) {
            mCornerRadiusInner = typedValue.getDimension(displayMetrics);
        } else {
            throw new RuntimeException("?attr/listItemCornerRadiusInner not defined.");
        }
    }

    @Override
    @ListItem.ListItemType
    public int getItemViewType(int position) {
        return getItem(position).type;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, @ListItem.ListItemType int viewType) {
        final View view;
        switch (viewType) {
            case LIST_ITEM_GROUP_BEGIN:
                view = LayoutInflater.from(parent.getContext()).inflate(io.github.muntashirakon.ui.R.layout.m3_preference_category, parent, false);
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
            case LIST_ITEM_REGULAR:
            default:
                view = LayoutInflater.from(parent.getContext()).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false);
                break;
        }
        return new ViewHolder(view, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ListItem listItem = getItem(position);

        // Set title
        holder.title.setText(listItem.getTitle());
        if (listItem.type == LIST_ITEM_GROUP_BEGIN) {
            return;
        }
        // At this point, itemView is a MaterialCardView
        MaterialCardView cardView = (MaterialCardView) holder.itemView;
        boolean isFirst = isFirstInGroup(position);
        boolean isLast = isLastInGroup(position);

        if (isFirst && isLast) {
            // Standalone preference
            UiUtils.setCardRadius(cardView, mCornerRadius, mCornerRadius);
        } else if (isFirst) {
            // Top of a preference group
            UiUtils.setCardRadius(cardView, mCornerRadius, mCornerRadiusInner);
        } else if (isLast) {
            // Bottom of a preference group
            UiUtils.setCardRadius(cardView, mCornerRadiusInner, mCornerRadius);
        } else {
            // Middle of a preference group
            UiUtils.setCardRadius(cardView, mCornerRadiusInner, mCornerRadiusInner);
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

    private boolean isCategoryHeader(int position) {
        return getItem(position).type == LIST_ITEM_GROUP_BEGIN;
    }

    private boolean isFirstInGroup(int position) {
        if (position == 0) return true;

        // If the element right above this choice is a section separator, this is the top card.
        return isCategoryHeader(position - 1);
    }

    private boolean isLastInGroup(int position) {
        if (position == getCurrentList().size() - 1) return true;

        // If the element right below this card is a header separator, this is the bottom card.
        return isCategoryHeader(position + 1);
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
                case LIST_ITEM_INLINE:
                    title = itemView.findViewById(android.R.id.title);
                    subtitle = itemView.findViewById(android.R.id.text1);
                    itemView.findViewById(android.R.id.summary).setVisibility(View.GONE);
                    break;
                default:
                    break;
            }
        }
    }
}