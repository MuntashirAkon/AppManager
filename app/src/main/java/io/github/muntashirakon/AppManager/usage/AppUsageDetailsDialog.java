// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.widget.RecyclerView;

public class AppUsageDetailsDialog extends CapsuleBottomSheetDialogFragment {
    public static final String TAG = AppUsageDetailsDialog.class.getSimpleName();

    private static final String ARG_PACKAGE_USAGE_INFO = "pkg_usg_info";

    @NonNull
    public static AppUsageDetailsDialog getInstance(@Nullable PackageUsageInfo usageInfo) {
        AppUsageDetailsDialog fragment = new AppUsageDetailsDialog();
        Bundle args = new Bundle();
        args.putParcelable(AppUsageDetailsDialog.ARG_PACKAGE_USAGE_INFO, usageInfo);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public View initRootView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_app_usage_details, container, false);
    }

    @Override
    public boolean displayLoaderByDefault() {
        return true;
    }

    @Override
    public void onBodyInitialized(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FragmentActivity activity = requireActivity();
        PackageUsageInfo usageInfo = requireArguments().getParcelable(ARG_PACKAGE_USAGE_INFO);
        if (usageInfo == null) {
            finishLoading();
            return;
        }
        // Set header first
        DialogTitleBuilder titleBuilder = new DialogTitleBuilder(activity)
                .setTitle(usageInfo.appLabel)
                .setTitleSelectable(true)
                .setSubtitle(usageInfo.packageName)
                .setSubtitleSelectable(true)
                .setEndIcon(R.drawable.ic_information, v -> {
                    Intent appDetailsIntent = AppDetailsActivity.getIntent(activity, usageInfo.packageName,
                            usageInfo.userId);
                    startActivity(appDetailsIntent);
                })
                .setEndIconContentDescription(R.string.app_info);
        ApplicationInfo applicationInfo = usageInfo.applicationInfo;
        if (applicationInfo != null) {
            PackageManager pm = activity.getPackageManager();
            titleBuilder.setStartIcon(applicationInfo.loadIcon(pm));
        }
        setHeader(titleBuilder.build());

        // Set body
        AppUsageStatsManager.DataUsage mobileData = usageInfo.mobileData;
        AppUsageStatsManager.DataUsage wifiData = usageInfo.wifiData;

        TextView screenTime = view.findViewById(R.id.screen_time);
        TextView timesOpened = view.findViewById(R.id.times_opened);
        TextView lastUsed = view.findViewById(R.id.last_used);
        TextView userId = view.findViewById(R.id.user_id);
        TextView mobileDataUsage = view.findViewById(R.id.data_usage);
        TextView wifiDataUsage = view.findViewById(R.id.wifi_usage);
        RecyclerView recyclerView = view.findViewById(android.R.id.list);
        AppUsageDetailsAdapter adapter = new AppUsageDetailsAdapter(activity);

        screenTime.setText(DateUtils.getFormattedDuration(requireContext(), usageInfo.screenTime));
        timesOpened.setText(getResources().getQuantityString(R.plurals.no_of_times_opened, usageInfo.timesOpened,
                usageInfo.timesOpened));
        long lastRun = usageInfo.lastUsageTime > 1 ? (System.currentTimeMillis() - usageInfo.lastUsageTime) : 0;
        if (usageInfo.packageName.equals(BuildConfig.APPLICATION_ID)) {
            // Special case for App Manager since the user is using the app right now
            lastUsed.setText(R.string.running);
        } else if (lastRun > 1) {
            lastUsed.setText(String.format(Locale.getDefault(), "%s %s", DateUtils.getFormattedDuration(
                            requireContext(), lastRun), getString(R.string.ago)));
        } else {
            lastUsed.setText(R.string._undefined);
        }
        userId.setText(String.format(Locale.getDefault(), "%d", usageInfo.userId));
        if ((mobileData == null && wifiData == null) || (mobileData != null && wifiData != null
                && (mobileData.getTotal() + wifiData.getTotal() == 0))) {
            view.findViewById(R.id.data_usage_layout).setVisibility(View.GONE);
        } else {
            if (mobileData != null && mobileData.getTotal() != 0) {
                String dataUsage = String.format("  \u2191 %1$s \u2193 %2$s",
                        Formatter.formatFileSize(requireContext(), mobileData.first),
                        Formatter.formatFileSize(requireContext(), mobileData.second));
                mobileDataUsage.setText(dataUsage);
            } else mobileDataUsage.setVisibility(View.GONE);
            if (wifiData != null && wifiData.getTotal() != 0) {
                String dataUsage = String.format("  \u2191 %1$s \u2193 %2$s",
                        Formatter.formatFileSize(requireContext(), wifiData.first),
                        Formatter.formatFileSize(requireContext(), wifiData.second));
                wifiDataUsage.setText(dataUsage);
            } else wifiDataUsage.setVisibility(View.GONE);
        }
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(adapter);
        adapter.setDefaultList(usageInfo.entries);

        // Load the body
        getView().postDelayed(this::finishLoading, 300);
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
            holder.title.setText(String.format(Locale.getDefault(), "%s", DateUtils.formatDateTime(entry.startTime)));
            holder.subtitle.setText(DateUtils.getFormattedDuration(context, entry.getDuration()));
            holder.itemView.setBackgroundColor(position % 2 == 0 ? mColorSemiTransparent : mColorTransparent);
        }
    }
}
