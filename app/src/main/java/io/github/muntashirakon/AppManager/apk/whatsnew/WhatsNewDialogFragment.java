/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.apk.whatsnew;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import io.github.muntashirakon.AppManager.R;

public class WhatsNewDialogFragment extends DialogFragment {
    public static final String TAG = "WhatsNewDialogFragment";
    public static final String ARG_NEW_PKG_INFO = "ARG_NEW_PKG_INFO";
    public static final String ARG_OLD_PKG_INFO = "ARG_OLD_PKG_INFO";

    public interface InstallInterface {
        void triggerInstall();
    }

    public void setOnTriggerInstall(InstallInterface installInterface) {
        this.installInterface = installInterface;
    }

    InstallInterface installInterface;
    FragmentActivity activity;
    WhatsNewRecyclerAdapter adapter;
    PackageInfo newPkgInfo;
    PackageInfo oldPkgInfo;
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        activity = requireActivity();
        newPkgInfo = (PackageInfo) requireArguments().get(ARG_NEW_PKG_INFO);
        oldPkgInfo = (PackageInfo) requireArguments().get(ARG_OLD_PKG_INFO);
        LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater == null) return super.onCreateDialog(savedInstanceState);
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.dialog_whats_new, null);
        RecyclerView recyclerView = (RecyclerView) view;
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        adapter = new WhatsNewRecyclerAdapter();
        recyclerView.setAdapter(adapter);
        new Thread(() -> {
            ApkWhatsNewFinder.Change[][] changes = ApkWhatsNewFinder.getInstance().getWhatsNew(newPkgInfo, oldPkgInfo);
            List<ApkWhatsNewFinder.Change> changeList = new ArrayList<>();
            for (ApkWhatsNewFinder.Change[] changes1: changes) {
                if (changes1.length > 0) Collections.addAll(changeList, changes1);
            }
            activity.runOnUiThread(() -> adapter.setAdapterList(changeList));
        }).start();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.whats_new)
                .setView(view);
        if (installInterface != null) {
            builder.setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.update, (dialog, which) -> installInterface.triggerInstall());
        } else builder.setNegativeButton(android.R.string.ok, null);
        return builder.create();
    }

    class WhatsNewRecyclerAdapter extends RecyclerView.Adapter<WhatsNewRecyclerAdapter.ViewHolder> {
        private final List<ApkWhatsNewFinder.Change> mAdapterList = new ArrayList<>();
        WhatsNewRecyclerAdapter() {}
        void setAdapterList(List<ApkWhatsNewFinder.Change> list) {
            mAdapterList.clear();
            mAdapterList.addAll(list);
            notifyDataSetChanged();
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
            if (change.value.startsWith(newPkgInfo.packageName))
                change.value = change.value.replaceFirst(newPkgInfo.packageName, "");
            switch (change.changeType) {
                case ApkWhatsNewFinder.CHANGE_ADD:
                    holder.textView.setText("+ " + change.value);
                    holder.textView.setTextColor(ContextCompat.getColor(activity, R.color.stopped));
                    holder.textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, activity.getResources().getDimension(R.dimen.subtitle_font));
                    break;
                case ApkWhatsNewFinder.CHANGE_INFO:
                    holder.textView.setText(change.value);
                    holder.textView.setTextColor(ContextCompat.getColor(activity, R.color.textColorSecondary));
                    holder.textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, activity.getResources().getDimension(R.dimen.title_font));
                    break;
                case ApkWhatsNewFinder.CHANGE_REMOVED:
                    holder.textView.setText("- " + change.value);
                    holder.textView.setTextColor(ContextCompat.getColor(activity, R.color.electric_red));
                    holder.textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, activity.getResources().getDimension(R.dimen.subtitle_font));
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return mAdapterList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = (TextView) itemView;
            }
        }
    }
}
