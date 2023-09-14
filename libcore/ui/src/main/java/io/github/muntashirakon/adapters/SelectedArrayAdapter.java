// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.ArrayRes;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Same as {@link ArrayAdapter} except that it selects the TextView to allow marquee mode.
 */
public class SelectedArrayAdapter<T> extends ArrayAdapter<T> {

    public static @NonNull ArrayAdapter<CharSequence> createFromResource(@NonNull Context context,
                                                                         @ArrayRes int textArrayResId,
                                                                         @LayoutRes int textViewResId) {
        final CharSequence[] strings = context.getResources().getTextArray(textArrayResId);
        return new SelectedArrayAdapter<>(context, textViewResId, 0, Arrays.asList(strings));
    }

    private final int mFieldId;

    public SelectedArrayAdapter(@NonNull Context context, @LayoutRes int resource) {
        this(context, resource, 0, new ArrayList<>());
    }

    public SelectedArrayAdapter(@NonNull Context context, @LayoutRes int resource, @IdRes int textViewResourceId) {
        this(context, resource, textViewResourceId, new ArrayList<>());
    }

    public SelectedArrayAdapter(@NonNull Context context, @LayoutRes int resource, @NonNull T[] objects) {
        this(context, resource, 0, Arrays.asList(objects));
    }

    public SelectedArrayAdapter(@NonNull Context context, @LayoutRes int resource, @IdRes int textViewResourceId, @NonNull T[] objects) {
        this(context, resource, textViewResourceId, Arrays.asList(objects));
    }

    public SelectedArrayAdapter(@NonNull Context context, @LayoutRes int resource, @NonNull List<T> objects) {
        this(context, resource, 0, objects);
    }

    public SelectedArrayAdapter(@NonNull Context context, @LayoutRes int resource, @IdRes int textViewResourceId, @NonNull List<T> objects) {
        super(context, resource, textViewResourceId, objects);
        mFieldId = 0;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View v = super.getView(position, convertView, parent);
        return setSelected(v);
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View v = super.getDropDownView(position, convertView, parent);
        return setSelected(v);
    }

    @NonNull
    private View setSelected(@NonNull View v) {
        TextView tv;
        if (mFieldId == 0) {
            //  If no custom field is assigned, assume the whole resource is a TextView
            tv = (TextView) v;
        } else {
            //  Otherwise, find the TextView field within the layout
            tv = v.findViewById(mFieldId);
        }
        if (tv.isSelected()) {
            tv.setSelected(false);
        }
        tv.setSelected(true);
        return v;
    }
}
