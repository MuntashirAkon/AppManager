// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.UserHandleHidden;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.oneclickops.ItemCount;
import io.github.muntashirakon.AppManager.rules.RulesTypeSelectionDialogFragment;
import io.github.muntashirakon.AppManager.rules.compontents.ExternalComponentsImporter;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.UiThreadHandler;

public class ImportExportRulesDialogFragment extends BottomSheetDialogFragment {
    public static final String TAG = "ImportExportRulesDialogFragment";

    private static final String MIME_JSON = "application/json";
    private static final String MIME_TSV = "text/tab-separated-values";
    private static final String MIME_XML = "text/xml";

    private final int userHandle = UserHandleHidden.myUserId();
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
                args.putIntArray(RulesTypeSelectionDialogFragment.ARG_USERS, Users.getUsersIds());
                dialogFragment.setArguments(args);
                dialogFragment.show(activity.getSupportFragmentManager(), RulesTypeSelectionDialogFragment.TAG);
            });
    private final ActivityResultLauncher<String> importRules = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                RulesTypeSelectionDialogFragment dialogFragment = new RulesTypeSelectionDialogFragment();
                Bundle args = new Bundle();
                args.putInt(RulesTypeSelectionDialogFragment.ARG_MODE, RulesTypeSelectionDialogFragment.MODE_IMPORT);
                args.putParcelable(RulesTypeSelectionDialogFragment.ARG_URI, uri);
                args.putStringArrayList(RulesTypeSelectionDialogFragment.ARG_PKG, null);
                args.putIntArray(RulesTypeSelectionDialogFragment.ARG_USERS, Users.getUsersIds());
                dialogFragment.setArguments(args);
                dialogFragment.show(activity.getSupportFragmentManager(), RulesTypeSelectionDialogFragment.TAG);
            });
    private final ActivityResultLauncher<String> importFromWatt = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(),
            uris -> {
                if (uris == null) {
                    // Back button pressed.
                    return;
                }
                new Thread(() -> {
                    List<String> failedFiles = ExternalComponentsImporter.applyFromWatt(activity
                            .getApplicationContext(), uris, Users.getUsersIds());
                    if (isDetached()) return;
                    activity.runOnUiThread(() -> {
                        if (failedFiles.size() == 0) {  // Not failed
                            UIUtils.displayLongToast(R.string.the_import_was_successful);
                        } else {
                            new MaterialAlertDialogBuilder(activity)
                                    .setTitle(activity.getResources().getQuantityString(R.plurals
                                                    .failed_to_import_files, failedFiles.size(), failedFiles.size()))
                                    .setItems(failedFiles.toArray(new String[0]), null)
                                    .setNegativeButton(R.string.close, null)
                                    .show();
                        }
                    });
                }).start();
                requireDialog().dismiss();
            });
    private final ActivityResultLauncher<String> importFromBlocker = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(),
            uris -> {
                if (uris == null) {
                    // Back button pressed.
                    return;
                }
                new Thread(() -> {
                    List<String> failedFiles = ExternalComponentsImporter.applyFromBlocker(activity
                            .getApplicationContext(), uris, Users.getUsersIds());
                    if (isDetached()) return;
                    activity.runOnUiThread(() -> {
                        if (failedFiles.size() == 0) {  // Not failed
                            UIUtils.displayLongToast(R.string.the_import_was_successful);
                        } else {
                            new MaterialAlertDialogBuilder(activity)
                                    .setTitle(activity.getResources().getQuantityString(R.plurals
                                                    .failed_to_import_files, failedFiles.size(), failedFiles.size()))
                                    .setItems(failedFiles.toArray(new String[0]), null)
                                    .setNegativeButton(R.string.close, null)
                                    .show();
                        }
                    });
                }).start();
                requireDialog().dismiss();
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_settings_import_export, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        activity = (SettingsActivity) requireActivity();
        view.findViewById(R.id.export_internal).setOnClickListener(v -> {
            final String fileName = "app_manager_rules_export-" + DateUtils.formatDateTime(System.currentTimeMillis()) + ".am.tsv";
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
    }

    private void importExistingRules(final boolean systemApps) {
        if (!AppPref.isRootEnabled()) {
            Toast.makeText(activity, R.string.only_works_in_root_mode, Toast.LENGTH_SHORT).show();
            return;
        }
        activity.progressIndicator.show();
        new Thread(() -> {
            final List<ItemCount> itemCounts = new ArrayList<>();
            ItemCount trackerCount;
            for (App app : AppManager.getDb().appDao().getAllInstalled()) {
                if (isDetached()) return;
                if (!systemApps && (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                    continue;
                trackerCount = new ItemCount();
                trackerCount.packageName = app.packageName;
                trackerCount.packageLabel = app.packageLabel;
                trackerCount.count = PackageUtils.getUserDisabledComponentsForPackage(app.packageName, userHandle).size();
                if (trackerCount.count > 0) itemCounts.add(trackerCount);
            }
            if (!itemCounts.isEmpty()) {
                final List<String> packages = new ArrayList<>();
                final CharSequence[] packagesWithItemCounts = new CharSequence[itemCounts.size()];
                for (int i = 0; i < itemCounts.size(); ++i) {
                    if (isDetached()) return;
                    trackerCount = itemCounts.get(i);
                    packages.add(trackerCount.packageName);
                    packagesWithItemCounts[i] = new SpannableStringBuilder(trackerCount.packageLabel).append("\n")
                            .append(UIUtils.getSmallerText(UIUtils.getSecondaryText(activity, activity.getResources()
                                    .getQuantityString(R.plurals.no_of_components, trackerCount.count,
                                            trackerCount.count))));
                }
                UiThreadHandler.run(() -> {
                    if (isDetached()) return;
                    activity.progressIndicator.hide();
                    new SearchableMultiChoiceDialogBuilder<>(activity, packages, packagesWithItemCounts)
                            .setTitle(R.string.filtered_packages)
                            .setPositiveButton(R.string.apply, (dialog, which, selectedPackages) -> {
                                activity.progressIndicator.show();
                                new Thread(() -> {
                                    List<String> failedPackages = ExternalComponentsImporter
                                            .applyFromExistingBlockList(selectedPackages, userHandle);
                                    if (!failedPackages.isEmpty()) {
                                        UiThreadHandler.run(() -> {
                                            new MaterialAlertDialogBuilder(activity)
                                                    .setTitle(R.string.failed_packages)
                                                    .setItems(failedPackages.toArray(new String[0]), null)
                                                    .setNegativeButton(R.string.ok, null)
                                                    .show();
                                            activity.progressIndicator.hide();
                                        });
                                    } else UiThreadHandler.run(() -> {
                                        Toast.makeText(activity, R.string.the_import_was_successful, Toast.LENGTH_SHORT).show();
                                        activity.progressIndicator.hide();
                                    });
                                }).start();
                            })
                            .setNegativeButton(R.string.cancel, (dialog, which, selectedItems) ->
                                    activity.progressIndicator.hide())
                            .show();
                });
            } else {
                UiThreadHandler.run(() -> {
                    Toast.makeText(activity, R.string.no_matching_package_found, Toast.LENGTH_SHORT).show();
                    activity.progressIndicator.hide();
                });
            }
        }).start();
    }
}
