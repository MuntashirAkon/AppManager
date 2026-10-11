// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.pdf;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.widget.RecyclerView;

/**
 * A vertically scrolling PDF viewport with document-wide pinch zoom.
 * <p>
 * Vertical movement is left to RecyclerView so the normal page list remains
 * usable. Once zoomed, horizontal movement translates the viewport and is
 * clamped to the scaled document width.
 */
public final class ZoomablePdfRecyclerView extends RecyclerView {
    private static final float MAX_SCALE = 8f;
    private static final float SCALE_EPSILON = 0.01f;

    private final ScaleGestureDetector mScaleDetector;
    private final GestureDetector mGestureDetector;
    private float mScale = 1f;
    private float mTranslationX;
    private float mLastX;
    private boolean mMoving;
    private boolean mScaling;
    private final int mBasePaddingBottom;

    public ZoomablePdfRecyclerView(@NonNull Context context) {
        this(context, null);
    }

    public ZoomablePdfRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mBasePaddingBottom = getPaddingBottom();
        mScaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                        mScaling = true;
                        return true;
                    }

                    @Override
                    public boolean onScale(@NonNull ScaleGestureDetector detector) {
                        float nextScale = Math.max(1f,
                                Math.min(MAX_SCALE, mScale * detector.getScaleFactor()));
                        if (nextScale == mScale) {
                            return true;
                        }
                        float factor = nextScale / mScale;
                        float focusX = detector.getFocusX();
                        mTranslationX = focusX - (focusX - mTranslationX) * factor;
                        mScale = nextScale;
                        invalidate();
                        return true;
                    }

                    @Override
                    public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                        mScaling = false;
                        constrainTranslation();
                        updateScrollExtent();
                        invalidate();
                    }
                });
        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent event) {
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent event) {
                if (mScale > 1f + SCALE_EPSILON) {
                    resetZoom();
                } else {
                    setZoom(2f, event.getX());
                }
                return true;
            }
        });
        setPivotX(0);
        setPivotY(0);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        mScaleDetector.onTouchEvent(event);
        mGestureDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mLastX = event.getX();
                mMoving = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mMoving && !mScaling && !mScaleDetector.isInProgress()
                        && event.getPointerCount() == 1
                        && mScale > 1f + SCALE_EPSILON) {
                    mTranslationX += event.getX() - mLastX;
                    constrainTranslation();
                    invalidate();
                    mLastX = event.getX();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mMoving = false;
                constrainTranslation();
                invalidate();
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    void resetZoom() {
        mScale = 1f;
        mTranslationX = 0f;
        updateScrollExtent();
        invalidate();
    }

    private void setZoom(float zoom, float focusX) {
        float nextScale = Math.max(1f, Math.min(MAX_SCALE, zoom));
        float factor = nextScale / mScale;
        mTranslationX = focusX - (focusX - mTranslationX) * factor;
        mScale = nextScale;
        constrainTranslation();
        updateScrollExtent();
        invalidate();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        int saveCount = canvas.save();
        canvas.scale(mScale, mScale);
        canvas.translate(mTranslationX / mScale, 0f);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(saveCount);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateScrollExtent();
    }

    private void constrainTranslation() {
        float scaledWidth = getWidth() * mScale;
        if (scaledWidth <= getWidth()) {
            mTranslationX = 0f;
        } else {
            mTranslationX = Math.max(getWidth() - scaledWidth,
                    Math.min(0f, mTranslationX));
        }
    }

    private void updateScrollExtent() {
        int extraBottomPadding = getHeight() > 0
                ? Math.round(getHeight() * (1f - 1f / mScale)) : 0;
        int bottomPadding = mBasePaddingBottom + Math.max(0, extraBottomPadding);
        if (getPaddingBottom() != bottomPadding) {
            setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), bottomPadding);
        }
    }
}
