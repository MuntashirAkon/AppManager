// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.NetworkPolicyManager;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.dialog.BackupRestoreDialogFragment;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.changelog.Changelog;
import io.github.muntashirakon.AppManager.changelog.ChangelogParser;
import io.github.muntashirakon.AppManager.changelog.ChangelogRecyclerAdapter;
import io.github.muntashirakon.AppManager.compat.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.logcat.LogViewerActivity;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.misc.HelpActivity;
import io.github.muntashirakon.AppManager.oneclickops.OneClickOpsActivity;
import io.github.muntashirakon.AppManager.profiles.ProfileManager;
import io.github.muntashirakon.AppManager.profiles.ProfileMetaManager;
import io.github.muntashirakon.AppManager.profiles.ProfilesActivity;
import io.github.muntashirakon.AppManager.rules.RulesTypeSelectionDialogFragment;
import io.github.muntashirakon.AppManager.runningapps.RunningAppsActivity;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.SettingsActivity;
import io.github.muntashirakon.AppManager.sysconfig.SysConfigActivity;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.usage.AppUsageActivity;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.StoragePermission;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.AlertDialogBuilder;
import io.github.muntashirakon.reflow.ReflowMenuViewWrapper;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.MultiSelectionView;
import io.github.muntashirakon.widget.SwipeRefreshLayout;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

