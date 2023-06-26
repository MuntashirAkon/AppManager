// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.resources.MaterialAttributes;
import com.leinardi.android.speeddial.FabWithLabelView;
import com.leinardi.android.speeddial.SpeedDialActionItem;
import com.leinardi.android.speeddial.SpeedDialOverlayLayout;
import com.leinardi.android.speeddial.SpeedDialView;

import io.github.muntashirakon.util.UiUtils;

// Based on https://github.com/zhanghai/MaterialFiles/blob/9a6db781087f9e3b6345af15c735c33b305d24c2/app/src/main/java/me/zhanghai/android/files/ui/ThemedSpeedDialView.kt
// TODO: 26/6/23 Replace this with a custom implementation by removing all those bad practices that this library follows
public class FloatingActionButtonGroup extends SpeedDialView {
    public FloatingActionButtonGroup(Context context) {
        this(context, null);
    }

    public FloatingActionButtonGroup(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FloatingActionButtonGroup(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        FloatingActionButton fab = getMainFab();
        int margin = UiUtils.dpToPx(getContext(), 16);
        MarginLayoutParams layoutParams = (MarginLayoutParams) fab.getLayoutParams();
        layoutParams.setMargins(margin, margin, margin, margin);
        fab.setLayoutParams(layoutParams);
        fab.setUseCompatPadding(false);
        setMainFabOpenedBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorError));
        setMainFabOpenedIconColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnError));
        setMainFabClosedBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer));
        setMainFabClosedIconColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSecondaryContainer));
    }

    @SuppressLint("RestrictedApi")
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        SpeedDialOverlayLayout overlayLayout = getOverlayLayout();
        if (overlayLayout != null) {
            overlayLayout.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @SuppressLint("RestrictedApi")
    @Nullable
    @Override
    public FabWithLabelView addActionItem(SpeedDialActionItem actionItem, int position, boolean animate) {
        int fabImageTintColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer);
        int fabBackgroundColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer);
        int labelColor = MaterialColors.getColor(this, android.R.attr.textColorSecondary);
        int labelBackgroundColor = Color.TRANSPARENT;
        Context context = getContext();
        SpeedDialActionItem item = new SpeedDialActionItem.Builder(actionItem.getId(), actionItem.getFabImageDrawable(context))
                .setLabel(actionItem.getLabel(context))
                .setFabImageTintColor(fabImageTintColor)
                .setFabBackgroundColor(fabBackgroundColor)
                .setLabelColor(labelColor)
                .setLabelBackgroundColor(labelBackgroundColor)
                .setLabelClickable(actionItem.isLabelClickable())
                .setTheme(actionItem.getTheme())
                .create();
        FabWithLabelView fabWrapper = super.addActionItem(item, position, animate);
        if (fabWrapper == null) {
            return null;
        }
        FloatingActionButton fab = fabWrapper.getFab();
        int margin = UiUtils.dpToPx(getContext(), 16);
        MarginLayoutParams layoutParams = (MarginLayoutParams) fab.getLayoutParams();
        layoutParams.setMargins(margin, 0, margin, 0);
        fab.setLayoutParams(layoutParams);
        fab.setUseCompatPadding(false);
        CardView labelBackground = fabWrapper.getLabelBackground();
        labelBackground.setUseCompatPadding(false);
        labelBackground.setContentPadding(0, 0, 0, 0);
        labelBackground.setForeground(null);
        TextView label = (TextView) labelBackground.getChildAt(0);
        int textAppearance = MaterialAttributes.resolveOrThrow(this, com.google.android.material.R.attr.textAppearanceLabelLarge);
        TextViewCompat.setTextAppearance(label, textAppearance);
        return fabWrapper;
    }
}
