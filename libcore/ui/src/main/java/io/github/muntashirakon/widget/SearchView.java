// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.widget.TintTypedArray;

import com.google.android.material.internal.ThemeEnforcement;
import com.google.android.material.resources.MaterialResources;
import com.google.android.material.resources.TextAppearance;
import com.google.android.material.resources.TextAppearanceFontCallback;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.shape.Shapeable;

import io.github.muntashirakon.ui.R;

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
        this(context, attrs, R.attr.searchViewStyle);
    }

    public SearchView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, DEF_STYLE_RES);
    }

    @SuppressLint("RestrictedApi")
    public SearchView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, @StyleRes int defStyleRes) {
        super(wrap(context, attrs, defStyleAttr, defStyleRes), attrs, defStyleAttr);

        context = getContext();
        mCloseButton = findViewById(R.id.search_close_btn);
        mSearchSrcTextView = findViewById(R.id.search_src_text);
        mSearchEditFrame = findViewById(R.id.search_edit_frame);
        mElevation = getElevation();

        final TintTypedArray a = ThemeEnforcement.obtainTintedStyledAttributes(
                context, attrs, R.styleable.SearchView, defStyleAttr, DEF_STYLE_RES);

        TextAppearance textAppearance = MaterialResources.getTextAppearance(context, a.getWrappedTypeArray(),
                R.styleable.SearchView_android_textAppearance);

        if (textAppearance != null) {
            mSearchSrcTextView.setHintTextColor(textAppearance.textColorHint);
            mSearchSrcTextView.setLinkTextColor(textAppearance.textColorLink);
            mSearchSrcTextView.setAllCaps(textAppearance.textAllCaps);
            mSearchSrcTextView.setTextColor(textAppearance.getTextColor());
            if (textAppearance.shadowColor != null) {
                mSearchSrcTextView.setShadowLayer(textAppearance.shadowRadius, textAppearance.shadowDx,
                        textAppearance.shadowDy, textAppearance.shadowColor.getDefaultColor());
            }
            mSearchSrcTextView.setLetterSpacing(textAppearance.letterSpacing);
            mSearchSrcTextView.setTextColor(textAppearance.getTextColor());
            mSearchSrcTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textAppearance.getTextSize());
            textAppearance.getFontAsync(context, new TextAppearanceFontCallback() {
                @Override
                public void onFontRetrieved(Typeface typeface, boolean fontResolvedSynchronously) {
                    mSearchSrcTextView.setTypeface(typeface);
                }

                @Override
                public void onFontRetrievalFailed(int reason) {
                }
            });
        }

        mCloseButton.setImageTintList(MaterialResources.getColorStateList(
                context, a, R.styleable.SearchView_closeIconTint));

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
