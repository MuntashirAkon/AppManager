// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.UserHandleHidden;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.utils.DateUtils;


public class RunningAppDetails extends BottomSheetDialogFragment {
    public static final String TAG = RunningAppDetails.class.getSimpleName();

    public static final String ARG_PS_ITEM = "ps_item";

    @NonNull
    public static RunningAppDetails getInstance(@NonNull ProcessItem processItem) {
        Bundle args = new Bundle();
        args.putParcelable(ARG_PS_ITEM, processItem);
        RunningAppDetails fragment = new RunningAppDetails();
        fragment.setArguments(args);
        return fragment;
    }

    private final ImageLoader mImageLoader = new ImageLoader();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_running_app_details, container, false);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ProcessItem processItem = requireArguments().getParcelable(ARG_PS_ITEM);
        LinearLayoutCompat appContainer = view.findViewById(R.id.app_container);
        ImageView appIcon = view.findViewById(R.id.icon);
        MaterialButton openAppInfoButton = view.findViewById(R.id.info);
        TextView appLabel = view.findViewById(R.id.name);
        TextView packageName = view.findViewById(R.id.package_name);
        TextView processName = view.findViewById(R.id.process_name);
        TextView pid = view.findViewById(R.id.pid);
        TextView ppid = view.findViewById(R.id.ppid);
        TextView rss = view.findViewById(R.id.rss);
        TextView vsz = view.findViewById(R.id.vsz);
        TextView cpuPercent = view.findViewById(R.id.cpu_percent);
        TextView cpuTime = view.findViewById(R.id.cpu_time);
        TextView priority = view.findViewById(R.id.priority);
        TextView threads = view.findViewById(R.id.threads);
        TextView user = view.findViewById(R.id.user);
        TextView state = view.findViewById(R.id.state);
        TextView seLinuxContext = view.findViewById(R.id.selinux_context);
        TextView cliArgs = view.findViewById(R.id.cli_args);

        processName.setText(processItem.name);
        pid.setText(String.valueOf(processItem.pid));
        ppid.setText(String.valueOf(processItem.ppid));
        rss.setText(Formatter.formatFileSize(requireContext(), processItem.getMemory()));
        vsz.setText(Formatter.formatFileSize(requireContext(), processItem.getVirtualMemory()));
        cpuPercent.setText(String.format(Locale.ROOT, "%.2f", processItem.getCpuTimeInPercent()));
        cpuTime.setText(DateUtils.getFormattedDuration(requireContext(), processItem.getCpuTimeInMillis(), false, true));
        priority.setText(String.valueOf(processItem.processEntry.priority));
        threads.setText(String.valueOf(processItem.processEntry.threadCount));
        user.setText(processItem.user + " (" + processItem.uid + ")");
        CharSequence stateInfo;
        if (TextUtils.isEmpty(processItem.state_extra)) {
            stateInfo = processItem.state;
        } else {
            stateInfo = processItem.state + " (" + processItem.state_extra + ")";
        }
        state.setText(stateInfo);
        seLinuxContext.setText(processItem.context);
        cliArgs.setText(processItem.getCommandlineArgsAsString());
        if (processItem instanceof AppProcessItem) {
            PackageInfo packageInfo = ((AppProcessItem) processItem).packageInfo;
            appContainer.setVisibility(View.VISIBLE);
            mImageLoader.displayImage(packageInfo.packageName, packageInfo.applicationInfo, appIcon);
            appLabel.setText(packageInfo.applicationInfo.loadLabel(requireContext().getPackageManager()));
            packageName.setText(packageInfo.packageName);
            openAppInfoButton.setOnClickListener(v -> {
                Intent appDetailsIntent = new Intent(requireContext(), AppDetailsActivity.class);
                appDetailsIntent.putExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME, packageInfo.packageName);
                appDetailsIntent.putExtra(AppDetailsActivity.EXTRA_USER_HANDLE, UserHandleHidden
                        .getUserId(processItem.uid));
                startActivity(appDetailsIntent);
                dismiss();
            });
        } else {
            appContainer.setVisibility(View.GONE);
        }
    }
}
