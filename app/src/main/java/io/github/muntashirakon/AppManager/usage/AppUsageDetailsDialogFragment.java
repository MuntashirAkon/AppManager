// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.widget.RecyclerViewWithEmptyView;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;

public class AppUsageDetailsDialogFragment extends DialogFragment {
    public static final String TAG = AppUsageDetailsDialogFragment.class.getSimpleName();
    public static final String ARG_PACKAGE_USAGE_INFO = "pkg_usg_info";

    @NonNull
    public static AppUsageDetailsDialogFragment getInstance(@Nullable PackageUsageInfo usageInfo) {
        AppUsageDetailsDialogFragment fragment = new AppUsageDetailsDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(AppUsageDetailsDialogFragment.ARG_PACKAGE_USAGE_INFO, usageInfo);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        FragmentActivity activity = requireActivity();
        PackageUsageInfo packageUsageInfo = requireArguments().getParcelable(ARG_PACKAGE_USAGE_INFO);
        LayoutInflater inflater = LayoutInflater.from(activity);
        if (packageUsageInfo == null || inflater == null) {
            return super.onCreateDialog(savedInstanceState);
        }
        View view = inflater.inflate(R.layout.dialog_app_usage_details, null);
        RecyclerViewWithEmptyView recyclerView = view.findViewById(android.R.id.list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        new FastScrollerBuilder(recyclerView).useMd2Style().build();
        recyclerView.setEmptyView(view.findViewById(android.R.id.empty));
        AppUsageDetailsAdapter adapter = new AppUsageDetailsAdapter(activity);
        recyclerView.setAdapter(adapter);
        adapter.setDefaultList(packageUsageInfo.entries);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setView(view)
                .setNegativeButton(R.string.ok, (dialog, which) -> {
                    if (getDialog() == null) dismiss();
                });
        DialogTitleBuilder titleBuilder = new DialogTitleBuilder(activity);
        ApplicationInfo applicationInfo = packageUsageInfo.applicationInfo;
        if (applicationInfo != null) {
            PackageManager pm = activity.getPackageManager();
            titleBuilder.setTitle(applicationInfo.loadLabel(pm)).setStartIcon(applicationInfo.loadIcon(pm));
        } else titleBuilder.setTitle(packageUsageInfo.packageName);
        if (Users.getUsersIds().length > 1) {
            titleBuilder.setSubtitle(getString(R.string.user_with_id, packageUsageInfo.userId));
        }
        builder.setCustomTitle(titleBuilder.build());
        return builder.create();
    }

    static class AppUsageDetailsAdapter extends RecyclerView.Adapter<AppUsageDetailsAdapter.ViewHolder> {
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView subtitle;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.item_title);
                subtitle = itemView.findViewById(R.id.item_subtitle);
            }
        }

        private List<PackageUsageInfo.Entry> mDefaultList;
        private List<PackageUsageInfo.Entry> mAdapterList;
        private final Context context;

        private final int mColorTransparent;
        private final int mColorSemiTransparent;

        AppUsageDetailsAdapter(@NonNull Activity activity) {
            context = activity;
            mColorTransparent = Color.TRANSPARENT;
            mColorSemiTransparent = ContextCompat.getColor(activity, R.color.semi_transparent);
            setHasStableIds(true);
        }

        void setDefaultList(List<PackageUsageInfo.Entry> list) {
            mDefaultList = list;
            mAdapterList = list;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return mAdapterList == null ? 0 : mAdapterList.size();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_usage_details, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public long getItemId(int position) {
            return mDefaultList.indexOf(mAdapterList.get(position));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PackageUsageInfo.Entry entry = mAdapterList.get(position);
            holder.title.setText(String.format(Locale.ROOT, "%s - %s", DateUtils.formatDateTime(entry.startTime),
                    DateUtils.formatDateTime(entry.endTime)));
            holder.subtitle.setText(DateUtils.getFormattedDuration(context, entry.getDuration()));
            holder.itemView.setBackgroundColor(position % 2 == 0 ? mColorSemiTransparent : mColorTransparent);
        }
    }
}
