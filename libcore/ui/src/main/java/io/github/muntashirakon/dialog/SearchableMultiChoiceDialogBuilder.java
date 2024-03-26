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
import androidx.collection.ArraySet;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.resources.MaterialAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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

    public SearchableMultiChoiceDialogBuilder<T> setOnMultiChoiceClickListener(@Nullable OnMultiChoiceClickListener<T>
                                                                                       onMultiChoiceClickListener) {
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
        mAdapter.notifyItemRangeChanged(0, mAdapter.getItemCount());
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
            mOnMultiChoiceClickListener.onClick(mDialog, index, mAdapter.mItems.get(index), isChecked);
        }
    }

    class SearchableRecyclerViewAdapter extends RecyclerView.Adapter<SearchableRecyclerViewAdapter.ViewHolder> {
        @NonNull
        private final List<CharSequence> mItemNames;
        @NonNull
        private final List<T> mItems;
        @NonNull
        private final List<T> mNotFoundItems = new ArrayList<>();
        @NonNull
        private final ArrayList<Integer> mFilteredItems = new ArrayList<>();
        @NonNull
        private final Set<Integer> mSelectedItems = new ArraySet<>();
        private final Set<Integer> mDisabledItems = new ArraySet<>();
        @LayoutRes
        private final int mLayoutId;

        SearchableRecyclerViewAdapter(@NonNull List<CharSequence> itemNames, @NonNull List<T> items, int layoutId) {
            mItemNames = itemNames;
            mItems = items;
            mLayoutId = layoutId;
            new Thread(() -> {
                synchronized (mFilteredItems) {
                    for (int i = 0; i < items.size(); ++i) {
                        mFilteredItems.add(i);
                    }
                }
            }, "searchable_multi_choice_dialog").start();
        }

        void setFilteredItems(CharSequence constraint) {
            Locale locale = Locale.getDefault();
            synchronized (mFilteredItems) {
                int previousCount = mFilteredItems.size();
                mFilteredItems.clear();
                for (int i = 0; i < mItems.size(); ++i) {
                    if (mItemNames.get(i).toString().toLowerCase(locale).contains(constraint)
                            || mItems.get(i).toString().toLowerCase(Locale.ROOT).contains(constraint)) {
                        mFilteredItems.add(i);
                    }
                }
                checkSelections();
                AdapterUtils.notifyDataSetChanged(this, previousCount, mFilteredItems.size());
            }
        }

        ArrayList<T> getSelectedItems() {
            ArrayList<T> selections = new ArrayList<>(mNotFoundItems);
            synchronized (mSelectedItems) {
                for (int item : mSelectedItems) {
                    selections.add(mItems.get(item));
                }
            }
            return selections;
        }

        void addSelectedItems(@Nullable List<T> selectedItems) {
            if (selectedItems != null) {
                for (T item : selectedItems) {
                    int index = mItems.indexOf(item);
                    if (index != -1) {
                        synchronized (mSelectedItems) {
                            mSelectedItems.add(index);
                        }
                    } else mNotFoundItems.add(item);
                }
            }
        }

        void addSelectedIndexes(@Nullable int[] selectedIndexes) {
            if (selectedIndexes != null) {
                for (int index : selectedIndexes) {
                    synchronized (mSelectedItems) {
                        mSelectedItems.add(index);
                    }
                }
            }
        }

        void removeSelectedIndexes(@Nullable int[] selectedIndexes) {
            if (selectedIndexes != null) {
                for (int index : selectedIndexes) {
                    synchronized (mSelectedItems) {
                        mSelectedItems.remove(index);
                    }
                }
            }
        }

        void addDisabledItems(@Nullable List<T> disabledItems) {
            if (disabledItems != null) {
                for (T item : disabledItems) {
                    int index = mItems.indexOf(item);
                    if (index != -1) {
                        synchronized (mDisabledItems) {
                            mDisabledItems.add(index);
                        }
                    }
                }
            }
        }

        void selectAll() {
            synchronized (mSelectedItems) {
                synchronized (mFilteredItems) {
                    List<Integer> newSelections = new ArrayList<>();
                    for (int index : mFilteredItems) {
                        if (!mSelectedItems.contains(index)) {
                            newSelections.add(index);
                        }
                    }
                    mSelectedItems.addAll(newSelections);
                    checkSelections();
                    for (int index : newSelections) {
                        triggerMultiChoiceClickListener(index, true);
                    }
                    notifyItemRangeChanged(0, getItemCount());
                }
            }
        }

        void deselectAll() {
            synchronized (mSelectedItems) {
                synchronized (mFilteredItems) {
                    List<Integer> oldSelections = new ArrayList<>();
                    for (int index : mFilteredItems) {
                        if (mSelectedItems.contains(index)) {
                            oldSelections.add(index);
                        }
                    }
                    //noinspection SlowAbstractSetRemoveAll
                    mSelectedItems.removeAll(oldSelections);
                    checkSelections();
                    for (int index : oldSelections) {
                        triggerMultiChoiceClickListener(index, false);
                    }
                    notifyItemRangeChanged(0, getItemCount());
                }
            }
        }

        boolean areAllSelected() {
            synchronized (mSelectedItems) {
                synchronized (mFilteredItems) {
                    return mSelectedItems.containsAll(mFilteredItems);
                }
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(mLayoutId, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SearchableRecyclerViewAdapter.ViewHolder holder, int position) {
            Integer index;
            synchronized (mFilteredItems) {
                index = mFilteredItems.get(position);
            }
            final AtomicBoolean selected;
            synchronized (mSelectedItems) {
                selected = new AtomicBoolean(mSelectedItems.contains(index));
            }
            holder.item.setText(mItemNames.get(index));
            holder.item.setTextIsSelectable(mIsTextSelectable);
            synchronized (mDisabledItems) {
                holder.item.setEnabled(!mDisabledItems.contains(index));
            }
            holder.item.setChecked(selected.get());
            holder.item.setOnClickListener(v -> {
                synchronized (mSelectedItems) {
                    boolean isSelected = selected.get();
                    if (isSelected) {
                        mSelectedItems.remove(index);
                    } else mSelectedItems.add(index);
                    selected.set(!isSelected);
                    holder.item.setChecked(!isSelected);
                    checkSelections();
                    triggerMultiChoiceClickListener(index, selected.get());
                }
            });
        }

        @Override
        public int getItemCount() {
            synchronized (mFilteredItems) {
                return mFilteredItems.size();
            }
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
