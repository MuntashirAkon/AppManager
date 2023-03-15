// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.ui.R;
import io.github.muntashirakon.util.UiUtils;
import me.zhanghai.android.fastscroll.FastScrollNestedScrollView;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;

public class NestedScrollView extends FastScrollNestedScrollView {
    public NestedScrollView(@NonNull Context context) {
        this(context, null);
    }

    public NestedScrollView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, androidx.core.R.attr.nestedScrollViewStyle);
    }

    public NestedScrollView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NestedScrollView);
        boolean fastScrollEnabled = a.getBoolean(R.styleable.NestedScrollView_fastScrollerEnabled, false);
        a.recycle();

        if (fastScrollEnabled) {
            new FastScrollerBuilder(this).useMd2Style().build();
        }
        UiUtils.applyWindowInsetsAsPaddingNoTop(this);
    }
}
