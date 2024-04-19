// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.preference;

import android.content.Context;
import android.os.Build;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import io.github.muntashirakon.ui.R;

public class DefaultAlertPreference extends Preference {
    private boolean mAddSpaceBetweenIconAndText = true;

    public DefaultAlertPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public DefaultAlertPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, R.style.Preference_M3_Alert);
    }

    public DefaultAlertPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.alertPreferenceStyle);
    }

    public DefaultAlertPreference(@NonNull Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        final TextView summaryView = (TextView) holder.findViewById(android.R.id.summary);
        if (summaryView != null) {
            summaryView.setMovementMethod(LinkMovementMethod.getInstance());
        }
        View imageFrame = holder.findViewById(androidx.preference.R.id.icon_frame);
        if (imageFrame == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            imageFrame = holder.findViewById(android.R.id.icon_frame);
        }
        if (imageFrame instanceof LinearLayoutCompat) {
            ((LinearLayoutCompat) imageFrame).setGravity(Gravity.START);
        }
        View empty = holder.findViewById(android.R.id.empty);
        if (empty != null) {
            if (mAddSpaceBetweenIconAndText && imageFrame != null && imageFrame.getVisibility() != View.GONE) {
                empty.setVisibility(View.VISIBLE);
            } else {
                empty.setVisibility(View.GONE);
            }
        }
    }

    public void setAddSpaceBetweenIconAndText(boolean addSpaceBetweenIconAndText) {
        mAddSpaceBetweenIconAndText = addSpaceBetweenIconAndText;
    }

    public boolean isAddSpaceBetweenIconAndText() {
        return mAddSpaceBetweenIconAndText;
    }
}
