// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.view;

import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;

import com.google.android.material.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

// TODO: 26/6/23 Make it a full replacement for TextInputLayout which is purposefully made for devs to suffer
public final class TextInputLayoutCompat {
    public static TextInputLayout fromTextInputEditText(@NonNull TextInputEditText editText) {
        return (TextInputLayout) editText.getParent().getParent();
    }

    public static void fixEndIcon(@NonNull TextInputLayout layout) {
        EditText editText = layout.getEditText();
        if (editText == null) {
            // Invalid
            return;
        }
        layout.post(() -> {
            AppCompatImageButton endIconView = layout.findViewById(R.id.text_input_end_icon);
            int endIconSize = layout.getEndIconMinSize();
            int width = editText.getMinimumHeight(); // For consistency
            int newHeight = endIconSize > 0 ? endIconSize : width;
            int newWidth = Math.max(width, newHeight);
            // AppCompatImageButton errorIconView = layout.findViewById(R.id.text_input_error_icon);
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(newWidth, newHeight);
            layoutParams.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
            endIconView.setLayoutParams(layoutParams);
            endIconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        });
    }
}
