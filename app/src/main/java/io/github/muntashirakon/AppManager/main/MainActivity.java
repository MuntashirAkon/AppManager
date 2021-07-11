// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupDialogFragment;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.logcat.LogViewerActivity;
import io.github.muntashirakon.AppManager.misc.HelpActivity;
import io.github.muntashirakon.AppManager.oneclickops.OneClickOpsActivity;
import io.github.muntashirakon.AppManager.profiles.ProfileManager;
import io.github.muntashirakon.AppManager.profiles.ProfileMetaManager;
import io.github.muntashirakon.AppManager.profiles.ProfilesActivity;
import io.github.muntashirakon.AppManager.rules.RulesTypeSelectionDialogFragment;
import io.github.muntashirakon.AppManager.runningapps.RunningAppsActivity;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.SettingsActivity;
import io.github.muntashirakon.AppManager.sysconfig.SysConfigActivity;
import io.github.muntashirakon.AppManager.types.ScrollableDialogBuilder;
import io.github.muntashirakon.AppManager.types.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.AppManager.usage.AppUsageActivity;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.StoragePermission;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.reflow.ReflowMenuViewWrapper;
import io.github.muntashirakon.widget.MultiSelectionView;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

public class MainActivity extends BaseActivity implements SearchView.OnQueryTextListener,
        SwipeRefreshLayout.OnRefreshListener, ReflowMenuViewWrapper.OnItemSelectedListener {
    private static final String PACKAGE_NAME_APK_UPDATER = "com.apkupdater";
    private static final String ACTIVITY_NAME_APK_UPDATER = "com.apkupdater.activity.MainActivity";
    private static final String PACKAGE_NAME_TERMUX = "com.termux";
    private static final String ACTIVITY_NAME_TERMUX = "com.termux.app.TermuxActivity";

    MainViewModel mModel;

    private MainRecyclerAdapter mAdapter;
    private SearchView mSearchView;
    private LinearProgressIndicator mProgressIndicator;
    private SwipeRefreshLayout mSwipeRefresh;
    private MultiSelectionView multiSelectionView;
    private MenuItem appUsageMenu;
    private MenuItem runningAppsMenu;
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
            actionBar.setTitle(getString(R.string.loading));
            mSearchView = UIUtils.setupSearchView(actionBar, this);
        }

        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        RecyclerView recyclerView = findViewById(R.id.item_list);
        mSwipeRefresh = findViewById(R.id.swipe_refresh);

        mSwipeRefresh.setColorSchemeColors(UIUtils.getAccentColor(this));
        mSwipeRefresh.setProgressBackgroundColorSchemeColor(UIUtils.getPrimaryColor(this));
        mSwipeRefresh.setOnRefreshListener(this);

        mAdapter = new MainRecyclerAdapter(MainActivity.this);
        mAdapter.setHasStableIds(true);
        recyclerView.setHasFixedSize(true);
        recyclerView.setOverScrollMode(RecyclerView.OVER_SCROLL_NEVER);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(mAdapter);
        new FastScrollerBuilder(recyclerView).useMd2Style().build();
        multiSelectionView = findViewById(R.id.selection_view);
        multiSelectionView.setOnItemSelectedListener(this);
        multiSelectionView.setAdapter(mAdapter);
        multiSelectionView.updateCounter(true);

        if ((boolean) AppPref.get(AppPref.PrefKey.PREF_SHOW_DISCLAIMER_BOOL)) {
            @SuppressLint("InflateParams")
            View view = getLayoutInflater().inflate(R.layout.dialog_disclaimer, null);
            new MaterialAlertDialogBuilder(this)
                    .setView(view)
                    .setCancelable(false)
                    .setPositiveButton(R.string.disclaimer_agree, (dialog, which) -> {
                        if (((MaterialCheckBox) view.findViewById(R.id.agree_forever)).isChecked()) {
                            AppPref.set(AppPref.PrefKey.PREF_SHOW_DISCLAIMER_BOOL, false);
                        }
                        checkFirstRun();
                        checkAppUpdate();
                    })
                    .setNegativeButton(R.string.disclaimer_exit, (dialog, which) -> finishAndRemoveTask())
                    .show();
        } else {
            checkFirstRun();
            checkAppUpdate();
        }

        // Set observer
        mModel.getApplicationItems().observe(this, applicationItems -> {
            if (mAdapter != null) mAdapter.setDefaultList(applicationItems);
            showProgressIndicator(false);
            // Set title and subtitle
            if (actionBar != null) {
                actionBar.setTitle(R.string.onboard);
                actionBar.setSubtitle(R.string.packages);
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
        runningAppsMenu = menu.findItem(R.id.action_running_apps);
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
        menu.findItem(R.id.action_sys_config).setVisible(AppPref.isRootEnabled());
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
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
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
                BackupDialogFragment backupDialogFragment = new BackupDialogFragment();
                Bundle args = new Bundle();
                args.putParcelableArrayList(BackupDialogFragment.ARG_PACKAGE_PAIRS, mModel.getSelectedPackagesWithUsers());
                backupDialogFragment.setArguments(args);
                backupDialogFragment.setOnActionBeginListener(mode -> showProgressIndicator(true));
                backupDialogFragment.setOnActionCompleteListener((mode, failedPackages) -> showProgressIndicator(false));
                backupDialogFragment.show(getSupportFragmentManager(), BackupDialogFragment.TAG);
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
        } else if (id == R.id.action_export_blocking_rules) {
            @SuppressLint("SimpleDateFormat") final String fileName = "app_manager_rules_export-" + DateUtils.formatDateTime(System.currentTimeMillis()) + ".am.tsv";
            batchExportRules.launch(fileName);
        } else if (id == R.id.action_force_stop) {
            handleBatchOp(BatchOpsManager.OP_FORCE_STOP);
        } else if (id == R.id.action_uninstall) {
            handleBatchOpWithWarning(BatchOpsManager.OP_UNINSTALL);
        } else if (id == R.id.action_add_to_profile) {
            HashMap<String, ProfileMetaManager> profilesMap = ProfileManager.getProfileMetadata();
            List<CharSequence> profileNames = new ArrayList<>(profilesMap.size());
            List<ProfileMetaManager> profiles = new ArrayList<>(profilesMap.size());
            ProfileMetaManager profileMetaManager;
            Spannable summary;
            for (String profileName : profilesMap.keySet()) {
                profileMetaManager = profilesMap.get(profileName);
                //noinspection ConstantConditions
                summary = com.android.internal.util.TextUtils.joinSpannable(", ", profileMetaManager.getLocalisedSummaryOrComment(this));
                profiles.add(profileMetaManager);
                profileNames.add(new SpannableStringBuilder(profileName).append("\n").append(getSecondaryText(this, getSmallerText(summary))));
            }
            new SearchableMultiChoiceDialogBuilder<>(this, profiles, profileNames)
                    .setTitle(R.string.add_to_profile)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.add, (dialog, which, selectedItems) -> {
                        multiSelectionView.cancel();
                        for (ProfileMetaManager metaManager : selectedItems) {
                            if (metaManager.profile != null) {
                                try {
                                    metaManager.profile.packages = ArrayUtils.concatElements(String.class, metaManager
                                            .profile.packages, mModel.getSelectedPackages().keySet()
                                            .toArray(new String[0]));
                                    metaManager.writeProfile();
                                } catch (Throwable e) {
                                    e.printStackTrace();
                                }
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
            if (AppPref.isRootOrAdbEnabled()) {
                if (runningAppsMenu != null) runningAppsMenu.setVisible(true);
            } else {
                if (mModel.getSortBy() == ListOptions.SORT_BY_BLOCKED_COMPONENTS) {
                    mModel.setSortBy(ListOptions.SORT_BY_APP_LABEL);
                }
                if (runningAppsMenu != null) runningAppsMenu.setVisible(false);
            }
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

    private void checkFirstRun() {
        if (Utils.isAppInstalled()) {
            // TODO(4/1/21): Do something relevant and useful
            AppPref.set(AppPref.PrefKey.PREF_LAST_VERSION_CODE_LONG, (long) BuildConfig.VERSION_CODE);
        }
    }

    private void checkAppUpdate() {
        if (Utils.isAppUpdated()) {
            // Clean old am.jar
            IOUtils.deleteSilently(ServerConfig.getDestJarFile());
            mModel.executor.submit(() -> {
                final Spanned spannedChangelog = HtmlCompat.fromHtml(IOUtils.getContentFromAssets(this, "changelog.html"), HtmlCompat.FROM_HTML_MODE_COMPACT);
                runOnUiThread(() ->
                        new ScrollableDialogBuilder(this, spannedChangelog)
                                .linkifyAll()
                                .setTitle(R.string.changelog)
                                .setNegativeButton(R.string.ok, null)
                                .setNeutralButton(R.string.instructions, (dialog, which, isChecked) -> {
                                    Intent helpIntent = new Intent(this, HelpActivity.class);
                                    startActivity(helpIntent);
                                }).show());
            });
            AppPref.set(AppPref.PrefKey.PREF_LAST_VERSION_CODE_LONG, (long) BuildConfig.VERSION_CODE);
        }
    }

    private void handleBatchOp(@BatchOpsManager.OpType int op) {
        if (mModel == null) return;
        showProgressIndicator(true);
        Intent intent = new Intent(this, BatchOpsService.class);
        BatchOpsManager.Result input = new BatchOpsManager.Result(mModel.getSelectedPackagesWithUsers());
        intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, input.getFailedPackages());
        intent.putIntegerArrayListExtra(BatchOpsService.EXTRA_OP_USERS, input.getAssociatedUserHandles());
        intent.putExtra(BatchOpsService.EXTRA_OP, op);
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
    public boolean onQueryTextChange(String searchQuery) {
        if (mModel != null) mModel.setSearchQuery(searchQuery.toLowerCase(Locale.ROOT));
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String s) {
        return false;
    }
}
