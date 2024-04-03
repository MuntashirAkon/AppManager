// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.button.MaterialButton;

import io.github.muntashirakon.ui.R;

public class PrimaryButtonPreference extends Preference {
    public PrimaryButtonPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public PrimaryButtonPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, R.style.Preference_M3_ButtonPreference_Primary);
    }

    public PrimaryButtonPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.primaryButtonPreferenceStyle);
    }

    public PrimaryButtonPreference(@NonNull Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View itemView = holder.itemView;
        MaterialButton button = (MaterialButton) holder.findViewById(android.R.id.button1);
        button.setText(getTitle());
        button.setIcon(getIcon());
        if (itemView.hasOnClickListeners()) {
            // Proxy listeners
            button.setOnClickListener(v -> itemView.callOnClick());
        }
        // Selectable
        boolean isSelectable = isSelectable();
        itemView.setClickable(false);
        itemView.setFocusable(false);
        button.setClickable(isSelectable);
        button.setFocusable(isSelectable);
    }
}
