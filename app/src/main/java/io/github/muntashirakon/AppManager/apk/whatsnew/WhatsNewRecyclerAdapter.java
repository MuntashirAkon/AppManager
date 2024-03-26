// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.whatsnew;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.widget.RecyclerView;

class WhatsNewRecyclerAdapter extends RecyclerView.Adapter<WhatsNewRecyclerAdapter.ViewHolder> {
        private final List<ApkWhatsNewFinder.Change> mAdapterList = new ArrayList<>();
        private final int mColorAdd;
        private final int mColorRemove;
        private final int mColorNeutral;
        private final Typeface mTypefaceNormal;
        private final Typeface mTypefaceMedium;
        private final String mPackageName;

        WhatsNewRecyclerAdapter(Context context, @NonNull String packageName) {
            mPackageName = packageName;
            mColorAdd = ColorCodes.getWhatsNewPlusIndicatorColor(context);
            mColorRemove = ColorCodes.getWhatsNewMinusIndicatorColor(context);
            mColorNeutral = UIUtils.getTextColorPrimary(context);
            mTypefaceNormal = Typeface.create("sans-serif", Typeface.NORMAL);
            mTypefaceMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        }

        void setAdapterList(List<ApkWhatsNewFinder.Change> list) {
            AdapterUtils.notifyDataSetChanged(this, mAdapterList, list);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_text_view, parent, false);
            return new ViewHolder(view);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ApkWhatsNewFinder.Change change = mAdapterList.get(position);
            if (change.value.startsWith(mPackageName)) {
                change.value = change.value.replaceFirst(mPackageName, "");
            }
            switch (change.changeType) {
                case ApkWhatsNewFinder.CHANGE_ADD:
                    holder.textView.setText("+ " + change.value);
                    holder.textView.setTextColor(mColorAdd);
                    holder.textView.setTypeface(mTypefaceNormal);
                    holder.textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    break;
                case ApkWhatsNewFinder.CHANGE_INFO:
                    holder.textView.setText(change.value);
                    holder.textView.setTextColor(mColorNeutral);
                    holder.textView.setTypeface(mTypefaceMedium);
                    holder.textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    break;
                case ApkWhatsNewFinder.CHANGE_REMOVED:
                    holder.textView.setText("- " + change.value);
                    holder.textView.setTextColor(mColorRemove);
                    holder.textView.setTypeface(mTypefaceNormal);
                    holder.textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return mAdapterList.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            MaterialTextView textView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = (MaterialTextView) itemView;
            }
        }
    }