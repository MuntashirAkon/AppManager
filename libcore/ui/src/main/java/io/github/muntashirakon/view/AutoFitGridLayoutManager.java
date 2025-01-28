// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.view;

import android.content.Context;

import androidx.annotation.Px;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// Source: https://stackoverflow.com/a/55487382
// Copyright 2019 Vishal Nagvadiya
public class AutoFitGridLayoutManager extends GridLayoutManager {
    @Px
    private int mColumnWidth;
    private boolean mColumnWidthChanged = true;

    public AutoFitGridLayoutManager(Context context, @Px int columnWidth) {
        super(context, 1);
        setColumnWidth(columnWidth);
    }


    public void setColumnWidth(@Px int newColumnWidth) {
        if (newColumnWidth > 0 && newColumnWidth != mColumnWidth) {
            mColumnWidth = newColumnWidth;
            mColumnWidthChanged = true;
        }
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (mColumnWidthChanged && mColumnWidth > 0) {
            int totalSpace;
            if (getOrientation() == VERTICAL) {
                totalSpace = getWidth() - getPaddingRight() - getPaddingLeft();
            } else {
                totalSpace = getHeight() - getPaddingTop() - getPaddingBottom();
            }
            int spanCount = Math.max(1, totalSpace / mColumnWidth);
            setSpanCount(spanCount);
            mColumnWidthChanged = false;
        }
        super.onLayoutChildren(recycler, state);
    }
}
