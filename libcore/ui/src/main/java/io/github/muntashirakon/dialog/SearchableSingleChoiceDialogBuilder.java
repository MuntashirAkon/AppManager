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
import io.github.muntashirakon.widget.SearchView;

public class SearchableSingleChoiceDialogBuilder<T> {
    @NonNull
    private final MaterialAlertDialogBuilder builder;
    private final SearchView searchView;
    @NonNull
    private final SearchableRecyclerViewAdapter adapter;
    @Nullable
    private AlertDialog dialog;
    @Nullable
    private OnSingleChoiceClickListener<T> onSingleChoiceClickListener;
    private boolean isTextSelectable;

    public interface OnClickListener<T> {
        void onClick(DialogInterface dialog, int which, @Nullable T selectedItem);
    }

    public interface OnSingleChoiceClickListener<T> {
        void onClick(DialogInterface dialog, int which, T item, boolean isChecked);
    }

    public SearchableSingleChoiceDialogBuilder(@NonNull Context context, @NonNull List<T> items, @ArrayRes int itemNames) {
        this(context, items, context.getResources().getTextArray(itemNames));
    }

    public SearchableSingleChoiceDialogBuilder(@NonNull Context context, @NonNull List<T> items, CharSequence[] itemNames) {
        this(context, items, Arrays.asList(itemNames));
    }

    public SearchableSingleChoiceDialogBuilder(@NonNull Context context, @NonNull T[] items, @NonNull CharSequence[] itemNames) {
        this(context, Arrays.asList(items), Arrays.asList(itemNames));
    }

    public SearchableSingleChoiceDialogBuilder(@NonNull Context context, @NonNull List<T> items, @NonNull List<CharSequence> itemNames) {
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
        if (items.size() < 6) {
            searchView.setVisibility(View.GONE);
        }
        builder = new MaterialAlertDialogBuilder(context)
                .setView(view);
        @SuppressLint({"RestrictedApi", "PrivateResource"})
        int layoutId = MaterialAttributes.resolveInteger(context, androidx.appcompat.R.attr.singleChoiceItemLayout,
                com.google.android.material.R.layout.mtrl_alert_select_dialog_singlechoice);
        adapter = new SearchableRecyclerViewAdapter(itemNames, items, layoutId);
        recyclerView.setAdapter(adapter);
    }

