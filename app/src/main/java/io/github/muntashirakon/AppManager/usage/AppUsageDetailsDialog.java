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
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.view.TextInputLayoutCompat;
import io.github.muntashirakon.widget.RecyclerView;
import io.github.muntashirakon.widget.TextInputTextView;

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
        PackageUsageInfo usageInfo = BundleCompat.getParcelable(requireArguments(), ARG_PACKAGE_USAGE_INFO, PackageUsageInfo.class);
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
                .setEndIcon(io.github.muntashirakon.ui.R.drawable.ic_information, v -> {
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

        RecyclerView recyclerView = view.findViewById(android.R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        AppUsageDetailsAdapter adapter = new AppUsageDetailsAdapter(activity);
        recyclerView.setAdapter(adapter);
        adapter.setDefaultList(usageInfo);

        // Load the body
        requireView().postDelayed(this::finishLoading, 300);
    }

    static class AppUsageDetailsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int VIEW_TYPE_HEADER = 1;
        private static final int VIEW_TYPE_LIST_ITEM = 2;

        static class ListHeaderViewHolder extends RecyclerView.ViewHolder {
            final TextInputTextView screenTime;
            final TextInputTextView timesOpened;
            final TextInputTextView lastUsed;
            final TextInputTextView userId;
            final TextInputTextView mobileDataUsage;
            final TextInputTextView wifiDataUsage;
            final LinearLayoutCompat dataUsageLayout;
            final TextInputLayout mobileDataUsageLayout;
            final TextInputLayout wifiDataUsageLayout;

            public ListHeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                screenTime = itemView.findViewById(R.id.screen_time);
                timesOpened = itemView.findViewById(R.id.times_opened);
                lastUsed = itemView.findViewById(R.id.last_used);
                userId = itemView.findViewById(R.id.user_id);
                mobileDataUsage = itemView.findViewById(R.id.data_usage);
                mobileDataUsageLayout = TextInputLayoutCompat.fromTextInputEditText(mobileDataUsage);
                wifiDataUsage = itemView.findViewById(R.id.wifi_usage);
                wifiDataUsageLayout = TextInputLayoutCompat.fromTextInputEditText(wifiDataUsage);
                dataUsageLayout = itemView.findViewById(R.id.data_usage_layout);
            }
        }

        static class ListItemViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView subtitle;

            public ListItemViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.item_title);
                subtitle = itemView.findViewById(R.id.item_subtitle);
            }
        }

        private final List<PackageUsageInfo.Entry> mAdapterList = new ArrayList<>();
        private PackageUsageInfo mUsageInfo;
        private final Context mContext;

        private final int mColorTransparent;
        private final int mColorSemiTransparent;

        AppUsageDetailsAdapter(@NonNull Activity activity) {
            mContext = activity;
            mColorTransparent = Color.TRANSPARENT;
            mColorSemiTransparent = ContextCompat.getColor(activity, io.github.muntashirakon.ui.R.color.semi_transparent);
            setHasStableIds(true);
        }

        void setDefaultList(@NonNull PackageUsageInfo usageInfo) {
            mUsageInfo = usageInfo;
            synchronized (mAdapterList) {
                AdapterUtils.notifyDataSetChanged(this, mAdapterList, usageInfo.entries);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return VIEW_TYPE_HEADER;
            }
            return VIEW_TYPE_LIST_ITEM;
        }

        @Override
        public int getItemCount() {
            synchronized (mAdapterList) {
                return mAdapterList.size() + 1;
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_HEADER:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_usage_details_header, parent, false);
                    return new ListHeaderViewHolder(view);
                default:
                case VIEW_TYPE_LIST_ITEM:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_usage_details, parent, false);
                    return new ListItemViewHolder(view);
            }
        }

        @Override
        public long getItemId(int position) {
            if (position == 0) {
                return 0;
            }
            synchronized (mAdapterList) {
                return mAdapterList.get(position - 1).hashCode();
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position == 0) {
                onBindViewHolder((ListHeaderViewHolder) holder);
            } else onBindViewHolder((ListItemViewHolder) holder, position - 1);
        }

        public void onBindViewHolder(@NonNull ListHeaderViewHolder holder) {
            AppUsageStatsManager.DataUsage mobileData = mUsageInfo.mobileData;
            AppUsageStatsManager.DataUsage wifiData = mUsageInfo.wifiData;

            holder.screenTime.setText(DateUtils.getFormattedDuration(mContext, mUsageInfo.screenTime));
            holder.timesOpened.setText(mContext.getResources().getQuantityString(R.plurals.no_of_times_opened,
                    mUsageInfo.timesOpened, mUsageInfo.timesOpened));
            long lastRun = mUsageInfo.lastUsageTime > 1 ? (System.currentTimeMillis() - mUsageInfo.lastUsageTime) : 0;
            if (mUsageInfo.packageName.equals(BuildConfig.APPLICATION_ID)) {
                // Special case for App Manager since the user is using the app right now
                holder.lastUsed.setText(R.string.running);
            } else if (lastRun > 1) {
                holder.lastUsed.setText(String.format(Locale.getDefault(), "%s %s", DateUtils.getFormattedDuration(
                        mContext, lastRun), mContext.getString(R.string.ago)));
            } else {
                holder.lastUsed.setText(R.string._undefined);
            }
            holder.userId.setText(String.format(Locale.getDefault(), "%d", mUsageInfo.userId));
            if ((mobileData == null && wifiData == null) || (mobileData != null && wifiData != null
                    && (mobileData.getTotal() + wifiData.getTotal() == 0))) {
                holder.dataUsageLayout.setVisibility(View.GONE);
            } else {
                holder.dataUsageLayout.setVisibility(View.VISIBLE);
                if (mobileData != null && mobileData.getTotal() != 0) {
                    String dataUsage = String.format("  ↑ %1$s ↓ %2$s",
                            Formatter.formatFileSize(mContext, mobileData.first),
                            Formatter.formatFileSize(mContext, mobileData.second));
                    holder.mobileDataUsageLayout.setVisibility(View.VISIBLE);
                    holder.mobileDataUsage.setText(dataUsage);
                } else holder.mobileDataUsageLayout.setVisibility(View.GONE);
                if (wifiData != null && wifiData.getTotal() != 0) {
                    String dataUsage = String.format("  ↑ %1$s ↓ %2$s",
                            Formatter.formatFileSize(mContext, wifiData.first),
                            Formatter.formatFileSize(mContext, wifiData.second));
                    holder.wifiDataUsageLayout.setVisibility(View.VISIBLE);
                    holder.wifiDataUsage.setText(dataUsage);
                } else holder.wifiDataUsageLayout.setVisibility(View.GONE);
            }
        }

        public void onBindViewHolder(@NonNull ListItemViewHolder holder, int position) {
            PackageUsageInfo.Entry entry;
            synchronized (mAdapterList) {
                entry = mAdapterList.get(position);
            }
            String dateTime = String.format(Locale.getDefault(), "%s",
                    DateUtils.formatDateTime(mContext, entry.startTime));
            holder.title.setText(dateTime);
            holder.subtitle.setText(DateUtils.getFormattedDuration(mContext, entry.getDuration()));
            holder.itemView.setBackgroundColor(position % 2 == 0 ? mColorSemiTransparent : mColorTransparent);
        }
    }
}
