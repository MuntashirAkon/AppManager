// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.dialog;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.lang.reflect.Method;
import java.util.Locale;

import io.github.muntashirakon.ui.R;
import io.github.muntashirakon.util.AnimationUtils;
import io.github.muntashirakon.util.UiUtils;

/**
 * A {@link BottomSheetDialogFragment} with a capsule on top. This is a widely used design but seems to be missing
 * from the Material Components library.
 */
// Copyright 2022 Muntashir Al-Islam
// Copyright 2022 Absinthe
public abstract class CapsuleBottomSheetDialogFragment extends BottomSheetDialogFragment
        implements View.OnLayoutChangeListener {
    public static final String TAG = CapsuleBottomSheetDialogFragment.class.getSimpleName();

    private static final long ANIMATION_DURATION = 350L;
    private static final int MAX_PEEK_SIZE = 0;

    private View mRootView;
    private View mCapsule;
    private boolean mIsCapsuleActivated;
    @NonNull
    private ValueAnimator mAnimator = new ObjectAnimator();
    private BottomSheetBehavior<FrameLayout> mBehavior;
    private final BottomSheetBehavior.BottomSheetCallback mBottomSheetCallback = new BottomSheetBehavior.BottomSheetCallback() {
        @Override
        public void onStateChanged(@NonNull View bottomSheet, int newState) {
            switch (newState) {
                case BottomSheetBehavior.STATE_DRAGGING:
                    if (!mIsCapsuleActivated) {
                        mIsCapsuleActivated = true;
                        onCapsuleActivated(true);
                    }
                    break;
                case BottomSheetBehavior.STATE_COLLAPSED:
                    if (mIsCapsuleActivated) {
                        mIsCapsuleActivated = false;
                        onCapsuleActivated(false);
                    }
                    break;
                case BottomSheetBehavior.STATE_EXPANDED:
                    if (mIsCapsuleActivated) {
                        mIsCapsuleActivated = false;
                        onCapsuleActivated(false);
                    }
                    bottomSheet.setBackground(createMaterialShapeDrawable(bottomSheet));
                case BottomSheetBehavior.STATE_HALF_EXPANDED:
                case BottomSheetBehavior.STATE_HIDDEN:
                case BottomSheetBehavior.STATE_SETTLING:
                    break;
            }
        }

        @Override
        public void onSlide(@NonNull View bottomSheet, float slideOffset) {
        }
    };

    public View getRootView() {
        return mRootView;
    }

    @NonNull
    public abstract View initRootView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState);

    public void init(@Nullable Bundle savedInstanceState) {
    }

    @CallSuper
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = new BottomSheetDialogInternal(requireContext(), getTheme());
        mBehavior = dialog.getBehavior();
        return dialog;
    }

    @NonNull
    @Override
    public final View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayoutCompat bottomSheetContainer = (LinearLayoutCompat) inflater.inflate(R.layout.dialog_bottom_sheet_capsule, container, false);
        mCapsule = bottomSheetContainer.findViewById(R.id.capsule);
        mCapsule.setBackground(new TransitionDrawable(new Drawable[]{
                ContextCompat.getDrawable(requireContext(), R.drawable.bottom_sheet_capsule),
                ContextCompat.getDrawable(requireContext(), R.drawable.bottom_sheet_capsule_activated)
        }));
        mRootView = initRootView(inflater, bottomSheetContainer, savedInstanceState);
        bottomSheetContainer.addView(getRootView(), new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        init(savedInstanceState);
        return bottomSheetContainer;
    }

    @CallSuper
    @Override
    public void onStart() {
        super.onStart();
        mBehavior.addBottomSheetCallback(mBottomSheetCallback);
        mRootView.addOnLayoutChangeListener(this);

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mRootView.post(() -> {
                try {
                    Method setStateInternal = BottomSheetBehavior.class.getDeclaredMethod("setStateInternal", int.class);
                    setStateInternal.setAccessible(true);
                    setStateInternal.invoke(mBehavior, BottomSheetBehavior.STATE_EXPANDED);
                } catch (Throwable ignore) {
                }
            });
        }
    }

    @CallSuper
    @Override
    public void onStop() {
        super.onStop();
        mBehavior.removeBottomSheetCallback(mBottomSheetCallback);
    }

    @CallSuper
    @Override
    public void onDetach() {
        mAnimator.cancel();
        super.onDetach();
    }

    @CallSuper
    @Override
    public void onDestroyView() {
        mAnimator.cancel();
        mRootView.removeOnLayoutChangeListener(this);
        mRootView = null;
        super.onDestroyView();
    }

    @CallSuper
    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        if ((bottom - top) != (oldBottom - oldTop)) {
            enqueueAnimation(() -> animateHeight(oldBottom - oldTop, bottom - top, () -> {
            }));
        }
    }

    @NonNull
    private Drawable createMaterialShapeDrawable(@NonNull View bottomSheet) {
        // Create a ShapeAppearanceModel with the same shapeAppearanceOverlay used in the style
        ShapeAppearanceModel shapeAppearanceModel = ShapeAppearanceModel.builder(requireContext(),
                0, R.style.ShapeAppearance_AppTheme_MediumComponent_RoundedTop).build();

        // Create a new MaterialShapeDrawable (you can't use the original MaterialShapeDrawable in the BottomSheet)
        MaterialShapeDrawable currentMaterialShapeDrawable = (MaterialShapeDrawable) bottomSheet.getBackground();
        MaterialShapeDrawable newMaterialShapeDrawable = new MaterialShapeDrawable(shapeAppearanceModel);

        // Copy the attributes in the new MaterialShapeDrawable
        newMaterialShapeDrawable.initializeElevationOverlay(requireContext());
        newMaterialShapeDrawable.setFillColor(currentMaterialShapeDrawable.getFillColor());
        newMaterialShapeDrawable.setTintList(currentMaterialShapeDrawable.getTintList());
        newMaterialShapeDrawable.setElevation(currentMaterialShapeDrawable.getElevation());
        newMaterialShapeDrawable.setStrokeWidth(currentMaterialShapeDrawable.getStrokeWidth());
        newMaterialShapeDrawable.setStrokeColor(currentMaterialShapeDrawable.getStrokeColor());
        return newMaterialShapeDrawable;
    }

    private void animateHeight(int from, int to, @NonNull Runnable onEnd) {
        mAnimator.cancel();
        mAnimator = ObjectAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(ANIMATION_DURATION);
        mAnimator.setInterpolator(new FastOutSlowInInterpolator());
        Log.d(TAG, String.format(Locale.ROOT, "animateHeight: %d -> %d", from, to));
        mAnimator.addUpdateListener(animation -> {
            Float scale = (Float) animation.getAnimatedValue();
            int newHeight = (int) ((to - from) * scale + from);
            setClippedHeight(newHeight);
        });
        AnimationUtils.doOnAnimationEnd(mAnimator, onEnd);
        mAnimator.start();
    }

    private void enqueueAnimation(@NonNull Runnable action) {
        if (!mAnimator.isRunning()) {
            action.run();
        } else {
            AnimationUtils.doOnAnimationEnd(mAnimator, action);
        }
    }

    private void setClippedHeight(int newHeight) {
        if (newHeight <= MAX_PEEK_SIZE || MAX_PEEK_SIZE == 0) {
            mBehavior.setPeekHeight(newHeight);
        }
    }

    public void onCapsuleActivated(boolean activated) {
        if (activated) {
            ((TransitionDrawable) mCapsule.getBackground()).startTransition(150);
        } else {
            ((TransitionDrawable) mCapsule.getBackground()).reverseTransition(150);
        }
    }

    private static class BottomSheetDialogInternal extends BottomSheetDialog {
        public BottomSheetDialogInternal(@NonNull Context context, int theme) {
            super(context, theme);
        }

        @Override
        public void onAttachedToWindow() {
            super.onAttachedToWindow();
            Window window = getWindow();
            if (window != null) {
                window.getAttributes().windowAnimations = R.style.AppTheme_BottomSheetAnimation;
                WindowCompat.setDecorFitsSystemWindows(window, false);
                UiUtils.setSystemBarStyle(window, true);
                new WindowInsetsControllerCompat(window, window.getDecorView())
                        .setAppearanceLightNavigationBars(!UiUtils.isDarkMode());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                    window.getAttributes().setBlurBehindRadius(64);
                    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                }
            }

            findViewById(R.id.container).setFitsSystemWindows(false);
            findViewById(R.id.coordinator).setFitsSystemWindows(false);
        }
    }
}
