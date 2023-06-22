// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.shape.Shapeable;

import io.github.muntashirakon.ui.R;

public class RoundedFirstAndLastChildViewGroup extends FlowLayout {
    public RoundedFirstAndLastChildViewGroup(Context context) {
        this(context, null);
    }

    public RoundedFirstAndLastChildViewGroup(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RoundedFirstAndLastChildViewGroup(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public RoundedFirstAndLastChildViewGroup(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void addView(View child, int index, LayoutParams params) {
        super.addView(child, index, params);
        updateFirstAndLastViews();
    }

    private void updateFirstAndLastViews() {
        int count = getChildCount();
        switch (count) {
            case 0:
                // No children
                return;
            case 1: {
                // Only one view present, make it rounded
                View v = getChildAt(0);
                if (v instanceof Shapeable) {
                    ShapeAppearanceModel model = ShapeAppearanceModel.builder(v.getContext(),
                                    R.style.ShapeAppearance_AppTheme_LargeComponent, 0)
                            .build();
                    ((Shapeable) v).setShapeAppearanceModel(model);
                }
                break;
            }
            case 2: {
                // 2 views present, need to set shape for both views
                View firstView = getChildAt(0);
                if (firstView instanceof Shapeable) {
                    ShapeAppearanceModel model = ShapeAppearanceModel.builder(firstView.getContext(),
                                    R.style.ShapeAppearance_AppTheme_LeftRounded, 0)
                            .build();
                    ((Shapeable) firstView).setShapeAppearanceModel(model);
                }
                View secondView = getChildAt(count - 1);
                if (secondView instanceof Shapeable) {
                    ShapeAppearanceModel model = ShapeAppearanceModel.builder(secondView.getContext(),
                                    R.style.ShapeAppearance_AppTheme_RightRounded, 0)
                            .build();
                    ((Shapeable) secondView).setShapeAppearanceModel(model);
                }
                break;
            }
            default: {
                // More than 2 views present
                View lastView = getChildAt(count - 2);
                if (lastView instanceof Shapeable) {
                    // Reset last view
                    ShapeAppearanceModel model = ShapeAppearanceModel.builder(lastView.getContext(),
                                    0, 0)
                            .build();
                    ((Shapeable) lastView).setShapeAppearanceModel(model);
                }
                View thisView = getChildAt(count - 1);
                if (thisView instanceof Shapeable) {
                    ShapeAppearanceModel model = ShapeAppearanceModel.builder(thisView.getContext(),
                                    R.style.ShapeAppearance_AppTheme_RightRounded,
                                    0)
                            .build();
                    ((Shapeable) thisView).setShapeAppearanceModel(model);
                }
            }
        }
    }
}
