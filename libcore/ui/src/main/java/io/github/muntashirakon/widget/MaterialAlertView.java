// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget;

import static com.google.android.material.theme.overlay.MaterialThemeOverlay.wrap;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.TintTypedArray;
import androidx.core.widget.TextViewCompat;
import androidx.transition.TransitionManager;
import androidx.transition.Visibility;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.internal.ThemeEnforcement;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.transition.MaterialFadeThrough;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.ui.R;

public class MaterialAlertView extends TextInputLayout {
    @IntDef({ALERT_TYPE_INFO, ALERT_TYPE_WARN, ALERT_TYPE_CUSTOM})
    @Retention(RetentionPolicy.SOURCE)
    public @interface AlertType {
    }

    public static final int ALERT_TYPE_CUSTOM = -1;
    public static final int ALERT_TYPE_INFO = 0;
    public static final int ALERT_TYPE_WARN = 1;

    private static final int DEF_STYLE_RES = R.style.Widget_AppTheme_MaterialAlertView;

    private final TextInputTextView mTextInputTextView;

    @AlertType
    private int mAlertType;

    public MaterialAlertView(@NonNull Context context) {
        this(context, null);
    }

    public MaterialAlertView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.materialAlertViewStyle);
    }

    @SuppressLint("RestrictedApi")
    public MaterialAlertView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(wrap(context, attrs, defStyleAttr, DEF_STYLE_RES), attrs, defStyleAttr);
        context = getContext();
        // AlertView only has a single layout by default
        mTextInputTextView = new TextInputTextView(context);
        mTextInputTextView.setMovementMethod(LinkMovementMethod.getInstance());

        addView(mTextInputTextView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TintTypedArray a = ThemeEnforcement.obtainTintedStyledAttributes(
                context, attrs, R.styleable.MaterialAlertView, defStyleAttr, DEF_STYLE_RES);
        mAlertType = a.getInt(R.styleable.MaterialAlertView_alertType, ALERT_TYPE_INFO);
        int texAppearance = a.getResourceId(R.styleable.MaterialAlertView_android_textAppearance, 0);
        ColorStateList textColor = a.getColorStateList(R.styleable.MaterialAlertView_android_textColor);
        CharSequence text = a.getText(R.styleable.MaterialAlertView_android_text);
        a.recycle();

        TextViewCompat.setTextAppearance(mTextInputTextView, texAppearance);
        if (textColor != null) {
            mTextInputTextView.setTextColor(textColor);
        }
        mTextInputTextView.setText(text);
        mTextInputTextView.setOverScrollMode(OVER_SCROLL_NEVER);

        // Override colors, drawables
        applyAlertType();
    }

    @AlertType
    public int getAlertType() {
        return mAlertType;
    }

    public void setAlertType(@AlertType int alertType) {
        mAlertType = alertType;
        applyAlertType();
    }

    public void setText(CharSequence text) {
        mTextInputTextView.setText(text);
    }

    public void setText(CharSequence text, @NonNull TextView.BufferType type) {
        mTextInputTextView.setText(text, type);
    }

    public void setText(@StringRes int resid) {
        mTextInputTextView.setText(resid);
    }

    public void setText(@StringRes int resid, @NonNull TextView.BufferType type) {
        mTextInputTextView.setText(resid, type);
    }

    public void setText(char[] text, int start, int len) {
        mTextInputTextView.setText(text, start, len);
    }

    public void setTextIsSelectable(boolean selectable) {
        mTextInputTextView.setTextIsSelectable(selectable);
    }

    public void setMovementMethod(MovementMethod movementMethod) {
        mTextInputTextView.setMovementMethod(movementMethod);
    }

    public void show() {
        MaterialFadeThrough fadeThrough = new MaterialFadeThrough();
        fadeThrough.addTarget(this);
        fadeThrough.setDuration(500);
        fadeThrough.setMode(Visibility.MODE_IN);
        ViewParent parent = getParent();
        if (parent instanceof ViewGroup) {
            TransitionManager.beginDelayedTransition((ViewGroup) parent, fadeThrough);
        } else {
            TransitionManager.beginDelayedTransition(this, fadeThrough);
        }
        setVisibility(View.VISIBLE);
    }

    public void hide() {
        MaterialFadeThrough fadeThrough = new MaterialFadeThrough();
        fadeThrough.addTarget(this);
        fadeThrough.setSecondaryAnimatorProvider(null);
        fadeThrough.setDuration(1000);
        fadeThrough.setMode(Visibility.MODE_OUT);
        ViewParent parent = getParent();
        if (parent instanceof ViewGroup) {
            TransitionManager.beginDelayedTransition((ViewGroup) parent, fadeThrough);
        } else {
            TransitionManager.beginDelayedTransition(this, fadeThrough);
        }
        setVisibility(View.GONE);
    }

    private void applyAlertType() {
        // Four things have to be changed: text colors and drawable
        switch (mAlertType) {
            case ALERT_TYPE_CUSTOM:
                // Do not change anything
                break;
            default:
            case ALERT_TYPE_INFO: {
                setStartIconDrawable(R.drawable.ic_information);
                ColorStateList foreground = ColorStateList.valueOf(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer));
                ColorStateList background = ColorStateList.valueOf(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer));
                setBoxBackgroundColorStateList(background);
                setStartIconTintList(foreground);
                mTextInputTextView.setTextColor(foreground);
                break;
            }
            case ALERT_TYPE_WARN: {
                setStartIconDrawable(R.drawable.ic_caution);
                ColorStateList foreground = ColorStateList.valueOf(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnErrorContainer));
                ColorStateList background = ColorStateList.valueOf(MaterialColors.getColor(this, com.google.android.material.R.attr.colorErrorContainer));
                setBoxBackgroundColorStateList(background);
                setStartIconTintList(foreground);
                mTextInputTextView.setTextColor(foreground);
                break;
            }
        }
    }
}
