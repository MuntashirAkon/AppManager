// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.widget.TintTypedArray;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.internal.ThemeEnforcement;
import com.google.android.material.resources.MaterialResources;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.shape.Shapeable;

import io.github.muntashirakon.ui.R;
import io.github.muntashirakon.view.AutoCompleteTextViewCompat;

import static com.google.android.material.theme.overlay.MaterialThemeOverlay.wrap;

public class SearchView extends androidx.appcompat.widget.SearchView implements Shapeable {
    private static final int DEF_STYLE_RES = R.style.Widget_AppTheme_SearchView;

    private final SearchAutoComplete mSearchSrcTextView;
    private final LinearLayout mSearchEditFrame;
    private final ImageView mCloseButton;
    private final MaterialShapeDrawable mExpandedSearchViewShapeDrawable;
    private float mElevation;

    public SearchView(@NonNull Context context) {
        this(context, null);
    }

    public SearchView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, androidx.appcompat.R.attr.searchViewStyle);
    }

    public SearchView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, DEF_STYLE_RES);
    }

    @SuppressLint("RestrictedApi")
    public SearchView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, @StyleRes int defStyleRes) {
        super(wrap(context, attrs, defStyleAttr, defStyleRes), attrs, defStyleAttr);

        context = getContext();
        mCloseButton = findViewById(com.google.android.material.R.id.search_close_btn);
        mSearchSrcTextView = findViewById(com.google.android.material.R.id.search_src_text);
        mSearchEditFrame = findViewById(com.google.android.material.R.id.search_edit_frame);
        mElevation = getElevation();

        final TintTypedArray a = ThemeEnforcement.obtainTintedStyledAttributes(
                context, attrs, R.styleable.SearchView, defStyleAttr, DEF_STYLE_RES);

        int textAppearance = a.getResourceId(R.styleable.SearchView_android_textAppearance, 0);
        TextViewCompat.setTextAppearance(mSearchSrcTextView, textAppearance);

        mCloseButton.setImageTintList(MaterialResources.getColorStateList(
                context, a, R.styleable.SearchView_closeIconTint));

        int popupBackgroundResource = a.getResourceId(R.styleable.SearchView_android_popupBackground, 0);
        if (popupBackgroundResource != 0) {
            mSearchSrcTextView.setDropDownBackgroundResource(popupBackgroundResource);
        }

        Drawable popupListSelector = a.getDrawable(R.styleable.SearchView_android_dropDownSelector);
        if (popupListSelector != null) {
            AutoCompleteTextViewCompat.setListSelector(mSearchSrcTextView, popupListSelector);
        }

        int frameMarginHorizontal = a.getDimensionPixelSize(R.styleable.SearchView_frameMarginHorizontal, 0);

        a.recycle();
        mExpandedSearchViewShapeDrawable = new MaterialShapeDrawable(context, attrs, defStyleAttr, DEF_STYLE_RES);
        ViewGroup.MarginLayoutParams layoutParams = (MarginLayoutParams) mSearchEditFrame.getLayoutParams();
        layoutParams.setMarginStart(frameMarginHorizontal);
        layoutParams.setMarginEnd(frameMarginHorizontal);
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
        mExpandedSearchViewShapeDrawable.setElevation(mElevation);
        setBackground(mExpandedSearchViewShapeDrawable);
    }
}
