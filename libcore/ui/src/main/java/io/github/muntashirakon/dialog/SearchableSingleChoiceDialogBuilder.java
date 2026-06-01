// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.FrameLayout;

import androidx.annotation.ArrayRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.resources.MaterialAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.ui.R;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.widget.RecyclerView;
import io.github.muntashirakon.widget.SearchView;

public class SearchableSingleChoiceDialogBuilder<T> {
    @NonNull
    private final MaterialAlertDialogBuilder mBuilder;
    private final SearchView mSearchView;
    private final RecyclerView mRecyclerView;
    private final FrameLayout mViewContainer;
    @NonNull
    private final SearchableRecyclerViewAdapter mAdapter;
    @Nullable
    private AlertDialog mDialog;
    @Nullable
    private OnSingleChoiceClickListener<T> mOnSingleChoiceClickListener;
    private boolean mIsTextSelectable;

    public interface OnClickListener<T> {
        void onClick(DialogInterface dialog, int which, @Nullable T selectedItem);
    }

    public interface OnSingleChoiceClickListener<T> {
        void onClick(DialogInterface dialog, int which, T item, boolean isChecked);
    }

    static class SingleChoiceItem<E> {
        final int id;
        @NonNull
        final CharSequence name;
        @NonNull
        final E rawItem;
        boolean isSelected;
        boolean isDisabled;

        SingleChoiceItem(int id, @NonNull CharSequence name, @NonNull E rawItem) {
            this.id = id;
            this.name = name;
            this.rawItem = rawItem;
            this.isSelected = false;
            this.isDisabled = false;
        }
    }

    public SearchableSingleChoiceDialogBuilder(@NonNull Context context, @NonNull List<T> items, @ArrayRes int itemNames) {
        this(context, items, context.getResources().getTextArray(itemNames));
    }

    public SearchableSingleChoiceDialogBuilder(@NonNull Context context, @NonNull List<T> items, @NonNull CharSequence[] itemNames) {
        this(context, items, Arrays.asList(itemNames));
    }

    public SearchableSingleChoiceDialogBuilder(@NonNull Context context, @NonNull T[] items, @NonNull CharSequence[] itemNames) {
        this(context, Arrays.asList(items), Arrays.asList(itemNames));
    }

