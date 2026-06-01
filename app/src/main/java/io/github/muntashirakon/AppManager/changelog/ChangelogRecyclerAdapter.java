// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.changelog;

import android.content.Context;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipDrawable;

import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.util.UiUtils;

public class ChangelogRecyclerAdapter extends ListAdapter<ChangelogItem, ChangelogRecyclerAdapter.ViewHolder> {
    private static final DiffUtil.ItemCallback<ChangelogItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<ChangelogItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull ChangelogItem oldItem, @NonNull ChangelogItem newItem) {
            // Check type, title, and text
            if (oldItem.type != newItem.type) {
                return false;
            }
            return Objects.equals(oldItem.getChangeText().toString(), newItem.getChangeText().toString())
                    && Objects.equals(oldItem.getChangeTitle(), newItem.getChangeTitle());
        }

        @Override
        public boolean areContentsTheSame(@NonNull ChangelogItem oldItem, @NonNull ChangelogItem newItem) {
            // Check structural modifications
            if (oldItem.isBulletedList() != newItem.isBulletedList()
                    || oldItem.isSubtext() != newItem.isSubtext()
                    || oldItem.getChangeTextType() != newItem.getChangeTextType()) {
                return false;
            }

            // Check for ChangelogHeader
            if (oldItem.type == ChangelogItem.HEADER && oldItem instanceof ChangelogHeader && newItem instanceof ChangelogHeader) {
                ChangelogHeader oldHeader = (ChangelogHeader) oldItem;
                ChangelogHeader newHeader = (ChangelogHeader) newItem;
                return Objects.equals(oldHeader.getReleaseType(), newHeader.getReleaseType())
                        && Objects.equals(oldHeader.getReleaseDate(), newHeader.getReleaseDate());
            }

            return true;
        }
    };

    public ChangelogRecyclerAdapter() {
        super(DIFF_CALLBACK);
    }

    @ChangelogItem.ChangelogType
    @Override
    public int getItemViewType(int position) {
        return getItem(position).type;
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
        ChangelogItem changelogItem = getItem(position);
        Context context = holder.itemView.getContext();
        switch (changelogItem.type) {
            case ChangelogItem.HEADER:
                holder.label.setText(((ChangelogHeader) changelogItem).getReleaseType());
                holder.title.setText(changelogItem.getChangeText());
                holder.subtitle.setText(((ChangelogHeader) changelogItem).getReleaseDate());
                break;
            default:
            case ChangelogItem.TITLE:
                TextViewCompat.setTextAppearance(holder.subtitle, getTitleTextAppearance(changelogItem.getChangeTextType()));
                holder.subtitle.setText(getChangeText(context, changelogItem));
                break;
            case ChangelogItem.FIX:
            case ChangelogItem.IMPROVE:
            case ChangelogItem.NEW:
            case ChangelogItem.NOTE:
                TextViewCompat.setTextAppearance(holder.subtitle, getChangeTextAppearance(changelogItem.getChangeTextType()));
                holder.subtitle.setText(getChangeText(context, changelogItem));
                break;
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
            @ColorInt
            int color;
            @ColorRes
            int backgroundColorRes;
            switch (item.type) {
                case ChangelogItem.FIX:
                    tagNameRes = R.string.changelog_type_fix;
                    backgroundColorRes = io.github.muntashirakon.ui.R.color.changelog_fix;
                    color = Color.BLACK;
                    break;
                case ChangelogItem.IMPROVE:
                    tagNameRes = R.string.changelog_type_improve;
                    backgroundColorRes = io.github.muntashirakon.ui.R.color.changelog_improve;
                    color = Color.WHITE;
                    break;
                case ChangelogItem.NEW:
                    tagNameRes = R.string.changelog_type_new;
                    backgroundColorRes = io.github.muntashirakon.ui.R.color.changelog_new;
                    color = Color.WHITE;
                    break;
                case ChangelogItem.HEADER:
                case ChangelogItem.TITLE:
                case ChangelogItem.NOTE:
                default:
                    tagNameRes = 0;
                    backgroundColorRes = 0;
                    color = 0;
                    break;
            }

            if (tagNameRes != 0) {
                ChipDrawable chip = ChipDrawable.createFromAttributes(context, null,
                        com.google.android.material.R.attr.chipStandaloneStyle,
                        com.google.android.material.R.style.Widget_Material3_Chip_Assist_Elevated);
                chip.setTextResource(tagNameRes);
                chip.setTextColor(color);
                chip.setTextSize(UiUtils.spToPx(context, 10));
                chip.setChipBackgroundColorResource(backgroundColorRes);
                chip.setCloseIconVisible(false);
                chip.setChipStartPadding(0);
                chip.setChipEndPadding(0);
                chip.setBounds(0, 0, chip.getIntrinsicWidth(), UiUtils.dpToPx(context, 20));
                ImageSpan span = new ImageSpan(chip);
                sb.append(" ");
                sb.setSpan(span, sb.length() - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append(" ");
            }
        }
        if (item.getChangeTitle() != null) {
            sb.append('[').append(item.getChangeTitle()).append("] ");
        }
        return sb.append(item.getChangeText());
    }

    @StyleRes
    public static int getChangeTextAppearance(@ChangelogItem.ChangeTextType int type) {
        switch (type) {
            default:
            case ChangelogItem.TEXT_MEDIUM:
                return com.google.android.material.R.style.TextAppearance_Material3_BodyMedium;
            case ChangelogItem.TEXT_LARGE:
                return com.google.android.material.R.style.TextAppearance_Material3_BodyLarge;
            case ChangelogItem.TEXT_SMALL:
                return com.google.android.material.R.style.TextAppearance_Material3_BodySmall;
        }
    }

    @StyleRes
    public static int getTitleTextAppearance(@ChangelogItem.ChangeTextType int type) {
        switch (type) {
            default:
            case ChangelogItem.TEXT_MEDIUM:
                return com.google.android.material.R.style.TextAppearance_Material3_TitleMedium;
            case ChangelogItem.TEXT_LARGE:
                return com.google.android.material.R.style.TextAppearance_Material3_TitleLarge;
            case ChangelogItem.TEXT_SMALL:
                return com.google.android.material.R.style.TextAppearance_Material3_TitleSmall;
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView label;
        public final TextView title;
        public final TextView subtitle;

        public ViewHolder(@NonNull View itemView, @ChangelogItem.ChangelogType int viewType) {
            super(itemView);
            label = itemView.findViewById(R.id.item_label);
            title = itemView.findViewById(R.id.item_title);
            subtitle = itemView.findViewById(R.id.item_subtitle);
            subtitle.setMovementMethod(LinkMovementMethod.getInstance());
            if (viewType == ChangelogItem.HEADER) {
                title.setMovementMethod(LinkMovementMethod.getInstance());
            }
        }
    }
}
