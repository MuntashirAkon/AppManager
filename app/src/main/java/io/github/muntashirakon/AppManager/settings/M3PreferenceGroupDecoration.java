package io.github.muntashirakon.AppManager.settings;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Px;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroupAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;

import io.github.muntashirakon.util.UiUtils;

@SuppressLint("RestrictedApi")
public class M3PreferenceGroupDecoration extends RecyclerView.ItemDecoration {
    @Px
    private final float mRadius;
    @Px
    private final float mRadiusInner;

    public M3PreferenceGroupDecoration(@Px float radius, @Px float innerRadius) {
        mRadius = radius;
        mRadiusInner = innerRadius;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.getItemOffsets(outRect, view, parent, state);

        if (!(view instanceof MaterialCardView)) {
            return;
        }

        MaterialCardView cardView = (MaterialCardView) view;
        RecyclerView.Adapter<?> rawAdapter = parent.getAdapter();
        if (!(rawAdapter instanceof PreferenceGroupAdapter)) {
            return;
        }

        PreferenceGroupAdapter adapter = (PreferenceGroupAdapter) rawAdapter;
        int position = parent.getChildAdapterPosition(view);
        if (position == RecyclerView.NO_POSITION) return;

        // Skip category header
        if (isCategoryHeader(adapter, position)) {
            return;
        }

        boolean isFirst = isFirstInGroup(adapter, position);
        boolean isLast = isLastInGroup(adapter, position);

        if (isFirst && isLast) {
            // Standalone preference
            UiUtils.setCardRadius(cardView, mRadius, mRadius);
        } else if (isFirst) {
            // Top of a preference group
            UiUtils.setCardRadius(cardView, mRadius, mRadiusInner);
        } else if (isLast) {
            // Bottom of a preference group
            UiUtils.setCardRadius(cardView, mRadiusInner, mRadius);
        } else {
            // Middle of a preference group
            UiUtils.setCardRadius(cardView, mRadiusInner, mRadiusInner);
        }
    }

    private boolean isCategoryHeader(@NonNull PreferenceGroupAdapter adapter, int position) {
        return adapter.getItem(position) instanceof PreferenceCategory;
    }

    private boolean isFirstInGroup(@NonNull PreferenceGroupAdapter adapter, int position) {
        if (position == 0) return true;

        // If the element right above this choice is a section separator, this is the top card.
        return isCategoryHeader(adapter, position - 1);
    }

    private boolean isLastInGroup(@NonNull PreferenceGroupAdapter adapter, int position) {
        if (position == adapter.getItemCount() - 1) return true;

        // If the element right below this card is a header separator, this is the bottom card.
        return isCategoryHeader(adapter, position + 1);
    }
}