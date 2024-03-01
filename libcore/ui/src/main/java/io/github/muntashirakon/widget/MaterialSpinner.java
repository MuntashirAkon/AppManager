// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Filterable;
import android.widget.ListAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;

import io.github.muntashirakon.ui.R;
import io.github.muntashirakon.view.TextInputLayoutCompat;

public class MaterialSpinner extends TextInputLayout {
    private final MaterialAutoCompleteTextView mAutoCompleteTextView;

    private int mSelection;

    public MaterialSpinner(@NonNull Context context) {
        this(context, null);
    }

    public MaterialSpinner(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.materialSpinnerStyle);
    }

    public MaterialSpinner(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        context = getContext();
        mAutoCompleteTextView = new MaterialAutoCompleteTextView(context);
        addView(mAutoCompleteTextView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mAutoCompleteTextView.setInputType(EditorInfo.TYPE_NULL);
        mAutoCompleteTextView.setKeyListener(null);
        mAutoCompleteTextView.setFocusable(false);
        setEndIconMode(END_ICON_DROPDOWN_MENU);
        TextInputLayoutCompat.fixEndIcon(this);
    }

    public <T extends ListAdapter & Filterable> void setAdapter(@Nullable T adapter) {
        mAutoCompleteTextView.setAdapter(adapter);
        if (adapter != null) {
            setSelection(mSelection);
        }
    }

    public void setOnItemClickListener(@Nullable AdapterView.OnItemClickListener listener) {
        mAutoCompleteTextView.setOnItemClickListener(listener);
    }

    public void setSelection(int position) {
        mSelection = position;
        ListAdapter adapter = mAutoCompleteTextView.getAdapter();
        Object object;
        if (adapter == null || mSelection < 0 || adapter.getCount() <= mSelection) {
            object = null;
        } else {
            object = adapter.getItem(mSelection);
        }
        mAutoCompleteTextView.setText(object == null ? "" : object.toString(), false);
    }
}
