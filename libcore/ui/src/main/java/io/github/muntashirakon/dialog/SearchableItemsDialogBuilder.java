// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ArrayRes;
import androidx.annotation.ColorInt;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.collection.ArraySet;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.resources.MaterialAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.muntashirakon.ui.R;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.widget.SearchView;

public class SearchableItemsDialogBuilder<T extends CharSequence> {
    @NonNull
    private final MaterialAlertDialogBuilder mBuilder;
    private final SearchView mSearchView;
    @NonNull
    private final SearchableRecyclerViewAdapter mAdapter;
    @Nullable
    private AlertDialog mDialog;
    @Nullable
    private OnItemClickListener<T> mOnItemClickListener;
    private boolean mIsTextSelectable;
    @ColorInt
    private Integer mListBackgroundColorEven;
    @ColorInt
    private Integer mListBackgroundColorOdd;

    public interface OnItemClickListener<T> {
        void onClick(DialogInterface dialog, int which, T item);
    }

    @SuppressWarnings("unchecked")
    public SearchableItemsDialogBuilder(@NonNull Context context, @ArrayRes int itemNames) {
        this(context, (T[]) context.getResources().getTextArray(itemNames));
    }

    public SearchableItemsDialogBuilder(@NonNull Context context, @NonNull T[] itemNames) {
        this(context, Arrays.asList(itemNames));
    }

