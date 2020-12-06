/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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