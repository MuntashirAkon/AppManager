// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.text.style;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.style.LeadingMarginSpan;

/**
 * Create numeric or bulleted list by aligning all the lines in a paragraph.
 */
public class ListSpan implements LeadingMarginSpan {
    private final int mLeadingGapWidth;
    private final int mTrailingGapWidth;
    private final String mText;

    /**
     * @param leadingGap  Leading gaps including the numeric digit(s).
     * @param trailingGap Gaps to add after the numeric digits.
     * @param index       Index number for a numeric list.
     */
    public ListSpan(int leadingGap, int trailingGap, int index) {
        this.mLeadingGapWidth = leadingGap;
        this.mTrailingGapWidth = trailingGap;
        this.mText = index + ".";
    }

    /**
     * @param leadingGap  Leading gaps including the numeric digit(s).
     * @param trailingGap Gaps to add after the numeric digits.
     * @param ch          Character for a bulleted list (preferably a bullet).
     */
    public ListSpan(int leadingGap, int trailingGap, char ch) {
        this.mLeadingGapWidth = leadingGap;
        this.mTrailingGapWidth = trailingGap;
        this.mText = String.valueOf(ch);
    }

    @Override
    public int getLeadingMargin(boolean first) {
        return mLeadingGapWidth + mTrailingGapWidth;
    }

    @Override
    public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom, CharSequence text, int start, int end, boolean first, Layout l) {
        if (first) {
            Paint.Style lastStyle = p.getStyle();
            p.setStyle(Paint.Style.FILL);
            float width = p.measureText(this.mText);
            c.drawText(this.mText, (mLeadingGapWidth + x - width / 2) * dir, bottom - p.descent(), p);
            p.setStyle(lastStyle);
        }
    }
}