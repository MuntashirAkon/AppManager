// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.usage;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.view.TextInputLayoutCompat;
import io.github.muntashirakon.widget.TextInputTextView;

public class AppUsageDetailsDialog extends CapsuleBottomSheetDialogFragment {
    public static final String TAG = AppUsageDetailsDialog.class.getSimpleName();

    private static final String ARG_PACKAGE_USAGE_INFO = "pkg_usg_info";
    private static final String ARG_INTERVAL_TYPE = "interval";
    private static final String ARG_DATE = "date";

    @NonNull
    public static AppUsageDetailsDialog getInstance(@Nullable PackageUsageInfo usageInfo,
                                                    @IntervalType int interval, long date) {
        AppUsageDetailsDialog fragment = new AppUsageDetailsDialog();
        Bundle args = new Bundle();
        args.putParcelable(AppUsageDetailsDialog.ARG_PACKAGE_USAGE_INFO, usageInfo);
        args.putInt(ARG_INTERVAL_TYPE, interval);
        args.putLong(ARG_DATE, date);
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
        @IntervalType int intervalType = requireArguments().getInt(ARG_INTERVAL_TYPE);
        long date = requireArguments().getLong(ARG_DATE);
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

        final BarChartView barChartView;
        final TextInputTextView screenTime = view.findViewById(R.id.screen_time);
        final TextInputTextView timesOpened = view.findViewById(R.id.times_opened);
        final TextInputTextView lastUsed = view.findViewById(R.id.last_used);
        final TextInputTextView userId = view.findViewById(R.id.user_id);
        final TextInputTextView mobileDataUsage = view.findViewById(R.id.data_usage);
        final TextInputLayout mobileDataUsageLayout = TextInputLayoutCompat.fromTextInputEditText(mobileDataUsage);
        final TextInputTextView wifiDataUsage = view.findViewById(R.id.wifi_usage);
        final TextInputLayout wifiDataUsageLayout = TextInputLayoutCompat.fromTextInputEditText(wifiDataUsage);
        final LinearLayoutCompat dataUsageLayout = view.findViewById(R.id.data_usage_layout);
        barChartView = view.findViewById(R.id.bar_chart);

        AppUsageStatsManager.DataUsage mobileData = usageInfo.mobileData;
        AppUsageStatsManager.DataUsage wifiData = usageInfo.wifiData;

        screenTime.setText(DateUtils.getFormattedDuration(requireContext(), usageInfo.screenTime));
        timesOpened.setText(getResources().getQuantityString(R.plurals.no_of_times_opened,
                usageInfo.timesOpened, usageInfo.timesOpened));
        long lastRun = usageInfo.lastUsageTime > 1 ? (System.currentTimeMillis() - usageInfo.lastUsageTime) : 0;
        if (usageInfo.packageName.equals(BuildConfig.APPLICATION_ID)) {
            // Special case for App Manager since the user is using the app right now
            lastUsed.setText(R.string.running);
        } else if (lastRun > 1) {
            lastUsed.setText(String.format(Locale.getDefault(), "%s %s", DateUtils
                    .getFormattedDuration(requireContext(), lastRun), getString(R.string.ago)));
        } else {
            lastUsed.setText(R.string._undefined);
        }
        userId.setText(String.format(Locale.getDefault(), "%d", usageInfo.userId));
        if ((mobileData == null && wifiData == null) || (mobileData != null && wifiData != null
                && (mobileData.getTotal() + wifiData.getTotal() == 0))) {
            dataUsageLayout.setVisibility(View.GONE);
        } else {
            dataUsageLayout.setVisibility(View.VISIBLE);
            if (mobileData != null && mobileData.getTotal() != 0) {
                String dataUsage = String.format("  ↑ %1$s ↓ %2$s",
                        Formatter.formatFileSize(requireContext(), mobileData.first),
                        Formatter.formatFileSize(requireContext(), mobileData.second));
                mobileDataUsageLayout.setVisibility(View.VISIBLE);
                mobileDataUsage.setText(dataUsage);
            } else mobileDataUsageLayout.setVisibility(View.GONE);
            if (wifiData != null && wifiData.getTotal() != 0) {
                String dataUsage = String.format("  ↑ %1$s ↓ %2$s",
                        Formatter.formatFileSize(requireContext(), wifiData.first),
                        Formatter.formatFileSize(requireContext(), wifiData.second));
                wifiDataUsageLayout.setVisibility(View.VISIBLE);
                wifiDataUsage.setText(dataUsage);
            } else wifiDataUsageLayout.setVisibility(View.GONE);
        }
        if (usageInfo.entries != null) {
            UsageDataProcessor.updateChartWithAppUsage(barChartView, usageInfo.entries, intervalType, date);
        }

        // Load the body
        requireView().postDelayed(this::finishLoading, 300);
    }
}
