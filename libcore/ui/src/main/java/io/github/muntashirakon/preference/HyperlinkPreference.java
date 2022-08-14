// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.preference;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

public class HyperlinkPreference extends Preference {
    public HyperlinkPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public HyperlinkPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public HyperlinkPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public HyperlinkPreference(@NonNull Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        final TextView summaryView = (TextView) holder.findViewById(android.R.id.summary);
        if (summaryView != null) {
            summaryView.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }
}
