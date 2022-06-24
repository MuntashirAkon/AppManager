// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.util;

import android.animation.Animator;

import androidx.annotation.NonNull;

public final class AnimationUtils {
    public static void doOnAnimationEnd(@NonNull Animator animator, @NonNull Runnable action) {
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                animator.removeListener(this);
                action.run();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
    }
}
