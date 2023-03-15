// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.dialog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnKeyListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.res.ColorStateList;
import android.content.res.Resources.Theme;
import android.database.Cursor;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListAdapter;

import androidx.annotation.ArrayRes;
import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.view.ViewCompat;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.InsetDialogOnTouchListener;
import com.google.android.material.dialog.MaterialDialogs;
import com.google.android.material.resources.MaterialAttributes;
import com.google.android.material.shape.MaterialShapeDrawable;

import io.github.muntashirakon.ui.R;
import io.github.muntashirakon.util.UiUtils;

import static com.google.android.material.theme.overlay.MaterialThemeOverlay.wrap;

/**
 * An extension of {@link AlertDialog.Builder} for use with a Material theme (e.g.,
 * Theme.MaterialComponents).
 *
 * <p>This Builder must be used in order for AlertDialog objects to respond to color and shape
 * theming provided by Material themes.
 *
 * <p>The type of dialog returned is still an {@link AlertDialog}; there is no specific Material
 * implementation of {@link AlertDialog}.
 */
// Copyright 2018 The Android Open Source Project
public class AlertDialogBuilder extends AlertDialog.Builder {
    @AttrRes
    private static final int DEF_STYLE_ATTR = androidx.appcompat.R.attr.alertDialogStyle;
    @StyleRes
    private static final int DEF_STYLE_RES = com.google.android.material.R.style.MaterialAlertDialog_Material3;

    @AttrRes
    private static final int MATERIAL_ALERT_DIALOG_THEME_OVERLAY = com.google.android.material.R.attr.materialAlertDialogTheme;

    @Nullable
    private final FullScreenDialogTitleBuilder mTitleBuilder;
    @NonNull
    private final Rect mBackgroundInsets;
    private final boolean mFullScreenMode;

    @Nullable
    private Drawable mBackground;
    private boolean mExitOnButtonPress = true;
    @Nullable
    private DialogInterface.OnClickListener mPositiveButtonListener;
    @Nullable
    private DialogInterface.OnClickListener mNegativeButtonListener;
    @Nullable
    private DialogInterface.OnClickListener mNeutralButtonListener;

    @SuppressLint("RestrictedApi")
    private static int getMaterialAlertDialogThemeOverlay(@NonNull Context context) {
        TypedValue materialAlertDialogThemeOverlay =
                MaterialAttributes.resolve(context, MATERIAL_ALERT_DIALOG_THEME_OVERLAY);
        if (materialAlertDialogThemeOverlay == null) {
            return 0;
        }
        return materialAlertDialogThemeOverlay.data;
    }

    @NonNull
    private static Context createMaterialAlertDialogThemedContext(@NonNull Context context) {
        int themeOverlayId = getMaterialAlertDialogThemeOverlay(context);
        Context themedContext = wrap(context, null, DEF_STYLE_ATTR, DEF_STYLE_RES);
        if (themeOverlayId == 0) {
            return themedContext;
        }
        return new ContextThemeWrapper(themedContext, themeOverlayId);
    }

    private static int getOverridingThemeResId(@NonNull Context context, int overrideThemeResId) {
        return overrideThemeResId == 0
                ? getMaterialAlertDialogThemeOverlay(context)
                : overrideThemeResId;
    }

    private static boolean supportsFullScreen(@NonNull Context context, boolean fullScreenMode) {
        return fullScreenMode && !context.getResources().getBoolean(R.bool.large_layout);
    }

    public AlertDialogBuilder(@NonNull Context context) {
        this(context, 0);
    }

    public AlertDialogBuilder(@NonNull Context context, boolean fullScreenMode) {
        this(context, supportsFullScreen(context, fullScreenMode) ?
                UiUtils.getStyle(context, R.attr.materialFullScreenAlertDialogTheme) : 0, fullScreenMode);
    }

    public AlertDialogBuilder(@NonNull Context context, @StyleRes int overrideThemeResId) {
        this(context, overrideThemeResId, false);
    }

