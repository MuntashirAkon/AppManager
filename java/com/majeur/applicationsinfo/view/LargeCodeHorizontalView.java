package com.majeur.applicationsinfo.view;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;

public class LargeCodeHorizontalView extends HorizontalScrollView {

    private ScalableTextView mScalableTextView;

    public LargeCodeHorizontalView(Context context) {
        super(context);
        mScalableTextView = new ScalableTextView(context);
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        addView(mScalableTextView, layoutParams);
    }

    public void setContent(String content) {
        mScalableTextView.setText(content);
    }
}
