// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import android.graphics.drawable.Drawable;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.widget.RecyclerView;

class AppUsageAdapter extends RecyclerView.ListAdapter<AppUsageAdapter.UsageListItem, RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_HEADER = 1;
    private static final int VIEW_TYPE_LIST_ITEM = 2;

    private final AppUsageActivity mActivity;

    abstract static class UsageListItem {
        static final class Header extends UsageListItem {
            @Override
            public boolean equals(Object obj) {
                return obj instanceof Header;
            }

            @Override
            public int hashCode() {
                return VIEW_TYPE_HEADER;
            }
        }

        static final class Item extends UsageListItem {
            @NonNull
            final PackageUsageInfo usageInfo;

            Item(@NonNull PackageUsageInfo usageInfo) {
                this.usageInfo = usageInfo;
            }
        }
    }

    private static final DiffUtil.ItemCallback<UsageListItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<UsageListItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull UsageListItem oldItem, @NonNull UsageListItem newItem) {
            if (oldItem instanceof UsageListItem.Header && newItem instanceof UsageListItem.Header) {
                return true;
            }
            if (oldItem instanceof UsageListItem.Item && newItem instanceof UsageListItem.Item) {
                return Objects.equals(((UsageListItem.Item) oldItem).usageInfo.packageName,
                        ((UsageListItem.Item) newItem).usageInfo.packageName);
            }
            return false;
        }

        @Override
        public boolean areContentsTheSame(@NonNull UsageListItem oldItem, @NonNull UsageListItem newItem) {
            if (oldItem instanceof UsageListItem.Header && newItem instanceof UsageListItem.Header) {
                // Always rebind the header
                return false;
            }
            if (oldItem instanceof UsageListItem.Item && newItem instanceof UsageListItem.Item) {
                PackageUsageInfo oldInfo = ((UsageListItem.Item) oldItem).usageInfo;
                PackageUsageInfo newInfo = ((UsageListItem.Item) newItem).usageInfo;
                return oldInfo.screenTime == newInfo.screenTime
                        && oldInfo.timesOpened == newInfo.timesOpened
                        && oldInfo.lastUsageTime == newInfo.lastUsageTime
                        && Objects.equals(oldInfo.appLabel, newInfo.appLabel);
            }
            return false;
        }
    };

    AppUsageAdapter(@NonNull AppUsageActivity activity) {
        super(DIFF_CALLBACK);
        mActivity = activity;
    }

    void setDefaultList(@Nullable List<PackageUsageInfo> list) {
        List<UsageListItem> combinedList = new ArrayList<>();
        combinedList.add(new UsageListItem.Header()); // Consumes position 0 natively

        if (list != null) {
            for (PackageUsageInfo info : list) {
                combinedList.add(new UsageListItem.Item(info));
            }
        }
        submitList(combinedList);
    }

    @Override
    public int getItemViewType(int position) {
        if (getItem(position) instanceof UsageListItem.Header) {
            return VIEW_TYPE_HEADER;
        }
        return VIEW_TYPE_LIST_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case VIEW_TYPE_HEADER:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_usage_header, parent, false);
                return new ListHeaderViewHolder(view);
            case VIEW_TYPE_LIST_ITEM:
            default:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_usage, parent, false);
                return new ListItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        UsageListItem item = getItem(position);
        if (item instanceof UsageListItem.Header) {
            onBindViewHolder((ListHeaderViewHolder) holder);
        } else if (item instanceof UsageListItem.Item) {
            onBindViewHolder((ListItemViewHolder) holder, ((UsageListItem.Item) item).usageInfo);
        }
    }

    private void onBindViewHolder(@NonNull ListHeaderViewHolder holder) {
        int intervalType = mActivity.viewModel.getCurrentInterval();
        long duration = mActivity.viewModel.getTotalScreenTime();
        long date = mActivity.viewModel.getCurrentDate();
        CharSequence formattedDuration = DateUtils.getFormattedDuration(mActivity, duration);
        CharSequence intervalDescription = UsageUtils.getIntervalDescription(mActivity, intervalType, date);
        StringBuilder contentDescription = new StringBuilder()
                .append(mActivity.getString(R.string.app_usage))
                .append(" ")
                .append(intervalDescription)
                .append(". ")
                .append(formattedDuration);
        holder.itemView.setContentDescription(contentDescription);
        holder.screenTimeView.setText(formattedDuration);
        holder.usageIntervalView.setText(intervalDescription);
        holder.nextButton.setVisibility(UsageUtils.hasNextDay(date) ? View.VISIBLE : View.INVISIBLE);
        holder.nextButton.setOnClickListener(v -> mActivity.viewModel.loadNext());
        holder.previousButton.setOnClickListener(v -> mActivity.viewModel.loadPrevious());
        UsageDataProcessor.updateChartWithAppUsage(holder.barChartView, mActivity.viewModel.getPackageUsageEntries(), intervalType, date);
    }

    private void onBindViewHolder(@NonNull ListItemViewHolder holder, @NonNull PackageUsageInfo usageInfo) {
        final int percentUsage = getUsagePercent(usageInfo.screenTime);
        // Set label (or package name on failure)
        holder.appLabel.setText(usageInfo.appLabel);
        // Set icon
        holder.appIcon.setTag(usageInfo.packageName);
        ImageLoader.getInstance().displayImage(usageInfo.packageName, usageInfo.applicationInfo, holder.appIcon);
        // Set user ID
        if (mActivity.viewModel.hasMultipleUsers()) {
            holder.badge.setVisibility(View.VISIBLE);
            holder.badge.setText(String.format(Locale.getDefault(), "%d", usageInfo.userId));
        } else {
            holder.badge.setVisibility(View.GONE);
        }
        // Set package name
        holder.packageName.setText(usageInfo.packageName);
        // Set usage
        long lastTimeUsed = usageInfo.lastUsageTime > 1 ? (System.currentTimeMillis() - usageInfo.lastUsageTime) : 0;
        long currentDate = mActivity.viewModel.getCurrentDate();
        if (usageInfo.packageName.equals(BuildConfig.APPLICATION_ID) && UsageUtils.isToday(currentDate)) {
            // Special case for App Manager since the user is using the app right now
            holder.lastUsageDate.setText(R.string.running);
        } else if (lastTimeUsed > 1) {
            holder.lastUsageDate.setText(String.format(Locale.getDefault(), "%s %s",
                    DateUtils.getFormattedDuration(mActivity, lastTimeUsed), mActivity.getString(R.string.ago)));
        } else {
            holder.lastUsageDate.setText(R.string._undefined);
        }
        String screenTimesWithTimesOpened;
        // Set times opened
        screenTimesWithTimesOpened = mActivity.getResources().getQuantityString(R.plurals.no_of_times_opened, usageInfo.timesOpened, usageInfo.timesOpened);
        // Set screen time
        screenTimesWithTimesOpened += ", " + DateUtils.getFormattedDuration(mActivity, usageInfo.screenTime);
        holder.screenTime.setText(screenTimesWithTimesOpened);
        // Set data usage
        AppUsageStatsManager.DataUsage mobileData = usageInfo.mobileData;
        if (mobileData != null && (mobileData.first != 0 || mobileData.second != 0)) {
            Drawable phoneIcon = ContextCompat.getDrawable(mActivity, R.drawable.ic_phone_android);
            String dataUsage = String.format("  ↑ %1$s ↓ %2$s",
                    Formatter.formatFileSize(mActivity, mobileData.first),
                    Formatter.formatFileSize(mActivity, mobileData.second));
            holder.mobileDataUsage.setText(UIUtils.setImageSpan(dataUsage, phoneIcon, holder.mobileDataUsage));
        } else holder.mobileDataUsage.setText("");
        AppUsageStatsManager.DataUsage wifiData = usageInfo.wifiData;
        if (wifiData != null && (wifiData.first != 0 || wifiData.second != 0)) {
            Drawable wifiIcon = ContextCompat.getDrawable(mActivity, R.drawable.ic_wifi);
            String dataUsage = String.format("  ↑ %1$s ↓ %2$s",
                    Formatter.formatFileSize(mActivity, wifiData.first),
                    Formatter.formatFileSize(mActivity, wifiData.second));
            holder.wifiDataUsage.setText(UIUtils.setImageSpan(dataUsage, wifiIcon, holder.wifiDataUsage));
        } else holder.wifiDataUsage.setText("");
        // Set usage percentage
        holder.percentUsage.setText(String.format(Locale.getDefault(), "%d%%", percentUsage));
        holder.usageIndicator.show();
        holder.usageIndicator.setProgress(percentUsage);
        // On Click Listener
        holder.itemView.setOnClickListener(v -> mActivity.viewModel.loadPackageUsageInfo(usageInfo));
    }

    int getUsagePercent(long screenTime) {
        return (int) (screenTime * 100. / mActivity.viewModel.getTotalScreenTime());
    }

    static class ListHeaderViewHolder extends RecyclerView.ViewHolder {
        final MaterialTextView screenTimeView;
        final MaterialTextView usageIntervalView;
        final MaterialButton previousButton;
        final MaterialButton nextButton;
        final BarChartView barChartView;

        public ListHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            screenTimeView = itemView.findViewById(R.id.screen_time);
            usageIntervalView = itemView.findViewById(R.id.time);
            previousButton = itemView.findViewById(R.id.action_previous);
            nextButton = itemView.findViewById(R.id.action_next);
            barChartView = itemView.findViewById(R.id.bar_chart);
        }
    }

    static class ListItemViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        MaterialTextView appLabel;
        MaterialTextView badge;
        MaterialTextView packageName;
        MaterialTextView lastUsageDate;
        MaterialTextView mobileDataUsage;
        MaterialTextView wifiDataUsage;
        MaterialTextView screenTime;
        MaterialTextView percentUsage;
        LinearProgressIndicator usageIndicator;

        public ListItemViewHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.icon);
            appIcon.setClipToOutline(true);
            badge = itemView.findViewById(R.id.badge);
            appLabel = itemView.findViewById(R.id.label);
            packageName = itemView.findViewById(R.id.package_name);
            lastUsageDate = itemView.findViewById(R.id.date);
            mobileDataUsage = itemView.findViewById(R.id.data_usage);
            wifiDataUsage = itemView.findViewById(R.id.wifi_usage);
            screenTime = itemView.findViewById(R.id.screen_time);
            percentUsage = itemView.findViewById(R.id.percent_usage);
            usageIndicator = itemView.findViewById(R.id.progress_linear);
        }
    }
}