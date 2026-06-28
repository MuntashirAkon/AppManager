// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;

import androidx.annotation.ArrayRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

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
import io.github.muntashirakon.widget.CheckBox;
import io.github.muntashirakon.widget.SearchView;

public class SearchableMultiChoiceDialogBuilder<T> {
    private final View mView;
    @NonNull
    private final MaterialAlertDialogBuilder mBuilder;
    private final SearchView mSearchView;
    private final CheckBox mSelectAll;
    @NonNull
    private final SearchableRecyclerViewAdapter mAdapter;
    @Nullable
    private AlertDialog mDialog;
    @Nullable
    private OnMultiChoiceClickListener<T> mOnMultiChoiceClickListener;
    private boolean mIsTextSelectable;

    public interface OnClickListener<T> {
        void onClick(DialogInterface dialog, int which, @NonNull ArrayList<T> selectedItems);
    }

    public interface OnMultiChoiceClickListener<T> {
        void onClick(DialogInterface dialog, int which, T item, boolean isChecked);
    }

    static class MultiChoiceItem<E> {
        final int id;
        @NonNull
        final CharSequence name;
        @NonNull
        final E rawItem;
        boolean isSelected;
        boolean isDisabled;

        MultiChoiceItem(int id, @NonNull CharSequence name, @NonNull E rawItem) {
            this.id = id;
            this.name = name;
            this.rawItem = rawItem;
            this.isSelected = false;
            this.isDisabled = false;
        }

        MultiChoiceItem(@NonNull MultiChoiceItem<E> other) {
            this.id = other.id;
            this.name = other.name;
            this.rawItem = other.rawItem;
            this.isSelected = other.isSelected;
            this.isDisabled = other.isDisabled;
        }
    }

    public SearchableMultiChoiceDialogBuilder(@NonNull Context context, @NonNull List<T> items, @ArrayRes int itemNames) {
        this(context, items, context.getResources().getTextArray(itemNames));
    }

    public SearchableMultiChoiceDialogBuilder(@NonNull Context context, @NonNull T[] items, @NonNull CharSequence[] itemNames) {
        this(context, Arrays.asList(items), Arrays.asList(itemNames));
    }

    public SearchableMultiChoiceDialogBuilder(@NonNull Context context, @NonNull List<T> items, @NonNull CharSequence[] itemNames) {
        this(context, items, Arrays.asList(itemNames));
    }

