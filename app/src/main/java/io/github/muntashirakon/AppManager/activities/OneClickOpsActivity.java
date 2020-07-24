package io.github.muntashirakon.AppManager.activities;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.ProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.storage.RulesStorageManager;
import io.github.muntashirakon.AppManager.storage.compontents.ExternalComponentsImporter;
import io.github.muntashirakon.AppManager.storage.compontents.TrackerComponentUtils;
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
        mItemCreator.addItemWithTitleSubtitle(getString(R.string.block_trackers),
                getString(R.string.block_trackers_description))
                .setOnClickListener(v -> blockTrackers());
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

    private void blockTrackers() {
        if (!AppPref.isRootEnabled()) {
            Toast.makeText(this, R.string.only_works_in_root_mode, Toast.LENGTH_SHORT).show();
            return;
        }
        mProgressIndicator.show();
        new Thread(() -> {
            List<ApplicationInfo> applicationInfoList = getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
            HashMap<String, Integer> trackerCount = new HashMap<>();
            HashMap<String, RulesStorageManager.Type> trackersPerPackage;
            for (ApplicationInfo applicationInfo: applicationInfoList) {
                trackersPerPackage = TrackerComponentUtils.getTrackerComponentsForPackage(applicationInfo.packageName);
                if (!trackersPerPackage.isEmpty()) {
                    trackerCount.put(applicationInfo.packageName, trackersPerPackage.size());
                }
            }
            if (!trackerCount.isEmpty()) {
                Set<String> selectedPackages = trackerCount.keySet();
                String[] trackerPackages = selectedPackages.toArray(new String[0]);
                String[] trackerPackagesWithTrackerCount = new String[trackerCount.size()];
                for (int i = 0; i<trackerCount.size(); ++i) trackerPackagesWithTrackerCount[i] = "(" + trackerCount.get(trackerPackages[i]) + ") " + trackerPackages[i];
                boolean[] checkedItems = new boolean[selectedPackages.size()];
                Arrays.fill(checkedItems, true);
                runOnUiThread(() -> {
                    mProgressIndicator.hide();
                    new MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialog)
                            .setMultiChoiceItems(trackerPackagesWithTrackerCount, checkedItems, (dialog, which, isChecked) -> {
                                if (!isChecked) selectedPackages.remove(trackerPackages[which]);
                                else selectedPackages.add(trackerPackages[which]);
                            })
                            .setTitle(R.string.found_trackers)
                            .setPositiveButton(R.string.apply, (dialog, which) -> {
                                mProgressIndicator.show();
                                new Thread(() -> {
                                    List<String> failedPackages = ExternalComponentsImporter.applyFromTrackingComponents(this, selectedPackages);
                                    if (!failedPackages.isEmpty()) {
                                        runOnUiThread(() -> {
                                            new MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialog)
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
        new MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialog)
                .setTitle(R.string.block_components_dots)
                .setView(view)
                .setPositiveButton(R.string.search, (dialog, which) -> {
                    final Editable signaturesEditable = ((TextInputEditText) view.findViewById(R.id.input_signatures)).getText();
                    if (signaturesEditable == null) return;
                    mProgressIndicator.show();
                    new Thread(() -> {
                        String[] signatures = signaturesEditable.toString().split("\\s+");
                        if (signatures.length == 0) return;
                        List<ApplicationInfo> applicationInfoList = getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
                        HashMap<String, Integer> componentsCount = new HashMap<>();
                        HashMap<String, RulesStorageManager.Type> componentsPerPackage;
                        for (ApplicationInfo applicationInfo: applicationInfoList) {
                            componentsPerPackage = PackageUtils.getFilteredComponents(applicationInfo.packageName, signatures);
                            if (!componentsPerPackage.isEmpty()) {
                                componentsCount.put(applicationInfo.packageName, componentsPerPackage.size());
                            }
                        }
                        if (!componentsCount.isEmpty()) {
                            Set<String> selectedPackages = componentsCount.keySet();
                            String[] filteredPackages = selectedPackages.toArray(new String[0]);
                            String[] filteredPackagesWithComponentCount = new String[componentsCount.size()];
                            for (int i = 0; i<componentsCount.size(); ++i) filteredPackagesWithComponentCount[i] = "(" + componentsCount.get(filteredPackages[i]) + ") " + filteredPackages[i];
                            boolean[] checkedItems = new boolean[selectedPackages.size()];
                            Arrays.fill(checkedItems, true);
                            runOnUiThread(() -> {
                                mProgressIndicator.hide();
                                new MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialog)
                                        .setMultiChoiceItems(filteredPackagesWithComponentCount, checkedItems, (dialog1, which1, isChecked) -> {
                                            if (!isChecked) selectedPackages.remove(filteredPackages[which1]);
                                            else selectedPackages.add(filteredPackages[which1]);
                                        })
                                        .setTitle(R.string.filtered_packages)
                                        .setPositiveButton(R.string.apply, (dialog1, which1) -> {
                                            mProgressIndicator.show();
                                            new Thread(() -> {
                                                List<String> failedPackages = ExternalComponentsImporter.applyFilteredComponents(this, selectedPackages, signatures);
                                                if (!failedPackages.isEmpty()) {
                                                    runOnUiThread(() -> {
                                                        new MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialog)
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
                                Toast.makeText(this, R.string.no_tracker_found, Toast.LENGTH_SHORT).show();
                                mProgressIndicator.hide();
                            });
                        }
                    }).start();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        Toast.makeText(this, "Not implemented yet.", Toast.LENGTH_SHORT).show();
    }

    private void blockAppOps() {
        // TODO
        Toast.makeText(this, "Not implemented yet.", Toast.LENGTH_SHORT).show();
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