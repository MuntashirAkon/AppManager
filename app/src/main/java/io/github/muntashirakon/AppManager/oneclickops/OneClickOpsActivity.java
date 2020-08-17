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

package io.github.muntashirakon.AppManager.oneclickops;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.ProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.rules.compontents.ExternalComponentsImporter;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ListItemCreator;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class OneClickOpsActivity extends AppCompatActivity {
    private ListItemCreator mItemCreator;
    private ProgressIndicator mProgressIndicator;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_one_click_ops);
        setSupportActionBar(findViewById(R.id.toolbar));
        mItemCreator = new ListItemCreator(this, R.id.container);
        mProgressIndicator = findViewById(R.id.progress_linear);
        setItems();
    }

    private void setItems() {
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.block_unblock_trackers),
                getString(R.string.block_unblock_trackers_description))
                .setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.block_unblock_trackers)
                        .setMessage(R.string.apply_to_system_apps_question)
                        .setPositiveButton(R.string.no, (dialog, which) -> blockTrackers(false))
                        .setNegativeButton(R.string.yes, ((dialog, which) -> blockTrackers(true)))
                        .show());
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.block_components_dots),
                getString(R.string.block_components_description))
                .setOnClickListener(v -> blockComponents());
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.deny_app_ops_dots),
                getString(R.string.deny_app_ops_description))
                .setOnClickListener(v -> blockAppOps());
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.clear_data_from_uninstalled_apps),
                getString(R.string.clear_data_from_uninstalled_apps_description))
                .setOnClickListener(v -> clearData());
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.clear_app_cache),
                getString(R.string.clear_app_cache_description))
                .setOnClickListener(v -> clearAppCache());
        mProgressIndicator.hide();
    }

    private void blockTrackers(final boolean systemApps) {
        if (!AppPref.isRootEnabled()) {
            Toast.makeText(this, R.string.only_works_in_root_mode, Toast.LENGTH_SHORT).show();
            return;
        }
        mProgressIndicator.show();
        new Thread(() -> {
            final List<ItemCount> trackerCounts = new ArrayList<>();
            ItemCount trackerCount;
            for (ApplicationInfo applicationInfo: getPackageManager().getInstalledApplications(0)) {
                if (!systemApps && (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                trackerCount = ComponentUtils.getTrackerCountForApp(applicationInfo);
                if (trackerCount.count > 0) trackerCounts.add(trackerCount);
            }
            if (!trackerCounts.isEmpty()) {
                final List<String> selectedPackages = new ArrayList<>();
                final String[] trackerPackagesWithTrackerCount = new String[trackerCounts.size()];
                for (int i = 0; i<trackerCounts.size(); ++i) {
                    trackerCount = trackerCounts.get(i);
                    selectedPackages.add(trackerCount.packageName);
                    trackerPackagesWithTrackerCount[i] = "(" + trackerCount.count + ") " + trackerCount.packageLabel;
                }
                final String[] trackerPackages = selectedPackages.toArray(new String[0]);
                final boolean[] checkedItems = new boolean[trackerPackages.length];
                Arrays.fill(checkedItems, true);
                runOnUiThread(() -> {
                    mProgressIndicator.hide();
                    new MaterialAlertDialogBuilder(this)
                            .setMultiChoiceItems(trackerPackagesWithTrackerCount, checkedItems, (dialog, which, isChecked) -> {
                                if (!isChecked) selectedPackages.remove(trackerPackages[which]);
                                else selectedPackages.add(trackerPackages[which]);
                            })
                            .setTitle(R.string.found_trackers)
                            .setPositiveButton(R.string.block, (dialog, which) -> {
                                mProgressIndicator.show();
                                new Thread(() -> {
                                    List<String> failedPackages = ComponentUtils.blockTrackingComponents(this, selectedPackages);
                                    if (!failedPackages.isEmpty()) {
                                        runOnUiThread(() -> {
                                            new MaterialAlertDialogBuilder(this)
                                                    .setTitle(R.string.failed_packages)
                                                    .setItems((CharSequence[]) failedPackages.toArray(), null)
                                                    .setNegativeButton(android.R.string.ok, null)
                                                    .show();
                                            mProgressIndicator.hide();
                                        });
                                    } else runOnUiThread(() -> {
                                        Toast.makeText(this, R.string.the_operation_was_successful, Toast.LENGTH_SHORT).show();
                                        mProgressIndicator.hide();
                                    });
                                }).start();
                            })
                            .setNeutralButton(R.string.unblock, (dialog, which) -> {
                                mProgressIndicator.show();
                                new Thread(() -> {
                                    List<String> failedPackages = ComponentUtils.unblockTrackingComponents(this, selectedPackages);
                                    if (!failedPackages.isEmpty()) {
                                        runOnUiThread(() -> {
                                            new MaterialAlertDialogBuilder(this)
                                                    .setTitle(R.string.failed_packages)
                                                    .setItems((CharSequence[]) failedPackages.toArray(), null)
                                                    .setNegativeButton(android.R.string.ok, null)
                                                    .show();
                                            mProgressIndicator.hide();
                                        });
                                    } else runOnUiThread(() -> {
                                        Toast.makeText(this, R.string.the_operation_was_successful, Toast.LENGTH_SHORT).show();
                                        mProgressIndicator.hide();
                                    });
                                }).start();
                            })
                            .setNegativeButton(android.R.string.cancel, (dialog, which) -> mProgressIndicator.hide())
                            .show();
                });
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.no_tracker_found, Toast.LENGTH_SHORT).show();
                    mProgressIndicator.hide();
                });
            }
        }).start();
    }

    private void blockComponents() {
        if (!AppPref.isRootEnabled()) {
            Toast.makeText(this, R.string.only_works_in_root_mode, Toast.LENGTH_SHORT).show();
            return;
        }
        View view = getLayoutInflater().inflate(R.layout.dialog_input_signatures, null);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.block_components_dots)
                .setView(view)
                .setPositiveButton(R.string.search, (dialog, which) -> {
                    final Editable signaturesEditable = ((TextInputEditText) view.findViewById(R.id.input_signatures)).getText();
                    final boolean systemApps = ((MaterialCheckBox) view.findViewById(R.id.checkbox_system_apps)).isChecked();
                    if (signaturesEditable == null) return;
                    mProgressIndicator.show();
                    new Thread(() -> {
                        String[] signatures = signaturesEditable.toString().split("\\s+");
                        if (signatures.length == 0) return;
                        final List<ItemCount> componentCounts = new ArrayList<>();
                        for (ApplicationInfo applicationInfo: getPackageManager().getInstalledApplications(0)) {
                            if (!systemApps && (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                            ItemCount componentCount = new ItemCount();
                            componentCount.packageName = applicationInfo.packageName;
                            componentCount.packageLabel = applicationInfo.loadLabel(getPackageManager()).toString();
                            componentCount.count = PackageUtils.getFilteredComponents(applicationInfo.packageName, signatures).size();
                            if (componentCount.count > 0) componentCounts.add(componentCount);
                        }
                        if (!componentCounts.isEmpty()) {
                            ItemCount componentCount;
                            final List<String> selectedPackages = new ArrayList<>();
                            final String[] filteredPackagesWithComponentCount = new String[componentCounts.size()];
                            for (int i = 0; i<componentCounts.size(); ++i) {
                                componentCount = componentCounts.get(i);
                                selectedPackages.add(componentCount.packageName);
                                filteredPackagesWithComponentCount[i] = "(" + componentCount.count + ") " + componentCount.packageLabel;
                            }
                            final String[] filteredPackages = selectedPackages.toArray(new String[0]);
                            final boolean[] checkedItems = new boolean[selectedPackages.size()];
                            Arrays.fill(checkedItems, true);
                            runOnUiThread(() -> {
                                mProgressIndicator.hide();
                                new MaterialAlertDialogBuilder(this)
                                        .setMultiChoiceItems(filteredPackagesWithComponentCount, checkedItems, (dialog1, which1, isChecked) -> {
                                            if (!isChecked) selectedPackages.remove(filteredPackages[which1]);
                                            else selectedPackages.add(filteredPackages[which1]);
                                        })
                                        .setTitle(R.string.filtered_packages)
                                        .setPositiveButton(R.string.apply, (dialog1, which1) -> {
                                            mProgressIndicator.show();
                                            new Thread(() -> {
                                                List<String> failedPackages = ComponentUtils.blockFilteredComponents(this, selectedPackages, signatures);
                                                if (!failedPackages.isEmpty()) {
                                                    runOnUiThread(() -> {
                                                        new MaterialAlertDialogBuilder(this)
                                                                .setTitle(R.string.failed_packages)
                                                                .setItems((CharSequence[]) failedPackages.toArray(), null)
                                                                .setNegativeButton(android.R.string.ok, null)
                                                                .show();
                                                        mProgressIndicator.hide();
                                                    });
                                                } else runOnUiThread(() -> {
                                                    Toast.makeText(this, R.string.the_operation_was_successful, Toast.LENGTH_SHORT).show();
                                                    mProgressIndicator.hide();
                                                });
                                            }).start();
                                        })
                                        .setNegativeButton(android.R.string.cancel, (dialog1, which1) -> mProgressIndicator.hide())
                                        .show();
                            });
                        } else {
                            runOnUiThread(() -> {
                                Toast.makeText(this, R.string.no_matching_package_found, Toast.LENGTH_SHORT).show();
                                mProgressIndicator.hide();
                            });
                        }
                    }).start();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void blockAppOps() {
        if (!AppPref.isRootEnabled() && !AppPref.isAdbEnabled()) {
            Toast.makeText(this, R.string.only_works_in_root_or_adb_mode, Toast.LENGTH_SHORT).show();
            return;
        }
        View view = getLayoutInflater().inflate(R.layout.dialog_input_app_ops, null);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.deny_app_ops_dots)
                .setView(view)
                .setPositiveButton(R.string.search, (dialog, which) -> {
                    final Editable appOpsEditable = ((TextInputEditText) view.findViewById(R.id.input_app_ops)).getText();
                    final boolean systemApps = ((MaterialCheckBox) view.findViewById(R.id.checkbox_system_apps)).isChecked();
                    if (appOpsEditable == null) return;
                    mProgressIndicator.show();
                    new Thread(() -> {
                        String[] appOpsStr = appOpsEditable.toString().split("\\s+");
                        if (appOpsStr.length == 0) return;
                        int[] appOps = new int[appOpsStr.length];
                        try {
                            for (int i = 0; i < appOpsStr.length; ++i)
                                appOps[i] = Integer.parseInt(appOpsStr[i]);
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                Toast.makeText(this, R.string.failed_to_parse_some_numbers, Toast.LENGTH_SHORT).show();
                                mProgressIndicator.hide();
                            });
                            return;
                        }
                        final List<ItemCount> appOpCounts = new ArrayList<>();
                        for (ApplicationInfo applicationInfo:
                                getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA)) {
                            if (!systemApps && (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                            ItemCount appOpCount = new ItemCount();
                            appOpCount.packageName = applicationInfo.packageName;
                            appOpCount.packageLabel = applicationInfo.loadLabel(getPackageManager()).toString();
                            appOpCount.count = PackageUtils.getFilteredAppOps(applicationInfo.packageName, appOps).size();
                            if (appOpCount.count > 0) appOpCounts.add(appOpCount);
                        }
                        if (!appOpCounts.isEmpty()) {
                            ItemCount appOpCount;
                            final List<String> selectedPackages = new ArrayList<>();
                            final String[] filteredPackagesWithAppOpCount = new String[appOpCounts.size()];
                            for (int i = 0; i<appOpCounts.size(); ++i) {
                                appOpCount = appOpCounts.get(i);
                                selectedPackages.add(appOpCount.packageName);
                                filteredPackagesWithAppOpCount[i] = "(" + appOpCount.count + ") " + appOpCount.packageLabel;
                            }
                            final String[] filteredPackages = selectedPackages.toArray(new String[0]);
                            final boolean[] checkedItems = new boolean[selectedPackages.size()];
                            Arrays.fill(checkedItems, true);
                            runOnUiThread(() -> {
                                mProgressIndicator.hide();
                                new MaterialAlertDialogBuilder(this)
                                        .setMultiChoiceItems(filteredPackagesWithAppOpCount, checkedItems, (dialog1, which1, isChecked) -> {
                                            if (!isChecked) selectedPackages.remove(filteredPackages[which1]);
                                            else selectedPackages.add(filteredPackages[which1]);
                                        })
                                        .setTitle(R.string.filtered_packages)
                                        .setPositiveButton(R.string.apply, (dialog1, which1) -> {
                                            mProgressIndicator.show();
                                            new Thread(() -> {
                                                List<String> failedPackages = ExternalComponentsImporter.denyFilteredAppOps(this, selectedPackages, appOps);
                                                if (!failedPackages.isEmpty()) {
                                                    runOnUiThread(() -> {
                                                        new MaterialAlertDialogBuilder(this)
                                                                .setTitle(R.string.failed_packages)
                                                                .setItems((CharSequence[]) failedPackages.toArray(), null)
                                                                .setNegativeButton(android.R.string.ok, null)
                                                                .show();
                                                        mProgressIndicator.hide();
                                                    });
                                                } else runOnUiThread(() -> {
                                                    Toast.makeText(this, R.string.the_operation_was_successful, Toast.LENGTH_SHORT).show();
                                                    mProgressIndicator.hide();
                                                });
                                            }).start();
                                        })
                                        .setNegativeButton(android.R.string.cancel, (dialog1, which1) -> mProgressIndicator.hide())
                                        .show();
                            });
                        } else {
                            runOnUiThread(() -> {
                                Toast.makeText(this, R.string.no_matching_package_found, Toast.LENGTH_SHORT).show();
                                mProgressIndicator.hide();
                            });
                        }
                    }).start();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void clearData() {
        // TODO
        Toast.makeText(this, "Not implemented yet.", Toast.LENGTH_SHORT).show();
    }

    private void clearAppCache() {
        // TODO
        Toast.makeText(this, "Not implemented yet.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}