    public SearchableMultiChoiceDialogBuilder(@NonNull Context context, @NonNull List<T> items, @NonNull List<CharSequence> itemNames) {
        mView = View.inflate(context, R.layout.dialog_searchable_multi_choice, null);
        RecyclerView recyclerView = mView.findViewById(android.R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        mSearchView = mView.findViewById(R.id.action_search);
        mSelectAll = mView.findViewById(android.R.id.checkbox);
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
        mBuilder = new MaterialAlertDialogBuilder(context).setView(mView);
        @SuppressLint({"RestrictedApi", "PrivateResource"})
        int layoutId = MaterialAttributes.resolveInteger(context, androidx.appcompat.R.attr.multiChoiceItemLayout,
                com.google.android.material.R.layout.mtrl_alert_select_dialog_multichoice);
        mAdapter = new SearchableRecyclerViewAdapter(itemNames, items, layoutId);
        recyclerView.setAdapter(mAdapter);
        mSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                mAdapter.selectAll();
            } else {
                mAdapter.deselectAll();
            }
        });
        if (items.size() < 2) {
            // No need to display select all if only one item is present
            mSelectAll.setVisibility(View.GONE);
        }
        checkSelections();
    }

    public View getView() {
        return mView;
    }

    public SearchableMultiChoiceDialogBuilder<T> setOnMultiChoiceClickListener(
            @Nullable OnMultiChoiceClickListener<T> onMultiChoiceClickListener) {
        mOnMultiChoiceClickListener = onMultiChoiceClickListener;
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> addDisabledItems(@Nullable List<T> disabledItems) {
        mAdapter.addDisabledItems(disabledItems);
        checkSelections();
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> addSelections(@Nullable List<T> selectedItems) {
        mAdapter.addSelectedItems(selectedItems);
        checkSelections();
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> addSelections(@Nullable int[] selectedIndexes) {
        mAdapter.addSelectedIndexes(selectedIndexes);
        checkSelections();
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> removeSelections(@Nullable int[] selectedIndexes) {
        mAdapter.removeSelectedIndexes(selectedIndexes);
        checkSelections();
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> reloadListUi() {
        mAdapter.notifyItemRangeChanged(0, mAdapter.getItemCount(), AdapterUtils.STUB);
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> setTextSelectable(boolean textSelectable) {
        mIsTextSelectable = textSelectable;
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> setCancelable(boolean cancelable) {
        mBuilder.setCancelable(cancelable);
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> hideSearchBar(boolean hide) {
        mSearchView.setVisibility(hide ? View.GONE : View.VISIBLE);
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> showSelectAll(boolean show) {
        mSelectAll.setVisibility(show ? View.VISIBLE : View.GONE);
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> setTitle(@Nullable CharSequence title) {
        mBuilder.setTitle(title);
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> setTitle(@StringRes int title) {
        mBuilder.setTitle(title);
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> setTitle(@Nullable View title) {
        mBuilder.setCustomTitle(title);
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> setPositiveButton(@StringRes int textId, @Nullable OnClickListener<T> listener) {
        mBuilder.setPositiveButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mAdapter.getSelectedItems());
        });
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> setPositiveButton(@NonNull CharSequence text, @Nullable OnClickListener<T> listener) {
        mBuilder.setPositiveButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mAdapter.getSelectedItems());
        });
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> setNegativeButton(@StringRes int textId, @Nullable OnClickListener<T> listener) {
        mBuilder.setNegativeButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mAdapter.getSelectedItems());
        });
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> setNegativeButton(@NonNull CharSequence text, @Nullable OnClickListener<T> listener) {
        mBuilder.setNegativeButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mAdapter.getSelectedItems());
        });
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> setNeutralButton(@StringRes int textId, @Nullable OnClickListener<T> listener) {
        mBuilder.setNeutralButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mAdapter.getSelectedItems());
        });
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> setNeutralButton(@NonNull CharSequence text, @Nullable OnClickListener<T> listener) {
        mBuilder.setNeutralButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, mAdapter.getSelectedItems());
        });
        return this;
    }

    public AlertDialog create() {
        return mDialog = mBuilder.create();
    }

    public void show() {
        create().show();
    }

    private void checkSelections() {
        mSelectAll.setChecked(mAdapter.areAllSelected(), false);
    }

    private void triggerMultiChoiceClickListener(int index, boolean isChecked) {
        if (mDialog != null && mOnMultiChoiceClickListener != null) {
            mOnMultiChoiceClickListener.onClick(mDialog, index, mAdapter.mMasterList.get(index).rawItem, isChecked);
        }
    }

    private static class ChoiceItemCallback<E> extends DiffUtil.ItemCallback<MultiChoiceItem<E>> {
        @Override
        public boolean areItemsTheSame(@NonNull MultiChoiceItem<E> oldItem, @NonNull MultiChoiceItem<E> newItem) {
            return oldItem.id == newItem.id && Objects.equals(oldItem.rawItem, newItem.rawItem);
        }

        @Override
        public boolean areContentsTheSame(@NonNull MultiChoiceItem<E> oldItem, @NonNull MultiChoiceItem<E> newItem) {
            return oldItem.isSelected == newItem.isSelected
                    && oldItem.isDisabled == newItem.isDisabled
                    && Objects.equals(oldItem.name.toString(), newItem.name.toString());
        }
    }

    class SearchableRecyclerViewAdapter extends ListAdapter<MultiChoiceItem<T>, SearchableRecyclerViewAdapter.ViewHolder> {
        @NonNull
        final List<MultiChoiceItem<T>> mMasterList = new ArrayList<>();
        @NonNull
        final List<T> mNotFoundItems = new ArrayList<>();
        @NonNull
        List<MultiChoiceItem<T>> mCurrentVisibleList = new ArrayList<>();
        @LayoutRes
        private final int mLayoutId;
        @Nullable
        private CharSequence mCurrentQuery = "";

        SearchableRecyclerViewAdapter(@NonNull List<CharSequence> itemNames, @NonNull List<T> items, int layoutId) {
            super(new ChoiceItemCallback<>());
            mLayoutId = layoutId;
            int size = Math.min(itemNames.size(), items.size());
            for (int i = 0; i < size; ++i) {
                mMasterList.add(new MultiChoiceItem<>(i, itemNames.get(i), items.get(i)));
            }
            dispatchFilteredList();
        }

        void setFilteredItems(CharSequence constraint) {
            mCurrentQuery = constraint;
            dispatchFilteredList();
            checkSelections();
        }

        private void dispatchFilteredList() {
            if (mCurrentQuery == null || mCurrentQuery.length() == 0) {
                mCurrentVisibleList = new ArrayList<>(mMasterList);
            } else {
                Locale locale = Locale.getDefault();
                String query = mCurrentQuery.toString().toLowerCase(locale);
                List<MultiChoiceItem<T>> filteredList = new ArrayList<>();
                for (MultiChoiceItem<T> item : mMasterList) {
                    if (item.name.toString().toLowerCase(locale).contains(query)
                            || item.rawItem.toString().toLowerCase(Locale.ROOT).contains(query)) {
                        filteredList.add(item);
                    }
                }
                mCurrentVisibleList = filteredList;
            }
            submitList(mCurrentVisibleList);
        }

        ArrayList<T> getSelectedItems() {
            ArrayList<T> selections = new ArrayList<>(mNotFoundItems);
            for (MultiChoiceItem<T> item : mMasterList) {
                if (item.isSelected) {
                    selections.add(item.rawItem);
                }
            }
            return selections;
        }

        void addSelectedItems(@Nullable List<T> selectedItems) {
            if (selectedItems == null) return;
            boolean listUpdated = false;
            for (T item : selectedItems) {
                boolean found = false;
                for (int i = 0; i < mMasterList.size(); i++) {
                    MultiChoiceItem<T> wrapper = mMasterList.get(i);
                    if (Objects.equals(wrapper.rawItem, item)) {
                        if (!wrapper.isSelected) {
                            MultiChoiceItem<T> newItem = new MultiChoiceItem<>(wrapper);
                            newItem.isSelected = true;
                            mMasterList.set(i, newItem);
                            listUpdated = true;
                        }
                        found = true;
                        break;
                    }
                }
                if (!found && !mNotFoundItems.contains(item)) {
                    mNotFoundItems.add(item);
                }
            }
            if (listUpdated) {
                dispatchFilteredList();
            }
        }

        void addSelectedIndexes(@Nullable int[] selectedIndexes) {
            if (selectedIndexes == null) {
                return;
            }
            for (int index : selectedIndexes) {
                if (index >= 0 && index < mMasterList.size()) {
                    mMasterList.get(index).isSelected = true;
                }
            }
            dispatchFilteredList();
        }

        void removeSelectedIndexes(@Nullable int[] selectedIndexes) {
            if (selectedIndexes == null) {
                return;
            }
            for (int index : selectedIndexes) {
                if (index >= 0 && index < mMasterList.size()) {
                    mMasterList.get(index).isSelected = false;
                }
            }
            dispatchFilteredList();
        }

        void addDisabledItems(@Nullable List<T> disabledItems) {
            if (disabledItems == null) {
                return;
            }
            for (T item : disabledItems) {
                for (MultiChoiceItem<T> wrapper : mMasterList) {
                    if (Objects.equals(wrapper.rawItem, item)) {
                        wrapper.isDisabled = true;
                        break;
                    }
                }
            }
            dispatchFilteredList();
        }

        void selectAll() {
            boolean listUpdated = false;
            for (MultiChoiceItem<T> item : mCurrentVisibleList) {
                if (!item.isSelected) {
                    MultiChoiceItem<T> newItem = new MultiChoiceItem<>(item);
                    newItem.isSelected = true;
                    mMasterList.set(newItem.id, newItem);
                    triggerMultiChoiceClickListener(newItem.id, true);
                    listUpdated = true;
                }
            }
            if (listUpdated) {
                dispatchFilteredList();
                checkSelections();
            }
        }

        void deselectAll() {
            boolean listUpdated = false;
            for (MultiChoiceItem<T> item : mCurrentVisibleList) {
                if (item.isSelected) {
                    MultiChoiceItem<T> newItem = new MultiChoiceItem<>(item);
                    newItem.isSelected = false;
                    mMasterList.set(newItem.id, newItem);
                    triggerMultiChoiceClickListener(newItem.id, false);
                    listUpdated = true;
                }
            }
            if (listUpdated) {
                dispatchFilteredList();
                checkSelections();
            }
        }

        boolean areAllSelected() {
            if (mCurrentVisibleList.isEmpty()) {
                return false;
            }
            for (MultiChoiceItem<T> item : mCurrentVisibleList) {
                if (!item.isSelected) {
                    return false;
                }
            }
            return true;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(mLayoutId, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SearchableRecyclerViewAdapter.ViewHolder holder, int position) {
            MultiChoiceItem<T> item = getItem(position);
            holder.item.setText(item.name);
            holder.item.setTextIsSelectable(mIsTextSelectable);
            holder.item.setEnabled(!item.isDisabled);
            holder.item.setChecked(item.isSelected);
            holder.item.setOnClickListener(v -> {
                MultiChoiceItem<T> newItem = new MultiChoiceItem<>(item);
                newItem.isSelected = !item.isSelected;
                mMasterList.set(newItem.id, newItem);
                dispatchFilteredList();
                checkSelections();
                triggerMultiChoiceClickListener(newItem.id, newItem.isSelected);
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
