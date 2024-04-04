// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.preference;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import io.github.muntashirakon.ui.R;

public class InfoAlertPreference extends DefaultAlertPreference {
    public InfoAlertPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setAddSpaceBetweenIconAndText(false);
    }

    public InfoAlertPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, R.style.Preference_M3_Alert);
    }

    public InfoAlertPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.alertPreferenceStyle);
    }

    public InfoAlertPreference(@NonNull Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        int background = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimaryContainer);
        int foreground = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnPrimaryContainer);
        if (holder.itemView instanceof MaterialCardView) {
            MaterialCardView cardView = (MaterialCardView) holder.itemView;
            cardView.setCardBackgroundColor(background);
        }
        final TextView summaryView = (TextView) holder.findViewById(android.R.id.summary);
        if (summaryView != null) {
            summaryView.setTextColor(foreground);
        }
        final ImageView imageView = (ImageView)holder.findViewById(android.R.id.icon);
        if (imageView != null) {
            imageView.setImageTintList(ColorStateList.valueOf(foreground));
        }
    }
}
