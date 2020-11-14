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
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class SearchableMultiChoiceDialogBuilder {
    @NonNull
    private FragmentActivity activity;
    @NonNull
    private List<String> items;
    @NonNull
    private List<CharSequence> itemNames;
    @Nullable
    private List<String> selectedItems;
    @NonNull
    private MaterialAlertDialogBuilder builder;
    @NonNull
    private RecyclerView recyclerView;
    SearchableRecyclerViewAdapter adapter;

    public interface OnClickListener {
        void onClick(DialogInterface dialog, int which, @NonNull List<String> selectedItems);
    }

    @SuppressLint("InflateParams")
    public SearchableMultiChoiceDialogBuilder(@NonNull FragmentActivity activity, @NonNull List<String> items, @NonNull List<CharSequence> itemNames) {
        this.activity = activity;
        this.items = items;
        this.itemNames = itemNames;
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_searchable_multi_choice, null);
        recyclerView = view.findViewById(android.R.id.list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false));
        TextInputEditText editText = view.findViewById(R.id.search_bar);
        editText.addTextChangedListener(new TextWatcher() {
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
        builder = new MaterialAlertDialogBuilder(activity).setView(view);
    }

    public SearchableMultiChoiceDialogBuilder setSelections(@Nullable List<String> selectedItems) {
        this.selectedItems = selectedItems;
        return this;
    }

    public SearchableMultiChoiceDialogBuilder setTitle(@Nullable CharSequence title) {
        builder.setTitle(title);
        return this;
    }

    public SearchableMultiChoiceDialogBuilder setTitle(@StringRes int title) {
        builder.setTitle(title);
        return this;
    }

    public SearchableMultiChoiceDialogBuilder setPositiveButton(@StringRes int textId, OnClickListener listener) {
        builder.setPositiveButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, adapter.getSelectedItems());
        });
        return this;
    }

    public SearchableMultiChoiceDialogBuilder setPositiveButton(@NonNull CharSequence text, OnClickListener listener) {
        builder.setPositiveButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, adapter.getSelectedItems());
        });
        return this;
    }

    public SearchableMultiChoiceDialogBuilder setNegativeButton(@StringRes int textId, OnClickListener listener) {
        builder.setNegativeButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, adapter.getSelectedItems());
        });
        return this;
    }

    public SearchableMultiChoiceDialogBuilder setNegativeButton(@NonNull CharSequence text, OnClickListener listener) {
        builder.setNegativeButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, adapter.getSelectedItems());
        });
        return this;
    }

    public SearchableMultiChoiceDialogBuilder setNeutralButton(@StringRes int textId, OnClickListener listener) {
        builder.setNeutralButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, adapter.getSelectedItems());
        });
        return this;
    }

    public SearchableMultiChoiceDialogBuilder setNeutralButton(@NonNull CharSequence text, OnClickListener listener) {
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
        private final List<String> items;
        @NonNull
        private final List<String> notFoundItems = new ArrayList<>();
        @NonNull
        private final ArrayList<Integer> filteredItems = new ArrayList<>();
        @NonNull
        private final ArrayList<Integer> selectedItems = new ArrayList<>();

        SearchableRecyclerViewAdapter(@NonNull List<CharSequence> itemNames, @NonNull List<String> items, @Nullable List<String> preSelectedItems) {
            this.itemNames = itemNames;
            this.items = items;
            if (preSelectedItems != null) {
                for (String item : preSelectedItems) {
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
                        || items.get(i).toLowerCase(Locale.ROOT).contains(constraint)) {
                    filteredItems.add(i);
                }
            }
            notifyDataSetChanged();
        }

        ArrayList<String> getSelectedItems() {
            ArrayList<String> selections = new ArrayList<>(notFoundItems);
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
