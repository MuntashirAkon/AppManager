package io.github.muntashirakon.AppManager.types;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

// https://stackoverflow.com/a/24453194/4147849 CC BY-SA 3.0
public class ScrollSafeSwipeRefreshLayout extends SwipeRefreshLayout {
    private int mTouchSlop;
    private float mPrevX;
    // Indicate if we've already declined the move event
    private boolean mDeclined;

    public ScrollSafeSwipeRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                MotionEvent motionEvent = MotionEvent.obtain(event);
                mPrevX = motionEvent.getX();
                mDeclined = false; // New action
                motionEvent.recycle();
                break;
            case MotionEvent.ACTION_MOVE:
                final float eventX = event.getX();
                float xDiff = Math.abs(eventX - mPrevX);
                if (mDeclined || xDiff > mTouchSlop) {
                    mDeclined = true; // Memorize
                    return false;
                }
        }
        return super.onInterceptTouchEvent(event);
    }
}