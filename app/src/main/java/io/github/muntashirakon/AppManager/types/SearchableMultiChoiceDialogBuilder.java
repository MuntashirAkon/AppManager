/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.types;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class SearchableMultiChoiceDialogBuilder<T> {
    @NonNull
    private final FragmentActivity activity;
    @NonNull
    private final List<T> items;
    @NonNull
    private final List<CharSequence> itemNames;
    @Nullable
    private List<T> selectedItems;
    @NonNull
    private final MaterialAlertDialogBuilder builder;
    @NonNull
    private final RecyclerView recyclerView;
    private final LinearLayoutCompat searchBar;

    SearchableRecyclerViewAdapter adapter;

    public interface OnClickListener<T> {
        void onClick(DialogInterface dialog, int which, @NonNull ArrayList<T> selectedItems);
    }

    public SearchableMultiChoiceDialogBuilder(@NonNull FragmentActivity activity, @NonNull List<T> items, @ArrayRes int itemNames) {
        this(activity, items, activity.getResources().getTextArray(itemNames));
    }

    public SearchableMultiChoiceDialogBuilder(@NonNull FragmentActivity activity, @NonNull List<T> items, CharSequence[] itemNames) {
        this(activity, items, Arrays.asList(itemNames));
    }

    @SuppressLint("InflateParams")
    public SearchableMultiChoiceDialogBuilder(@NonNull FragmentActivity activity, @NonNull List<T> items, @NonNull List<CharSequence> itemNames) {
        this.activity = activity;
        this.items = items;
        this.itemNames = itemNames;
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_searchable_multi_choice, null);
        recyclerView = view.findViewById(android.R.id.list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false));
        searchBar = view.findViewById(R.id.search_bar);
        TextInputEditText searchInput = view.findViewById(R.id.search_input);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.setFilteredItems(s);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        // Don't display search bar if items are less than 6
        if (this.items.size() < 6) searchBar.setVisibility(View.GONE);
        builder = new MaterialAlertDialogBuilder(activity).setView(view);
    }

    public SearchableMultiChoiceDialogBuilder<T> setSelections(@Nullable List<T> selectedItems) {
        this.selectedItems = selectedItems;
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> hideSearchBar(boolean hide) {
        this.searchBar.setVisibility(hide ? View.GONE : View.VISIBLE);
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> setTitle(@Nullable CharSequence title) {
        builder.setTitle(title);
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> setTitle(@StringRes int title) {
        builder.setTitle(title);
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> setPositiveButton(@StringRes int textId, OnClickListener<T> listener) {
        builder.setPositiveButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, adapter.getSelectedItems());
        });
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> setPositiveButton(@NonNull CharSequence text, OnClickListener<T> listener) {
        builder.setPositiveButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, adapter.getSelectedItems());
        });
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> setNegativeButton(@StringRes int textId, OnClickListener<T> listener) {
        builder.setNegativeButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, adapter.getSelectedItems());
        });
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> setNegativeButton(@NonNull CharSequence text, OnClickListener<T> listener) {
        builder.setNegativeButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, adapter.getSelectedItems());
        });
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> setNeutralButton(@StringRes int textId, OnClickListener<T> listener) {
        builder.setNeutralButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, adapter.getSelectedItems());
        });
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> setNeutralButton(@NonNull CharSequence text, OnClickListener<T> listener) {
        builder.setNeutralButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, adapter.getSelectedItems());
        });
        return this;
    }

    public AlertDialog create() {
        adapter = new SearchableRecyclerViewAdapter(itemNames, items, selectedItems);
        recyclerView.setAdapter(adapter);
        return builder.create();
    }

    public void show() {
        create().show();
    }

    class SearchableRecyclerViewAdapter extends RecyclerView.Adapter<SearchableRecyclerViewAdapter.ViewHolder> {
        @NonNull
        private final List<CharSequence> itemNames;
        @NonNull
        private final List<T> items;
        @NonNull
        private final List<T> notFoundItems = new ArrayList<>();
        @NonNull
        private final ArrayList<Integer> filteredItems = new ArrayList<>();
        @NonNull
        private final ArrayList<Integer> selectedItems = new ArrayList<>();

        SearchableRecyclerViewAdapter(@NonNull List<CharSequence> itemNames, @NonNull List<T> items, @Nullable List<T> preSelectedItems) {
            this.itemNames = itemNames;
            this.items = items;
            if (preSelectedItems != null) {
                for (T item : preSelectedItems) {
                    int index = items.indexOf(item);
                    if (index != -1) {
                        selectedItems.add(index);
                    } else notFoundItems.add(item);
                }
            }
            for (int i = 0; i < items.size(); ++i) {
                filteredItems.add(i);
            }
        }

        void setFilteredItems(CharSequence constraint) {
            Locale locale = LangUtils.getLocaleByLanguage(activity);
            filteredItems.clear();
            for (int i = 0; i < items.size(); ++i) {
                if (itemNames.get(i).toString().toLowerCase(locale).contains(constraint)
                        || items.get(i).toString().toLowerCase(Locale.ROOT).contains(constraint)) {
                    filteredItems.add(i);
                }
            }
            notifyDataSetChanged();
        }

        ArrayList<T> getSelectedItems() {
            ArrayList<T> selections = new ArrayList<>(notFoundItems);
            for (int item : selectedItems) {
                selections.add(items.get(item));
            }
            return selections;
        }

        @NonNull
        @Override
        public SearchableRecyclerViewAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            @SuppressLint("PrivateResource")
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.mtrl_alert_select_dialog_multichoice, parent, false);
            return new SearchableRecyclerViewAdapter.ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SearchableRecyclerViewAdapter.ViewHolder holder, int position) {
            Integer index = filteredItems.get(position);
            boolean selected = selectedItems.contains(index);
            holder.item.setText(itemNames.get(index));
            holder.item.setChecked(selected);
            holder.item.setOnClickListener(v -> {
                if (selected) {
                    selectedItems.remove(index);
                } else selectedItems.add(index);
                holder.item.setChecked(!selected);
            });
        }

        @Override
        public int getItemCount() {
            return filteredItems.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            CheckedTextView item;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                item = itemView.findViewById(android.R.id.text1);
            }
        }


    }
}