    public SearchableSingleChoiceDialogBuilder<T> setOnSingleChoiceClickListener(@Nullable OnSingleChoiceClickListener<T>
                                                                                         onSingleChoiceClickListener) {
        this.onSingleChoiceClickListener = onSingleChoiceClickListener;
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> addDisabledItems(@Nullable List<T> disabledItems) {
        adapter.addDisabledItems(disabledItems);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setSelection(@Nullable T selectedItem) {
        adapter.setSelection(selectedItem);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setSelectionIndex(int selectedIndex) {
        adapter.setSelectedIndex(selectedIndex);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> removeSelection() {
        adapter.setSelectedIndex(-1);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> reloadListUi() {
        adapter.notifyDataSetChanged();
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setTextSelectable(boolean textSelectable) {
        this.isTextSelectable = textSelectable;
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setCancelable(boolean cancelable) {
        builder.setCancelable(cancelable);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> hideSearchBar(boolean hide) {
        this.searchView.setVisibility(hide ? View.GONE : View.VISIBLE);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setTitle(@Nullable CharSequence title) {
        builder.setTitle(title);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setTitle(@StringRes int title) {
        builder.setTitle(title);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setTitle(View title) {
        builder.setCustomTitle(title);
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setPositiveButton(@StringRes int textId, OnClickListener<T> listener) {
        builder.setPositiveButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, adapter.getSelection());
        });
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setPositiveButton(@NonNull CharSequence text, OnClickListener<T> listener) {
        builder.setPositiveButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, adapter.getSelection());
        });
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setNegativeButton(@StringRes int textId, OnClickListener<T> listener) {
        builder.setNegativeButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, adapter.getSelection());
        });
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setNegativeButton(@NonNull CharSequence text, OnClickListener<T> listener) {
        builder.setNegativeButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, adapter.getSelection());
        });
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setNeutralButton(@StringRes int textId, OnClickListener<T> listener) {
        builder.setNeutralButton(textId, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, adapter.getSelection());
        });
        return this;
    }

    public SearchableSingleChoiceDialogBuilder<T> setNeutralButton(@NonNull CharSequence text, OnClickListener<T> listener) {
        builder.setNeutralButton(text, (dialog, which) -> {
            if (listener != null) listener.onClick(dialog, which, adapter.getSelection());
        });
        return this;
    }

    public AlertDialog create() {
        return dialog = builder.create();
    }

    public AlertDialog show() {
        return dialog = builder.show();
    }

    private void triggerSingleChoiceClickListener(int index, boolean isChecked) {
        if (dialog != null && onSingleChoiceClickListener != null) {
            onSingleChoiceClickListener.onClick(dialog, index, adapter.items.get(index), isChecked);
        }
    }

    class SearchableRecyclerViewAdapter extends RecyclerView.Adapter<SearchableRecyclerViewAdapter.ViewHolder> {
        @NonNull
        private final List<CharSequence> itemNames;
        @NonNull
        private final List<T> items;
        @NonNull
        private final ArrayList<Integer> filteredItems = new ArrayList<>();
        private int selectedItem = -1;
        private final Set<Integer> disabledItems = new ArraySet<>();
        @LayoutRes
        private final int layoutId;


        SearchableRecyclerViewAdapter(@NonNull List<CharSequence> itemNames, @NonNull List<T> items, int layoutId) {
            this.itemNames = itemNames;
            this.items = items;
            this.layoutId = layoutId;
            new Thread(() -> {
                synchronized (filteredItems) {
                    for (int i = 0; i < items.size(); ++i) {
                        filteredItems.add(i);
                    }
                }
            }, "searchable_single_choice_dialog").start();
        }

        void setFilteredItems(CharSequence constraint) {
            Locale locale = Locale.getDefault();
            synchronized (filteredItems) {
                filteredItems.clear();
                for (int i = 0; i < items.size(); ++i) {
                    if (itemNames.get(i).toString().toLowerCase(locale).contains(constraint)
                            || items.get(i).toString().toLowerCase(Locale.ROOT).contains(constraint)) {
                        filteredItems.add(i);
                    }
                }
                notifyDataSetChanged();
            }
        }

        @Nullable
        T getSelection() {
            if (selectedItem >= 0) {
                return items.get(selectedItem);
            }
            return null;
        }

        void setSelection(@Nullable T selectedItem) {
            if (selectedItem != null) {
                int index = items.indexOf(selectedItem);
                if (index != -1) {
                    setSelectedIndex(index);
                }
            }
        }

        void setSelectedIndex(int selectedIndex) {
            if (selectedIndex == selectedItem) {
                // Do nothing
                return;
            }
            updateSelection(false);
            selectedItem = selectedIndex;
            updateSelection(true);
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
        public SearchableRecyclerViewAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
            return new SearchableRecyclerViewAdapter.ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SearchableRecyclerViewAdapter.ViewHolder holder, int position) {
            Integer index;
            synchronized (filteredItems) {
                index = filteredItems.get(position);
            }
            final AtomicBoolean selected = new AtomicBoolean(selectedItem == index);
            holder.item.setText(itemNames.get(index));
            holder.item.setTextIsSelectable(isTextSelectable);
            synchronized (disabledItems) {
                holder.item.setEnabled(!disabledItems.contains(index));
            }
            holder.item.setChecked(selected.get());
            holder.item.setOnClickListener(v -> {
                if (selected.get()) {
                    // Already selected, do nothing
                    return;
                }
                // Unselect the previous and select this one
                updateSelection(false);
                selectedItem = index;
                // Update selection manually
                selected.set(!selected.get());
                holder.item.setChecked(selected.get());
                triggerSingleChoiceClickListener(index, selected.get());
            });
        }

        @Override
        public int getItemCount() {
            synchronized (filteredItems) {
                return filteredItems.size();
            }
        }

        private void updateSelection(boolean selected) {
            if (selectedItem < 0) {
                return;
            }
            int position;
            synchronized (filteredItems) {
                position = filteredItems.indexOf(selectedItem);
            }
            if (position >= 0) {
                notifyItemChanged(position);
            }
            triggerSingleChoiceClickListener(selectedItem, selected);
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
