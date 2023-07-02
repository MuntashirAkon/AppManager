// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runningapps;

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
import androidx.core.os.BundleCompat;

import com.google.android.material.button.MaterialButton;

import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment;


public class RunningAppDetails extends CapsuleBottomSheetDialogFragment {
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

    @NonNull
    @Override
    public View initRootView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_running_app_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ProcessItem processItem = BundleCompat.getParcelable(requireArguments(), ARG_PS_ITEM, ProcessItem.class);
        if (processItem == null) {
            dismiss();
            return;
        }
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
        pid.setText(String.format(Locale.getDefault(), "%d", processItem.pid));
        ppid.setText(String.format(Locale.getDefault(), "%d", processItem.ppid));
        rss.setText(Formatter.formatFileSize(requireContext(), processItem.getMemory()));
        vsz.setText(Formatter.formatFileSize(requireContext(), processItem.getVirtualMemory()));
        cpuPercent.setText(String.format(Locale.getDefault(), "%.2f", processItem.getCpuTimeInPercent()));
        cpuTime.setText(DateUtils.getFormattedDuration(requireContext(), processItem.getCpuTimeInMillis(), false, true));
        priority.setText(String.format(Locale.getDefault(), "%d", processItem.getPriority()));
        threads.setText(String.format(Locale.getDefault(), "%d", processItem.getThreadCount()));
        user.setText(String.format(Locale.getDefault(), "%s (%d)", processItem.user, processItem.uid));
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
            ImageLoader.getInstance().displayImage(packageInfo.packageName, packageInfo.applicationInfo, appIcon);
            appLabel.setText(packageInfo.applicationInfo.loadLabel(requireContext().getPackageManager()));
            packageName.setText(packageInfo.packageName);
            openAppInfoButton.setOnClickListener(v -> {
                Intent appDetailsIntent = AppDetailsActivity.getIntent(requireContext(), packageInfo.packageName,
                        UserHandleHidden.getUserId(processItem.uid));
                startActivity(appDetailsIntent);
                dismiss();
            });
        } else {
            appContainer.setVisibility(View.GONE);
        }
    }
}
