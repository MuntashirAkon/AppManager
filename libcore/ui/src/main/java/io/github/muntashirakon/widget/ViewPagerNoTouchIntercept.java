// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

public class ViewPagerNoTouchIntercept extends ViewPager {
    private final boolean mDisallowInterceptTouchEvent = true;

    public ViewPagerNoTouchIntercept(@NonNull Context context) {
        this(context, null);
    }

    public ViewPagerNoTouchIntercept(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        requestDisallowInterceptTouchEvent(mDisallowInterceptTouchEvent);
    }

    @CallSuper
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        getParent().requestDisallowInterceptTouchEvent(mDisallowInterceptTouchEvent);
        return super.dispatchTouchEvent(ev);
    }

    @CallSuper
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (mDisallowInterceptTouchEvent) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    requestDisallowInterceptTouchEvent(false);
                    break;
            }
        }
        return super.onTouchEvent(event);
    }
}