    @SuppressLint("RestrictedApi")
    public AlertDialogBuilder(@NonNull Context context, @StyleRes int overrideThemeResId, boolean fullScreenMode) {
        // Only pass in 0 for overrideThemeResId if both overrideThemeResId and
        // MATERIAL_ALERT_DIALOG_THEME_OVERLAY are 0 otherwise alertDialogTheme will override both.
        super(createMaterialAlertDialogThemedContext(context),
                getOverridingThemeResId(context, overrideThemeResId));
        mFullScreenMode = supportsFullScreen(context, fullScreenMode);
        // Ensure we are using the correctly themed context rather than the context that was passed in.
        context = getContext();
        Theme theme = context.getTheme();
        if (fullScreenMode) {
            mTitleBuilder = new FullScreenDialogTitleBuilder(context);
        } else mTitleBuilder = null;

        mBackgroundInsets = MaterialDialogs.getDialogBackgroundInsets(context, DEF_STYLE_ATTR, DEF_STYLE_RES);

        int surfaceColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, getClass().getCanonicalName());
        MaterialShapeDrawable materialShapeDrawable =
                new MaterialShapeDrawable(context, null, DEF_STYLE_ATTR, DEF_STYLE_RES);
        materialShapeDrawable.initializeElevationOverlay(context);
        materialShapeDrawable.setFillColor(ColorStateList.valueOf(surfaceColor));

