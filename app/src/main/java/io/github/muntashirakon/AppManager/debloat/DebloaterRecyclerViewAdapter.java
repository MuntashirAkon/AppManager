// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getColoredText;
import static io.github.muntashirakon.util.AdapterUtils.PAYLOAD_HIGHLIGHT_CHANGED;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.util.AccessibilityUtils;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.widget.MultiSelectionView;

public class DebloaterRecyclerViewAdapter extends MultiSelectionView.Adapter<DebloatObject, DebloaterRecyclerViewAdapter.ViewHolder> {
    private final FragmentActivity mActivity;
    @ColorInt
    private final int mRemovalSafeColor;
    @ColorInt
    private final int mRemovalReplaceColor;
    @ColorInt
    private final int mRemovalUnsafeColor;
    @ColorInt
    private final int mRemovalCautionColor;
    @ColorInt
    private final int mColorSurface;
    private final int mQueryStringHighlightColor;
    @NonNull
    private final DebloaterViewModel mViewModel;
    @NonNull
    private final Drawable mDefaultIcon;
    @Nullable
    private String mSearchQuery;

    private static final DiffUtil.ItemCallback<DebloatObject> DIFF_CALLBACK = new DiffUtil.ItemCallback<DebloatObject>() {
        @Override
        public boolean areItemsTheSame(@NonNull DebloatObject oldItem, @NonNull DebloatObject newItem) {
            return oldItem.getId() == newItem.getId()
                    && Objects.equals(oldItem.packageName, newItem.packageName);
        }

        @Override
        public boolean areContentsTheSame(@NonNull DebloatObject oldItem, @NonNull DebloatObject newItem) {
            return oldItem.getRemoval() == newItem.getRemoval()
                    && Objects.equals(oldItem.type, newItem.type)
                    && Objects.equals(oldItem.getWarning(), newItem.getWarning())
                    && Objects.equals(oldItem.getLabelOrPackageName(), newItem.getLabelOrPackageName());
        }
    };

