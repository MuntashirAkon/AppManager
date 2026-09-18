// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

// Inspired by the work of Laurence Dawson, which is based on ImageViewTouchBase class in the AOSP.
public class ZoomableImageView extends AppCompatImageView {
    private static final float MAX_SCALE = 8f;
    private static final float DOUBLE_TAP_SCALE = 2f;

    private final Matrix mImageMatrix = new Matrix();
    private final ScaleGestureDetector mScaleDetector;
    private final GestureDetector mGestureDetector;
    private float mScale = 1f;
    private int mRotation;
    private float mLastX;
    private float mLastY;
    private boolean mMoving;
    private boolean mScaling;

    public ZoomableImageView(@NonNull Context context) {
        this(context, null);
    }

    public ZoomableImageView(@NonNull Context context, @Nullable android.util.AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);
        mScaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                mScaling = true;
                return getDrawable() != null;
            }

            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                if (getDrawable() == null) return false;
                float nextScale = Math.max(1f, Math.min(MAX_SCALE, mScale * detector.getScaleFactor()));
                float factor = nextScale / mScale;
                mImageMatrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                mScale = nextScale;
                constrainMatrix();
                setImageMatrix(mImageMatrix);
                return true;
            }

            @Override
            public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                mScaling = false;
                constrainMatrix();
                setImageMatrix(mImageMatrix);
            }
        });
        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent event) {
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent event) {
                if (getDrawable() == null) return true;
                if (mScale > 1.01f) {
                    resetTransform();
                } else {
                    float factor = DOUBLE_TAP_SCALE / mScale;
                    mImageMatrix.postScale(factor, factor, event.getX(), event.getY());
                    mScale = DOUBLE_TAP_SCALE;
                    constrainMatrix();
                    setImageMatrix(mImageMatrix);
                }
                return true;
            }
        });
    }

    @Override
    public void setImageBitmap(@Nullable Bitmap bitmap) {
        super.setImageBitmap(bitmap);
        mRotation = 0;
        mScale = 1f;
        post(this::resetTransform);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (getDrawable() != null) resetTransform();
    }

    public void resetTransform() {
        if (getDrawable() == null || getWidth() <= 0 || getHeight() <= 0) return;
        mScale = 1f;
        mImageMatrix.reset();
        RectF image = getImageBounds();
        float scale = Math.min(getWidth() / image.width(), getHeight() / image.height());
        mImageMatrix.postScale(scale, scale, image.centerX(), image.centerY());
        mImageMatrix.postRotate(mRotation, image.centerX(), image.centerY());
        image = getImageBounds();
        mImageMatrix.postTranslate(getWidth() / 2f - image.centerX(), getHeight() / 2f - image.centerY());
        constrainMatrix();
        setImageMatrix(mImageMatrix);
    }

    public void rotateLeft() {
        mRotation = (mRotation + 270) % 360;
        resetTransform();
    }

    public void rotateRight() {
        mRotation = (mRotation + 90) % 360;
        resetTransform();
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        mScaleDetector.onTouchEvent(event);
        mGestureDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mLastX = event.getX();
                mLastY = event.getY();
                mMoving = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!mScaling && event.getPointerCount() == 1 && mMoving && getDrawable() != null) {
                    float dx = event.getX() - mLastX;
                    float dy = event.getY() - mLastY;
                    mImageMatrix.postTranslate(dx, dy);
                    constrainMatrix();
                    setImageMatrix(mImageMatrix);
                    mLastX = event.getX();
                    mLastY = event.getY();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mMoving = false;
                constrainMatrix();
                setImageMatrix(mImageMatrix);
                return true;
            default:
                return true;
        }
    }

    @NonNull
    private RectF getImageBounds() {
        android.graphics.drawable.Drawable drawable = getDrawable();
        RectF bounds = new RectF(0, 0, drawable != null ? drawable.getIntrinsicWidth() : 0,
                drawable != null ? drawable.getIntrinsicHeight() : 0);
        Matrix matrix = new Matrix();
        matrix.setRotate(mRotation, bounds.centerX(), bounds.centerY());
        matrix.mapRect(bounds);
        return bounds;
    }

    private void constrainMatrix() {
        if (getDrawable() == null) return;
        RectF bounds = new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight());
        mImageMatrix.mapRect(bounds);
        float dx = bounds.width() <= getWidth() ? getWidth() / 2f - bounds.centerX()
                : Math.max(getWidth() - bounds.right, Math.min(-bounds.left, 0));
        float dy = bounds.height() <= getHeight() ? getHeight() / 2f - bounds.centerY()
                : Math.max(getHeight() - bounds.bottom, Math.min(-bounds.top, 0));
        mImageMatrix.postTranslate(dx, dy);
    }
}
