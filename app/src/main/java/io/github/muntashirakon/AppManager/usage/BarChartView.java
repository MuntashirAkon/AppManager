// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import static com.google.android.material.theme.overlay.MaterialThemeOverlay.wrap;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TintTypedArray;

import com.google.android.material.internal.ThemeEnforcement;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.util.UiUtils;

@SuppressWarnings("unused")
public class BarChartView extends View {
    private static final int DEF_STYLE_RES = R.style.Widget_AppTheme_BarChartView;
    // Data structures
    private final List<BarData> mBarDataList = new ArrayList<>();
    private final List<String> mXAxisLabels = new ArrayList<>();

    // Drawing objects
    private Paint mBarPaint;
    private Paint mSelectedBarPaint;
    private Paint mGridPaint;
    private Paint mTextPaint;
    private Paint mTouchLinePaint;
    private Paint mTooltipTextPaint;
    private Paint mTooltipBgPaint;

    private float mChartWidth;
    private float mChartHeight;
    private float mChartLeft;
    private float mChartTop;
    private float mChartRight;
    private float mChartBottom;

    // Touch handling
    private boolean mShowTouchLine = false;
    private int mTouchedBarIndex = -1;

    // Chart configuration
    private int mGridLineCount;
    private boolean mShowGridLabelsOnLeft;
    private float mMaxValue = 0f;
    @Nullable
    private Float mManualMinValue = null;
    @Nullable
    private Float mManualMaxValue = null;
    @Nullable
    private String mYAxisFormat;
    private final DecimalFormat mValueFormatter = new DecimalFormat("#.#");

    // Custom attributes with defaults
    private int mBarColor;
    private int mSelectedBarColor;
    private int mGridColor;
    private float mGridStrokeWidth;
    private int mTextColor;
    private float mTextSizeSp;
    private int mTouchLineColor;
    private float mTouchLineWidth;
    private float mMinBarWidthDp;
    private float mMaxBarWidthDp;
    private int mTooltipBgColor;
    private int mTooltipTextColor;
    private float mTooltipCornerRadius;
    private boolean mUseCustomLabelSpacing = false;
    private int mCustomSkipEvery = 1;
    private int mCustomStartFrom = 0;
    @Nullable
    private String mEmptyText;
    private boolean mValueOnTopOfBar;

    // Tooltip listener
    @Nullable
    private TooltipListener mTooltipListener = null;

    public interface TooltipListener {
        /**
         * Called when tooltip should be displayed
         *
         * @param barIndex Index of the touched bar
         * @param value    Value of the bar
         * @param label    Label of the bar
         * @return Custom tooltip text, or null to use default format
         */
        @Nullable
        String getTooltipText(int barIndex, float value, String label);
    }

    public BarChartView(@NonNull Context context) {
        this(context, null);
    }

