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
import androidx.collection.ArraySet;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

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
        mBuilder = new MaterialAlertDialogBuilder(context)
                .setView(view);
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

    public SearchableSingleChoiceDialogBuilder<T> setOnSingleChoiceClickListener(@Nullable OnSingleChoiceClickListener<T>
                                                                                         onSingleChoiceClickListener) {
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
        mAdapter.notifyItemRangeChanged(0, mAdapter.getItemCount());
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
            mOnSingleChoiceClickListener.onClick(mDialog, index, mAdapter.mItems.get(index), isChecked);
        }
    }

    class SearchableRecyclerViewAdapter extends RecyclerView.Adapter<SearchableRecyclerViewAdapter.ViewHolder> {
        @NonNull
        private final List<CharSequence> mItemNames;
        @NonNull
        private final List<T> mItems;
        @NonNull
        private final ArrayList<Integer> mFilteredItems = new ArrayList<>();
        private int mSelectedItem = -1;
        private final Set<Integer> mDisabledItems = new ArraySet<>();
        @LayoutRes
        private final int mLayoutId;

        SearchableRecyclerViewAdapter(@NonNull List<CharSequence> itemNames, @NonNull List<T> items, int layoutId) {
            mItemNames = itemNames;
            mItems = items;
            mLayoutId = layoutId;
            synchronized (mFilteredItems) {
                for (int i = 0; i < items.size(); ++i) {
                    mFilteredItems.add(i);
                }
            }
        }

        void setFilteredItems(String constraint) {
            constraint = TextUtils.isEmpty(constraint) ? null : constraint.toLowerCase(Locale.ROOT);
            Locale locale = Locale.getDefault();
            synchronized (mFilteredItems) {
                int previousCount = mFilteredItems.size();
                mFilteredItems.clear();
                for (int i = 0; i < mItems.size(); ++i) {
                    if (constraint == null
                            || mItemNames.get(i).toString().toLowerCase(locale).contains(constraint)
                            || mItems.get(i).toString().toLowerCase(Locale.ROOT).contains(constraint)) {
                        mFilteredItems.add(i);
                    }
                }
                AdapterUtils.notifyDataSetChanged(this, previousCount, mFilteredItems.size());
            }
        }

        @Nullable
        T getSelection() {
            if (mSelectedItem >= 0) {
                return mItems.get(mSelectedItem);
            }
            return null;
        }

        void setSelection(@Nullable T selectedItem) {
            if (selectedItem != null) {
                int index = mItems.indexOf(selectedItem);
                if (index != -1) {
                    setSelectedIndex(index);
                }
            }
        }

        void setSelectedIndex(int selectedIndex) {
            if (selectedIndex == mSelectedItem) {
                // Do nothing
                return;
            }
            updateSelection(false);
            mSelectedItem = selectedIndex;
            updateSelection(true);
            mRecyclerView.setSelection(selectedIndex);
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

        @NonNull
        @Override
        public SearchableRecyclerViewAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(mLayoutId, parent, false);
            return new SearchableRecyclerViewAdapter.ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SearchableRecyclerViewAdapter.ViewHolder holder, int position) {
            Integer index;
            synchronized (mFilteredItems) {
                index = mFilteredItems.get(position);
            }
            final AtomicBoolean selected = new AtomicBoolean(mSelectedItem == index);
            holder.item.setText(mItemNames.get(index));
            holder.item.setTextIsSelectable(mIsTextSelectable);
            synchronized (mDisabledItems) {
                holder.item.setEnabled(!mDisabledItems.contains(index));
            }
            holder.item.setChecked(selected.get());
            holder.item.setOnClickListener(v -> {
                if (selected.get()) {
                    // Already selected, do nothing
                    return;
                }
                // Unselect the previous and select this one
                updateSelection(false);
                mSelectedItem = index;
                // Update selection manually
                selected.set(!selected.get());
                holder.item.setChecked(selected.get());
                triggerSingleChoiceClickListener(index, selected.get());
            });
        }

        @Override
        public int getItemCount() {
            synchronized (mFilteredItems) {
                return mFilteredItems.size();
            }
        }

        private void updateSelection(boolean selected) {
            if (mSelectedItem < 0) {
                return;
            }
            int position;
            synchronized (mFilteredItems) {
                position = mFilteredItems.indexOf(mSelectedItem);
            }
            if (position >= 0) {
                notifyItemChanged(position);
            }
            triggerSingleChoiceClickListener(mSelectedItem, selected);
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
