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

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AnyFilterArrayAdapter<T> extends ArrayAdapter<T> {
    @NonNull
    private final List<T> objects;
    private final Filter filter = new Filter() {
        @NonNull
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults filterResults = new FilterResults();
            String query;
            if (constraint == null || (query = constraint.toString().trim()).length() == 0) {
                filterResults.count = objects.size();
                filterResults.values = objects;
                return filterResults;
            }

            query = query.toLowerCase(Locale.ROOT);
            List<T> list = new ArrayList<>(objects.size());
            for (T item : objects) {
                if (item.toString().toLowerCase(Locale.ROOT).contains(query))
                    list.add(item);
            }
            filterResults.count = list.size();
            filterResults.values = list;
            return filterResults;
        }

        @Override
        protected void publishResults(CharSequence constraint, @NonNull FilterResults results) {
            clear();
            //noinspection unchecked
            addAll((List<T>) results.values);
            notifyDataSetChanged();
        }
    };

    public AnyFilterArrayAdapter(@NonNull Context context, int resource, @NonNull List<T> objects) {
        super(context, resource, new ArrayList<>(objects));
        this.objects = objects;
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return filter;
    }
}
