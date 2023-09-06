// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.adapters;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An {@link ArrayAdapter} that filters using {@link String#contains(CharSequence)} (case-insensitive) rather than using
 * {@link String#startsWith(String)} (i.e. prefix matching).
 */
public class AnyFilterArrayAdapter<T> extends SelectedArrayAdapter<T> {
    @NonNull
    private final List<T> mObjects;
    private final Filter mFilter = new Filter() {
        @NonNull
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults filterResults = new FilterResults();
            String query;
            if (constraint == null || constraint.length() == 0) {
                filterResults.count = mObjects.size();
                filterResults.values = mObjects;
                return filterResults;
            }

            query = constraint.toString().toLowerCase(Locale.ROOT);
            List<T> list = new ArrayList<>(mObjects.size());
            for (T item : mObjects) {
                if (item.toString().toLowerCase(Locale.ROOT).contains(query))
                    list.add(item);
            }
            filterResults.count = list.size();
            filterResults.values = list;
            return filterResults;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence constraint, @NonNull FilterResults results) {
            clear();
            addAll((List<T>) results.values);
            notifyDataSetChanged();
        }
    };

    public AnyFilterArrayAdapter(@NonNull Context context, @LayoutRes int resource, @NonNull List<T> objects) {
        super(context, resource, new ArrayList<>(objects));
        mObjects = objects;
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return mFilter;
    }
}
