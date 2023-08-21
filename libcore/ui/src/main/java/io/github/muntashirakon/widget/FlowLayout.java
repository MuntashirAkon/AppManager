// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.ui.R;

// Copyright 2017 Tianxing Li
// Copyright 2021 Muntashir Al-Islam
@SuppressWarnings("unused")
public class FlowLayout extends ViewGroup {
    private static final String TAG = FlowLayout.class.getSimpleName();

    /**
     * Special value for the child view spacing.
     * SPACING_AUTO mean's that the actual spacing is calculated according to the size of the
     * container and the number of the child views, so that the child views are placed evenly in
     * the container.
     */
    public static final int SPACING_AUTO = -65536;

    /**
     * Special value for the horizontal spacing of the child views in the last row
     * SPACING_ALIGN means that the horizontal spacing of the child views in the last row keeps
     * the same with the spacing used in the row above. If there is only one row, this value is
     * ignored and the spacing will be calculated according to childSpacing.
     */
    public static final int SPACING_ALIGN = -65537;

    private static final int SPACING_UNDEFINED = -65538;

    private static final int UNSPECIFIED_GRAVITY = -1;

    private static final int ROW_VERTICAL_GRAVITY_AUTO = -65536;

    private boolean mSingleLine;
    private int mChildSpacing;
    private int mMinChildSpacing;
    private int mChildSpacingForLastRow;
    private float mRowSpacing;
    private float mAdjustedRowSpacing = 0;
    private int mMaxRows;
    private int mGravity;
    private int mRowVerticalGravity;
    private int mExactMeasuredHeight;

    private final List<Float> mHorizontalSpacingForRow = new ArrayList<>();
    private final List<Integer> mHeightForRow = new ArrayList<>();
    private final List<Integer> mWidthForRow = new ArrayList<>();
    private final List<Integer> mChildNumForRow = new ArrayList<>();

    public FlowLayout(Context context) {
        this(context, null);
    }

