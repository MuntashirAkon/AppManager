// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.changelog;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipDrawable;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.util.UiUtils;


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
        Context context = holder.itemView.getContext();
        switch (changelogItem.type) {
            case ChangelogItem.HEADER:
                holder.title.setText(changelogItem.getChangeText());
                holder.subtitle.setText(((ChangelogHeader) changelogItem).getReleaseDate());
                break;
            default:
            case ChangelogItem.FIX:
            case ChangelogItem.IMPROVE:
            case ChangelogItem.NEW:
            case ChangelogItem.NOTE:
                holder.subtitle.setText(getChangeText(context, changelogItem));
                break;
        }
    }

    @Override
    public int getItemCount() {
        synchronized (adapterList) {
            return adapterList.size();
        }
    }

    @NonNull
    private CharSequence getChangeText(@NonNull Context context, @NonNull ChangelogItem item) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (item.isBulletedList()) {
            if (item.isSubtext()) {
                sb.append("    ");
            }
            sb.append("• ");
        } else {
            // Display tag
            @StringRes
            int tagNameRes;
            @ColorRes
            int colorRes;
            switch (item.type) {
                case ChangelogItem.FIX:
                    tagNameRes = R.string.changelog_type_fix;
                    colorRes = R.color.orange;
                    break;
                case ChangelogItem.IMPROVE:
                    tagNameRes = R.string.changelog_type_improve;
                    colorRes = R.color.purple;
                    break;
                case ChangelogItem.NEW:
                    tagNameRes = R.string.changelog_type_new;
                    colorRes = R.color.stopped;
                    break;
                default:
                case ChangelogItem.HEADER:
                case ChangelogItem.NOTE:
                    tagNameRes = 0;
                    colorRes = 0;
                    break;
            }

            if (tagNameRes != 0) {
                ChipDrawable chip = ChipDrawable.createFromAttributes(context, null, R.attr.chipStandaloneStyle, R.style.Widget_AppTheme_Chip_Assist_Elevated);
                chip.setTextResource(tagNameRes);
                chip.setTextColor(MaterialColors.getColor(context, R.attr.colorSurface, "LinearLayoutCompat"));
                chip.setTextSize(UiUtils.spToPx(context, 10));
                chip.setChipBackgroundColorResource(colorRes);
                chip.setCloseIconVisible(false);
                chip.setChipStartPadding(0);
                chip.setChipEndPadding(0);
                chip.setBounds(0, 0, chip.getIntrinsicWidth(), UiUtils.dpToPx(context, 14));
                ImageSpan span = new ImageSpan(chip);
                sb.append(" ");
                sb.setSpan(span, sb.length() - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append(" ");
            }
        }
        if (item.getChangeTitle() != null) {
            sb.append('[').append(item.getChangeTitle()).append("] ");
        }
        sb.append(item.getChangeText());
        return item.isSubtext() ? UIUtils.getSmallerText(sb) : sb;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView title;
        public final TextView subtitle;

        public ViewHolder(@NonNull View itemView, @ChangelogItem.ChangelogType int viewType) {
            super(itemView);
            title = itemView.findViewById(R.id.item_title);
            subtitle = itemView.findViewById(R.id.item_subtitle);
            subtitle.setMovementMethod(LinkMovementMethod.getInstance());
            if (viewType == ChangelogItem.HEADER) {
                title.setMovementMethod(LinkMovementMethod.getInstance());
            }
        }
    }
}
