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
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
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
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class SearchableMultiChoiceDialog extends DialogFragment {
    public static final String TAG = "MultiChoiceDialogFragment";
    public static final String EXTRA_ITEMS = "items";
    public static final String EXTRA_ITEM_NAMES = "item_names";
    public static final String EXTRA_SELECTED_ITEMS = "selected_items";

    public interface SelectionCompleteInterface {
        void onSelectionComplete(@NonNull List<String> selectedItems);
    }

    SelectionCompleteInterface selectionCompleteInterface;

    public void setOnSelectionComplete(SelectionCompleteInterface selectionCompleteInterface) {
        this.selectionCompleteInterface = selectionCompleteInterface;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        FragmentActivity activity = requireActivity();
        List<String> items = requireArguments().getStringArrayList(EXTRA_ITEMS);
        List<CharSequence> itemNames = requireArguments().getCharSequenceArrayList(EXTRA_ITEM_NAMES);
        List<String> selectedItems = requireArguments().getStringArrayList(EXTRA_SELECTED_ITEMS);
        if (items == null || itemNames == null || items.size() != itemNames.size())
            return super.onCreateDialog(savedInstanceState);
        LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater == null) return super.onCreateDialog(savedInstanceState);
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.dialog_searchable_multi_choice, null);
        RecyclerView recyclerView = view.findViewById(android.R.id.list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false));
        SearchableRecyclerViewAdapter adapter = new SearchableRecyclerViewAdapter(itemNames, items, selectedItems);
        recyclerView.setAdapter(adapter);
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
        return new MaterialAlertDialogBuilder(activity)
                .setView(view)
                .setTitle(R.string.apps)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    if (selectionCompleteInterface != null) {
                        selectionCompleteInterface.onSelectionComplete(adapter.getSelectedItems());
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
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
            Locale locale = LangUtils.getLocaleByLanguage(requireActivity());
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
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.mtrl_alert_select_dialog_multichoice, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
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
