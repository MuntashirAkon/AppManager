// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.adapters;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * An {@link ArrayAdapter} incapable of filtering i.e. returns everything regardless of the filtered text.
 */
public class NoFilterArrayAdapter<T> extends SelectedArrayAdapter<T> {
    private final Filter mDummyFilter = new Filter() {
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

    public NoFilterArrayAdapter(@NonNull Context context, @LayoutRes int resource, @NonNull List<T> objects) {
        super(context, resource, objects);
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return mDummyFilter;
    }
}
