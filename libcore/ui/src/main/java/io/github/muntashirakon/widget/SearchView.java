// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.widget.TintTypedArray;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.internal.ThemeEnforcement;
import com.google.android.material.resources.MaterialResources;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.shape.Shapeable;

import io.github.muntashirakon.ui.R;

import static com.google.android.material.theme.overlay.MaterialThemeOverlay.wrap;

public class SearchView extends androidx.appcompat.widget.SearchView implements Shapeable {
    private static final int DEF_STYLE_RES = R.style.Widget_AppTheme_SearchView;

    private final MaterialShapeDrawable mExpandedSearchViewShapeDrawable;
    private ColorStateList mBoxBackgroundColor;
    private float mElevation;

    public SearchView(@NonNull Context context) {
        this(context, null);
    }

    public SearchView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.searchViewStyle);
    }

    public SearchView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, DEF_STYLE_RES);
    }

    @SuppressLint("RestrictedApi")
    public SearchView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, @StyleRes int defStyleRes) {
        super(wrap(context, attrs, defStyleAttr, defStyleRes), attrs, defStyleAttr);

        context = getContext();
        mElevation = getElevation();

        final TintTypedArray a = ThemeEnforcement.obtainTintedStyledAttributes(
                context, attrs, R.styleable.TextInputLayout, defStyleAttr, DEF_STYLE_RES);

        mBoxBackgroundColor = MaterialResources.getColorStateList(
                context, a, R.styleable.TextInputLayout_boxBackgroundColor);
        if (mBoxBackgroundColor == null) {
            mBoxBackgroundColor = ColorStateList.valueOf(MaterialColors.getColor(
                    context, R.attr.colorSurface, SearchView.class.getSimpleName()));
        }

        a.recycle();
        mExpandedSearchViewShapeDrawable = new MaterialShapeDrawable(context, attrs, defStyleAttr, DEF_STYLE_RES);
        updateBackgroundExpanded();
    }

    @NonNull
    @Override
    public ShapeAppearanceModel getShapeAppearanceModel() {
        return mExpandedSearchViewShapeDrawable.getShapeAppearanceModel();
    }

    @Override
    public void setShapeAppearanceModel(@NonNull ShapeAppearanceModel shapeAppearanceModel) {
        mExpandedSearchViewShapeDrawable.setShapeAppearanceModel(shapeAppearanceModel);
    }

    @Override
    public void setElevation(float elevation) {
        mElevation = elevation;
        super.setElevation(elevation);
        if (mExpandedSearchViewShapeDrawable != null) {
            mExpandedSearchViewShapeDrawable.setElevation(elevation);
        }
    }

    private void updateBackgroundExpanded() {
        mExpandedSearchViewShapeDrawable.initializeElevationOverlay(getContext());
        mExpandedSearchViewShapeDrawable.setFillColor(mBoxBackgroundColor);
        mExpandedSearchViewShapeDrawable.setElevation(mElevation);
        setBackground(mExpandedSearchViewShapeDrawable);
    }
}
