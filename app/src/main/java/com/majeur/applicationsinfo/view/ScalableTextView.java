package com.majeur.applicationsinfo.view;

import android.content.Context;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.TextView;

public class ScalableTextView extends TextView {

    private ScaleGestureDetector mScaleGestureDetector;

    public ScalableTextView(Context context) {
        super(context);
        mScaleGestureDetector = new ScaleGestureDetector(context, new ScaleDetector());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getPointerCount() > 1) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Disallow ScrollView to intercept touch events.
                    getParent().requestDisallowInterceptTouchEvent(true);
                    mScaleGestureDetector.onTouchEvent(event);
                    break;

                case MotionEvent.ACTION_MOVE:
                    // Disallow ScrollView to intercept touch events.
                    getParent().requestDisallowInterceptTouchEvent(true);
                    mScaleGestureDetector.onTouchEvent(event);
                    break;

                case MotionEvent.ACTION_UP:
                    // Allow ScrollView to intercept touch events.
                    getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
        }
        return true;
    }

    private class ScaleDetector extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float size = getTextSize();

            float factor = detector.getScaleFactor();

            float product = size * factor;
            setTextSize(TypedValue.COMPLEX_UNIT_PX, product);
            return true;
        }
    }
}
