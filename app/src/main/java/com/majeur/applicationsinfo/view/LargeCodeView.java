package com.majeur.applicationsinfo.view;

import android.content.Context;
import android.widget.ScrollView;

public class LargeCodeView extends ScrollView {

    private LargeCodeHorizontalView mLargeCodeHorizontalView;

    public LargeCodeView(Context context) {
        super(context);
        mLargeCodeHorizontalView = new LargeCodeHorizontalView(context);
        addView(mLargeCodeHorizontalView);
    }

    public void setContent(String content) {
        mLargeCodeHorizontalView.setContent(content);
    }
}
