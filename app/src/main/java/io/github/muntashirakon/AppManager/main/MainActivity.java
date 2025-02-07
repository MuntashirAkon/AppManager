// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.collection.ArrayMap;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.behavior.FreezeUnfreeze;
import io.github.muntashirakon.AppManager.apk.dexopt.DexOptDialog;
import io.github.muntashirakon.AppManager.apk.list.ListExporter;
import io.github.muntashirakon.AppManager.backup.dialog.BackupRestoreDialogFragment;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.batchops.struct.BatchFreezeOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchNetPolicyOptions;
import io.github.muntashirakon.AppManager.batchops.struct.IBatchOpOptions;
import io.github.muntashirakon.AppManager.changelog.Changelog;
import io.github.muntashirakon.AppManager.changelog.ChangelogParser;
import io.github.muntashirakon.AppManager.changelog.ChangelogRecyclerAdapter;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.debloat.DebloaterActivity;
import io.github.muntashirakon.AppManager.filters.FinderActivity;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.misc.HelpActivity;
import io.github.muntashirakon.AppManager.misc.LabsActivity;
import io.github.muntashirakon.AppManager.oneclickops.OneClickOpsActivity;
import io.github.muntashirakon.AppManager.profiles.AddToProfileDialogFragment;
import io.github.muntashirakon.AppManager.profiles.ProfilesActivity;
import io.github.muntashirakon.AppManager.rules.RulesTypeSelectionDialogFragment;
import io.github.muntashirakon.AppManager.runningapps.RunningAppsActivity;
import io.github.muntashirakon.AppManager.self.life.FundingCampaignChecker;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.settings.SettingsActivity;
import io.github.muntashirakon.AppManager.usage.AppUsageActivity;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.StoragePermission;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.AlertDialogBuilder;
import io.github.muntashirakon.dialog.ScrollableDialogBuilder;
import io.github.muntashirakon.dialog.SearchableFlagsDialogBuilder;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.multiselection.MultiSelectionActionsView;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.MultiSelectionView;
import io.github.muntashirakon.widget.SwipeRefreshLayout;