    public SearchableSingleChoiceDialogBuilder(@NonNull Context context, @NonNull List<T> items, @NonNull List<CharSequence> itemNames) {
        View view = View.inflate(context, R.layout.dialog_searchable_single_choice, null);
        mRecyclerView = view.findViewById(android.R.id.list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        mViewContainer = view.findViewById(R.id.container);
        mSearchView = view.findViewById(R.id.action_search);
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                mAdapter.setFilteredItems(newText);
                return true;
            }
        });
        // Don't display search bar if items are less than 6
        if (items.size() < 6) {
            mSearchView.setVisibility(View.GONE);
        }
        mBuilder = new MaterialAlertDialogBuilder(context).setView(view);
        @SuppressLint({"RestrictedApi", "PrivateResource"})
        int layoutId = MaterialAttributes.resolveInteger(context, androidx.appcompat.R.attr.singleChoiceItemLayout,
                com.google.android.material.R.layout.mtrl_alert_select_dialog_singlechoice);
        mAdapter = new SearchableRecyclerViewAdapter(itemNames, items, layoutId);
        mRecyclerView.setAdapter(mAdapter);
    }

    public SearchableSingleChoiceDialogBuilder<T> setOnDismissListener(@Nullable DialogInterface.OnDismissListener dismissListener) {
        mBuilder.setOnDismissListener(dismissListener);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setOnSingleChoiceClickListener(
            @Nullable OnSingleChoiceClickListener<T> onSingleChoiceClickListener) {
        mOnSingleChoiceClickListener = onSingleChoiceClickListener;
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> addDisabledItems(@Nullable List<T> disabledItems) {
        mAdapter.addDisabledItems(disabledItems);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setSelection(@Nullable T selectedItem) {
        mAdapter.setSelection(selectedItem);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setSelectionIndex(int selectedIndex) {
        mAdapter.setSelectedIndex(selectedIndex);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> removeSelection() {
        mAdapter.setSelectedIndex(-1);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setView(@Nullable View view) {
        mViewContainer.removeAllViews();
        if (view != null) {
            mViewContainer.addView(view);
        }
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> reloadListUi() {
        mAdapter.notifyItemRangeChanged(0, mAdapter.getItemCount(), AdapterUtils.STUB);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setTextSelectable(boolean textSelectable) {
        mIsTextSelectable = textSelectable;
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setCancelable(boolean cancelable) {
        mBuilder.setCancelable(cancelable);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> hideSearchBar(boolean hide) {
        mSearchView.setVisibility(hide ? View.GONE : View.VISIBLE);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setIcon(@DrawableRes int iconRes) {
        mBuilder.setIcon(iconRes);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setIcon(@Nullable Drawable icon) {
        mBuilder.setIcon(icon);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setTitle(@Nullable CharSequence title) {
        mBuilder.setTitle(title);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setTitle(@StringRes int title) {
        mBuilder.setTitle(title);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setTitle(@Nullable View title) {
        mBuilder.setCustomTitle(title);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setPositiveButton(@StringRes int textId, @Nullable OnClickListener<T> listener) {
        mBuilder.setPositiveButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mAdapter.getSelection());
        });
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setPositiveButton(@NonNull CharSequence text, @Nullable OnClickListener<T> listener) {
        mBuilder.setPositiveButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mAdapter.getSelection());
        });
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setNegativeButton(@StringRes int textId, @Nullable OnClickListener<T> listener) {
        mBuilder.setNegativeButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mAdapter.getSelection());
        });
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setNegativeButton(@NonNull CharSequence text, @Nullable OnClickListener<T> listener) {
        mBuilder.setNegativeButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mAdapter.getSelection());
        });
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setNeutralButton(@StringRes int textId, @Nullable OnClickListener<T> listener) {
        mBuilder.setNeutralButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mAdapter.getSelection());
        });
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setNeutralButton(@NonNull CharSequence text, @Nullable OnClickListener<T> listener) {
        mBuilder.setNeutralButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mAdapter.getSelection());
        });
        return this;
    }

    @NonNull
    public AlertDialog create() {
        return mDialog = mBuilder.create();
    }

    @NonNull
    public AlertDialog show() {
        return mDialog = mBuilder.show();
    }

    private void triggerSingleChoiceClickListener(int index, boolean isChecked) {
        if (mDialog != null && mOnSingleChoiceClickListener != null) {
            mOnSingleChoiceClickListener.onClick(mDialog, index, mAdapter.mMasterList.get(index).rawItem, isChecked);
        }
    }

    private static class ChoiceItemCallback<E> extends DiffUtil.ItemCallback<SingleChoiceItem<E>> {
        @Override
        public boolean areItemsTheSame(@NonNull SingleChoiceItem<E> oldItem, @NonNull SingleChoiceItem<E> newItem) {
            return oldItem.id == newItem.id && Objects.equals(oldItem.rawItem, newItem.rawItem);
        }

        @Override
        public boolean areContentsTheSame(@NonNull SingleChoiceItem<E> oldItem, @NonNull SingleChoiceItem<E> newItem) {
            return oldItem.isSelected == newItem.isSelected
                    && oldItem.isDisabled == newItem.isDisabled
                    && Objects.equals(oldItem.name.toString(), newItem.name.toString());
        }
    }

    class SearchableRecyclerViewAdapter extends RecyclerView.ListAdapter<SingleChoiceItem<T>, SearchableRecyclerViewAdapter.ViewHolder> {
        @NonNull
        final List<SingleChoiceItem<T>> mMasterList = new ArrayList<>();
        @LayoutRes
        private final int mLayoutId;
        @Nullable
        private String mCurrentQuery = null;
        @Nullable
        private T mSelectedRawItem = null;

        SearchableRecyclerViewAdapter(@NonNull List<CharSequence> itemNames, @NonNull List<T> items, int layoutId) {
            super(new ChoiceItemCallback<>());
            mLayoutId = layoutId;
            int size = Math.min(itemNames.size(), items.size());
            for (int i = 0; i < size; ++i) {
                mMasterList.add(new SingleChoiceItem<>(i, itemNames.get(i), items.get(i)));
            }
            dispatchFilteredList();
        }

        void setFilteredItems(String constraint) {
            mCurrentQuery = TextUtils.isEmpty(constraint) ? null : constraint.toLowerCase(Locale.ROOT);
            dispatchFilteredList();
        }

        private void dispatchFilteredList() {
            if (mCurrentQuery == null) {
                submitList(new ArrayList<>(mMasterList));
                return;
            }
            Locale locale = Locale.getDefault();
            List<SingleChoiceItem<T>> filteredList = new ArrayList<>();
            for (SingleChoiceItem<T> item : mMasterList) {
                if (item.name.toString().toLowerCase(locale).contains(mCurrentQuery)
                        || item.rawItem.toString().toLowerCase(Locale.ROOT).contains(mCurrentQuery)) {
                    filteredList.add(item);
                }
            }
            submitList(filteredList);
        }

        @Nullable
        T getSelection() {
            return mSelectedRawItem;
        }

        void setSelection(@Nullable T selectedItem) {
            if (Objects.equals(mSelectedRawItem, selectedItem)) {
                return;
            }
            mSelectedRawItem = selectedItem;
            for (SingleChoiceItem<T> item : mMasterList) {
                boolean targetSelectionState = Objects.equals(item.rawItem, selectedItem);
                if (item.isSelected != targetSelectionState) {
                    item.isSelected = targetSelectionState;
                    triggerSingleChoiceClickListener(item.id, targetSelectionState);
                }
            }
            dispatchFilteredList();
        }

        void setSelectedIndex(int selectedIndex) {
            SingleChoiceItem<T> targetItem = (selectedIndex >= 0 && selectedIndex < mMasterList.size())
                    ? mMasterList.get(selectedIndex) : null;
            setSelection(targetItem != null ? targetItem.rawItem : null);
            if (selectedIndex >= 0) {
                mRecyclerView.setSelection(selectedIndex);
            }
        }

        void addDisabledItems(@Nullable List<T> disabledItems) {
            if (disabledItems == null) return;
            for (T item : disabledItems) {
                for (SingleChoiceItem<T> wrapper : mMasterList) {
                    if (Objects.equals(wrapper.rawItem, item)) {
                        wrapper.isDisabled = true;
                        break;
                    }
                }
            }
            dispatchFilteredList();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(mLayoutId, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SingleChoiceItem<T> item = getItem(position);
            holder.item.setText(item.name);
            holder.item.setTextIsSelectable(mIsTextSelectable);
            holder.item.setEnabled(!item.isDisabled);
            holder.item.setChecked(item.isSelected);
            holder.item.setOnClickListener(v -> {
                if (item.isSelected) {
                    // Already selected, do nothing
                    return;
                }
                mSelectedRawItem = item.rawItem;
                for (SingleChoiceItem<T> target : mMasterList) {
                    boolean wasSelected = target.isSelected;
                    target.isSelected = (target.id == item.id);
                    if (wasSelected != target.isSelected) {
                        // Trigger only if this item wasn't selected before
                        triggerSingleChoiceClickListener(target.id, target.isSelected);
                    }
                }
                dispatchFilteredList();
            });
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            CheckedTextView item;

            @SuppressLint("RestrictedApi")
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                item = itemView.findViewById(android.R.id.text1);
                int textAppearanceBodyLarge = MaterialAttributes.resolveInteger(item.getContext(), com.google.android.material.R.attr.textAppearanceBodyLarge, 0);
                TextViewCompat.setTextAppearance(item, textAppearanceBodyLarge);
                item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                item.setTextColor(MaterialColors.getColor(item.getContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, -1));
            }
        }
    }
}
