// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.style.LeadingMarginSpan;

public class EmptySpan implements LeadingMarginSpan {
    private final int gapWidth;
    private final int leadWidth;

    public EmptySpan(int leadGap, int gapWidth) {
        this.leadWidth = leadGap;
        this.gapWidth = gapWidth;
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
            c.drawText("", (leadWidth + x) * dir, bottom - p.descent(), p);
            p.setStyle(orgStyle);
        }
    }
}