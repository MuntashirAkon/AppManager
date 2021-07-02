// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.collection.ArraySet;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.widget.CheckBox;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;

public class SearchableMultiChoiceDialogBuilder<T> {
    @NonNull
    private final FragmentActivity activity;
    @NonNull
    private final MaterialAlertDialogBuilder builder;
    private final View searchBar;
    private final CheckBox selectAll;
    @NonNull
    private final SearchableRecyclerViewAdapter adapter;

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
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_searchable_multi_choice, null);
        RecyclerView recyclerView = view.findViewById(android.R.id.list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false));
        new FastScrollerBuilder(recyclerView).useMd2Style().build();
        searchBar = view.findViewById(R.id.search_bar);
        selectAll = view.findViewById(android.R.id.checkbox);
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
        if (items.size() < 6) searchBar.setVisibility(View.GONE);
        builder = new MaterialAlertDialogBuilder(activity).setView(view);
        adapter = new SearchableRecyclerViewAdapter(itemNames, items);
        recyclerView.setAdapter(adapter);
        selectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                adapter.selectAll();
            } else {
                adapter.deselectAll();
            }
        });
        if (items.size() < 2) {
            // No need to display select all if only one item is present
            selectAll.setVisibility(View.GONE);
        }
        checkSelections();
    }

    public SearchableMultiChoiceDialogBuilder<T> setSelections(@Nullable List<T> selectedItems) {
        adapter.setSelectedItems(selectedItems);
        checkSelections();
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> hideSearchBar(boolean hide) {
        this.searchBar.setVisibility(hide ? View.GONE : View.VISIBLE);
        return this;
    }

    public SearchableMultiChoiceDialogBuilder<T> showSelectAll(boolean show) {
        this.selectAll.setVisibility(show ? View.VISIBLE : View.GONE);
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
        return builder.create();
    }

    public void show() {
        create().show();
    }

    private void checkSelections() {
        selectAll.setChecked(adapter.areAllSelected(), false);
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
        private final Set<Integer> selectedItems = new ArraySet<>();

        SearchableRecyclerViewAdapter(@NonNull List<CharSequence> itemNames, @NonNull List<T> items) {
            this.itemNames = itemNames;
            this.items = items;
            new Thread(() -> {
                synchronized (filteredItems) {
                    for (int i = 0; i < items.size(); ++i) {
                        filteredItems.add(i);
                    }
                }
            }).start();
        }

        void setFilteredItems(CharSequence constraint) {
            Locale locale = LangUtils.getLocaleByLanguage(activity);
            synchronized (filteredItems) {
                filteredItems.clear();
                for (int i = 0; i < items.size(); ++i) {
                    if (itemNames.get(i).toString().toLowerCase(locale).contains(constraint)
                            || items.get(i).toString().toLowerCase(Locale.ROOT).contains(constraint)) {
                        filteredItems.add(i);
                    }
                }
                checkSelections();
                notifyDataSetChanged();
            }
        }

        ArrayList<T> getSelectedItems() {
            ArrayList<T> selections = new ArrayList<>(notFoundItems);
            synchronized (selectedItems) {
                for (int item : selectedItems) {
                    selections.add(items.get(item));
                }
            }
            return selections;
        }

        void setSelectedItems(@Nullable List<T> selectedItems) {
            if (selectedItems != null) {
                for (T item : selectedItems) {
                    int index = items.indexOf(item);
                    if (index != -1) {
                        synchronized (this.selectedItems) {
                            this.selectedItems.add(index);
                        }
                    } else notFoundItems.add(item);
                }
            }
        }

        void selectAll() {
            synchronized (selectedItems) {
                synchronized (filteredItems) {
                    selectedItems.addAll(filteredItems);
                    checkSelections();
                    notifyDataSetChanged();
                }
            }
        }

        void deselectAll() {
            synchronized (selectedItems) {
                synchronized (filteredItems) {
                    //noinspection SlowAbstractSetRemoveAll
                    selectedItems.removeAll(filteredItems);
                    checkSelections();
                    notifyDataSetChanged();
                }
            }
        }

        boolean areAllSelected() {
            synchronized (selectedItems) {
                synchronized (filteredItems) {
                    return selectedItems.containsAll(filteredItems);
                }
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            @SuppressLint("PrivateResource")
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.mtrl_alert_select_dialog_multichoice, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SearchableRecyclerViewAdapter.ViewHolder holder, int position) {
            Integer index;
            synchronized (filteredItems) {
                index = filteredItems.get(position);
            }
            final AtomicBoolean selected;
            synchronized (selectedItems) {
                selected = new AtomicBoolean(selectedItems.contains(index));
            }
            holder.item.setText(itemNames.get(index));
            holder.item.setChecked(selected.get());
            holder.item.setOnClickListener(v -> {
                synchronized (selectedItems) {
                    if (selected.get()) {
                        selectedItems.remove(index);
                    } else selectedItems.add(index);
                }
                selected.set(!selected.get());
                holder.item.setChecked(selected.get());
                checkSelections();
            });
        }

        @Override
        public int getItemCount() {
            synchronized (filteredItems) {
                return filteredItems.size();
            }
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
