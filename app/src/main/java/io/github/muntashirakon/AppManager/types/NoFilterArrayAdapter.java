// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class NoFilterArrayAdapter<T> extends ArrayAdapter<T> {
    private final Filter dummyFilter = new Filter() {
        @Override
        @Nullable
        protected FilterResults performFiltering(CharSequence constraint) {
            return null;
        }

        @Override
        protected void publishResults(CharSequence constraint, @Nullable FilterResults results) {
            notifyDataSetChanged();
        }
    };

    public NoFilterArrayAdapter(@NonNull Context context, int resource, @NonNull List<T> objects) {
        super(context, resource, objects);
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return dummyFilter;
    }
}