    public BarChartView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.barChartViewStyle);
    }

    @SuppressLint("RestrictedApi")
    public BarChartView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(wrap(context, attrs, defStyleAttr, DEF_STYLE_RES), attrs, defStyleAttr);
        context = getContext();
        final TintTypedArray a = ThemeEnforcement.obtainTintedStyledAttributes(
                context, attrs, R.styleable.BarChartView, defStyleAttr, DEF_STYLE_RES);
        try {
            mBarColor = a.getColor(R.styleable.BarChartView_barColor, 0);
            mSelectedBarColor = a.getColor(R.styleable.BarChartView_selectedBarColor, 0);
            mGridColor = a.getColor(R.styleable.BarChartView_gridColor, 0);
            mGridStrokeWidth = a.getDimension(R.styleable.BarChartView_gridStrokeWidth, 0);
            mTextColor = a.getColor(R.styleable.BarChartView_textColor, 0);
            mTextSizeSp = a.getDimension(R.styleable.BarChartView_textSize, 0);
            mTouchLineColor = a.getColor(R.styleable.BarChartView_touchLineColor, 0);
            mTouchLineWidth = a.getDimension(R.styleable.BarChartView_touchLineWidth, 0);
            mMinBarWidthDp = a.getDimension(R.styleable.BarChartView_minBarWidth, 0);
            mMaxBarWidthDp = a.getDimension(R.styleable.BarChartView_maxBarWidth, 0);
            mTooltipBgColor = a.getColor(R.styleable.BarChartView_tooltipBackgroundColor, 0);
            mTooltipTextColor = a.getColor(R.styleable.BarChartView_tooltipTextColor, 0);
            mTooltipCornerRadius = a.getDimension(R.styleable.BarChartView_tooltipCornerRadius, 0);
            mValueOnTopOfBar = a.getBoolean(R.styleable.BarChartView_valueOnTopOfBar, false);
            mGridLineCount = a.getInt(R.styleable.BarChartView_gridLineCount, 0);
            mShowGridLabelsOnLeft = a.getBoolean(R.styleable.BarChartView_gridLabelsOnLeft, true);
            String format = a.getString(R.styleable.BarChartView_yAxisFormat);
            if (!TextUtils.isEmpty(format)) {
                mYAxisFormat = format;
            } else mYAxisFormat = null;
            mEmptyText = a.getString(R.styleable.BarChartView_emptyText);
        } finally {
            a.recycle();
        }
        initializePaints();
    }

    private void initializePaints() {
        mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBarPaint.setColor(mBarColor);
        mBarPaint.setStyle(Paint.Style.FILL);

        mSelectedBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mSelectedBarPaint.setColor(mSelectedBarColor);
        mSelectedBarPaint.setStyle(Paint.Style.FILL);

        mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGridPaint.setColor(mGridColor);
        mGridPaint.setStrokeWidth(mGridStrokeWidth);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(mTextColor);
        mTextPaint.setTextSize(mTextSizeSp);

        mTouchLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTouchLinePaint.setColor(mTouchLineColor);
        mTouchLinePaint.setStrokeWidth(mTouchLineWidth);
        mTouchLinePaint.setStyle(Paint.Style.STROKE);
        mTouchLinePaint.setPathEffect(new DashPathEffect(new float[]{12, 6}, 0));

        mTooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTooltipTextPaint.setColor(mTooltipTextColor);
        mTooltipTextPaint.setTextSize(spToPx(13));

        mTooltipBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTooltipBgPaint.setColor(mTooltipBgColor);
        mTooltipBgPaint.setStyle(Paint.Style.FILL);
    }

    private float dpToPx(float dp) {
        return UiUtils.dpToPx(getContext(), dp);
    }

    private float spToPx(float sp) {
        return UiUtils.spToPx(getContext(), sp);
    }

    public void setTooltipListener(@Nullable TooltipListener listener) {
        mTooltipListener = listener;
    }

    public void setManualYAxisRange(@Nullable Float minValue, @Nullable Float maxValue) {
        mManualMinValue = minValue;
        mManualMaxValue = maxValue;
        calculateValueRange();
        invalidate();
    }

    // e.g., "%.2f min"
    public void setYAxisFormat(@Nullable String format) {
        mYAxisFormat = format;
        invalidate();
    }

    public void setBarColor(@ColorInt int color) {
        mBarColor = color;
        mBarPaint.setColor(color);
        invalidate();
    }

    public void setSelectedBarColor(@ColorInt int color) {
        mSelectedBarColor = color;
        mSelectedBarPaint.setColor(color);
        invalidate();
    }

    /**
     * Set data for the bar chart
     */
    public void setData(@Nullable List<Float> values, @Nullable List<String> labels) {
        mBarDataList.clear();
        mXAxisLabels.clear();

        if (values != null && labels != null && values.size() == labels.size()) {
            for (int i = 0; i < values.size(); i++) {
                mBarDataList.add(new BarData(values.get(i), labels.get(i)));
                mXAxisLabels.add(labels.get(i));
            }
            calculateValueRange();
        }

        invalidate();
    }

    private void calculateValueRange() {
        if (mBarDataList.isEmpty()) {
            mMaxValue = 1f;
            return;
        }

        List<Float> values = new ArrayList<>();
        for (BarData bar : mBarDataList) {
            values.add(bar.value);
        }

        if (mManualMaxValue != null) {
            mMaxValue = mManualMaxValue;
        } else {
            mMaxValue = Collections.max(values);
        }

        if (mManualMinValue != null) {
            // TODO: 9/21/25 Handle manual min value. All our charts for now begins with 0
        }
    }

    /**
     * Specify the position of the Y-axis labels. Labels are shown on the left hand-side by default.
     * Setting the argument to false results in labels being shown on the right hand-side.
     */
    public void setGridLabelsOnLeft(boolean onLeft) {
        mShowGridLabelsOnLeft = onLeft;
        invalidate();
    }

    /**
     * A minimum 2 grid lines are required. Default is 6.
     */
    public void setGridLineCount(int count) {
        mGridLineCount = Math.max(2, count);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        calculateDynamicMargins(w, h);
    }

    private void calculateDynamicMargins(int width, int height) {
        float leftMargin;
        float rightMargin;
        float topMargin;
        float bottomMargin;
        if (mBarDataList.isEmpty()) {
            leftMargin = dpToPx(40);
            rightMargin = dpToPx(20);
            topMargin = dpToPx(20);
            bottomMargin = dpToPx(40);
        } else {
            // Measure Y-axis labels
            float maxYLabelWidth = 0f;
            for (int i = 0; i < mGridLineCount; i++) {
                float value = (mMaxValue * i) / (mGridLineCount - 1);
                String label = formatYAxisValue(value);
                Rect textBounds = new Rect();
                mTextPaint.getTextBounds(label, 0, label.length(), textBounds);
                maxYLabelWidth = Math.max(maxYLabelWidth, textBounds.width());
            }

            // Measure X-axis labels
            float maxXLabelWidth = 0f;
            float maxXLabelHeight = 0f;
            for (String label : mXAxisLabels) {
                Rect textBounds = new Rect();
                mTextPaint.getTextBounds(label, 0, label.length(), textBounds);
                maxXLabelWidth = Math.max(maxXLabelWidth, textBounds.width());
                maxXLabelHeight = Math.max(maxXLabelHeight, textBounds.height());
            }

            leftMargin = maxYLabelWidth + dpToPx(12);
            rightMargin = Math.max(dpToPx(16), maxXLabelWidth * 0.5f);
            topMargin = dpToPx(30);
            bottomMargin = maxXLabelHeight + dpToPx(16);
        }

        mChartLeft = leftMargin;
        mChartTop = topMargin;
        mChartRight = width - rightMargin;
        mChartBottom = height - bottomMargin;
        mChartWidth = mChartRight - mChartLeft;
        mChartHeight = mChartBottom - mChartTop;
    }

    @NonNull
    private String formatYAxisValue(float value) {
        if (mYAxisFormat != null) {
            return String.format(mYAxisFormat, value);
        } else {
            return mValueFormatter.format(value);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        if (mBarDataList.isEmpty()) {
            drawEmptyState(canvas);
            return;
        }

        calculateDynamicMargins(getWidth(), getHeight());

        drawGridLines(canvas);
        drawBarsWithSpacing(canvas);
        drawXAxisLabelsWithTickMarks(canvas);

        // Draw touch line LAST to ensure visibility
        if (mShowTouchLine && mTouchedBarIndex >= 0) {
            drawTouchLineAndTooltip(canvas);
        }
    }

    private void drawEmptyState(@NonNull Canvas canvas) {
        if (TextUtils.isEmpty(mEmptyText)) {
            return;
        }
        Paint emptyTextPaint = new Paint(mTextPaint);
        emptyTextPaint.setTextSize(spToPx(16));
        emptyTextPaint.setColor(mTextColor);

        Rect textBounds = new Rect();
        emptyTextPaint.getTextBounds(mEmptyText, 0, mEmptyText.length(), textBounds);

        float x = (getWidth() - textBounds.width()) / 2f;
        float y = (getHeight() + textBounds.height()) / 2f;

        canvas.drawText(mEmptyText, x, y, emptyTextPaint);
    }

    private void drawGridLines(@NonNull Canvas canvas) {
        for (int i = 0; i < mGridLineCount; i++) {
            float y = mChartBottom - (i * mChartHeight / (mGridLineCount - 1));

            // Draw horizontal grid line
            canvas.drawLine(mChartLeft, y, mChartRight, y, mGridPaint);

            // Draw grid value labels
            float value = (mMaxValue * i) / (mGridLineCount - 1);
            String label = formatYAxisValue(value);

            Rect textBounds = new Rect();
            mTextPaint.getTextBounds(label, 0, label.length(), textBounds);

            if (mShowGridLabelsOnLeft) {
                canvas.drawText(label, mChartLeft - textBounds.width() - dpToPx(8),
                        y + textBounds.height() / 2f, mTextPaint);
            } else {
                canvas.drawText(label, mChartRight + dpToPx(8),
                        y + textBounds.height() / 2f, mTextPaint);
            }
        }
    }

    private void drawBarsWithSpacing(@NonNull Canvas canvas) {
        int barCount = mBarDataList.size();
        if (barCount == 0) return;

        // Calculate bar dimensions
        BarDimensions dims = calculateBarDimensions();

        for (int i = 0; i < barCount; i++) {
            BarData bar = mBarDataList.get(i);

            float barLeft = mChartLeft + dims.gapWidth + (i * (dims.barWidth + dims.gapWidth));
            float barRight = barLeft + dims.barWidth;
            float barTop = mChartBottom - (bar.value / mMaxValue) * mChartHeight;
            float barBottom = mChartBottom;

            // Choose paint based on selection state
            Paint currentBarPaint = (i == mTouchedBarIndex) ? mSelectedBarPaint : mBarPaint;

            // Draw bar
            canvas.drawRect(barLeft, barTop, barRight, barBottom, currentBarPaint);

            if (mValueOnTopOfBar) {
                // Draw value on top of bar
                if (bar.value > 0 && barTop > mChartTop + dpToPx(20)) {
                    String valueText = formatYAxisValue(bar.value);
                    Rect textBounds = new Rect();
                    mTextPaint.getTextBounds(valueText, 0, valueText.length(), textBounds);

                    float textX = barLeft + (dims.barWidth - textBounds.width()) / 2f;
                    float textY = barTop - dpToPx(5);

                    if (textBounds.width() <= dims.barWidth) {
                        canvas.drawText(valueText, textX, textY, mTextPaint);
                    }
                }
            }
        }
    }

    @NonNull
    private BarDimensions calculateBarDimensions() {
        int barCount = mBarDataList.size();
        float minGap = dpToPx(4);
        float totalGapWidth = minGap * (barCount + 1);
        float availableWidthForBars = mChartWidth - totalGapWidth;
        float calculatedBarWidth = availableWidthForBars / barCount;

        // Apply min/max constraints
        float barWidth = Math.max(mMinBarWidthDp, Math.min(calculatedBarWidth, mMaxBarWidthDp));

        float actualTotalBarWidth = barWidth * barCount;
        float remainingWidth = mChartWidth - actualTotalBarWidth;
        float gapWidth = remainingWidth / (barCount + 1);

        return new BarDimensions(barWidth, gapWidth);
    }

    private static class BarDimensions {
        final float barWidth;
        final float gapWidth;

        BarDimensions(float barWidth, float gapWidth) {
            this.barWidth = barWidth;
            this.gapWidth = gapWidth;
        }
    }

    private void drawTouchLineAndTooltip(@NonNull Canvas canvas) {
        if (mTouchedBarIndex < 0 || mTouchedBarIndex >= mBarDataList.size()) return;

        BarDimensions dims = calculateBarDimensions();
        float barCenter = mChartLeft + dims.gapWidth + (mTouchedBarIndex * (dims.barWidth + dims.gapWidth)) + dims.barWidth / 2f;

        // Calculate the bar's top position
        BarData bar = mBarDataList.get(mTouchedBarIndex);
        float barTop = mChartBottom - (bar.value / mMaxValue) * mChartHeight;

        // Draw vertical line from chart top to bar top
        canvas.drawLine(barCenter, mChartTop, barCenter, barTop, mTouchLinePaint);

        // Draw tooltip
        drawTooltip(canvas, barCenter);
    }

    private void drawTooltip(@NonNull Canvas canvas, float lineX) {
        if (mTouchedBarIndex < 0 || mTouchedBarIndex >= mBarDataList.size()) return;

        BarData bar = mBarDataList.get(mTouchedBarIndex);
        String tooltipText;

        // Use custom tooltip if listener is set
        if (mTooltipListener != null) {
            String customText = mTooltipListener.getTooltipText(mTouchedBarIndex, bar.value, bar.label);
            if (customText != null) {
                tooltipText = customText;
            } else {
                tooltipText = String.format("(%s, %s)", bar.label, formatYAxisValue(bar.value));
            }
        } else {
            tooltipText = String.format("(%s, %s)", bar.label, formatYAxisValue(bar.value));
        }

        Rect textBounds = new Rect();
        mTooltipTextPaint.getTextBounds(tooltipText, 0, tooltipText.length(), textBounds);

        float tooltipWidth = textBounds.width() + dpToPx(16);
        float tooltipHeight = textBounds.height() + dpToPx(12);

        float tooltipX = lineX - tooltipWidth / 2f;
        float tooltipY = mChartTop - tooltipHeight - dpToPx(8);

        // Keep tooltip within bounds
        tooltipX = Math.max(dpToPx(8), Math.min(tooltipX, getWidth() - tooltipWidth - dpToPx(8)));
        tooltipY = Math.max(dpToPx(8), tooltipY);

        // Draw tooltip background
        canvas.drawRoundRect(tooltipX, tooltipY,
                tooltipX + tooltipWidth, tooltipY + tooltipHeight,
                mTooltipCornerRadius, mTooltipCornerRadius, mTooltipBgPaint);

        // Draw tooltip text
        canvas.drawText(tooltipText,
                tooltipX + dpToPx(8),
                tooltipY + tooltipHeight - dpToPx(6),
                mTooltipTextPaint);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                handleTouch(event.getX(), event.getY());
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mShowTouchLine = false;
                mTouchedBarIndex = -1;
                invalidate();
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    private void handleTouch(float x, float y) {
        if (mBarDataList.isEmpty()) return;

        if (x < mChartLeft || x > mChartRight || y < mChartTop || y > mChartBottom) {
            mShowTouchLine = false;
            mTouchedBarIndex = -1;
            invalidate();
            return;
        }

        BarDimensions dims = calculateBarDimensions();

        for (int i = 0; i < mBarDataList.size(); i++) {
            float barLeft = mChartLeft + dims.gapWidth + (i * (dims.barWidth + dims.gapWidth));
            float barRight = barLeft + dims.barWidth;

            if (x >= barLeft && x <= barRight) {
                mTouchedBarIndex = i;
                mShowTouchLine = true;
                invalidate();
                break;
            }
        }
    }

    private void drawXAxisLabelsWithTickMarks(@NonNull Canvas canvas) {
        int barCount = mBarDataList.size();
        if (barCount == 0) return;

        BarDimensions dims = calculateBarDimensions();

        // Calculate label spacing to prevent overlapping
        LabelSpacing spacing;
        if (mUseCustomLabelSpacing) {
            spacing = new LabelSpacing(mCustomSkipEvery, mCustomStartFrom);
        } else {
            spacing = calculateOptimalLabelSpacing(dims);
        }

        for (int i = 0; i < mXAxisLabels.size(); i++) {
            // Only draw labels that should be shown based on spacing calculation
            if (!spacing.shouldShowLabel(i)) {
                continue;
            }

            float barCenter = mChartLeft + dims.gapWidth + (i * (dims.barWidth + dims.gapWidth)) + dims.barWidth / 2f;

            // Draw tick mark ONLY for labels that are shown
            float tickTop = mChartBottom;
            float tickBottom = mChartBottom + dpToPx(4); // 4dp tick mark height
            canvas.drawLine(barCenter, tickTop, barCenter, tickBottom, mGridPaint);

            // Draw label
            String label = mXAxisLabels.get(i);
            Rect textBounds = new Rect();
            mTextPaint.getTextBounds(label, 0, label.length(), textBounds);

            float textX = barCenter - textBounds.width() / 2f;
            float textY = mChartBottom + textBounds.height() + dpToPx(8);

            canvas.drawText(label, textX, textY, mTextPaint);
        }
    }

    @NonNull
    private LabelSpacing calculateOptimalLabelSpacing(@NonNull BarDimensions dims) {
        if (mXAxisLabels.isEmpty()) {
            return new LabelSpacing(1, 0);
        }

        // Measure the widest label to determine minimum spacing needed
        float maxLabelWidth = 0f;
        for (String label : mXAxisLabels) {
            Rect textBounds = new Rect();
            mTextPaint.getTextBounds(label, 0, label.length(), textBounds);
            maxLabelWidth = Math.max(maxLabelWidth, textBounds.width());
        }

        // Add padding between labels
        float minSpacingNeeded = maxLabelWidth + dpToPx(8);

        // Calculate available space per label
        float availableSpacePerLabel = dims.barWidth + dims.gapWidth;

        // Determine how many labels to skip
        int skipCount = 1;
        if (availableSpacePerLabel < minSpacingNeeded) {
            skipCount = (int) Math.ceil(minSpacingNeeded / availableSpacePerLabel);
        }

        int startOffset = 0;

        return new LabelSpacing(skipCount, startOffset);
    }

    private static class LabelSpacing {
        private final int skipCount;
        private final int startOffset;

        LabelSpacing(int skipCount, int startOffset) {
            this.skipCount = Math.max(1, skipCount);
            this.startOffset = Math.max(0, startOffset);
        }

        boolean shouldShowLabel(int index) {
            return (index - startOffset) % skipCount == 0 && index >= startOffset;
        }
    }

    /**
     * Set custom label spacing pattern
     *
     * @param skipEvery Show every Nth label (1 = show all, 2 = show every other, etc.)
     * @param startFrom Index to start showing labels from (0-based)
     */
    public void setLabelSkipPattern(int skipEvery, int startFrom) {
        mCustomSkipEvery = Math.max(1, skipEvery);
        mCustomStartFrom = Math.max(0, startFrom);
        mUseCustomLabelSpacing = true;
        invalidate();
    }

    /**
     * Enable or disable automatic label spacing (default: true)
     * When auto is true, the chart automatically determines optimal label spacing
     * When auto is false, uses custom skip pattern if set
     */
    public void setAutoLabelSpacing(boolean auto) {
        mUseCustomLabelSpacing = !auto;
        invalidate();
    }

    /**
     * Get the current label skip pattern being used
     *
     * @return Array [skipEvery, startFrom] or null if auto-spacing is used
     */
    @NonNull
    public int[] getCurrentLabelSpacing() {
        if (mUseCustomLabelSpacing) {
            return new int[]{mCustomSkipEvery, mCustomStartFrom};
        }

        // Calculate current auto spacing
        if (mBarDataList.isEmpty()) {
            return new int[]{1, 0};
        }

        BarDimensions dims = calculateBarDimensions();
        LabelSpacing spacing = calculateOptimalLabelSpacing(dims);
        return new int[]{spacing.skipCount, spacing.startOffset};
    }

    private static class BarData {
        float value;
        String label;

        BarData(float value, String label) {
            this.value = value;
            this.label = label;
        }
    }
}