        // dialogCornerRadius first appeared in Android Pie
        if (Build.VERSION.SDK_INT >= VERSION_CODES.P) {
            TypedValue dialogCornerRadiusValue = new TypedValue();
            theme.resolveAttribute(android.R.attr.dialogCornerRadius, dialogCornerRadiusValue, true);
            float dialogCornerRadius =
                    dialogCornerRadiusValue.getDimension(getContext().getResources().getDisplayMetrics());
            if (dialogCornerRadiusValue.type == TypedValue.TYPE_DIMENSION && dialogCornerRadius >= 0) {
                materialShapeDrawable.setCornerSize(dialogCornerRadius);
            }
        }
        mBackground = materialShapeDrawable;
    }

    @SuppressLint("RestrictedApi")
    @NonNull
    @Override
    public AlertDialog create() {
        AlertDialog alertDialog = super.create();
        Window window = alertDialog.getWindow();
        // TODO: 28/1/22 Use Handler instead of lamda functions
        if (mFullScreenMode && mTitleBuilder != null) {
            alertDialog.setOnShowListener(dialog -> {
                Button neutralButton = alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL);
                if (mNeutralButtonListener != null) {
                    neutralButton.setOnClickListener(v -> {
                        mNeutralButtonListener.onClick(alertDialog, DialogInterface.BUTTON_NEUTRAL);
                        if (mExitOnButtonPress) alertDialog.dismiss();
                    });
                }
            });
            mTitleBuilder.setOnPositiveButtonClickListener(mPositiveButtonListener);
            mTitleBuilder.setOnCloseButtonClickListener(mNegativeButtonListener);
            alertDialog.setCustomTitle(mTitleBuilder.build(alertDialog));
            window.setWindowAnimations(R.style.AppTheme_FullScreenDialog_Animation);
            // No need to set any insets
            return alertDialog;
        }
        alertDialog.setOnShowListener(dialog -> {
            Button neutralButton = alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            if (mNeutralButtonListener != null) {
                neutralButton.setOnClickListener(v -> {
                    mNeutralButtonListener.onClick(alertDialog, DialogInterface.BUTTON_NEUTRAL);
                    if (mExitOnButtonPress) alertDialog.dismiss();
                });
            }
            Button positiveButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (mPositiveButtonListener != null) {
                positiveButton.setOnClickListener(v -> {
                    mPositiveButtonListener.onClick(alertDialog, DialogInterface.BUTTON_POSITIVE);
                    if (mExitOnButtonPress) alertDialog.dismiss();
                });
            }
            Button negativeButton = alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
            if (mNegativeButtonListener != null) {
                negativeButton.setOnClickListener(v -> {
                    mNegativeButtonListener.onClick(alertDialog, DialogInterface.BUTTON_NEGATIVE);
                    if (mExitOnButtonPress) alertDialog.dismiss();
                });
            }
        });
        /* {@link Window#getDecorView()} should be called before any changes are made to the Window
         * as it locks in attributes and affects layout. */
        View decorView = window.getDecorView();
        if (mBackground instanceof MaterialShapeDrawable) {
            ((MaterialShapeDrawable) mBackground).setElevation(ViewCompat.getElevation(decorView));
        }

        Drawable insetDrawable = MaterialDialogs.insetDrawable(mBackground, mBackgroundInsets);
        window.setBackgroundDrawable(insetDrawable);
        decorView.setOnTouchListener(new InsetDialogOnTouchListener(alertDialog, mBackgroundInsets));
        return alertDialog;
    }

    @Nullable
    public Drawable getBackground() {
        return mBackground;
    }

    @NonNull
    public AlertDialogBuilder setBackground(@Nullable Drawable background) {
        mBackground = background;
        return this;
    }

    @NonNull
    public AlertDialogBuilder setBackgroundInsetStart(@Px int backgroundInsetStart) {
        if (getContext().getResources().getConfiguration().getLayoutDirection() == ViewCompat.LAYOUT_DIRECTION_RTL) {
            mBackgroundInsets.right = backgroundInsetStart;
        } else {
            mBackgroundInsets.left = backgroundInsetStart;
        }
        return this;
    }

    @NonNull
    public AlertDialogBuilder setBackgroundInsetTop(@Px int backgroundInsetTop) {
        mBackgroundInsets.top = backgroundInsetTop;
        return this;
    }

    @NonNull
    public AlertDialogBuilder setBackgroundInsetEnd(@Px int backgroundInsetEnd) {
        if (getContext().getResources().getConfiguration().getLayoutDirection() == ViewCompat.LAYOUT_DIRECTION_RTL) {
            mBackgroundInsets.left = backgroundInsetEnd;
        } else {
            mBackgroundInsets.right = backgroundInsetEnd;
        }
        return this;
    }

    @NonNull
    public AlertDialogBuilder setBackgroundInsetBottom(@Px int backgroundInsetBottom) {
        mBackgroundInsets.bottom = backgroundInsetBottom;
        return this;
    }

    /**
     * Whether to exit after a button (negative, positive or neutral) has been pressed.
     */
    public AlertDialogBuilder setExitOnButtonPress(boolean exitOnButtonPress) {
        if (mTitleBuilder != null) {
            mTitleBuilder.setExitOnButtonPress(exitOnButtonPress);
        }
        mExitOnButtonPress = exitOnButtonPress;
        return this;
    }

    // The following methods are all pass-through methods used to specify the return type for the
    // builder chain.

    @NonNull
    @Override
    public AlertDialogBuilder setTitle(@StringRes int titleId) {
        if (mFullScreenMode && mTitleBuilder != null) {
            mTitleBuilder.setTitle(titleId);
            return this;
        }
        return (AlertDialogBuilder) super.setTitle(titleId);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setTitle(@Nullable CharSequence title) {
        if (mFullScreenMode && mTitleBuilder != null) {
            mTitleBuilder.setTitle(title);
            return this;
        }
        return (AlertDialogBuilder) super.setTitle(title);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setCustomTitle(@Nullable View customTitleView) {
        return (AlertDialogBuilder) super.setCustomTitle(customTitleView);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setMessage(@StringRes int messageId) {
        return (AlertDialogBuilder) super.setMessage(messageId);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setMessage(@Nullable CharSequence message) {
        return (AlertDialogBuilder) super.setMessage(message);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setIcon(@DrawableRes int iconId) {
        return (AlertDialogBuilder) super.setIcon(iconId);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setIcon(@Nullable Drawable icon) {
        return (AlertDialogBuilder) super.setIcon(icon);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setIconAttribute(@AttrRes int attrId) {
        return (AlertDialogBuilder) super.setIconAttribute(attrId);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setPositiveButton(
            @StringRes int textId, @Nullable final OnClickListener listener) {
        mPositiveButtonListener = listener;
        if (mFullScreenMode && mTitleBuilder != null) {
            mTitleBuilder.setPositiveButtonText(textId);
            return this;
        }
        return (AlertDialogBuilder) super.setPositiveButton(textId, null);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setPositiveButton(
            @Nullable CharSequence text, @Nullable final OnClickListener listener) {
        mPositiveButtonListener = listener;
        if (mFullScreenMode && mTitleBuilder != null) {
            mTitleBuilder.setPositiveButtonText(text);
            return this;
        }
        return (AlertDialogBuilder) super.setPositiveButton(text, null);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setPositiveButtonIcon(@Nullable Drawable icon) {
        if (mFullScreenMode && mTitleBuilder != null) {
            mTitleBuilder.setPositiveButtonIcon(icon);
            return this;
        }
        return (AlertDialogBuilder) super.setPositiveButtonIcon(icon);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setNegativeButton(
            @StringRes int textId, @Nullable final OnClickListener listener) {
        mNegativeButtonListener = listener;
        if (mFullScreenMode && mTitleBuilder != null) {
            mTitleBuilder.setCloseIconContentDescription(textId);
            return this;
        }
        return (AlertDialogBuilder) super.setNegativeButton(textId, null);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setNegativeButton(
            @Nullable CharSequence text, @Nullable final OnClickListener listener) {
        mNegativeButtonListener = listener;
        if (mFullScreenMode && mTitleBuilder != null) {
            mTitleBuilder.setCloseIconContentDescription(text);
            return this;
        }
        return (AlertDialogBuilder) super.setNegativeButton(text, null);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setNegativeButtonIcon(@Nullable Drawable icon) {
        if (mFullScreenMode && mTitleBuilder != null) {
            mTitleBuilder.setCloseButtonIcon(icon);
            return this;
        }
        return (AlertDialogBuilder) super.setNegativeButtonIcon(icon);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setNeutralButton(
            @StringRes int textId, @Nullable final OnClickListener listener) {
        mNeutralButtonListener = listener;
        return (AlertDialogBuilder) super.setNeutralButton(textId, null);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setNeutralButton(
            @Nullable CharSequence text, @Nullable final OnClickListener listener) {
        mNeutralButtonListener = listener;
        return (AlertDialogBuilder) super.setNeutralButton(text, null);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setNeutralButtonIcon(@Nullable Drawable icon) {
        return (AlertDialogBuilder) super.setNeutralButtonIcon(icon);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setCancelable(boolean cancelable) {
        return (AlertDialogBuilder) super.setCancelable(cancelable);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setOnCancelListener(
            @Nullable OnCancelListener onCancelListener) {
        return (AlertDialogBuilder) super.setOnCancelListener(onCancelListener);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setOnDismissListener(
            @Nullable OnDismissListener onDismissListener) {
        return (AlertDialogBuilder) super.setOnDismissListener(onDismissListener);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setOnKeyListener(@Nullable OnKeyListener onKeyListener) {
        return (AlertDialogBuilder) super.setOnKeyListener(onKeyListener);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setItems(
            @ArrayRes int itemsId, @Nullable final OnClickListener listener) {
        return (AlertDialogBuilder) super.setItems(itemsId, listener);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setItems(
            @Nullable CharSequence[] items, @Nullable final OnClickListener listener) {
        return (AlertDialogBuilder) super.setItems(items, listener);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setAdapter(
            @Nullable final ListAdapter adapter, @Nullable final OnClickListener listener) {
        return (AlertDialogBuilder) super.setAdapter(adapter, listener);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setCursor(
            @Nullable final Cursor cursor,
            @Nullable final OnClickListener listener,
            @NonNull String labelColumn) {
        return (AlertDialogBuilder) super.setCursor(cursor, listener, labelColumn);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setMultiChoiceItems(
            @ArrayRes int itemsId,
            @Nullable boolean[] checkedItems,
            @Nullable final OnMultiChoiceClickListener listener) {
        return (AlertDialogBuilder) super.setMultiChoiceItems(itemsId, checkedItems, listener);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setMultiChoiceItems(
            @Nullable CharSequence[] items,
            @Nullable boolean[] checkedItems,
            @Nullable final OnMultiChoiceClickListener listener) {
        return (AlertDialogBuilder) super.setMultiChoiceItems(items, checkedItems, listener);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setMultiChoiceItems(
            @Nullable Cursor cursor,
            @NonNull String isCheckedColumn,
            @NonNull String labelColumn,
            @Nullable final OnMultiChoiceClickListener listener) {
        return (AlertDialogBuilder) super.setMultiChoiceItems(cursor, isCheckedColumn, labelColumn, listener);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setSingleChoiceItems(
            @ArrayRes int itemsId, int checkedItem, @Nullable final OnClickListener listener) {
        return (AlertDialogBuilder) super.setSingleChoiceItems(itemsId, checkedItem, listener);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setSingleChoiceItems(
            @Nullable Cursor cursor,
            int checkedItem,
            @NonNull String labelColumn,
            @Nullable final OnClickListener listener) {
        return (AlertDialogBuilder)
                super.setSingleChoiceItems(cursor, checkedItem, labelColumn, listener);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setSingleChoiceItems(
            @Nullable CharSequence[] items, int checkedItem, @Nullable final OnClickListener listener) {
        return (AlertDialogBuilder) super.setSingleChoiceItems(items, checkedItem, listener);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setSingleChoiceItems(
            @Nullable ListAdapter adapter, int checkedItem, @Nullable final OnClickListener listener) {
        return (AlertDialogBuilder) super.setSingleChoiceItems(adapter, checkedItem, listener);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setOnItemSelectedListener(
            @Nullable final AdapterView.OnItemSelectedListener listener) {
        return (AlertDialogBuilder) super.setOnItemSelectedListener(listener);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setView(int layoutResId) {
        return (AlertDialogBuilder) super.setView(layoutResId);
    }

    @NonNull
    @Override
    public AlertDialogBuilder setView(@Nullable View view) {
        return (AlertDialogBuilder) super.setView(view);
    }
}