public class MainActivity extends BaseActivity implements AdvancedSearchView.OnQueryTextListener,
        SwipeRefreshLayout.OnRefreshListener, ReflowMenuViewWrapper.OnItemSelectedListener,
        MultiSelectionView.OnSelectionChangeListener {
    private static final String PACKAGE_NAME_APK_UPDATER = "com.apkupdater";
    private static final String ACTIVITY_NAME_APK_UPDATER = "com.apkupdater.activity.MainActivity";
    private static final String PACKAGE_NAME_TERMUX = "com.termux";
    private static final String ACTIVITY_NAME_TERMUX = "com.termux.app.TermuxActivity";

    private static boolean SHOW_DISCLAIMER = true;

    MainViewModel mModel;

    private MainRecyclerAdapter mAdapter;
    private AdvancedSearchView mSearchView;
    private LinearProgressIndicator mProgressIndicator;
    private SwipeRefreshLayout mSwipeRefresh;
    private MultiSelectionView multiSelectionView;
    private Menu selectionMenu;
    private MenuItem appUsageMenu;
    private MenuItem logViewerMenu;

    private final StoragePermission storagePermission = StoragePermission.init(this);

    private final ActivityResultLauncher<String> batchExportRules = registerForActivityResult(
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
                args.putStringArrayList(RulesTypeSelectionDialogFragment.ARG_PKG, new ArrayList<>(mModel.getSelectedPackages().keySet()));
                args.putIntArray(RulesTypeSelectionDialogFragment.ARG_USERS, Users.getUsersIds());
                dialogFragment.setArguments(args);
                dialogFragment.show(getSupportFragmentManager(), RulesTypeSelectionDialogFragment.TAG);
                multiSelectionView.cancel();
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
        mModel = new ViewModelProvider(this).get(MainViewModel.class);
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
        }

        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        RecyclerView recyclerView = findViewById(R.id.item_list);
        recyclerView.requestFocus(); // Initially (the view isn't actually focusable)
        mSwipeRefresh = findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setOnRefreshListener(this);

        mAdapter = new MainRecyclerAdapter(MainActivity.this);
        mAdapter.setHasStableIds(true);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(mAdapter);
        multiSelectionView = findViewById(R.id.selection_view);
        multiSelectionView.setOnItemSelectedListener(this);
        multiSelectionView.setAdapter(mAdapter);
        multiSelectionView.updateCounter(true);
        multiSelectionView.setOnSelectionChangeListener(this);
        selectionMenu = multiSelectionView.getMenu();

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
        mModel.getApplicationItems().observe(this, applicationItems -> {
            if (mAdapter != null) mAdapter.setDefaultList(applicationItems);
            showProgressIndicator(false);
            if (recyclerView.getFocusedChild() == null) {
                View v = recyclerView.getChildAt(0);
                if (v != null) v.requestFocus();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (mAdapter != null && multiSelectionView != null && mAdapter.isInSelectionMode()) {
            multiSelectionView.cancel();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (mAdapter != null) {
            mAdapter.imageLoader.close();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main_actions, menu);
        appUsageMenu = menu.findItem(R.id.action_app_usage);
        logViewerMenu = menu.findItem(R.id.action_log_viewer);
        MenuItem apkUpdaterMenu = menu.findItem(R.id.action_apk_updater);
        try {
            if (!getPackageManager().getApplicationInfo(PACKAGE_NAME_APK_UPDATER, 0).enabled)
                throw new PackageManager.NameNotFoundException();
            apkUpdaterMenu.setVisible(true);
        } catch (PackageManager.NameNotFoundException e) {
            apkUpdaterMenu.setVisible(false);
        }
        MenuItem termuxMenu = menu.findItem(R.id.action_termux);
        try {
            if (!getPackageManager().getApplicationInfo(PACKAGE_NAME_TERMUX, 0).enabled)
                throw new PackageManager.NameNotFoundException();
            termuxMenu.setVisible(true);
        } catch (PackageManager.NameNotFoundException e) {
            termuxMenu.setVisible(false);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.action_sys_config).setVisible(Ops.isRoot());
        appUsageMenu.setVisible(FeatureController.isUsageAccessEnabled());
        logViewerMenu.setVisible(FeatureController.isLogViewerEnabled());
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
            ListOptions listOptions = new ListOptions();
            listOptions.show(getSupportFragmentManager(), ListOptions.TAG);
        } else if (id == R.id.action_refresh) {
            if (mModel != null) {
                showProgressIndicator(true);
                mModel.loadApplicationItems();
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
        } else if (id == R.id.action_termux) {
            try {
                if (!getPackageManager().getApplicationInfo(PACKAGE_NAME_TERMUX, 0).enabled)
                    throw new PackageManager.NameNotFoundException();
                Intent intent = new Intent();
                intent.setClassName(PACKAGE_NAME_TERMUX, ACTIVITY_NAME_TERMUX);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception ignored) {
            }
        } else if (id == R.id.action_running_apps) {
            Intent runningAppsIntent = new Intent(this, RunningAppsActivity.class);
            startActivity(runningAppsIntent);
        } else if (id == R.id.action_sys_config) {
            Intent sysConfigIntent = new Intent(this, SysConfigActivity.class);
            startActivity(sysConfigIntent);
        } else if (id == R.id.action_profiles) {
            Intent profilesIntent = new Intent(this, ProfilesActivity.class);
            startActivity(profilesIntent);
        } else if (id == R.id.action_log_viewer) {
            Intent intent = new Intent(getApplicationContext(), LogViewerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_backup) {
            if (mModel != null) {
                BackupRestoreDialogFragment fragment = BackupRestoreDialogFragment.getInstance(mModel.getSelectedPackagesWithUsers());
                fragment.setOnActionBeginListener(mode -> showProgressIndicator(true));
                fragment.setOnActionCompleteListener((mode, failedPackages) -> showProgressIndicator(false));
                fragment.show(getSupportFragmentManager(), BackupRestoreDialogFragment.TAG);
                multiSelectionView.cancel();
            }
        } else if (id == R.id.action_save_apk) {
            storagePermission.request(granted -> {
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
        } else if (id == R.id.action_enable_disable) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.enable_disable)
                    .setMessage(R.string.choose_what_to_do)
                    .setPositiveButton(R.string.disable, (dialog, which) ->
                            handleBatchOp(BatchOpsManager.OP_DISABLE))
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.enable, (dialog, which) ->
                            handleBatchOp(BatchOpsManager.OP_ENABLE))
                    .show();
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
            int[] polices = new int[netPolicyMap.size()];
            String[] policyStrings = new String[netPolicyMap.size()];
            AtomicInteger selectedPolicies = new AtomicInteger(NetworkPolicyManager.POLICY_NONE);
            for (int i = 0; i < netPolicyMap.size(); ++i) {
                polices[i] = netPolicyMap.keyAt(i);
                policyStrings[i] = netPolicyMap.valueAt(i);
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.net_policy)
                    .setMultiChoiceItems(policyStrings, null, (dialog, which, isChecked) -> {
                        int currentPolicies = selectedPolicies.get();
                        if (isChecked) selectedPolicies.set(currentPolicies | polices[which]);
                        else selectedPolicies.set(currentPolicies & ~polices[which]);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.apply, (dialog, which) -> {
                        Bundle args = new Bundle();
                        args.putInt(BatchOpsManager.ARG_NET_POLICIES, selectedPolicies.get());
                        handleBatchOp(BatchOpsManager.OP_NET_POLICY, args);
                    })
                    .show();
        } else if (id == R.id.action_export_blocking_rules) {
            @SuppressLint("SimpleDateFormat") final String fileName = "app_manager_rules_export-" + DateUtils.formatDateTime(System.currentTimeMillis()) + ".am.tsv";
            batchExportRules.launch(fileName);
        } else if (id == R.id.action_force_stop) {
            handleBatchOp(BatchOpsManager.OP_FORCE_STOP);
        } else if (id == R.id.action_uninstall) {
            handleBatchOpWithWarning(BatchOpsManager.OP_UNINSTALL);
        } else if (id == R.id.action_add_to_profile) {
            List<ProfileMetaManager> profiles = ProfileManager.getProfileMetadata();
            List<CharSequence> profileNames = new ArrayList<>(profiles.size());
            for (ProfileMetaManager profileMetaManager : profiles) {
                profileNames.add(new SpannableStringBuilder(profileMetaManager.getProfileName()).append("\n")
                        .append(getSecondaryText(this, getSmallerText(profileMetaManager.toLocalizedString(this)))));
            }
            new SearchableMultiChoiceDialogBuilder<>(this, profiles, profileNames)
                    .setTitle(R.string.add_to_profile)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.add, (dialog, which, selectedItems) -> {
                        for (ProfileMetaManager metaManager : selectedItems) {
                            try {
                                metaManager.appendPackages(mModel.getSelectedPackages().keySet());
                                multiSelectionView.cancel();
                                metaManager.writeProfile();
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        }
                        UIUtils.displayShortToast(R.string.done);
                    })
                    .show();
        } else {
            multiSelectionView.cancel();
            return false;
        }
        return true;
    }

    @Override
    public void onSelectionChange(int selectionCount) {
        if (selectionMenu == null || mModel == null) return;
        // TODO: 11/10/21 There is an efficient way to handle this:
        //  1. Declare MenuItems as field variables
        //  2. Check for properties during selection
        ArrayList<ApplicationItem> selectedItems = mModel.getSelectedApplicationItems();
        MenuItem uninstallMenu = selectionMenu.findItem(R.id.action_uninstall);
        MenuItem enableDisableMenu = selectionMenu.findItem(R.id.action_enable_disable);
        MenuItem forceStopMenu = selectionMenu.findItem(R.id.action_force_stop);
        MenuItem clearDataCacheMenu = selectionMenu.findItem(R.id.action_clear_data_cache);
        MenuItem saveApkMenu = selectionMenu.findItem(R.id.action_save_apk);
        MenuItem backupRestoreMenu = selectionMenu.findItem(R.id.action_backup);
        MenuItem preventBackgroundMenu = selectionMenu.findItem(R.id.action_disable_background);
        MenuItem blockUnblockTrackersMenu = selectionMenu.findItem(R.id.action_block_unblock_trackers);
        MenuItem netPolicyMenu = selectionMenu.findItem(R.id.action_net_policy);
        MenuItem exportRulesMenu = selectionMenu.findItem(R.id.action_export_blocking_rules);
        MenuItem addToProfileMenu = selectionMenu.findItem(R.id.action_add_to_profile);
        boolean nonZeroSelection = selectedItems.size() > 0;
        // It was ensured that the algorithm is greedy
        // Best case: O(1)
        // Worst case: O(n)
        boolean areAllInstalled = true;
        boolean areAllUninstalledSystem = true;
        boolean areAllUninstalledHasBackup = true;
        for (ApplicationItem item : selectedItems) {
            if (item.isInstalled) continue;
            areAllInstalled = false;
            if (!areAllUninstalledHasBackup && !areAllUninstalledSystem) {
                // No need to check further
                break;
            }
            if (areAllUninstalledSystem && item.isUser) areAllUninstalledSystem = false;
            if (areAllUninstalledHasBackup && item.backup == null) areAllUninstalledHasBackup = false;
        }
        /* === Enable/Disable === */
        // Enable “Uninstall” action iff all selections are installed
        uninstallMenu.setEnabled(nonZeroSelection && areAllInstalled);
        // Enable the following actions iff root/ADB enabled and all selections are installed
        enableDisableMenu.setEnabled(nonZeroSelection && areAllInstalled);
        forceStopMenu.setEnabled(nonZeroSelection && areAllInstalled);
        clearDataCacheMenu.setEnabled(nonZeroSelection && areAllInstalled);
        preventBackgroundMenu.setEnabled(nonZeroSelection && areAllInstalled);
        netPolicyMenu.setEnabled(nonZeroSelection && areAllInstalled);
        // Enable “Save APK” action iff all selections are installed or the uninstalled apps are all system apps
        saveApkMenu.setEnabled(nonZeroSelection && (areAllInstalled || areAllUninstalledSystem));
        // Enable “Backup/restore” action iff all selections are installed or all the uninstalled apps have backups
        backupRestoreMenu.setEnabled(nonZeroSelection && (areAllInstalled || areAllUninstalledHasBackup));
        // Enable “Block/unblock trackers” action iff root is enabled and all selections are installed
        blockUnblockTrackersMenu.setEnabled(nonZeroSelection && areAllInstalled);
        // Rests are enabled by default
        exportRulesMenu.setEnabled(nonZeroSelection);
        addToProfileMenu.setEnabled(nonZeroSelection);
        /* === Visible/Invisible === */
        boolean privileged = Ops.isPrivileged();
        uninstallMenu.setVisible(privileged);
        enableDisableMenu.setVisible(privileged);
        forceStopMenu.setVisible(privileged);
        clearDataCacheMenu.setVisible(privileged);
        preventBackgroundMenu.setVisible(privileged);
        netPolicyMenu.setVisible(privileged);
        blockUnblockTrackersMenu.setVisible(privileged);
    }

    @Override
    public void onRefresh() {
        showProgressIndicator(true);
        if (mModel != null) mModel.loadApplicationItems();
        mSwipeRefresh.setRefreshing(false);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Set filter
        if (mModel != null && mSearchView != null && !TextUtils.isEmpty(mModel.getSearchQuery())) {
            mSearchView.setIconified(false);
            mSearchView.setQuery(mModel.getSearchQuery(), false);
        }
        // Show/hide app usage menu
        if (appUsageMenu != null) {
            appUsageMenu.setVisible(FeatureController.isUsageAccessEnabled());
        }
        // Show/hide log viewer menu
        if (logViewerMenu != null) {
            logViewerMenu.setVisible(FeatureController.isLogViewerEnabled());
        }
        // Set sort by
        if (mModel != null) {
            if (!Ops.isPrivileged()) {
                if (mModel.getSortBy() == ListOptions.SORT_BY_BLOCKED_COMPONENTS) {
                    mModel.setSortBy(ListOptions.SORT_BY_APP_LABEL);
                }
            }
        }
        // Check for backup volume
        if (!AppPref.getAppManagerDirectory().exists()) {
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
        if (mModel != null) mModel.onResume();
        registerReceiver(mBatchOpsBroadCastReceiver, new IntentFilter(BatchOpsService.ACTION_BATCH_OPS_COMPLETED));
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
        Snackbar.make(findViewById(android.R.id.content), R.string.view_changelog, 3 * 60 * 1000)
                .setAction(R.string.ok, v -> {
                    long lastVersion = (long) AppPref.get(AppPref.PrefKey.PREF_DISPLAY_CHANGELOG_LAST_VERSION_LONG);
                    AppPref.set(AppPref.PrefKey.PREF_DISPLAY_CHANGELOG_BOOL, false);
                    AppPref.set(AppPref.PrefKey.PREF_DISPLAY_CHANGELOG_LAST_VERSION_LONG, (long) BuildConfig.VERSION_CODE);
                    mModel.executor.submit(() -> {
                        Changelog changelog;
                        try {
                            changelog = new ChangelogParser(getApplication(), R.raw.changelog, lastVersion).parse();
                        } catch (IOException | XmlPullParserException e) {
                            return;
                        }
                        runOnUiThread(() -> {
                            RecyclerView recyclerView = (RecyclerView) View.inflate(this, R.layout.dialog_whats_new, null);
                            recyclerView.setHasFixedSize(true);
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

    private void handleBatchOp(@BatchOpsManager.OpType int op) {
        handleBatchOp(op, null);
    }

    private void handleBatchOp(@BatchOpsManager.OpType int op, @Nullable Bundle args) {
        if (mModel == null) return;
        showProgressIndicator(true);
        Intent intent = new Intent(this, BatchOpsService.class);
        BatchOpsManager.Result input = new BatchOpsManager.Result(mModel.getSelectedPackagesWithUsers());
        intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, input.getFailedPackages());
        intent.putIntegerArrayListExtra(BatchOpsService.EXTRA_OP_USERS, input.getAssociatedUserHandles());
        intent.putExtra(BatchOpsService.EXTRA_OP, op);
        intent.putExtra(BatchOpsService.EXTRA_OP_EXTRA_ARGS, args);
        ContextCompat.startForegroundService(this, intent);
        multiSelectionView.cancel();
    }

    private void handleBatchOpWithWarning(@BatchOpsManager.OpType int op) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.are_you_sure)
                .setMessage(R.string.this_action_cannot_be_undone)
                .setPositiveButton(R.string.yes, (dialog, which) -> handleBatchOp(op))
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void showProgressIndicator(boolean show) {
        if (show) mProgressIndicator.show();
        else mProgressIndicator.hide();
    }

    @Override
    public boolean onQueryTextChange(String searchQuery, @AdvancedSearchView.SearchType int type) {
        if (mModel != null) mModel.setSearchQuery(searchQuery, type);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query, int type) {
        return false;
    }
}
