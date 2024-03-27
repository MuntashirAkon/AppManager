// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.SwitchPreferenceCompat;

import io.github.muntashirakon.ui.R;

public class TopSwitchPreference extends SwitchPreferenceCompat {
    public TopSwitchPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, R.style.Preference_M3_TopSwitchPreference);
    }

    public TopSwitchPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public TopSwitchPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.topSwitchPreferenceStyle);
    }

    public TopSwitchPreference(@NonNull Context context) {
        this(context, null);
    }
}