public class MainActivity extends BaseActivity implements AdvancedSearchView.OnQueryTextListener,
        SwipeRefreshLayout.OnRefreshListener, MultiSelectionActionsView.OnItemSelectedListener {
    private static final String PACKAGE_NAME_APK_UPDATER = "com.apkupdater";
    private static final String ACTIVITY_NAME_APK_UPDATER = "com.apkupdater.activity.MainActivity";

    private static boolean SHOW_DISCLAIMER = true;

    MainViewModel viewModel;

    private MainRecyclerAdapter mAdapter;
    private AdvancedSearchView mSearchView;
    private LinearProgressIndicator mProgressIndicator;
    private SwipeRefreshLayout mSwipeRefresh;
    private MultiSelectionView mMultiSelectionView;
    MainBatchOpsHandler mBatchOpsHandler;
    private MenuItem mAppUsageMenu;

    private final StoragePermission mStoragePermission = StoragePermission.init(this);

    private final ActivityResultLauncher<String> mBatchExportRules = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/tab-separated-values"),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                if (viewModel == null) {
                    // Invalid state
                    return;
                }
                RulesTypeSelectionDialogFragment dialogFragment = new RulesTypeSelectionDialogFragment();
                Bundle args = new Bundle();
                args.putInt(RulesTypeSelectionDialogFragment.ARG_MODE, RulesTypeSelectionDialogFragment.MODE_EXPORT);
                args.putParcelable(RulesTypeSelectionDialogFragment.ARG_URI, uri);
                args.putStringArrayList(RulesTypeSelectionDialogFragment.ARG_PKG, new ArrayList<>(viewModel.getSelectedPackages().keySet()));
                args.putIntArray(RulesTypeSelectionDialogFragment.ARG_USERS, Users.getUsersIds());
                dialogFragment.setArguments(args);
                dialogFragment.show(getSupportFragmentManager(), RulesTypeSelectionDialogFragment.TAG);
            });

    private final ActivityResultLauncher<String> mExportAppListCsv = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                mProgressIndicator.show();
                viewModel.saveExportedAppList(ListExporter.EXPORT_TYPE_CSV, Paths.get(uri));
            });
    private final ActivityResultLauncher<String> mExportAppListJson = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                mProgressIndicator.show();
                viewModel.saveExportedAppList(ListExporter.EXPORT_TYPE_JSON, Paths.get(uri));
            });
    private final ActivityResultLauncher<String> mExportAppListXml = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/xml"),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                mProgressIndicator.show();
                viewModel.saveExportedAppList(ListExporter.EXPORT_TYPE_XML, Paths.get(uri));
            });
    private final ActivityResultLauncher<String> mExportAppListMarkdown = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/markdown"),
            uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                mProgressIndicator.show();
                viewModel.saveExportedAppList(ListExporter.EXPORT_TYPE_MARKDOWN, Paths.get(uri));
            });

    private final BroadcastReceiver mBatchOpsBroadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showProgressIndicator(false);
        }
    };

    @SuppressLint("RestrictedApi")
    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
            AdvancedSearchView searchView = new AdvancedSearchView(actionBar.getThemedContext());
            searchView.setId(R.id.action_search);
            searchView.setOnQueryTextListener(this);
            // Set layout params
            ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.gravity = Gravity.CENTER;
            actionBar.setCustomView(searchView, layoutParams);
            mSearchView = searchView;
            mSearchView.setIconifiedByDefault(false);
            mSearchView.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    UiUtils.hideKeyboard(v);
                }
            });
            // Check for market://search/?q=<query>
            Uri marketUri = getIntent().getData();
            if (marketUri != null && "market".equals(marketUri.getScheme()) && "search".equals(marketUri.getHost())) {
                String query = marketUri.getQueryParameter("q");
                if (query != null) {
                    mSearchView.setQuery(query, true);
                }
            }
        }

        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        RecyclerView recyclerView = findViewById(R.id.item_list);
        recyclerView.requestFocus(); // Initially (the view isn't actually focusable)
        mSwipeRefresh = findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setOnRefreshListener(this);

        mAdapter = new MainRecyclerAdapter(MainActivity.this);
        mAdapter.setHasStableIds(true);
        recyclerView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(this));
        recyclerView.setAdapter(mAdapter);
        mMultiSelectionView = findViewById(R.id.selection_view);
        mMultiSelectionView.setOnItemSelectedListener(this);
        mMultiSelectionView.setAdapter(mAdapter);
        mMultiSelectionView.updateCounter(true);
        mBatchOpsHandler = new MainBatchOpsHandler(mMultiSelectionView, viewModel);
        mMultiSelectionView.setOnSelectionChangeListener(mBatchOpsHandler);

        if (SHOW_DISCLAIMER && AppPref.getBoolean(AppPref.PrefKey.PREF_SHOW_DISCLAIMER_BOOL)) {
            // Disclaimer will only be shown the first time it is loaded.
            SHOW_DISCLAIMER = false;
            View view = View.inflate(this, R.layout.dialog_disclaimer, null);
            new MaterialAlertDialogBuilder(this)
                    .setView(view)
                    .setCancelable(false)
                    .setPositiveButton(R.string.disclaimer_agree, (dialog, which) -> {
                        if (((MaterialCheckBox) view.findViewById(R.id.agree_forever)).isChecked()) {
                            AppPref.set(AppPref.PrefKey.PREF_SHOW_DISCLAIMER_BOOL, false);
                        }
                        displayChangelogIfRequired();
                    })
                    .setNegativeButton(R.string.disclaimer_exit, (dialog, which) -> finishAndRemoveTask())
                    .show();
        } else {
            displayChangelogIfRequired();
        }

        // Set observer
        viewModel.getApplicationItems().observe(this, applicationItems -> {
            if (mAdapter != null) mAdapter.setDefaultList(applicationItems);
            showProgressIndicator(false);
        });
        viewModel.getOperationStatus().observe(this, status -> {
            mProgressIndicator.hide();
            if (status) {
                UIUtils.displayShortToast(R.string.done);
            } else {
                UIUtils.displayLongToast(R.string.failed);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (mAdapter != null && mMultiSelectionView != null && mAdapter.isInSelectionMode()) {
            mMultiSelectionView.cancel();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main_actions, menu);
        mAppUsageMenu = menu.findItem(R.id.action_app_usage);
        MenuItem apkUpdaterMenu = menu.findItem(R.id.action_apk_updater);
        try {
            if (!getPackageManager().getApplicationInfo(PACKAGE_NAME_APK_UPDATER, 0).enabled)
                throw new PackageManager.NameNotFoundException();
            apkUpdaterMenu.setVisible(true);
        } catch (PackageManager.NameNotFoundException e) {
            apkUpdaterMenu.setVisible(false);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        mAppUsageMenu.setVisible(FeatureController.isUsageAccessEnabled());
        return true;
    }

    @SuppressLint("InflateParams")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_instructions) {
            Intent helpIntent = new Intent(this, HelpActivity.class);
            helpIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(helpIntent);
        } else if (id == R.id.action_list_options) {
            MainListOptions listOptions = new MainListOptions();
            listOptions.setListOptionActions(viewModel);
            listOptions.show(getSupportFragmentManager(), MainListOptions.TAG);
        } else if (id == R.id.action_refresh) {
            if (viewModel != null) {
                showProgressIndicator(true);
                viewModel.loadApplicationItems();
            }
        } else if (id == R.id.action_settings) {
            Intent settingsIntent = SettingsActivity.getIntent(this);
            startActivity(settingsIntent);
        } else if (id == R.id.action_app_usage) {
            Intent usageIntent = new Intent(this, AppUsageActivity.class);
            startActivity(usageIntent);
        } else if (id == R.id.action_one_click_ops) {
            Intent onClickOpsIntent = new Intent(this, OneClickOpsActivity.class);
            startActivity(onClickOpsIntent);
        } else if (id == R.id.action_finder) {
            Intent intent = new Intent(this, FinderActivity.class);
            startActivity(intent);
        } else if (id == R.id.action_apk_updater) {
            try {
                if (!getPackageManager().getApplicationInfo(PACKAGE_NAME_APK_UPDATER, 0).enabled)
                    throw new PackageManager.NameNotFoundException();
                Intent intent = new Intent();
                intent.setClassName(PACKAGE_NAME_APK_UPDATER, ACTIVITY_NAME_APK_UPDATER);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception ignored) {
            }
        } else if (id == R.id.action_running_apps) {
            Intent runningAppsIntent = new Intent(this, RunningAppsActivity.class);
            startActivity(runningAppsIntent);
        } else if (id == R.id.action_profiles) {
            Intent profilesIntent = new Intent(this, ProfilesActivity.class);
            startActivity(profilesIntent);
        } else if (id == R.id.action_labs) {
            Intent intent = new Intent(getApplicationContext(), LabsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else if (id == R.id.action_debloater) {
            Intent intent = new Intent(getApplicationContext(), DebloaterActivity.class);
            startActivity(intent);
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_backup) {
            if (viewModel != null) {
                BackupRestoreDialogFragment fragment = BackupRestoreDialogFragment.getInstance(viewModel.getSelectedPackagesWithUsers());
                fragment.setOnActionBeginListener(mode -> showProgressIndicator(true));
                fragment.setOnActionCompleteListener((mode, failedPackages) -> showProgressIndicator(false));
                fragment.show(getSupportFragmentManager(), BackupRestoreDialogFragment.TAG);
            }
        } else if (id == R.id.action_save_apk) {
            mStoragePermission.request(granted -> {
                if (granted) handleBatchOp(BatchOpsManager.OP_BACKUP_APK);
            });
        } else if (id == R.id.action_block_unblock_trackers) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.block_unblock_trackers)
                    .setMessage(R.string.choose_what_to_do)
                    .setPositiveButton(R.string.block, (dialog, which) ->
                            handleBatchOp(BatchOpsManager.OP_BLOCK_TRACKERS))
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.unblock, (dialog, which) ->
                            handleBatchOp(BatchOpsManager.OP_UNBLOCK_TRACKERS))
                    .show();
        } else if (id == R.id.action_clear_data_cache) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.clear)
                    .setMessage(R.string.choose_what_to_do)
                    .setPositiveButton(R.string.clear_cache, (dialog, which) ->
                            handleBatchOp(BatchOpsManager.OP_CLEAR_CACHE))
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.clear_data, (dialog, which) ->
                            handleBatchOp(BatchOpsManager.OP_CLEAR_DATA))
                    .show();
        } else if (id == R.id.action_freeze_unfreeze) {
            showFreezeUnfreezeDialog(Prefs.Blocking.getDefaultFreezingMethod());
        } else if (id == R.id.action_disable_background) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.are_you_sure)
                    .setMessage(R.string.disable_background_run_description)
                    .setPositiveButton(R.string.yes, (dialog, which) ->
                            handleBatchOp(BatchOpsManager.OP_DISABLE_BACKGROUND))
                    .setNegativeButton(R.string.no, null)
                    .show();
        } else if (id == R.id.action_net_policy) {
            ArrayMap<Integer, String> netPolicyMap = NetworkPolicyManagerCompat.getAllReadablePolicies(this);
            Integer[] polices = new Integer[netPolicyMap.size()];
            String[] policyStrings = new String[netPolicyMap.size()];
            Collection<ApplicationItem> applicationItems = viewModel.getSelectedPackages().values();
            Iterator<ApplicationItem> it = applicationItems.iterator();
            int selectedPolicies = applicationItems.size() == 1 && it.hasNext() ?
                    NetworkPolicyManagerCompat.getUidPolicy(it.next().uid) : 0;
            for (int i = 0; i < netPolicyMap.size(); ++i) {
                polices[i] = netPolicyMap.keyAt(i);
                policyStrings[i] = netPolicyMap.valueAt(i);
            }
            new SearchableFlagsDialogBuilder<>(this, polices, policyStrings, selectedPolicies)
                    .setTitle(R.string.net_policy)
                    .showSelectAll(false)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.apply, (dialog, which, selections) -> {
                        int flags = 0;
                        for (int flag : selections) {
                            flags |= flag;
                        }
                        BatchNetPolicyOptions options = new BatchNetPolicyOptions(flags);
                        handleBatchOp(BatchOpsManager.OP_NET_POLICY, options);
                    })
                    .show();
        } else if (id == R.id.action_optimize) {
            DexOptDialog dialog = DexOptDialog.getInstance(viewModel.getSelectedPackages().keySet().toArray(new String[0]));
            dialog.show(getSupportFragmentManager(), DexOptDialog.TAG);
        } else if (id == R.id.action_export_blocking_rules) {
            final String fileName = "app_manager_rules_export-" + DateUtils.formatDateTime(this, System.currentTimeMillis()) + ".am.tsv";
            mBatchExportRules.launch(fileName);
        } else if (id == R.id.action_export_app_list) {
            List<Integer> exportTypes = Arrays.asList(ListExporter.EXPORT_TYPE_CSV,
                    ListExporter.EXPORT_TYPE_JSON,
                    ListExporter.EXPORT_TYPE_XML,
                    ListExporter.EXPORT_TYPE_MARKDOWN);
            new SearchableSingleChoiceDialogBuilder<>(this, exportTypes, R.array.export_app_list_options)
                    .setTitle(R.string.export_app_list_select_format)
                    .setOnSingleChoiceClickListener((dialog, which, item1, isChecked) -> {
                        if (!isChecked) {
                            return;
                        }
                        String filename = "app_manager_app_list-" + DateUtils.formatLongDateTime(this, System.currentTimeMillis()) + ".am";
                        switch (item1) {
                            case ListExporter.EXPORT_TYPE_CSV:
                                mExportAppListCsv.launch(filename + ".csv");
                                break;
                            case ListExporter.EXPORT_TYPE_JSON:
                                mExportAppListJson.launch(filename + ".json");
                                break;
                            case ListExporter.EXPORT_TYPE_XML:
                                mExportAppListXml.launch(filename + ".xml");
                                break;
                            case ListExporter.EXPORT_TYPE_MARKDOWN:
                                mExportAppListMarkdown.launch(filename + ".md");
                                break;
                        }
                    })
                    .setNegativeButton(R.string.close, null)
                    .show();
        } else if (id == R.id.action_force_stop) {
            handleBatchOp(BatchOpsManager.OP_FORCE_STOP);
        } else if (id == R.id.action_uninstall) {
            handleBatchOpWithWarning(BatchOpsManager.OP_UNINSTALL);
        } else if (id == R.id.action_add_to_profile) {
            AddToProfileDialogFragment dialog = AddToProfileDialogFragment.getInstance(viewModel.getSelectedPackages()
                    .keySet().toArray(new String[0]));
            dialog.show(getSupportFragmentManager(), AddToProfileDialogFragment.TAG);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public void onRefresh() {
        showProgressIndicator(true);
        if (viewModel != null) viewModel.loadApplicationItems();
        mSwipeRefresh.setRefreshing(false);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Set filter
        if (viewModel != null && mSearchView != null && !TextUtils.isEmpty(viewModel.getSearchQuery())) {
            if (mSearchView.isIconified()) {
                mSearchView.setIconified(false);
            }
            mSearchView.setQuery(viewModel.getSearchQuery(), false);
        }
        // Show/hide app usage menu
        if (mAppUsageMenu != null) {
            mAppUsageMenu.setVisible(FeatureController.isUsageAccessEnabled());
        }
        // Check for backup volume
        if (!Prefs.BackupRestore.backupDirectoryExists()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.backup_volume)
                    .setMessage(R.string.backup_volume_unavailable_warning)
                    .setPositiveButton(R.string.close, null)
                    .setNeutralButton(R.string.change_backup_volume, (dialog, which) -> {
                        Intent intent = SettingsActivity.getIntent(this, "backup_restore_prefs", "backup_volume");
                        startActivity(intent);
                    })
                    .show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null) viewModel.onResume();
        if (mAdapter != null && mBatchOpsHandler != null && mAdapter.isInSelectionMode()) {
            mBatchOpsHandler.updateConstraints();
            mMultiSelectionView.updateCounter(false);
        }
        ContextCompat.registerReceiver(this, mBatchOpsBroadCastReceiver,
                new IntentFilter(BatchOpsService.ACTION_BATCH_OPS_COMPLETED), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBatchOpsBroadCastReceiver);
    }

    private void displayChangelogIfRequired() {
        if (!AppPref.getBoolean(AppPref.PrefKey.PREF_DISPLAY_CHANGELOG_BOOL)) {
            return;
        }
        if (FundingCampaignChecker.campaignRunning()) {
            new ScrollableDialogBuilder(this)
                    .setMessage(R.string.funding_campaign_dialog_message)
                    .enableAnchors()
                    .show();
        }
        Snackbar.make(findViewById(android.R.id.content), R.string.view_changelog, 3 * 60 * 1000)
                .setAction(R.string.ok, v -> {
                    long lastVersion = AppPref.getLong(AppPref.PrefKey.PREF_DISPLAY_CHANGELOG_LAST_VERSION_LONG);
                    AppPref.set(AppPref.PrefKey.PREF_DISPLAY_CHANGELOG_BOOL, false);
                    AppPref.set(AppPref.PrefKey.PREF_DISPLAY_CHANGELOG_LAST_VERSION_LONG, (long) BuildConfig.VERSION_CODE);
                    viewModel.executor.submit(() -> {
                        Changelog changelog;
                        try {
                            changelog = new ChangelogParser(getApplication(), R.raw.changelog, lastVersion).parse();
                        } catch (IOException | XmlPullParserException e) {
                            return;
                        }
                        runOnUiThread(() -> {
                            View view = View.inflate(this, R.layout.dialog_whats_new, null);
                            RecyclerView recyclerView = view.findViewById(android.R.id.list);
                            recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
                            ChangelogRecyclerAdapter adapter = new ChangelogRecyclerAdapter();
                            recyclerView.setAdapter(adapter);
                            adapter.setAdapterList(changelog.getChangelogItems());
                            new AlertDialogBuilder(this, true)
                                    .setTitle(R.string.changelog)
                                    .setView(recyclerView)
                                    .show();
                        });
                    });
                }).show();
    }

    private void showFreezeUnfreezeDialog(int freezeType) {
        View view = View.inflate(this, R.layout.item_checkbox, null);
        MaterialCheckBox checkBox = view.findViewById(R.id.checkbox);
        checkBox.setText(R.string.freeze_prefer_per_app_option);
        FreezeUnfreeze.getFreezeDialog(this, freezeType)
                .setIcon(R.drawable.ic_snowflake)
                .setTitle(R.string.freeze_unfreeze)
                .setView(view)
                .setPositiveButton(R.string.freeze, (dialog, which, selectedItem) -> {
                    if (selectedItem == null) {
                        return;
                    }
                    BatchFreezeOptions options = new BatchFreezeOptions(selectedItem, checkBox.isChecked());
                    handleBatchOp(BatchOpsManager.OP_ADVANCED_FREEZE, options);
                })
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.unfreeze, (dialog, which, selectedItem) ->
                        handleBatchOp(BatchOpsManager.OP_UNFREEZE))
                .show();
    }

    private void handleBatchOp(@BatchOpsManager.OpType int op) {
        handleBatchOp(op, null);
    }

    private void handleBatchOp(@BatchOpsManager.OpType int op, @Nullable IBatchOpOptions options) {
        if (viewModel == null) return;
        showProgressIndicator(true);
        Intent intent = new Intent(this, BatchOpsService.class);
        BatchOpsManager.Result input = new BatchOpsManager.Result(viewModel.getSelectedPackagesWithUsers());
        BatchQueueItem item = BatchQueueItem.getBatchOpQueue(op, input.getFailedPackages(), input.getAssociatedUsers(), options);
        intent.putExtra(BatchOpsService.EXTRA_QUEUE_ITEM, item);
        ContextCompat.startForegroundService(this, intent);
    }

    private void handleBatchOpWithWarning(@BatchOpsManager.OpType int op) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.are_you_sure)
                .setMessage(R.string.this_action_cannot_be_undone)
                .setPositiveButton(R.string.yes, (dialog, which) -> handleBatchOp(op))
                .setNegativeButton(R.string.no, null)
                .show();
    }

    void showProgressIndicator(boolean show) {
        if (show) mProgressIndicator.show();
        else mProgressIndicator.hide();
    }

    @Override
    public boolean onQueryTextChange(String searchQuery, @AdvancedSearchView.SearchType int type) {
        if (viewModel != null) viewModel.setSearchQuery(searchQuery, type);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query, int type) {
        return false;
    }
}
