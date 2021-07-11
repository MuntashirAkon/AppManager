// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.whatsnew;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.muntashirakon.AppManager.R;

public class WhatsNewDialogFragment extends DialogFragment {
    public static final String TAG = "WhatsNewDialogFragment";
    public static final String ARG_NEW_PKG_INFO = "ARG_NEW_PKG_INFO";
    public static final String ARG_OLD_PKG_INFO = "ARG_OLD_PKG_INFO";
    public static final String ARG_INSTALL_NAME = "ARG_INSTALL_NAME";

    public interface InstallInterface {
        void triggerInstall();

        void triggerCancel();
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
        final String installName = requireArguments().getString(ARG_INSTALL_NAME);
        LayoutInflater inflater = LayoutInflater.from(activity);
        if (inflater == null) return super.onCreateDialog(savedInstanceState);
        View view = inflater.inflate(R.layout.dialog_whats_new, null);
        RecyclerView recyclerView = (RecyclerView) view;
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        adapter = new WhatsNewRecyclerAdapter();
        recyclerView.setAdapter(adapter);
        new Thread(() -> {
            ApkWhatsNewFinder.Change[][] changes = ApkWhatsNewFinder.getInstance().getWhatsNew(newPkgInfo, oldPkgInfo);
            List<ApkWhatsNewFinder.Change> changeList = new ArrayList<>();
            for (ApkWhatsNewFinder.Change[] changes1 : changes) {
                if (changes1.length > 0) Collections.addAll(changeList, changes1);
            }
            if (isDetached()) return;
            if (changeList.size() == 0) {
                changeList.add(new ApkWhatsNewFinder.Change(ApkWhatsNewFinder.CHANGE_INFO, getString(R.string.no_changes)));
            }
            activity.runOnUiThread(() -> adapter.setAdapterList(changeList));
        }).start();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.whats_new)
                .setView(view);
        if (installInterface != null) {
            PackageManager pm = activity.getPackageManager();
            builder.setTitle(pm.getApplicationLabel(newPkgInfo.applicationInfo))
                    .setIcon(pm.getApplicationIcon(newPkgInfo.applicationInfo))
                    .setNegativeButton(R.string.cancel, (dialog, which) -> installInterface.triggerCancel())
                    .setPositiveButton(installName, (dialog, which) -> installInterface.triggerInstall());
        } else builder.setNegativeButton(R.string.ok, null);
        return builder.create();
    }

    @Override
    public void show(@NonNull FragmentManager manager, @Nullable String tag) {
        FragmentTransaction ft = manager.beginTransaction();
        ft.add(this, tag);
        ft.commitAllowingStateLoss();
    }

    class WhatsNewRecyclerAdapter extends RecyclerView.Adapter<WhatsNewRecyclerAdapter.ViewHolder> {
        private final List<ApkWhatsNewFinder.Change> mAdapterList = new ArrayList<>();

        WhatsNewRecyclerAdapter() {
        }

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