    public SearchableItemsDialogBuilder(@NonNull Context context, @NonNull List<T> itemNames) {
        View view = View.inflate(context, R.layout.dialog_searchable_single_choice, null);
        RecyclerView recyclerView = view.findViewById(android.R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
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
        if (itemNames.size() < 6) {
            mSearchView.setVisibility(View.GONE);
        }
        mBuilder = new MaterialAlertDialogBuilder(context).setView(view);
        @SuppressLint({"RestrictedApi", "PrivateResource"})
        int layoutId = MaterialAttributes.resolveInteger(context, androidx.appcompat.R.attr.listItemLayout,
                R.layout.m3_alert_select_dialog_item);
        mAdapter = new SearchableRecyclerViewAdapter(itemNames, layoutId);
        recyclerView.setAdapter(mAdapter);
    }

    public SearchableItemsDialogBuilder<T> setOnItemClickListener(@Nullable OnItemClickListener<T>
                                                                          onItemClickListener) {
        mOnItemClickListener = onItemClickListener;
        return this;
    }

    public SearchableItemsDialogBuilder<T> addDisabledItems(@Nullable List<T> disabledItems) {
        mAdapter.addDisabledItems(disabledItems);
        return this;
    }

    public SearchableItemsDialogBuilder<T> setListBackgroundColorEven(@ColorInt Integer color) {
        mListBackgroundColorEven = color;
        return this;
    }

    public SearchableItemsDialogBuilder<T> setListBackgroundColorOdd(@ColorInt Integer color) {
        mListBackgroundColorOdd = color;
        return this;
    }

    public SearchableItemsDialogBuilder<T> reloadListUi() {
        mAdapter.notifyItemRangeChanged(0, mAdapter.getItemCount());
        return this;
    }

    public SearchableItemsDialogBuilder<T> setTextSelectable(boolean textSelectable) {
        mIsTextSelectable = textSelectable;
        return this;
    }

    public SearchableItemsDialogBuilder<T> setCancelable(boolean cancelable) {
        mBuilder.setCancelable(cancelable);
        return this;
    }

    public SearchableItemsDialogBuilder<T> hideSearchBar(boolean hide) {
        mSearchView.setVisibility(hide ? View.GONE : View.VISIBLE);
        return this;
    }

    public SearchableItemsDialogBuilder<T> setTitle(@Nullable CharSequence title) {
        mBuilder.setTitle(title);
        return this;
    }

    public SearchableItemsDialogBuilder<T> setTitle(@StringRes int title) {
        mBuilder.setTitle(title);
        return this;
    }

    public SearchableItemsDialogBuilder<T> setTitle(View title) {
        mBuilder.setCustomTitle(title);
        return this;
    }

    public SearchableItemsDialogBuilder<T> setPositiveButton(@StringRes int textId,
                                                             @Nullable DialogInterface.OnClickListener listener) {
        mBuilder.setPositiveButton(textId, listener);
        return this;
    }

    public SearchableItemsDialogBuilder<T> setPositiveButton(@NonNull CharSequence text,
                                                             @Nullable DialogInterface.OnClickListener listener) {
        mBuilder.setPositiveButton(text, listener);
        return this;
    }

    public SearchableItemsDialogBuilder<T> setNegativeButton(@StringRes int textId,
                                                             @Nullable DialogInterface.OnClickListener listener) {
        mBuilder.setNegativeButton(textId, listener);
        return this;
    }

    public SearchableItemsDialogBuilder<T> setNegativeButton(@NonNull CharSequence text,
                                                             @Nullable DialogInterface.OnClickListener listener) {
        mBuilder.setNegativeButton(text, listener);
        return this;
    }

    public SearchableItemsDialogBuilder<T> setNeutralButton(@StringRes int textId,
                                                            @Nullable DialogInterface.OnClickListener listener) {
        mBuilder.setNeutralButton(textId, listener);
        return this;
    }

    public SearchableItemsDialogBuilder<T> setNeutralButton(@NonNull CharSequence text,
                                                            @Nullable DialogInterface.OnClickListener listener) {
        mBuilder.setNeutralButton(text, listener);
        return this;
    }

    public AlertDialog create() {
        return mDialog = mBuilder.create();
    }

    public AlertDialog show() {
        return mDialog = mBuilder.show();
    }

    private void triggerItemClickListener(int index) {
        if (mDialog != null && mOnItemClickListener != null) {
            mOnItemClickListener.onClick(mDialog, index, mAdapter.mItems.get(index));
        }
    }

    class SearchableRecyclerViewAdapter extends RecyclerView.Adapter<SearchableRecyclerViewAdapter.ViewHolder> {
        @NonNull
        private final List<T> mItems;
        @NonNull
        private final ArrayList<Integer> mFilteredItems = new ArrayList<>();
        private final Set<Integer> mDisabledItems = new ArraySet<>();
        @LayoutRes
        private final int mLayoutId;

        SearchableRecyclerViewAdapter(@NonNull List<T> items, int layoutId) {
            mItems = items;
            mLayoutId = layoutId;
            synchronized (mFilteredItems) {
                for (int i = 0; i < mItems.size(); ++i) {
                    mFilteredItems.add(i);
                }
            }
        }

        void setFilteredItems(CharSequence constraint) {
            Locale locale = Locale.getDefault();
            synchronized (mFilteredItems) {
                int previousCount = mFilteredItems.size();
                mFilteredItems.clear();
                for (int i = 0; i < mItems.size(); ++i) {
                    if (mItems.get(i).toString().toLowerCase(locale).contains(constraint)) {
                        mFilteredItems.add(i);
                    }
                }
                AdapterUtils.notifyDataSetChanged(this, previousCount, mFilteredItems.size());
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

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(mLayoutId, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Integer index;
            synchronized (mFilteredItems) {
                index = mFilteredItems.get(position);
            }
            holder.item.setText(mItems.get(index));
            holder.item.setTextIsSelectable(mIsTextSelectable);
            synchronized (mDisabledItems) {
                holder.item.setEnabled(!mDisabledItems.contains(index));
            }
            holder.itemView.setOnClickListener(v -> triggerItemClickListener(index));
            // Set background colors if set (position starts at 1, so the situation is reversed)
            setBackgroundColor(holder.itemView, position % 2 == 0 ? mListBackgroundColorOdd : mListBackgroundColorEven);
        }

        private void setBackgroundColor(View view, @Nullable Integer color) {
            if (view instanceof MaterialCardView) {
                MaterialCardView card = (MaterialCardView) view;
                if (color != null) {
                    card.setCardBackgroundColor(color);
                } else {
                    // Reset background
                    card.setCardBackgroundColor(null);
                }
            } else {
                if (color != null) {
                    view.setBackgroundColor(color);
                } else {
                    // Reset background
                    view.setBackground(null);
                }
            }
        }

        @Override
        public int getItemCount() {
            synchronized (mFilteredItems) {
                return mFilteredItems.size();
            }
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView item;

            @SuppressLint("RestrictedApi")
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                itemView.findViewById(R.id.icon_frame).setVisibility(View.GONE);
                itemView.findViewById(android.R.id.widget_frame).setVisibility(View.GONE);
                item = itemView.findViewById(android.R.id.text1);
            }
        }
    }
}
