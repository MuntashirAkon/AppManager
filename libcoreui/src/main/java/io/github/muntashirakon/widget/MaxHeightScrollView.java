// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.ScrollView;

import io.github.muntashirakon.libcoreui.R;
import io.github.muntashirakon.utils.UiUtils;

// Copyright 2016 Bumsoo Kim
public class MaxHeightScrollView extends ScrollView {
    private int maxHeight;

    public MaxHeightScrollView(Context context) {
        super(context);
    }

    public MaxHeightScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public MaxHeightScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public MaxHeightScrollView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs == null) {
            return;
        }

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MaxHeightScrollView);

        if (a.hasValue(R.styleable.MaxHeightScrollView_maxHeight)) {
            setMaxHeight(a.getDimensionPixelSize(R.styleable.MaxHeightScrollView_maxHeight, 0));
        }

        a.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
    }

    public void setMaxHeightDp(int maxHeightDp) {
        this.maxHeight = UiUtils.dpToPx(getContext(), maxHeightDp);
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public int getMaxHeightDp() {
        return UiUtils.pxToDp(getContext(), maxHeight);
    }
}
