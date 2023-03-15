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
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.collection.ArraySet;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.resources.MaterialAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.muntashirakon.ui.R;
import io.github.muntashirakon.widget.SearchView;

public class SearchableItemsDialogBuilder<T extends CharSequence> {
    @NonNull
    private final MaterialAlertDialogBuilder builder;
    private final SearchView searchView;
    @NonNull
    private final SearchableRecyclerViewAdapter adapter;
    @Nullable
    private AlertDialog dialog;
    @Nullable
    private OnItemClickListener<T> onItemClickListener;
    private boolean isTextSelectable;

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
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        searchView = view.findViewById(R.id.action_search);
        searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.setFilteredItems(newText);
                return true;
            }
        });
        // Don't display search bar if items are less than 6
        if (itemNames.size() < 6) {
            searchView.setVisibility(View.GONE);
        }
        builder = new MaterialAlertDialogBuilder(context).setView(view);
        @SuppressLint({"RestrictedApi", "PrivateResource"})
        int layoutId = MaterialAttributes.resolveInteger(context, androidx.appcompat.R.attr.listItemLayout,
                R.layout.m3_alert_select_dialog_item);
        adapter = new SearchableRecyclerViewAdapter(itemNames, layoutId);
        recyclerView.setAdapter(adapter);
    }

    public SearchableItemsDialogBuilder<T> setOnItemClickListener(@Nullable OnItemClickListener<T>
                                                                          onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
        return this;
    }

    public SearchableItemsDialogBuilder<T> addDisabledItems(@Nullable List<T> disabledItems) {
        adapter.addDisabledItems(disabledItems);
        return this;
    }

    public SearchableItemsDialogBuilder<T> reloadListUi() {
        adapter.notifyDataSetChanged();
        return this;
    }

    public SearchableItemsDialogBuilder<T> setTextSelectable(boolean textSelectable) {
        this.isTextSelectable = textSelectable;
        return this;
    }

    public SearchableItemsDialogBuilder<T> setCancelable(boolean cancelable) {
        builder.setCancelable(cancelable);
        return this;
    }

    public SearchableItemsDialogBuilder<T> hideSearchBar(boolean hide) {
        this.searchView.setVisibility(hide ? View.GONE : View.VISIBLE);
        return this;
    }

    public SearchableItemsDialogBuilder<T> setTitle(@Nullable CharSequence title) {
        builder.setTitle(title);
        return this;
    }

    public SearchableItemsDialogBuilder<T> setTitle(@StringRes int title) {
        builder.setTitle(title);
        return this;
    }

    public SearchableItemsDialogBuilder<T> setTitle(View title) {
        builder.setCustomTitle(title);
        return this;
    }

    public SearchableItemsDialogBuilder<T> setPositiveButton(@StringRes int textId,
                                                             @Nullable DialogInterface.OnClickListener listener) {
        builder.setPositiveButton(textId, listener);
        return this;
    }

    public SearchableItemsDialogBuilder<T> setPositiveButton(@NonNull CharSequence text,
                                                             @Nullable DialogInterface.OnClickListener listener) {
        builder.setPositiveButton(text, listener);
        return this;
    }

    public SearchableItemsDialogBuilder<T> setNegativeButton(@StringRes int textId,
                                                             @Nullable DialogInterface.OnClickListener listener) {
        builder.setNegativeButton(textId, listener);
        return this;
    }

    public SearchableItemsDialogBuilder<T> setNegativeButton(@NonNull CharSequence text,
                                                             @Nullable DialogInterface.OnClickListener listener) {
        builder.setNegativeButton(text, listener);
        return this;
    }

    public SearchableItemsDialogBuilder<T> setNeutralButton(@StringRes int textId,
                                                            @Nullable DialogInterface.OnClickListener listener) {
        builder.setNeutralButton(textId, listener);
        return this;
    }

    public SearchableItemsDialogBuilder<T> setNeutralButton(@NonNull CharSequence text,
                                                            @Nullable DialogInterface.OnClickListener listener) {
        builder.setNeutralButton(text, listener);
        return this;
    }

    public AlertDialog create() {
        return dialog = builder.create();
    }

    public AlertDialog show() {
        return dialog = builder.show();
    }

    private void triggerItemClickListener(int index) {
        if (dialog != null && onItemClickListener != null) {
            onItemClickListener.onClick(dialog, index, adapter.items.get(index));
        }
    }

    class SearchableRecyclerViewAdapter extends RecyclerView.Adapter<SearchableRecyclerViewAdapter.ViewHolder> {
        @NonNull
        private final List<T> items;
        @NonNull
        private final ArrayList<Integer> filteredItems = new ArrayList<>();
        private final Set<Integer> disabledItems = new ArraySet<>();
        @LayoutRes
        private final int layoutId;


        SearchableRecyclerViewAdapter(@NonNull List<T> items, int layoutId) {
            this.items = items;
            this.layoutId = layoutId;
            new Thread(() -> {
                synchronized (filteredItems) {
                    for (int i = 0; i < this.items.size(); ++i) {
                        filteredItems.add(i);
                    }
                }
            }, "searchable_items_dialog").start();
        }

        void setFilteredItems(CharSequence constraint) {
            Locale locale = Locale.getDefault();
            synchronized (filteredItems) {
                filteredItems.clear();
                for (int i = 0; i < items.size(); ++i) {
                    if (items.get(i).toString().toLowerCase(locale).contains(constraint)) {
                        filteredItems.add(i);
                    }
                }
                notifyDataSetChanged();
            }
        }

        void addDisabledItems(@Nullable List<T> disabledItems) {
            if (disabledItems != null) {
                for (T item : disabledItems) {
                    int index = items.indexOf(item);
                    if (index != -1) {
                        synchronized (this.disabledItems) {
                            this.disabledItems.add(index);
                        }
                    }
                }
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Integer index;
            synchronized (filteredItems) {
                index = filteredItems.get(position);
            }
            holder.item.setText(items.get(index));
            holder.item.setTextIsSelectable(isTextSelectable);
            synchronized (disabledItems) {
                holder.item.setEnabled(!disabledItems.contains(index));
            }
            holder.itemView.setOnClickListener(v -> triggerItemClickListener(index));
        }

        @Override
        public int getItemCount() {
            synchronized (filteredItems) {
                return filteredItems.size();
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