    public DebloaterRecyclerViewAdapter(DebloaterActivity activity) {
        super(DIFF_CALLBACK);
        mActivity = activity;
        mRemovalSafeColor = ColorCodes.getRemovalSafeIndicatorColor(activity);
        mRemovalReplaceColor = ColorCodes.getRemovalReplaceIndicatorColor(activity);
        mRemovalCautionColor = ColorCodes.getRemovalCautionIndicatorColor(activity);
        mRemovalUnsafeColor = ColorCodes.getRemovalUnsafeIndicatorColor(activity);
        mColorSurface = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorSurface,
                DebloaterRecyclerViewAdapter.class.getCanonicalName());
        mQueryStringHighlightColor = ColorCodes.getQueryStringHighlightColor(activity);
        mViewModel = activity.viewModel;
        mDefaultIcon = activity.getPackageManager().getDefaultActivityIcon();
    }

    public void setDefaultList(List<DebloatObject> debloatObjects) {
        String oldSearchQuery = mSearchQuery;
        mSearchQuery = mViewModel.getQueryString();
        if (mSearchQuery != null) {
            mSearchQuery = mSearchQuery.toLowerCase(Locale.ROOT);
        }
        submitListWithScrollState(
                new ArrayList<>(debloatObjects),
                AdapterUtils.isStartingSearch(oldSearchQuery, mSearchQuery),
                AdapterUtils.isClearingSearch(oldSearchQuery, mSearchQuery)
        );
        if (!Objects.equals(oldSearchQuery, mSearchQuery)) {
            notifyItemRangeChanged(0, getItemCount(), PAYLOAD_HIGHLIGHT_CHANGED);
        }
        notifySelectionChange();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_debloater, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            for (Object payload : payloads) {
                if (Objects.equals(payload, PAYLOAD_HIGHLIGHT_CHANGED)) {
                    updateTextHighlights(holder, getItem(position));
                }
            }
        }
        // Handle other stuff
        super.onBindViewHolder(holder, position, payloads);
    }

    private void updateTextHighlights(@NonNull ViewHolder holder, @NonNull DebloatObject debloatObject) {
        holder.labelView.setText(UIUtils.getHighlightedText(debloatObject.getLabelOrPackageName().toString(), mSearchQuery, mQueryStringHighlightColor));
        holder.packageNameView.setText(UIUtils.getHighlightedText(debloatObject.packageName, mSearchQuery, mQueryStringHighlightColor));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DebloatObject debloatObject = getItem(position);
        Context context = holder.itemView.getContext();
        Drawable icon = debloatObject.getIcon() != null ? debloatObject.getIcon() : mDefaultIcon;
        String warning = debloatObject.getWarning();
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int removalColor;
        @StringRes
        int removalRes;
        switch (debloatObject.getRemoval()) {
            case DebloatObject.REMOVAL_SAFE:
                removalColor = mRemovalSafeColor;
                removalRes = R.string.debloat_removal_safe_short_description;
                break;
            default:
            case DebloatObject.REMOVAL_CAUTION:
                removalColor = mRemovalCautionColor;
                removalRes = R.string.debloat_removal_caution_short_description;
                break;
            case DebloatObject.REMOVAL_REPLACE:
                removalColor = mRemovalReplaceColor;
                removalRes = R.string.debloat_removal_replace_short_description;
                break;
            case DebloatObject.REMOVAL_UNSAFE:
                removalColor = mRemovalUnsafeColor;
                removalRes = R.string.debloat_removal_unsafe;
                break;
        }
        sb.append(getColoredText(context.getString(removalRes), removalColor));
        if (!TextUtils.isEmpty(warning)) {
            sb.append(" — ").append(warning);
        }
        holder.iconView.setImageDrawable(icon);
        holder.listTypeView.setText(debloatObject.type);
        holder.labelView.setText(UIUtils.getHighlightedText(debloatObject.getLabelOrPackageName().toString(), mSearchQuery, mQueryStringHighlightColor));
        holder.packageNameView.setText(UIUtils.getHighlightedText(debloatObject.packageName, mSearchQuery, mQueryStringHighlightColor));
        holder.descriptionView.setText(sb);
        holder.itemView.setStrokeColor(removalColor);
        holder.itemView.setOnLongClickListener(v -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                toggleSelection(currentPos);
                AccessibilityUtils.requestAccessibilityFocus(holder.itemView);
            }
            return true;
        });

        holder.iconView.setOnClickListener(v -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                toggleSelection(currentPos);
                AccessibilityUtils.requestAccessibilityFocus(holder.itemView);
            }
        });
        holder.itemView.setOnClickListener(v -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION) return;
            if (isInSelectionMode()) {
                toggleSelection(currentPos);
                AccessibilityUtils.requestAccessibilityFocus(holder.itemView);
            } else {
                BloatwareDetailsDialog dialog = BloatwareDetailsDialog.getInstance(debloatObject.packageName);
                dialog.show(mActivity.getSupportFragmentManager(), BloatwareDetailsDialog.TAG);
            }
        });
        super.onBindViewHolder(holder, position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getId();
    }

    @Override
    protected boolean select(int position) {
        mViewModel.select(getItem(position));
        return true;
    }

    @Override
    protected boolean deselect(int position) {
        mViewModel.deselect(getItem(position));
        return true;
    }

    @Override
    protected void cancelSelection() {
        super.cancelSelection();
        mViewModel.deselectAll();
    }

    @Override
    protected boolean isSelected(int position) {
        return mViewModel.isSelected(getItem(position));
    }

    @Override
    protected int getSelectedItemCount() {
        return mViewModel.getSelectedItemCount();
    }

    @Override
    protected int getTotalItemCount() {
        return mViewModel.getTotalItemCount();
    }

    public static class ViewHolder extends MultiSelectionView.ViewHolder {
        public final MaterialCardView itemView;
        public final AppCompatImageView iconView;
        public final MaterialTextView listTypeView;
        public final MaterialTextView labelView;
        public final MaterialTextView packageNameView;
        public final MaterialTextView descriptionView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = (MaterialCardView) itemView;
            iconView = itemView.findViewById(R.id.icon);
            listTypeView = itemView.findViewById(R.id.list_type);
            labelView = itemView.findViewById(R.id.label);
            packageNameView = itemView.findViewById(R.id.package_name);
            descriptionView = itemView.findViewById(R.id.apk_description);
        }
    }
}
