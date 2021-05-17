// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.style.LeadingMarginSpan;

public class NumericSpan implements LeadingMarginSpan {
    private final int gapWidth;
    private final int leadWidth;
    private final String text;

    public NumericSpan(int leadGap, int gapWidth, int index) {
        this.leadWidth = leadGap;
        this.gapWidth = gapWidth;
        this.text = index + ".";
    }

    @Override
    public int getLeadingMargin(boolean first) {
        return leadWidth + gapWidth;
    }

    @Override
    public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom, CharSequence text, int start, int end, boolean first, Layout l) {
        if (first) {
            Paint.Style orgStyle = p.getStyle();
            p.setStyle(Paint.Style.FILL);
            float width = p.measureText(this.text);
            c.drawText(this.text, (leadWidth + x - width / 2) * dir, bottom - p.descent(), p);
            p.setStyle(orgStyle);
        }
    }
}