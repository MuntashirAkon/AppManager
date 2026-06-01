// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.whatsnew;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.google.android.material.textview.MaterialTextView;

import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.widget.RecyclerView;

class WhatsNewRecyclerAdapter extends RecyclerView.ListAdapter<ApkWhatsNewFinder.Change, WhatsNewRecyclerAdapter.ViewHolder> {
    private final int mColorAdd;
    private final int mColorRemove;
    private final int mColorNeutral;
    private final Typeface mTypefaceNormal;
    private final Typeface mTypefaceMedium;
    private final String mPackageName;

    private static final DiffUtil.ItemCallback<ApkWhatsNewFinder.Change> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ApkWhatsNewFinder.Change>() {
                @Override
                public boolean areItemsTheSame(@NonNull ApkWhatsNewFinder.Change oldItem, @NonNull ApkWhatsNewFinder.Change newItem) {
                    return oldItem.changeType == newItem.changeType && Objects.equals(oldItem.value, newItem.value);
                }

                @Override
                public boolean areContentsTheSame(@NonNull ApkWhatsNewFinder.Change oldItem, @NonNull ApkWhatsNewFinder.Change newItem) {
                    // Nothing to do
                    return true;
                }
            };

    WhatsNewRecyclerAdapter(Context context, @NonNull String packageName) {
        super(DIFF_CALLBACK);
        mPackageName = packageName;
        mColorAdd = ColorCodes.getWhatsNewPlusIndicatorColor(context);
        mColorRemove = ColorCodes.getWhatsNewMinusIndicatorColor(context);
        mColorNeutral = UIUtils.getTextColorPrimary(context);
        mTypefaceNormal = Typeface.create("sans-serif", Typeface.NORMAL);
        mTypefaceMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId;
        if (viewType == ApkWhatsNewFinder.CHANGE_INFO) {
            layoutId = R.layout.item_text_view;
        } else {
            layoutId = R.layout.item_whats_new;
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ApkWhatsNewFinder.Change change = getItem(position);
        String displayValue = change.value;
        if (displayValue.startsWith(mPackageName)) {
            displayValue = displayValue.replaceFirst(mPackageName, "");
        }
        switch (change.changeType) {
            case ApkWhatsNewFinder.CHANGE_ADD:
                holder.changeSign.setText("+");
                holder.changeSign.setTextColor(mColorAdd);
                holder.textView.setText(displayValue);
                holder.textView.setTextColor(mColorAdd);
                break;
            case ApkWhatsNewFinder.CHANGE_INFO:
                holder.textView.setText(displayValue);
                holder.textView.setTextColor(mColorNeutral);
                holder.textView.setTypeface(mTypefaceMedium);
                holder.textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                break;
            case ApkWhatsNewFinder.CHANGE_REMOVED:
                holder.changeSign.setText("-");
                holder.changeSign.setTextColor(mColorRemove);
                holder.textView.setText(displayValue);
                holder.textView.setTextColor(mColorRemove);
                break;
        }
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).changeType;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialTextView changeSign;
        final MaterialTextView textView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            changeSign = itemView.findViewById(android.R.id.text2);
            textView = itemView.findViewById(android.R.id.text1);
        }
    }
}