    public FlowLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FlowLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public FlowLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        TypedArray array = context.getTheme().obtainStyledAttributes(attrs, R.styleable.FlowLayout, 0, 0);
        try {
            mSingleLine = array.getBoolean(R.styleable.FlowLayout_singleLine, false);
            mChildSpacing = getDimensionOrInt(array, R.styleable.FlowLayout_childSpacing, 0);
            mMinChildSpacing = getDimensionOrInt(array, R.styleable.FlowLayout_minChildSpacing, 0);
            mChildSpacingForLastRow = getDimensionOrInt(array, R.styleable.FlowLayout_childSpacingForLastRow, SPACING_UNDEFINED);
            mRowSpacing = getDimensionOrInt(array, R.styleable.FlowLayout_rowSpacing, 0);
            mMaxRows = array.getInt(R.styleable.FlowLayout_maxRows, Integer.MAX_VALUE);
            mGravity = array.getInt(R.styleable.FlowLayout_android_gravity, UNSPECIFIED_GRAVITY);
            mRowVerticalGravity = array.getInt(R.styleable.FlowLayout_rowVerticalGravity, ROW_VERTICAL_GRAVITY_AUTO);
            if (isInEditMode()) {
                int listItemId = array.getResourceId(R.styleable.FlowLayout_listItem, 0);
                int itemCount = array.getInt(R.styleable.FlowLayout_itemCount, 10);
                initPreview(listItemId, itemCount);
            }
        } finally {
            array.recycle();
        }
    }

    private void initPreview(@LayoutRes int layoutId, int count) {
        if (layoutId != 0) {
            for (int i = 0; i < count; ++i) {
                LayoutInflater.from(getContext()).inflate(layoutId, this, true);
            }
        }
    }

    private int getDimensionOrInt(@NonNull TypedArray a, int index, int defValue) {
        TypedValue tv = new TypedValue();
        a.getValue(index, tv);
        if (tv.type == TypedValue.TYPE_DIMENSION) {
            return a.getDimensionPixelSize(index, defValue);
        } else {
            return a.getInt(index, defValue);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        mHorizontalSpacingForRow.clear();
        mHeightForRow.clear();
        mWidthForRow.clear();
        mChildNumForRow.clear();

        int measuredHeight = 0;
        int measuredWidth = 0;
        int childCount = getChildCount();
        int rowWidth = 0;
        int maxChildHeightInRow = 0;
        int childNumInRow = 0;
        final int rowSize = widthSize - getPaddingLeft() - getPaddingRight();
        int rowTotalChildWidth = 0;
        final boolean allowFlow = widthMode != MeasureSpec.UNSPECIFIED && !mSingleLine;
        final int childSpacing = mChildSpacing == SPACING_AUTO && widthMode == MeasureSpec.UNSPECIFIED
                ? 0 : mChildSpacing;
        final float tmpSpacing = childSpacing == SPACING_AUTO ? mMinChildSpacing : childSpacing;

        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }

            LayoutParams childParams = child.getLayoutParams();
            int horizontalMargin = 0;
            int verticalMargin = 0;
            if (childParams instanceof MarginLayoutParams) {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, measuredHeight);
                MarginLayoutParams marginParams = (MarginLayoutParams) childParams;
                horizontalMargin = marginParams.leftMargin + marginParams.rightMargin;
                verticalMargin = marginParams.topMargin + marginParams.bottomMargin;
            } else {
                measureChild(child, widthMeasureSpec, heightMeasureSpec);
            }

            int childWidth = child.getMeasuredWidth() + horizontalMargin;
            int childHeight = child.getMeasuredHeight() + verticalMargin;
            if (allowFlow && rowWidth + childWidth > rowSize) { // Need flow to next row
                // Save parameters for current row
                mHorizontalSpacingForRow.add(
                        getSpacingForRow(childSpacing, rowSize, rowTotalChildWidth, childNumInRow));
                mChildNumForRow.add(childNumInRow);
                mHeightForRow.add(maxChildHeightInRow);
                mWidthForRow.add(rowWidth - (int) tmpSpacing);
                if (mHorizontalSpacingForRow.size() <= mMaxRows) {
                    measuredHeight += maxChildHeightInRow;
                }
                measuredWidth = Math.max(measuredWidth, rowWidth);

                // Place the child view to next row
                childNumInRow = 1;
                rowWidth = childWidth + (int) tmpSpacing;
                rowTotalChildWidth = childWidth;
                maxChildHeightInRow = childHeight;
            } else {
                childNumInRow++;
                rowWidth += childWidth + (int) tmpSpacing;
                rowTotalChildWidth += childWidth;
                maxChildHeightInRow = Math.max(maxChildHeightInRow, childHeight);
            }
        }

        // Measure remaining child views in the last row
        if (mChildSpacingForLastRow == SPACING_ALIGN) {
            // For SPACING_ALIGN, use the same spacing from the row above if there is more than one row.
            if (mHorizontalSpacingForRow.size() >= 1) {
                mHorizontalSpacingForRow.add(
                        mHorizontalSpacingForRow.get(mHorizontalSpacingForRow.size() - 1));
            } else {
                mHorizontalSpacingForRow.add(
                        getSpacingForRow(childSpacing, rowSize, rowTotalChildWidth, childNumInRow));
            }
        } else if (mChildSpacingForLastRow != SPACING_UNDEFINED) {
            // For SPACING_AUTO and specific DP values, apply them to the spacing strategy.
            mHorizontalSpacingForRow.add(
                    getSpacingForRow(mChildSpacingForLastRow, rowSize, rowTotalChildWidth, childNumInRow));
        } else {
            // For SPACING_UNDEFINED, apply childSpacing to the spacing strategy for the last row.
            mHorizontalSpacingForRow.add(
                    getSpacingForRow(childSpacing, rowSize, rowTotalChildWidth, childNumInRow));
        }

        mChildNumForRow.add(childNumInRow);
        mHeightForRow.add(maxChildHeightInRow);
        mWidthForRow.add(rowWidth - (int) tmpSpacing);
        if (mHorizontalSpacingForRow.size() <= mMaxRows) {
            measuredHeight += maxChildHeightInRow;
        }
        measuredWidth = Math.max(measuredWidth, rowWidth);

        if (childSpacing == SPACING_AUTO) {
            measuredWidth = widthSize;
        } else if (widthMode == MeasureSpec.UNSPECIFIED) {
            measuredWidth = measuredWidth + getPaddingLeft() + getPaddingRight();
        } else {
            measuredWidth = Math.min(measuredWidth + getPaddingLeft() + getPaddingRight(), widthSize);
        }

        measuredHeight += getPaddingTop() + getPaddingBottom();
        int rowNum = Math.min(mHorizontalSpacingForRow.size(), mMaxRows);
        float rowSpacing = mRowSpacing == SPACING_AUTO && heightMode == MeasureSpec.UNSPECIFIED
                ? 0 : mRowSpacing;
        if (rowSpacing == SPACING_AUTO) {
            if (rowNum > 1) {
                mAdjustedRowSpacing = (heightSize - measuredHeight) / (rowNum - 1f);
            } else {
                mAdjustedRowSpacing = 0;
            }
            measuredHeight = heightSize;
        } else {
            mAdjustedRowSpacing = rowSpacing;
            if (rowNum > 1) {
                measuredHeight = heightMode == MeasureSpec.UNSPECIFIED
                        ? ((int) (measuredHeight + mAdjustedRowSpacing * (rowNum - 1)))
                        : (Math.min((int) (measuredHeight + mAdjustedRowSpacing * (rowNum - 1)),
                        heightSize));
            }
        }

        mExactMeasuredHeight = measuredHeight;

        measuredWidth = widthMode == MeasureSpec.EXACTLY ? widthSize : measuredWidth;
        measuredHeight = heightMode == MeasureSpec.EXACTLY ? heightSize : measuredHeight;

        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int paddingStart = getPaddingStart();
        final int paddingEnd = getPaddingEnd();
        final int paddingTop = getPaddingTop();
        final int paddingBottom = getPaddingBottom();

        final boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int x;
        int y = paddingTop;

        final int verticalGravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
        final int horizontalGravity = mGravity & Gravity.HORIZONTAL_GRAVITY_MASK;

        // Calculate y
        switch (verticalGravity) {
            case Gravity.CENTER_VERTICAL: {
                int offset = (b - t - paddingTop - paddingBottom - mExactMeasuredHeight) / 2;
                y += offset;
                break;
            }
            case Gravity.BOTTOM: {
                int offset = b - t - paddingTop - paddingBottom - mExactMeasuredHeight;
                y += offset;
                break;
            }
            default:
                break;
        }

        final int horizontalPadding = paddingStart + paddingEnd;
        final int layoutWidth = r - l;

        int verticalRowGravity = mRowVerticalGravity & Gravity.VERTICAL_GRAVITY_MASK;

        int rowCount = mChildNumForRow.size();
        int childIdx = 0;
        for (int row = 0; row < Math.min(rowCount, mMaxRows); row++) {
            int childNum = mChildNumForRow.get(row);
            int rowHeight = mHeightForRow.get(row);
            float spacing = mHorizontalSpacingForRow.get(row);
            x = paddingStart + getHorizontalGravityOffsetForRow(horizontalGravity, layoutWidth, horizontalPadding, row);
            if (isRtl) {
                x = layoutWidth - x;
            }
            for (int i = 0; i < childNum && childIdx < getChildCount(); ) {
                View child = getChildAt(childIdx++);
                if (child.getVisibility() == GONE) {
                    continue;
                } else {
                    i++;
                }

                LayoutParams childParams = child.getLayoutParams();
                int marginStart = 0;
                int marginTop = 0;
                int marginBottom = 0;
                int marginEnd = 0;
                if (childParams instanceof MarginLayoutParams) {
                    MarginLayoutParams marginParams = (MarginLayoutParams) childParams;
                    marginStart = marginParams.getMarginStart();
                    marginEnd = marginParams.getMarginEnd();
                    marginTop = marginParams.topMargin;
                    marginBottom = marginParams.bottomMargin;
                }

                int childWidth = child.getMeasuredWidth();
                int childHeight = child.getMeasuredHeight();
                int tt = y + marginTop;
                if (verticalRowGravity == Gravity.BOTTOM) {
                    tt = y + rowHeight - marginBottom - childHeight;
                } else if (verticalRowGravity == Gravity.CENTER_VERTICAL) {
                    tt = y + marginTop + (rowHeight - marginTop - marginBottom - childHeight) / 2;
                }
                int bb = tt + childHeight;
                if (isRtl) {
                    int l2 = x - marginStart;
                    int r2 = l2 - childWidth;
                    child.layout(r2, tt, l2, bb);
                } else {
                    int l2 = x + marginStart;
                    int r2 = l2 + childWidth;
                    child.layout(l2, tt, r2, bb);
                }
                // The width consumed by this child
                int consumedWidth = childWidth + (int) spacing + marginStart + marginEnd;
                x = x + (isRtl ? -consumedWidth : consumedWidth);
            }
            y += rowHeight + (int) mAdjustedRowSpacing;
        }

        for (int i = childIdx; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            if (isRtl) {
                child.layout(layoutWidth, 0, layoutWidth, 0);
            } else {
                child.layout(0, 0, 0, 0);
            }
        }
    }

    private int getHorizontalGravityOffsetForRow(int horizontalGravity, int parentLayoutWidth, int horizontalPadding,
                                                 int row) {
        if (mChildSpacing == SPACING_AUTO || row >= mWidthForRow.size()
                || row >= mChildNumForRow.size() || mChildNumForRow.get(row) <= 0) {
            return 0;
        }

        int offset = 0;
        switch (horizontalGravity) {
            case Gravity.CENTER_HORIZONTAL:
                // (Layout width - (total horizontal padding + real row width)) / 2
                offset = (parentLayoutWidth - horizontalPadding - mWidthForRow.get(row)) / 2;
                break;
            case Gravity.END:
                offset = parentLayoutWidth - horizontalPadding - mWidthForRow.get(row);
                break;
            default:
                break;
        }
        return offset;
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return new MarginLayoutParams(p);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    /**
     * Whether the items are constrained to a single horizontal line.
     *
     * @return {@code true} if the items are constrained to a single horizontal line, {@code false} otherwise.
     */
    public boolean isSingleLine() {
        return mSingleLine;
    }

    /**
     * Sets whether to constraint items to a single horizontal line.
     *
     * @param singleLine {@code true} to constraint items to a single horizontal line, {@code false} otherwise.
     */
    public void setSingleLine(boolean singleLine) {
        mSingleLine = singleLine;
        requestLayout();
    }

    /**
     * Returns the horizontal spacing between child views.
     *
     * @return The spacing, either {@link FlowLayout#SPACING_AUTO}, or a fixed size in pixels.
     */
    public int getChildSpacing() {
        return mChildSpacing;
    }

    /**
     * Sets the horizontal spacing between child views.
     *
     * @param childSpacing The spacing, either {@link FlowLayout#SPACING_AUTO}, or a fixed size in
     *                     pixels.
     */
    public void setChildSpacing(int childSpacing) {
        mChildSpacing = childSpacing;
        requestLayout();
    }

    /**
     * Returns the horizontal spacing between child views of the last row.
     *
     * @return The spacing, either {@link FlowLayout#SPACING_AUTO},
     * {@link FlowLayout#SPACING_ALIGN}, or a fixed size in pixels
     */
    public int getChildSpacingForLastRow() {
        return mChildSpacingForLastRow;
    }

    /**
     * Sets the horizontal spacing between child views of the last row.
     *
     * @param childSpacingForLastRow The spacing, either {@link FlowLayout#SPACING_AUTO},
     *                               {@link FlowLayout#SPACING_ALIGN}, or a fixed size in pixels
     */
    public void setChildSpacingForLastRow(int childSpacingForLastRow) {
        mChildSpacingForLastRow = childSpacingForLastRow;
        requestLayout();
    }

    /**
     * Returns the vertical spacing between rows.
     *
     * @return The spacing, either {@link FlowLayout#SPACING_AUTO}, or a fixed size in pixels.
     */
    public float getRowSpacing() {
        return mRowSpacing;
    }

    /**
     * Sets the vertical spacing between rows in pixels. Use SPACING_AUTO to evenly place all rows
     * in vertical.
     *
     * @param rowSpacing The spacing, either {@link FlowLayout#SPACING_AUTO}, or a fixed size in
     *                   pixels.
     */
    public void setRowSpacing(float rowSpacing) {
        mRowSpacing = rowSpacing;
        requestLayout();
    }

    /**
     * Returns the maximum number of rows of the FlowLayout.
     *
     * @return The maximum number of rows.
     */
    public int getMaxRows() {
        return mMaxRows;
    }

    /**
     * Sets the height of the FlowLayout to be at most maxRows tall.
     *
     * @param maxRows The maximum number of rows.
     */
    public void setMaxRows(int maxRows) {
        mMaxRows = maxRows;
        requestLayout();
    }

    public void setGravity(int gravity) {
        if (mGravity != gravity) {
            mGravity = gravity;
            requestLayout();
        }
    }

    public void setRowVerticalGravity(int rowVerticalGravity) {
        if (mRowVerticalGravity != rowVerticalGravity) {
            mRowVerticalGravity = rowVerticalGravity;
            requestLayout();
        }
    }

    public int getMinChildSpacing() {
        return mMinChildSpacing;
    }

    public void setMinChildSpacing(int minChildSpacing) {
        this.mMinChildSpacing = minChildSpacing;
        requestLayout();
    }

    public int getRowsCount() {
        return mChildNumForRow.size();
    }

    private float getSpacingForRow(int spacingAttribute, int rowSize, int usedSize, int childNum) {
        if (spacingAttribute == SPACING_AUTO) {
            if (childNum > 1) {
                return (rowSize - usedSize) / (childNum - 1f);
            }
            return 0;
        }
        return spacingAttribute;
    }
}