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

package io.github.muntashirakon.AppManager.settings;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.oneclickops.ItemCount;
import io.github.muntashirakon.AppManager.rules.RulesTypeSelectionDialogFragment;
import io.github.muntashirakon.AppManager.rules.compontents.ExternalComponentsImporter;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class ImportExportDialogFragment extends DialogFragment {
    public static final String TAG = "ImportExportDialogFragment";

    private static final String MIME_JSON = "application/json";
    private static final String MIME_TSV = "text/tab-separated-values";
    private static final String MIME_XML = "text/xml";

    private final int userHandle = Users.getCurrentUserHandle();
    private SettingsActivity activity;
    private final ActivityResultLauncher<String> exportRules = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                RulesTypeSelectionDialogFragment dialogFragment = new RulesTypeSelectionDialogFragment();
                Bundle args = new Bundle();
                args.putInt(RulesTypeSelectionDialogFragment.ARG_MODE, RulesTypeSelectionDialogFragment.MODE_EXPORT);
                args.putParcelable(RulesTypeSelectionDialogFragment.ARG_URI, uri);
                args.putStringArrayList(RulesTypeSelectionDialogFragment.ARG_PKG, null);
                args.putIntArray(RulesTypeSelectionDialogFragment.ARG_USERS, Users.getUsersHandles());
                dialogFragment.setArguments(args);
                activity.getSupportFragmentManager().popBackStackImmediate();
                dialogFragment.show(activity.getSupportFragmentManager(), RulesTypeSelectionDialogFragment.TAG);
                if (getDialog() != null) getDialog().cancel();
            });
    private final ActivityResultLauncher<String> importRules = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
        RulesTypeSelectionDialogFragment dialogFragment = new RulesTypeSelectionDialogFragment();
        Bundle args = new Bundle();
        args.putInt(RulesTypeSelectionDialogFragment.ARG_MODE, RulesTypeSelectionDialogFragment.MODE_IMPORT);
        args.putParcelable(RulesTypeSelectionDialogFragment.ARG_URI, uri);
        args.putStringArrayList(RulesTypeSelectionDialogFragment.ARG_PKG, null);
        args.putIntArray(RulesTypeSelectionDialogFragment.ARG_USERS, Users.getUsersHandles());
        dialogFragment.setArguments(args);
        activity.getSupportFragmentManager().popBackStackImmediate();
        dialogFragment.show(activity.getSupportFragmentManager(), RulesTypeSelectionDialogFragment.TAG);
        if (getDialog() != null) getDialog().cancel();
    });
    private final ActivityResultLauncher<String> importFromWatt = registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
        Pair<Boolean, Integer> status = ExternalComponentsImporter.applyFromWatt(activity.getApplicationContext(), uris, Users.getUsersHandles());
        if (!status.first) {  // Not failed
            Toast.makeText(getContext(), R.string.the_import_was_successful, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getContext(), getResources().getQuantityString(R.plurals.failed_to_import_files, status.second, status.second), Toast.LENGTH_LONG).show();
        }
        if (getDialog() != null) getDialog().cancel();
    });
    private final ActivityResultLauncher<String> importFromBlocker = registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
        Pair<Boolean, Integer> status = ExternalComponentsImporter.applyFromBlocker(activity.getApplicationContext(), uris, Users.getUsersHandles());
        if (!status.first) {  // Not failed
            Toast.makeText(getContext(), R.string.the_import_was_successful, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getContext(), getResources().getQuantityString(R.plurals.failed_to_import_files, status.second, status.second), Toast.LENGTH_LONG).show();
        }
        if (getDialog() != null) getDialog().cancel();
    });

    @SuppressLint("SimpleDateFormat")
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        activity = (SettingsActivity) requireActivity();
        LayoutInflater inflater = LayoutInflater.from(activity);
        if (inflater == null) return super.onCreateDialog(savedInstanceState);
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.dialog_settings_import_export, null);
        view.findViewById(R.id.export_internal).setOnClickListener(v -> {
            final String fileName = "app_manager_rules_export-" + (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime())) + ".am.tsv";
            exportRules.launch(fileName);
        });
        view.findViewById(R.id.import_internal).setOnClickListener(v -> importRules.launch(MIME_TSV));
        view.findViewById(R.id.import_existing).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.pref_import_existing)
                        .setMessage(R.string.apply_to_system_apps_question)
                        .setPositiveButton(R.string.no, (dialog, which) -> importExistingRules(false))
                        .setNegativeButton(R.string.yes, ((dialog, which) -> importExistingRules(true)))
                        .show());
        view.findViewById(R.id.import_watt).setOnClickListener(v -> importFromWatt.launch(MIME_XML));
        view.findViewById(R.id.import_blocker).setOnClickListener(v -> importFromBlocker.launch(MIME_JSON));
        return new MaterialAlertDialogBuilder(activity)
                .setView(view)
                .setTitle(R.string.pref_import_export_blocking_rules)
                .setNegativeButton(R.string.cancel, null)
                .create();
    }

    private void importExistingRules(final boolean systemApps) {
        if (!AppPref.isRootEnabled()) {
            Toast.makeText(activity, R.string.only_works_in_root_mode, Toast.LENGTH_SHORT).show();
            return;
        }
        activity.progressIndicator.show();
        final Handler handler = new Handler(Looper.getMainLooper());
        final PackageManager pm = activity.getPackageManager();
        new Thread(() -> {
            final List<ItemCount> itemCounts = new ArrayList<>();
            ItemCount trackerCount;
            for (ApplicationInfo applicationInfo : pm.getInstalledApplications(0)) {
                if (!systemApps && (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                    continue;
                trackerCount = new ItemCount();
                trackerCount.packageName = applicationInfo.packageName;
                trackerCount.packageLabel = applicationInfo.loadLabel(pm).toString();
                trackerCount.count = PackageUtils.getUserDisabledComponentsForPackage(applicationInfo.packageName).size();
                if (trackerCount.count > 0) itemCounts.add(trackerCount);
            }
            if (!itemCounts.isEmpty()) {
                final List<String> selectedPackages = new ArrayList<>();
                final String[] packagesWithItemCounts = new String[itemCounts.size()];
                for (int i = 0; i < itemCounts.size(); ++i) {
                    trackerCount = itemCounts.get(i);
                    selectedPackages.add(trackerCount.packageName);
                    packagesWithItemCounts[i] = "(" + trackerCount.count + ") " + trackerCount.packageLabel;
                }
                final String[] trackerPackages = selectedPackages.toArray(new String[0]);
                final boolean[] checkedItems = new boolean[trackerPackages.length];
                Arrays.fill(checkedItems, false);
                handler.post(() -> {
                    activity.progressIndicator.hide();
                    new MaterialAlertDialogBuilder(activity)
                            .setMultiChoiceItems(packagesWithItemCounts, checkedItems, (dialog, which, isChecked) -> {
                                if (!isChecked) selectedPackages.remove(trackerPackages[which]);
                                else selectedPackages.add(trackerPackages[which]);
                            })
                            .setTitle(R.string.filtered_packages)
                            .setPositiveButton(R.string.apply, (dialog, which) -> {
                                activity.progressIndicator.show();
                                new Thread(() -> {
                                    List<String> failedPackages = ExternalComponentsImporter.applyFromExistingBlockList(selectedPackages, userHandle);
                                    if (!failedPackages.isEmpty()) {
                                        handler.post(() -> {
                                            new MaterialAlertDialogBuilder(activity)
                                                    .setTitle(R.string.failed_packages)
                                                    .setItems((CharSequence[]) failedPackages.toArray(), null)
                                                    .setNegativeButton(R.string.ok, null)
                                                    .show();
                                            activity.progressIndicator.hide();
                                        });
                                    } else handler.post(() -> {
                                        Toast.makeText(activity, R.string.the_import_was_successful, Toast.LENGTH_SHORT).show();
                                        activity.progressIndicator.hide();
                                    });
                                }).start();
                            })
                            .setNegativeButton(R.string.cancel, (dialog, which) -> activity.progressIndicator.hide())
                            .show();
                });
            } else {
                handler.post(() -> {
                    Toast.makeText(activity, R.string.no_matching_package_found, Toast.LENGTH_SHORT).show();
                    activity.progressIndicator.hide();
                });
            }
        }).start();
    }
}
