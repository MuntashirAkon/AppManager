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

package io.github.muntashirakon.AppManager.details;

import android.annotation.SuppressLint;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.ApkUtils;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity;
import io.github.muntashirakon.AppManager.apk.whatsnew.WhatsNewDialogFragment;
import io.github.muntashirakon.AppManager.backup.BackupDialogFragment;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.RulesTypeSelectionDialogFragment;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.scanner.ScannerActivity;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.sharedpref.SharedPrefsActivity;
import io.github.muntashirakon.AppManager.types.PrivilegedFile;
import io.github.muntashirakon.AppManager.types.ScrollableDialogBuilder;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.usage.UsageUtils;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.BetterActivityResult;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.KeyStoreUtils;
import io.github.muntashirakon.AppManager.utils.MagiskUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.PermissionUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagDisabledComponents;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagSigningInfo;
import static io.github.muntashirakon.AppManager.utils.PermissionUtils.TERMUX_PERM_RUN_COMMAND;

public class AppInfoFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {
    private static final String UID_STATS_PATH = "/proc/uid_stat/";
    private static final String UID_STATS_TX = "tcp_rcv";
    private static final String UID_STATS_RX = "tcp_snd";

    private static final String PACKAGE_NAME_FDROID = "org.fdroid.fdroid";
    private static final String PACKAGE_NAME_AURORA_DROID = "com.aurora.adroid";
    private static final String PACKAGE_NAME_AURORA_STORE = "com.aurora.store";
    private static final String ACTIVITY_NAME_FDROID = "org.fdroid.fdroid.views.AppDetailsActivity";
    private static final String ACTIVITY_NAME_AURORA_DROID = "com.aurora.adroid.ui.details.DetailsActivity";
    private static final String ACTIVITY_NAME_AURORA_STORE = "com.aurora.store.ui.details.DetailsActivity";

    private PackageManager mPackageManager;
    private String mPackageName;
    private PackageInfo mPackageInfo;
    private PackageInfo mInstalledPackageInfo;
    private AppDetailsActivity mActivity;
    private ApplicationInfo mApplicationInfo;
    private LinearLayout mHorizontalLayout;
    private ChipGroup mTagCloud;
    @SuppressLint("SimpleDateFormat")
    private final SimpleDateFormat mDateFormatter = new SimpleDateFormat("EE LLL dd yyyy kk:mm:ss");
    private SwipeRefreshLayout mSwipeRefresh;
    private int mAccentColor;
    private CharSequence mPackageLabel;
    private LinearProgressIndicator mProgressIndicator;
    private AppDetailsViewModel mainModel;
    private AppInfoRecyclerAdapter adapter;
    // Headers
    private TextView labelView;
    private TextView packageNameView;
    private ImageView iconView;
    private TextView versionView;

    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    private boolean isExternalApk;
    private boolean isRootEnabled;
    private boolean isAdbEnabled;

    @GuardedBy("mListItems")
    private final List<ListItem> mListItems = new ArrayList<>();
    private final BetterActivityResult<String, Uri> export = BetterActivityResult.registerForActivityResult(this, new ActivityResultContracts.CreateDocument());
    private final BetterActivityResult<String, Boolean> termux = BetterActivityResult.registerForActivityResult(this, new ActivityResultContracts.RequestPermission());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        mActivity = (AppDetailsActivity) requireActivity();
        mainModel = mActivity.model;
        isRootEnabled = AppPref.isRootEnabled();
        isAdbEnabled = AppPref.isAdbEnabled();
        mPackageManager = mActivity.getPackageManager();
        mAccentColor = UIUtils.getAccentColor(mActivity);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.pager_app_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Swipe refresh
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setColorSchemeColors(mAccentColor);
        mSwipeRefresh.setProgressBackgroundColorSchemeColor(UIUtils.getPrimaryColor(mActivity));
        mSwipeRefresh.setOnRefreshListener(this);
        // Recycler view
        RecyclerView recyclerView = view.findViewById(android.R.id.list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(mActivity));
        // Horizontal view
        mHorizontalLayout = view.findViewById(R.id.horizontal_layout);
        // Progress indicator
        mProgressIndicator = view.findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        // Header
        mTagCloud = view.findViewById(R.id.tag_cloud);
        labelView = view.findViewById(R.id.label);
        packageNameView = view.findViewById(R.id.packageName);
        iconView = view.findViewById(R.id.icon);
        versionView = view.findViewById(R.id.version);
        // Set adapter only after package info is loaded
        executor.submit(() -> {
            mPackageName = mainModel.getPackageName();
            if (mPackageName == null) {
                mainModel.setPackageInfo(false);
                mPackageName = mainModel.getPackageName();
            }
            isExternalApk = mainModel.getIsExternalApk();
            adapter = new AppInfoRecyclerAdapter();
            recyclerView.setAdapter(adapter);
        });
        // Set observer
        mainModel.get(AppDetailsFragment.APP_INFO).observe(getViewLifecycleOwner(), appDetailsItems -> {
            if (!appDetailsItems.isEmpty() && mainModel.isPackageExist()) {
                AppDetailsItem appDetailsItem = appDetailsItems.get(0);
                mPackageInfo = (PackageInfo) appDetailsItem.vanillaItem;
                mPackageName = appDetailsItem.name;
                showProgressIndicator(true);
                executor.submit(this::getPackageInfo);
            }
        });
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        if (!mainModel.getIsExternalApk()) inflater.inflate(R.menu.fragment_app_info_actions, menu);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        if (isExternalApk) return;
        menu.findItem(R.id.action_open_in_termux).setVisible(isRootEnabled);
        menu.findItem(R.id.action_enable_magisk_hide).setVisible(isRootEnabled);
        boolean isDebuggable = false;
        if (mApplicationInfo != null) {
            isDebuggable = (mApplicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        }
        menu.findItem(R.id.action_run_in_termux).setVisible(isDebuggable);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_refresh_detail) {
            refreshDetails();
        } else if (itemId == R.id.action_share_apk) {
            executor.submit(() -> {
                try {
                    File tmpApkSource = ApkUtils.getSharableApkFile(mPackageInfo);
                    runOnUiThread(() -> {
                        Intent intent = new Intent(Intent.ACTION_SEND)
                                .setType("application/*")
                                .putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(mActivity, BuildConfig.APPLICATION_ID + ".provider", tmpApkSource))
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(intent, getString(R.string.share_apk)));
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(mActivity, getString(R.string.failed_to_extract_apk_file), Toast.LENGTH_SHORT).show());
                }
            });
        } else if (itemId == R.id.action_backup) {
            BackupDialogFragment backupDialogFragment = new BackupDialogFragment();
            Bundle args = new Bundle();
            args.putStringArrayList(BackupDialogFragment.ARG_PACKAGES, new ArrayList<>(Collections.singleton(mPackageName)));
            backupDialogFragment.setArguments(args);
            backupDialogFragment.setOnActionBeginListener(mode -> showProgressIndicator(true));
            backupDialogFragment.setOnActionCompleteListener((mode, failedPackages) -> showProgressIndicator(false));
            backupDialogFragment.show(mActivity.getSupportFragmentManager(), BackupDialogFragment.TAG);
        } else if (itemId == R.id.action_view_settings) {
            Intent infoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            infoIntent.addCategory(Intent.CATEGORY_DEFAULT);
            infoIntent.setData(Uri.parse("package:" + mPackageName));
            startActivity(infoIntent);
        } else if (itemId == R.id.action_export_blocking_rules) {
            @SuppressLint("SimpleDateFormat") final String fileName = "app_manager_rules_export-" + (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime())) + ".am.tsv";
            export.launch(fileName, uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                RulesTypeSelectionDialogFragment dialogFragment = new RulesTypeSelectionDialogFragment();
                Bundle exportArgs = new Bundle();
                ArrayList<String> packages = new ArrayList<>();
                packages.add(mPackageName);
                exportArgs.putInt(RulesTypeSelectionDialogFragment.ARG_MODE, RulesTypeSelectionDialogFragment.MODE_EXPORT);
                exportArgs.putParcelable(RulesTypeSelectionDialogFragment.ARG_URI, uri);
                exportArgs.putStringArrayList(RulesTypeSelectionDialogFragment.ARG_PKG, packages);
                exportArgs.putIntArray(RulesTypeSelectionDialogFragment.ARG_USERS, new int[]{mainModel.getUserHandle()});
                dialogFragment.setArguments(exportArgs);
                dialogFragment.show(mActivity.getSupportFragmentManager(), RulesTypeSelectionDialogFragment.TAG);
            });
        } else if (itemId == R.id.action_open_in_termux) {
            if (PermissionUtils.hasTermuxPermission(mActivity)) {
                openInTermux();
            } else termux.launch(TERMUX_PERM_RUN_COMMAND, granted -> {
                if (granted) openInTermux();
            });
        } else if (itemId == R.id.action_run_in_termux) {
            if (PermissionUtils.hasTermuxPermission(mActivity)) {
                runInTermux();
            } else termux.launch(TERMUX_PERM_RUN_COMMAND, granted -> {
                if (granted) runInTermux();
            });
        } else if (itemId == R.id.action_enable_magisk_hide) {
            if (MagiskUtils.hide(mPackageName)) {
                try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(mPackageName, mainModel.getUserHandle())) {
                    cb.setMagiskHide(true);
                    refreshDetails();
                }
            } else {
                Toast.makeText(mActivity, R.string.failed_to_enable_magisk_hide, Toast.LENGTH_SHORT).show();
            }
        } else if (itemId == R.id.action_extract_icon) {
            String iconName = mPackageLabel + "_icon.png";
            export.launch(iconName, uri -> {
                if (uri == null) {
                    // Back button pressed.
                    return;
                }
                try {
                    try (OutputStream outputStream = mActivity.getContentResolver().openOutputStream(uri)) {
                        if (outputStream == null) {
                            throw new IOException("Unable to open output stream.");
                        }
                        Bitmap bitmap = IOUtils.getBitmapFromDrawable(mApplicationInfo.loadIcon(mPackageManager));
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                        outputStream.flush();
                        Toast.makeText(mActivity, R.string.saved_successfully, Toast.LENGTH_SHORT).show();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(mActivity, R.string.saving_failed, Toast.LENGTH_SHORT).show();
                }
            });
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mActivity.searchView != null) mActivity.searchView.setVisibility(View.GONE);
    }

    @Override
    public void onRefresh() {
        mSwipeRefresh.setRefreshing(false);
        refreshDetails();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mActivity.searchView != null) mActivity.searchView.setVisibility(View.GONE);
    }

    @Override
    public void onDetach() {
        executor.shutdownNow();
        super.onDetach();
    }

    @Override
    public void onDestroy() {
        IOUtils.deleteDir(mActivity.getExternalCacheDir());
        super.onDestroy();
    }

    private void openInTermux() {
        runWithTermux(new String[]{"su", "-", String.valueOf(mApplicationInfo.uid)});
    }

    private void runInTermux() {
        runWithTermux(new String[]{"su", "-c", "run-as", mPackageName});
    }

    private void runWithTermux(String[] command) {
        Intent intent = new Intent();
        intent.setClassName("com.termux", "com.termux.app.RunCommandService");
        intent.setAction("com.termux.RUN_COMMAND");
        intent.putExtra("com.termux.RUN_COMMAND_PATH", Utils.TERMUX_LOGIN_PATH);
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", command);
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
        try {
            ActivityCompat.startForegroundService(mActivity, intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void install() {
        Intent intent = new Intent(this.getContext(), PackageInstallerActivity.class);
        intent.putExtra(PackageInstallerActivity.EXTRA_APK_FILE_KEY, mainModel.getApkFileKey());
        try {
            startActivity(intent);
        } catch (Exception ignore) {
        }
    }

    private void refreshDetails() {
        showProgressIndicator(true);
        mainModel.setIsPackageChanged();
    }

    /**
     * Set views up to details_container.
     */
    @WorkerThread
    private void setHeaders() {
        if (isDetached()) return;
        // Set Application Name, aka Label
        runOnUiThread(() -> labelView.setText(mPackageLabel));

        // Set Package Name
        runOnUiThread(() -> packageNameView.setText(mPackageName));

        // Set App Icon
        final Drawable appIcon = mApplicationInfo.loadIcon(mPackageManager);
        runOnUiThread(() -> iconView.setImageDrawable(appIcon));

        // Set App Version
        CharSequence version = getString(R.string.version_name_with_code, mPackageInfo.versionName, PackageInfoCompat.getLongVersionCode(mPackageInfo));
        runOnUiThread(() -> versionView.setText(version));

        // Tag cloud //
        HashMap<String, RulesStorageManager.Type> trackerComponents;
        trackerComponents = ComponentUtils.getTrackerComponentsForPackageInfo(mPackageInfo);
        boolean isRunning;
        if (isExternalApk) isRunning = false;
        else isRunning = PackageUtils.hasRunningServices(mPackageName);
        boolean isSystemlessPath;
        boolean hasMasterkey;
        boolean hasKeystore;
        boolean isMagiskHideEnabled;
        if (!isExternalApk && isRootEnabled) {
            isSystemlessPath = MagiskUtils.isSystemlessPath(PackageUtils
                    .getHiddenCodePathOrDefault(mPackageName, mApplicationInfo.publicSourceDir));
            hasMasterkey = KeyStoreUtils.hasMasterKey(mApplicationInfo.uid);
            hasKeystore = KeyStoreUtils.hasKeyStore(mApplicationInfo.uid);
            isMagiskHideEnabled = MagiskUtils.isHidden(mPackageName);
        } else {
            isSystemlessPath = false;
            hasMasterkey = false;
            hasKeystore = false;
            isMagiskHideEnabled = false;
        }
        runOnUiThread(() -> {
            if (isDetached()) return;
            mTagCloud.removeAllViews();
            // Add tracker chip
            if (!trackerComponents.isEmpty()) {
                addChip(getResources().getQuantityString(R.plurals.no_of_trackers,
                        trackerComponents.size(), trackerComponents.size()), R.color.tracker)
                        .setOnClickListener(v -> {
                            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(mActivity)
                                    .setTitle(R.string.trackers)
                                    .setItems(trackerComponents.keySet().toArray(new String[0]), null);
                            if (!isExternalApk && isRootEnabled) {
                                builder.setPositiveButton(R.string.block, (dialog, which) -> {
                                    Intent intent = new Intent(mActivity, BatchOpsService.class);
                                    intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, new ArrayList<>(Collections.singletonList(mPackageName)));
                                    intent.putIntegerArrayListExtra(BatchOpsService.EXTRA_OP_USERS, new ArrayList<>(Collections.singletonList(mainModel.getUserHandle())));
                                    intent.putExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_BLOCK_TRACKERS);
                                    intent.putExtra(BatchOpsService.EXTRA_HEADER, getString(R.string.one_click_ops));
                                    ContextCompat.startForegroundService(mActivity, intent);
                                }).setNeutralButton(R.string.unblock, (dialog, which) -> {
                                    Intent intent = new Intent(mActivity, BatchOpsService.class);
                                    intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, new ArrayList<>(Collections.singletonList(mPackageName)));
                                    intent.putIntegerArrayListExtra(BatchOpsService.EXTRA_OP_USERS, new ArrayList<>(Collections.singletonList(mainModel.getUserHandle())));
                                    intent.putExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_UNBLOCK_TRACKERS);
                                    intent.putExtra(BatchOpsService.EXTRA_HEADER, getString(R.string.one_click_ops));
                                    ContextCompat.startForegroundService(mActivity, intent);
                                }).setNegativeButton(R.string.cancel, null);
                            } else builder.setNegativeButton(R.string.close, null);
                            builder.show();
                        });
            }
            if ((mApplicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                if (isSystemlessPath) {
                    addChip(R.string.systemless_app);
                } else addChip(R.string.system_app);
                if ((mApplicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
                    addChip(R.string.updated_app);
            } else if (!mainModel.getIsExternalApk()) addChip(R.string.user_app);
            int countSplits = mainModel.getSplitCount();
            if (countSplits > 0) {
                addChip(getResources().getQuantityString(R.plurals.no_of_splits, countSplits,
                        countSplits)).setOnClickListener(v -> {
                    ApkFile apkFile = ApkFile.getInstance(mainModel.getApkFileKey());
                    // Display a list of apks
                    List<ApkFile.Entry> apkEntries = apkFile.getEntries();
                    String[] entryNames = new String[countSplits];
                    for (int i = 0; i < countSplits; ++i) {
                        entryNames[i] = apkEntries.get(i + 1).toLocalizedString(mActivity);
                    }
                    new MaterialAlertDialogBuilder(mActivity)
                            .setTitle(R.string.splits)
                            .setItems(entryNames, null)
                            .setNegativeButton(R.string.close, null)
                            .show();
                });
            }
            if ((mApplicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                addChip(R.string.debuggable);
            if ((mApplicationInfo.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0)
                addChip(R.string.test_only);
            if ((mApplicationInfo.flags & ApplicationInfo.FLAG_HAS_CODE) == 0)
                addChip(R.string.no_code);
            if ((mApplicationInfo.flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0)
                addChip(R.string.requested_large_heap, R.color.tracker);
            if (isRunning) {
                addChip(R.string.running, R.color.running).setOnClickListener(v ->
                        mActivity.viewPager.setCurrentItem(AppDetailsFragment.SERVICES));
            }
            if ((mApplicationInfo.flags & ApplicationInfo.FLAG_STOPPED) != 0)
                addChip(R.string.stopped, R.color.stopped);
            if (!mApplicationInfo.enabled) addChip(R.string.disabled_app, R.color.disabled_user);
            if (isMagiskHideEnabled) {
                addChip(R.string.magisk_hide_enabled).setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                        .setTitle(R.string.magisk_hide_enabled)
                        .setMessage(R.string.disable_magisk_hide)
                        .setPositiveButton(R.string.disable, (dialog, which) -> {
                            if (MagiskUtils.unhide(mPackageName)) {
                                try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(mPackageName, mainModel.getUserHandle())) {
                                    cb.setMagiskHide(false);
                                    refreshDetails();
                                }
                            } else {
                                Toast.makeText(mActivity, R.string.failed_to_disable_magisk_hide,
                                        Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show());
            }
            if (hasKeystore) {
                Chip chip;
                if (hasMasterkey) chip = addChip(R.string.keystore, R.color.tracker);
                else chip = addChip(R.string.keystore);
                chip.setOnClickListener(view -> new MaterialAlertDialogBuilder(mActivity)
                        .setTitle(R.string.keystore)
                        .setItems(KeyStoreUtils.getKeyStoreFiles(mApplicationInfo.uid,
                                mainModel.getUserHandle()).toArray(new String[0]), null)
                        .setNegativeButton(R.string.close, null)
                        .show());
            }
        });
    }

    private void setHorizontalActions() {
        mHorizontalLayout.removeAllViews();
        if (!mainModel.getIsExternalApk()) {
            // Set open
            final Intent launchIntentForPackage = mPackageManager.getLaunchIntentForPackage(mPackageName);
            if (launchIntentForPackage != null) {
                addToHorizontalLayout(R.string.launch_app, R.drawable.ic_open_in_new_black_24dp)
                        .setOnClickListener(v -> startActivity(launchIntentForPackage));
            }
            // Set disable
            if (isRootEnabled || isAdbEnabled) {
                if (mApplicationInfo.enabled) {
                    addToHorizontalLayout(R.string.disable, R.drawable.ic_block_black_24dp).setOnClickListener(v -> {
                        try {
                            PackageManagerCompat.setApplicationEnabledSetting(mPackageName,
                                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                                    0, mainModel.getUserHandle());
                        } catch (RemoteException e) {
                            Log.e("AppInfo", e);
                            Toast.makeText(mActivity, getString(R.string.failed_to_disable, mPackageLabel), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
            // Set uninstall
            addToHorizontalLayout(R.string.uninstall, R.drawable.ic_delete_black_24dp).setOnClickListener(v -> {
                final boolean isSystemApp = (mApplicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (AppPref.isRootOrAdbEnabled()) {
                    ScrollableDialogBuilder builder = new ScrollableDialogBuilder(mActivity,
                            isSystemApp ? R.string.uninstall_system_app_message : R.string.uninstall_app_message)
                            .setCheckboxLabel(R.string.keep_data_and_signatures)
                            .setTitle(mPackageLabel)
                            .setPositiveButton(R.string.uninstall, (dialog, which, keepData) -> executor.submit(() -> {
                                Runner.Result result;
                                if (keepData) {
                                    result = RunnerUtils.uninstallPackageWithoutData(mPackageName, mainModel.getUserHandle());
                                } else {
                                    result = RunnerUtils.uninstallPackageWithData(mPackageName, mainModel.getUserHandle());
                                }
                                if (result.isSuccessful()) {
                                    runOnUiThread(() -> {
                                        Toast.makeText(mActivity, getString(R.string.uninstalled_successfully, mPackageLabel), Toast.LENGTH_LONG).show();
                                        mActivity.finish();
                                    });
                                } else {
                                    runOnUiThread(() -> Toast.makeText(mActivity, getString(R.string.failed_to_uninstall, mPackageLabel), Toast.LENGTH_LONG).show());
                                }
                            }))
                            .setNegativeButton(R.string.cancel, (dialog, which, keepData) -> {
                                if (dialog != null) dialog.cancel();
                            });
                    if ((mApplicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                        builder.setNeutralButton(R.string.uninstall_updates, (dialog, which, keepData) ->
                                executor.submit(() -> {
                                    Runner.Result result = RunnerUtils.uninstallPackageUpdate(mPackageName, mainModel.getUserHandle(), keepData);
                                    if (result.isSuccessful()) {
                                        runOnUiThread(() -> Toast.makeText(mActivity, getString(R.string.update_uninstalled_successfully, mPackageLabel), Toast.LENGTH_LONG).show());
                                    } else {
                                        runOnUiThread(() -> Toast.makeText(mActivity, getString(R.string.failed_to_uninstall_updates, mPackageLabel), Toast.LENGTH_LONG).show());
                                    }
                                }));
                    }
                    builder.show();
                } else {
                    Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
                    uninstallIntent.setData(Uri.parse("package:" + mPackageName));
                    startActivity(uninstallIntent);
                }
            });
            // Enable/disable app (root only)
            if (isRootEnabled || isAdbEnabled) {
                if (!mApplicationInfo.enabled) {
                    // Enable app
                    addToHorizontalLayout(R.string.enable, R.drawable.ic_baseline_get_app_24).setOnClickListener(v -> {
                        try {
                            PackageManagerCompat.setApplicationEnabledSetting(mPackageName,
                                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0,
                                    mainModel.getUserHandle());
                        } catch (RemoteException e) {
                            Log.e("AppInfo", e);
                            Toast.makeText(mActivity, getString(R.string.failed_to_enable, mPackageLabel), Toast.LENGTH_LONG).show();
                        }
                    });
                }
                // Force stop
                if ((mApplicationInfo.flags & ApplicationInfo.FLAG_STOPPED) == 0) {
                    addToHorizontalLayout(R.string.force_stop, R.drawable.ic_baseline_power_settings_new_24).setOnClickListener(v -> executor.submit(() -> {
                        try {
                            PackageManagerCompat.forceStopPackage(mPackageName, mainModel.getUserHandle());
                            runOnUiThread(this::refreshDetails);
                        } catch (RemoteException|SecurityException e) {
                            runOnUiThread(() -> Toast.makeText(mActivity, getString(R.string.failed_to_stop, mPackageLabel), Toast.LENGTH_LONG).show());
                        }
                    }));
                }
                // Clear data
                addToHorizontalLayout(R.string.clear_data, R.drawable.ic_delete_black_24dp)
                        .setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                                .setTitle(mPackageLabel)
                                .setMessage(R.string.clear_data_message)
                                .setPositiveButton(R.string.clear, (dialog, which) ->
                                        executor.submit(() -> {
                                            if (PackageManagerCompat.clearApplicationUserData(mPackageName, mainModel.getUserHandle())) {
                                                runOnUiThread(this::refreshDetails);
                                            }
                                        }))
                                .setNegativeButton(R.string.cancel, null)
                                .show());
                // Clear cache
                if (isRootEnabled) {
                    addToHorizontalLayout(R.string.clear_cache, R.drawable.ic_delete_black_24dp)
                            .setOnClickListener(v -> executor.submit(() -> {
                                if (PackageManagerCompat.deleteApplicationCacheFilesAsUser(mPackageName, mainModel.getUserHandle())) {
                                    runOnUiThread(this::refreshDetails);
                                }
                            }));
                }
            }  // End root only
        } else {
            if (mInstalledPackageInfo == null) {
                // App not installed
                addToHorizontalLayout(R.string.install, R.drawable.ic_baseline_get_app_24)
                        .setOnClickListener(v -> install());
            } else {
                // App is installed
                long installedVersionCode = PackageInfoCompat.getLongVersionCode(mInstalledPackageInfo);
                long thisVersionCode = PackageInfoCompat.getLongVersionCode(mPackageInfo);
                if (installedVersionCode < thisVersionCode) {  // FIXME: Check for signature
                    // Needs update
                    addToHorizontalLayout(R.string.whats_new, R.drawable.ic_info_outline_black_24dp)
                            .setOnClickListener(v -> {
                                Bundle args = new Bundle();
                                args.putParcelable(WhatsNewDialogFragment.ARG_NEW_PKG_INFO, mPackageInfo);
                                args.putParcelable(WhatsNewDialogFragment.ARG_OLD_PKG_INFO, mInstalledPackageInfo);
                                WhatsNewDialogFragment dialogFragment = new WhatsNewDialogFragment();
                                dialogFragment.setArguments(args);
                                dialogFragment.show(mActivity.getSupportFragmentManager(), WhatsNewDialogFragment.TAG);
                            });
                    addToHorizontalLayout(R.string.update, R.drawable.ic_baseline_get_app_24)
                            .setOnClickListener(v -> install());
                } else if (installedVersionCode == thisVersionCode) {
                    // Needs reinstall
                    addToHorizontalLayout(R.string.reinstall, R.drawable.ic_baseline_get_app_24)
                            .setOnClickListener(v -> install());
                } else {
                    // Needs downgrade
                    if (AppPref.isRootOrAdbEnabled()) {
                        addToHorizontalLayout(R.string.downgrade, R.drawable.ic_baseline_get_app_24)
                                .setOnClickListener(v -> install());
                    }
                }
            }
        }
        // Set manifest
        addToHorizontalLayout(R.string.manifest, R.drawable.ic_tune_black_24dp).setOnClickListener(v -> {
            Intent intent = new Intent(mActivity, ManifestViewerActivity.class);
            ApkFile apkFile = ApkFile.getInstance(mainModel.getApkFileKey());
            if (apkFile.isSplit()) {
                // Display a list of apks
                List<ApkFile.Entry> apkEntries = apkFile.getEntries();
                String[] entryNames = new String[apkEntries.size()];
                for (int i = 0; i < apkEntries.size(); ++i) {
                    entryNames[i] = apkEntries.get(i).toLocalizedString(requireActivity());
                }
                new MaterialAlertDialogBuilder(mActivity)
                        .setTitle(R.string.select_apk)
                        .setItems(entryNames, (dialog, which) -> executor.submit(() -> {
                            try {
                                File file = apkEntries.get(which).getRealCachedFile();
                                intent.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"));
                                runOnUiThread(() -> startActivity(intent));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            } else {
                // Open directly
                if (mainModel.getIsExternalApk()) {
                    File file = new File(mApplicationInfo.publicSourceDir);
                    intent.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"));
                } else {
                    intent.putExtra(ManifestViewerActivity.EXTRA_PACKAGE_NAME, mPackageName);
                }
                startActivity(intent);
            }
        });
        // Set scanner
        addToHorizontalLayout(R.string.scanner, R.drawable.ic_baseline_security_24).setOnClickListener(v -> {
            Intent intent = new Intent(mActivity, ScannerActivity.class);
            intent.putExtra(ScannerActivity.EXTRA_IS_EXTERNAL, isExternalApk);
            File file = new File(mApplicationInfo.publicSourceDir);
            intent.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"));
            startActivity(intent);
        });
        // Root only features
        if (!mainModel.getIsExternalApk() && isRootEnabled) {
            // Shared prefs (root only)
            final List<PrivilegedFile> sharedPrefs = new ArrayList<>();
            PrivilegedFile[] tmpPaths = getSharedPrefs(mApplicationInfo.dataDir);
            if (tmpPaths != null) sharedPrefs.addAll(Arrays.asList(tmpPaths));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tmpPaths = getSharedPrefs(mApplicationInfo.deviceProtectedDataDir);
                if (tmpPaths != null) sharedPrefs.addAll(Arrays.asList(tmpPaths));
            }
            if (!sharedPrefs.isEmpty()) {
                CharSequence[] sharedPrefNames = new CharSequence[sharedPrefs.size()];
                for (int i = 0; i < sharedPrefs.size(); ++i) {
                    sharedPrefNames[i] = sharedPrefs.get(i).getName();
                }
                addToHorizontalLayout(R.string.shared_prefs, R.drawable.ic_view_list_black_24dp)
                        .setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                                .setTitle(R.string.shared_prefs)
                                .setItems(sharedPrefNames, (dialog, which) -> {
                                    Intent intent = new Intent(mActivity, SharedPrefsActivity.class);
                                    intent.putExtra(SharedPrefsActivity.EXTRA_PREF_LOCATION, sharedPrefs.get(which).getAbsolutePath());
                                    intent.putExtra(SharedPrefsActivity.EXTRA_PREF_LABEL, mPackageLabel);
                                    startActivity(intent);
                                })
                                .setNegativeButton(R.string.ok, null)
                                .show());
            }
            // Databases (root only)
            final List<PrivilegedFile> databases = new ArrayList<>();
            tmpPaths = getDatabases(mApplicationInfo.dataDir);
            if (tmpPaths != null) databases.addAll(Arrays.asList(tmpPaths));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tmpPaths = getDatabases(mApplicationInfo.deviceProtectedDataDir);
                if (tmpPaths != null) databases.addAll(Arrays.asList(tmpPaths));
            }
            if (!databases.isEmpty()) {
                CharSequence[] databases2 = new CharSequence[databases.size()];
                for (int i = 0; i < databases.size(); ++i) {
                    databases2[i] = databases.get(i).getName();
                }
                addToHorizontalLayout(R.string.databases, R.drawable.ic_assignment_black_24dp)
                        .setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity)
                                .setTitle(R.string.databases)
                                .setItems(databases2, (dialog, which) -> {
                                    // TODO(10/9/20): Need a custom ContentProvider
//                                    File realFile = databases.get(which);
//                                    File sharableFile = new File(AppManager.getContext().getExternalCacheDir(), realFile.getName());
//                                    RunnerUtils.cp(realFile, sharableFile);
//                                    Intent openFile = new Intent(Intent.ACTION_VIEW)
//                                            .setDataAndType(FileProvider.getUriForFile(mActivity, BuildConfig.APPLICATION_ID + ".provider", sharableFile), "application/x-sqlite3")
//                                            .putExtra("real_path", realFile.getAbsolutePath())
//                                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                                    if (openFile.resolveActivityInfo(mPackageManager, 0) != null)
//                                        startActivity(openFile);
                                })
                                .setNegativeButton(R.string.ok, null)
                                .show());
            }
        }  // End root only features
        // Set F-Droid or Aurora Droid
        try {
            if (!mPackageManager.getApplicationInfo(PACKAGE_NAME_AURORA_DROID, 0).enabled)
                throw new PackageManager.NameNotFoundException();
            addToHorizontalLayout(R.string.aurora, R.drawable.ic_frost_auroradroid_black_24dp)
                    .setOnClickListener(v -> {
                        Intent intent = new Intent();
                        intent.setClassName(PACKAGE_NAME_AURORA_DROID, ACTIVITY_NAME_AURORA_DROID);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra("INTENT_PACKAGE_NAME", mPackageName);
                        try {
                            startActivity(intent);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (PackageManager.NameNotFoundException e) {
            try {
                if (!mPackageManager.getApplicationInfo(PACKAGE_NAME_FDROID, 0).enabled)
                    throw new PackageManager.NameNotFoundException();
                addToHorizontalLayout(R.string.fdroid, R.drawable.ic_frost_fdroid_black_24dp)
                        .setOnClickListener(v -> {
                            Intent intent = new Intent();
                            intent.setClassName(PACKAGE_NAME_FDROID, ACTIVITY_NAME_FDROID);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.putExtra("appid", mPackageName);
                            try {
                                startActivity(intent);
                            } catch (Exception ignored) {
                            }
                        });
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        // Set Aurora Store
        try {
            if (!mPackageManager.getApplicationInfo(PACKAGE_NAME_AURORA_STORE, 0).enabled)
                throw new PackageManager.NameNotFoundException();
            addToHorizontalLayout(R.string.store, R.drawable.ic_frost_aurorastore_black_24dp)
                    .setOnClickListener(v -> {
                        Intent intent = new Intent();
                        intent.setClassName(PACKAGE_NAME_AURORA_STORE, ACTIVITY_NAME_AURORA_STORE);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra("INTENT_PACKAGE_NAME", mPackageName);
                        try {
                            startActivity(intent);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }

    @GuardedBy("mListItems")
    private void setPathsAndDirectories() {
        synchronized (mListItems) {
            if (isDetached()) return;
            // Paths and directories
            mListItems.add(ListItem.getGroupHeader(getString(R.string.paths_and_directories)));
            // Source directory (apk path)
            String sourceDir = new File(mApplicationInfo.publicSourceDir).getParent();
            mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.source_dir),
                    mApplicationInfo.publicSourceDir, openAsFolderInFM(sourceDir)));
            // Split source directories
            ApkFile apkFile = ApkFile.getInstance(mainModel.getApkFileKey());
            int countSplits = apkFile.getEntries().size() - 1;
            ApkFile.Entry entry;
            // Base.apk is always on top, so count from 1
            for (int i = 1; i <= countSplits; ++i) {
                entry = apkFile.getEntries().get(i);
                mListItems.add(ListItem.getSelectableRegularItem(entry.toLocalizedString(mActivity),
                        entry.getApkSource(), openAsFolderInFM(entry.getApkSource())));
            }
            // Data dir
            mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.data_dir),
                    mApplicationInfo.dataDir, openAsFolderInFM(mApplicationInfo.dataDir)));
            // Device-protected data dir
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.dev_protected_data_dir),
                        mApplicationInfo.deviceProtectedDataDir, openAsFolderInFM(mApplicationInfo.deviceProtectedDataDir)));
            }
            // External data dirs
            File[] dataDirs = mActivity.getExternalCacheDirs();
            if (dataDirs != null) {
                List<String> extDataDirs = new ArrayList<>();
                String tmpDataDir;
                for (File dataDir : dataDirs) {
                    if (dataDir == null) continue;
                    tmpDataDir = dataDir.getParent();
                    if (tmpDataDir != null) tmpDataDir = new File(tmpDataDir).getParent();
                    if (tmpDataDir != null)
                        extDataDirs.add(tmpDataDir + File.separatorChar + mPackageName);
                }
                if (extDataDirs.size() == 1) {
                    if (new File(extDataDirs.get(0)).exists()) {
                        mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.external_data_dir),
                                extDataDirs.get(0), openAsFolderInFM(extDataDirs.get(0))));
                    }
                } else {
                    for (int i = 0; i < extDataDirs.size(); ++i) {
                        if (new File(extDataDirs.get(i)).exists()) {
                            mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.external_multiple_data_dir, i),
                                    extDataDirs.get(i), openAsFolderInFM(extDataDirs.get(i))));
                        }
                    }
                }
            }
            // Native JNI library dir
            File nativeLib = new File(mApplicationInfo.nativeLibraryDir);
            if (nativeLib.exists()) {
                mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.native_library_dir),
                        mApplicationInfo.nativeLibraryDir, openAsFolderInFM(mApplicationInfo.nativeLibraryDir)));
            }
            mListItems.add(ListItem.getGroupDivider());
        }
    }

    @GuardedBy("mListItems")
    private void setMoreInfo() {
        synchronized (mListItems) {
            if (isDetached()) return;
            // Set more info
            mListItems.add(ListItem.getGroupHeader(getString(R.string.more_info)));

            // Set installer version info
            if (isExternalApk && mInstalledPackageInfo != null) {
                ListItem listItem = ListItem.getSelectableRegularItem(getString(R.string.installed_version),
                        getString(R.string.version_name_with_code, mInstalledPackageInfo.versionName,
                                PackageInfoCompat.getLongVersionCode(mInstalledPackageInfo)), v -> {
                            Intent appDetailsIntent = new Intent(mActivity, AppDetailsActivity.class);
                            appDetailsIntent.putExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME, mPackageName);
                            mActivity.startActivity(appDetailsIntent);
                        });
                listItem.actionIcon = R.drawable.ic_info_outline_black_24dp;
                mListItems.add(listItem);
            }

            // SDK
            final StringBuilder sdk = new StringBuilder();
            sdk.append(getString(R.string.sdk_max)).append(": ").append(mApplicationInfo.targetSdkVersion);
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M)
                sdk.append(", ").append(getString(R.string.sdk_min)).append(": ").append(mApplicationInfo.minSdkVersion);
            mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.sdk), sdk.toString()));

            // Set Flags
            final StringBuilder flags = new StringBuilder();
            if ((mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                flags.append("FLAG_DEBUGGABLE");
            if ((mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0)
                flags.append(flags.length() == 0 ? "" : "|").append("FLAG_TEST_ONLY");
            if ((mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_MULTIARCH) != 0)
                flags.append(flags.length() == 0 ? "" : "|").append("FLAG_MULTIARCH");
            if ((mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_HARDWARE_ACCELERATED) != 0)
                flags.append(flags.length() == 0 ? "" : "|").append("FLAG_HARDWARE_ACCELERATED");

            if (flags.length() != 0) {
                ListItem flagsItem = ListItem.getSelectableRegularItem(getString(R.string.sdk_flags), flags.toString());
                flagsItem.flags |= LIST_ITEM_FLAG_MONOSPACE;
                mListItems.add(flagsItem);
            }
            if (isExternalApk) return;

            mListItems.add(ListItem.getRegularItem(getString(R.string.date_installed), getTime(mPackageInfo.firstInstallTime)));
            mListItems.add(ListItem.getRegularItem(getString(R.string.date_updated), getTime(mPackageInfo.lastUpdateTime)));
            if (!mPackageName.equals(mApplicationInfo.processName))
                mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.process_name), mApplicationInfo.processName));
            try {
                String installerPackageName = mPackageManager.getInstallerPackageName(mPackageName);
                if (installerPackageName != null) {
                    String applicationLabel;
                    try {
                        applicationLabel = mPackageManager.getApplicationInfo(installerPackageName, 0).loadLabel(mPackageManager).toString();
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                        applicationLabel = installerPackageName;
                    }
                    mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.installer_app), applicationLabel));
                }
            } catch (IllegalArgumentException ignore) {
            }
            mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.user_id), Integer.toString(mApplicationInfo.uid)));
            if (mPackageInfo.sharedUserId != null)
                mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.shared_user_id), mPackageInfo.sharedUserId));
            // Main activity
            final Intent launchIntentForPackage = mPackageManager.getLaunchIntentForPackage(mPackageName);
            if (launchIntentForPackage != null) {
                final ComponentName launchComponentName = launchIntentForPackage.getComponent();
                if (launchComponentName != null) {
                    final String mainActivity = launchIntentForPackage.getComponent().getClassName();
                    mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.main_activity),
                            mainActivity, view -> startActivity(launchIntentForPackage)));
                }
            }
            mListItems.add(ListItem.getGroupDivider());
        }
    }

    private void setDataUsage() {
        try {
            // Net statistics
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if ((Boolean) AppPref.get(AppPref.PrefKey.PREF_USAGE_ACCESS_ENABLED_BOOL)) {
                    try {
                        final Pair<Pair<Long, Long>, Pair<Long, Long>> dataUsage;
                        dataUsage = AppUsageStatsManager.getWifiMobileUsageForPackage(mActivity,
                                mPackageName, UsageUtils.USAGE_LAST_BOOT);
                        setDataUsageHelper(getReadableSize(dataUsage.first.first +
                                dataUsage.second.first), getReadableSize(dataUsage
                                .first.second + dataUsage.second.second));
                    } catch (SecurityException e) {
                        runOnUiThread(() -> Toast.makeText(mActivity, R.string.failed_to_get_data_usage_information, Toast.LENGTH_SHORT).show());
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else {
                final Pair<String, String> uidNetStats = getNetStats(mApplicationInfo.uid);
                setDataUsageHelper(uidNetStats.first, uidNetStats.second);
            }
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(mActivity, R.string.failed_to_get_data_usage_information, Toast.LENGTH_LONG).show());
            e.printStackTrace();
        }
    }

    @WorkerThread
    @GuardedBy("mListItems")
    private void setVerticalView() {
        synchronized (mListItems) {
            mListItems.clear();
            if (isDetached()) return;
            if (!isExternalApk) {
                setPathsAndDirectories();
                setDataUsage();
                // Storage and Cache
                if ((Boolean) AppPref.get(AppPref.PrefKey.PREF_USAGE_ACCESS_ENABLED_BOOL))
                    setStorageAndCache();
            }
            setMoreInfo();
            runOnUiThread(() -> adapter.setAdapterList(mListItems));
        }
    }

    @GuardedBy("mListItems")
    private void setDataUsageHelper(String txData, String rxData) {
        synchronized (mListItems) {
            if (isDetached()) return;
            mListItems.add(ListItem.getGroupHeader(getString(R.string.data_usage_msg)));
            mListItems.add(ListItem.getInlineItem(getString(R.string.data_transmitted), txData));
            mListItems.add(ListItem.getInlineItem(getString(R.string.data_received), rxData));
            mListItems.add(ListItem.getGroupDivider());
        }
    }

    @Nullable
    private PrivilegedFile[] getSharedPrefs(@NonNull String sourceDir) {
        PrivilegedFile sharedPath = new PrivilegedFile(sourceDir, "shared_prefs");
        return sharedPath.listFiles();
    }

    private PrivilegedFile[] getDatabases(@NonNull String sourceDir) {
        PrivilegedFile sharedPath = new PrivilegedFile(sourceDir, "databases");
        return sharedPath.listFiles((dir, name) -> !name.endsWith("-journal"));
    }

    @NonNull
    private View.OnClickListener openAsFolderInFM(String dir) {
        return view -> {
            Intent openFile = new Intent(Intent.ACTION_VIEW);
            openFile.setDataAndType(Uri.parse(dir), "resource/folder");
            openFile.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (openFile.resolveActivityInfo(mPackageManager, 0) != null)
                startActivity(openFile);
        };
    }

    @NonNull
    private Chip addChip(@StringRes int resId, @ColorRes int color) {
        Chip chip = new Chip(mActivity);
        chip.setText(resId);
        chip.setChipBackgroundColorResource(color);
        mTagCloud.addView(chip);
        return chip;
    }

    @NonNull
    private Chip addChip(CharSequence text, @SuppressWarnings("SameParameterValue") @ColorRes int color) {
        Chip chip = new Chip(mActivity);
        chip.setText(text);
        chip.setChipBackgroundColorResource(color);
        mTagCloud.addView(chip);
        return chip;
    }

    @NonNull
    private Chip addChip(@StringRes int resId) {
        Chip chip = new Chip(mActivity);
        chip.setText(resId);
        mTagCloud.addView(chip);
        return chip;
    }

    @NonNull
    private Chip addChip(CharSequence text) {
        Chip chip = new Chip(mActivity);
        chip.setText(text);
        mTagCloud.addView(chip);
        return chip;
    }

    @NonNull
    private View addToHorizontalLayout(@StringRes int stringResId, @DrawableRes int iconResId) {
        View view = getLayoutInflater().inflate(R.layout.item_app_info_actions, mHorizontalLayout, false);
        TextView textView = view.findViewById(R.id.item_text);
        textView.setText(stringResId);
        textView.setCompoundDrawablesWithIntrinsicBounds(null, ContextCompat.getDrawable(mActivity, iconResId), null, null);
        mHorizontalLayout.addView(view);
        return view;
    }

    /**
     * Load package sizes and update views if success.
     */
    @SuppressWarnings("deprecation")
    private void setStorageAndCache() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            try {
                Method getPackageSizeInfo = mPackageManager.getClass().getMethod(
                        "getPackageSizeInfo", String.class, IPackageStatsObserver.class);

                getPackageSizeInfo.invoke(mPackageManager, mPackageName, new IPackageStatsObserver.Stub() {
                    @Override
                    public void onGetStatsCompleted(final PackageStats pStats, boolean succeeded) {
                        setStorageInfo(pStats.codeSize + pStats.externalCodeSize,
                                pStats.dataSize + pStats.externalDataSize,
                                pStats.cacheSize + pStats.externalCacheSize,
                                pStats.externalObbSize, pStats.externalMediaSize);
                    }
                });
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        } else {
            if (!Utils.checkUsageStatsPermission(mActivity)) {
                runOnUiThread(() -> new MaterialAlertDialogBuilder(mActivity)
                        .setTitle(R.string.grant_usage_access)
                        .setMessage(R.string.grant_usage_acess_message)
                        .setPositiveButton(R.string.go, (dialog, which) -> startActivityForResult(new Intent(
                                Settings.ACTION_USAGE_ACCESS_SETTINGS), 0))
                        .setNegativeButton(R.string.cancel, null)
                        .setNeutralButton(R.string.never_ask, (dialog, which) ->
                                AppPref.set(AppPref.PrefKey.PREF_USAGE_ACCESS_ENABLED_BOOL, false))
                        .setCancelable(false)
                        .show());
                return;
            }
            try {
                StorageStatsManager storageStatsManager = (StorageStatsManager) mActivity.getSystemService(Context.STORAGE_STATS_SERVICE);
                StorageStats storageStats = storageStatsManager.queryStatsForPackage(mApplicationInfo.storageUuid, mPackageName, Process.myUserHandle());
                // TODO: List obb and media size
                long cacheSize = storageStats.getCacheBytes();
                setStorageInfo(storageStats.getAppBytes(), storageStats.getDataBytes() - cacheSize, cacheSize, 0, 0);
            } catch (IOException | PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    @GuardedBy("mListItems")
    private void setStorageInfo(long codeSize, long dataSize, long cacheSize, long obbSize, long mediaSize) {
        synchronized (mListItems) {
            if (isDetached()) return;
            mListItems.add(ListItem.getGroupHeader(getString(R.string.storage_and_cache)));
            mListItems.add(ListItem.getInlineItem(getString(R.string.app_size), getReadableSize(codeSize)));
            mListItems.add(ListItem.getInlineItem(getString(R.string.data_size), getReadableSize(dataSize)));
            mListItems.add(ListItem.getInlineItem(getString(R.string.cache_size), getReadableSize(cacheSize)));
            if (obbSize != 0)
                mListItems.add(ListItem.getInlineItem(getString(R.string.obb_size), getReadableSize(obbSize)));
            if (mediaSize != 0)
                mListItems.add(ListItem.getInlineItem(getString(R.string.media_size), getReadableSize(mediaSize)));
            mListItems.add(ListItem.getInlineItem(getString(R.string.total_size), getReadableSize(codeSize
                    + dataSize + cacheSize + obbSize + mediaSize)));
            mListItems.add(ListItem.getGroupDivider());
        }
    }

    /**
     * Get package info.
     */
    @SuppressLint("WrongConstant")
    @WorkerThread
    private void getPackageInfo() {
        if (mPackageInfo == null) {
            runOnUiThread(() -> showProgressIndicator(false));
            return;
        }
        if (isExternalApk) {
            try {
                mInstalledPackageInfo = PackageManagerCompat.getPackageInfo(mPackageName,
                        PackageManager.GET_PERMISSIONS | PackageManager.GET_ACTIVITIES
                                | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                                | PackageManager.GET_SERVICES | PackageManager.GET_URI_PERMISSION_PATTERNS
                                | flagDisabledComponents | flagSigningInfo | PackageManager.GET_CONFIGURATIONS
                                | PackageManager.GET_SHARED_LIBRARY_FILES, mainModel.getUserHandle());
            } catch (Exception e) {
                mInstalledPackageInfo = null;
            }
        }
        mApplicationInfo = mPackageInfo.applicationInfo;
        mPackageLabel = mApplicationInfo.loadLabel(mPackageManager);
        // (Re)load views
        setHeaders();
        runOnUiThread(this::setHorizontalActions);
        setVerticalView();
        runOnUiThread(() -> showProgressIndicator(false));
    }

    /**
     * Get Unix time to formatted time.
     *
     * @param time Unix time
     * @return Formatted time
     */
    @NonNull
    private String getTime(long time) {
        Date date = new Date(time);
        return mDateFormatter.format(date);
    }

    /**
     * Get network stats.
     *
     * @param uid Application UID
     * @return A tuple consisting of transmitted and received data
     */
    @NonNull
    private Pair<String, String> getNetStats(int uid) {
        String tx = getReadableSize(0);
        String rx = getReadableSize(0);
        File uidStatsDir = new File(UID_STATS_PATH + uid);
        if (uidStatsDir.exists() && uidStatsDir.isDirectory()) {
            for (File child : Objects.requireNonNull(uidStatsDir.listFiles())) {
                if (child.getName().equals(UID_STATS_TX))
                    tx = getReadableSize(Long.parseLong(IOUtils.getFileContent(child, "-1").trim()));
                else if (child.getName().equals(UID_STATS_RX))
                    rx = getReadableSize(Long.parseLong(IOUtils.getFileContent(child, "-1").trim()));
            }
        }
        return new Pair<>(tx, rx);
    }

    /**
     * Format sizes (bytes to B, KB, MB etc.).
     *
     * @param size Size in Bytes
     * @return Formatted size
     */
    private String getReadableSize(long size) {
        return Formatter.formatFileSize(mActivity, size);
    }

    private void showProgressIndicator(boolean show) {
        if (mProgressIndicator == null) return;
        if (show) mProgressIndicator.show();
        else mProgressIndicator.hide();
    }

    private void runOnUiThread(Runnable runnable) {
        mActivity.runOnUiThread(runnable);
    }

    @IntDef(value = {
            LIST_ITEM_GROUP_BEGIN,
            LIST_ITEM_GROUP_END,
            LIST_ITEM_REGULAR,
            LIST_ITEM_INLINE
    })
    private @interface ListItemType {
    }

    private static final int LIST_ITEM_GROUP_BEGIN = 0;  // Group header
    private static final int LIST_ITEM_GROUP_END = 1;  // Group divider
    private static final int LIST_ITEM_REGULAR = 2;
    private static final int LIST_ITEM_INLINE = 3;

    @IntDef(flag = true, value = {
            LIST_ITEM_FLAG_SELECTABLE,
            LIST_ITEM_FLAG_MONOSPACE
    })
    private @interface ListItemFlag {
    }

    private static final int LIST_ITEM_FLAG_SELECTABLE = 1;
    private static final int LIST_ITEM_FLAG_MONOSPACE = 1 << 1;

    static class ListItem {
        @ListItemType
        int type;
        @ListItemFlag
        int flags = 0;
        String title;
        String subtitle;
        @DrawableRes
        int icon = 0;
        @DrawableRes
        int actionIcon = 0;
        View.OnClickListener actionListener;

        @NonNull
        static ListItem getGroupHeader(String title) {
            ListItem listItem = new ListItem();
            listItem.type = LIST_ITEM_GROUP_BEGIN;
            listItem.title = title;
            return listItem;
        }

        @NonNull
        static ListItem getGroupDivider() {
            ListItem listItem = new ListItem();
            listItem.type = LIST_ITEM_GROUP_END;
            return listItem;
        }

        @NonNull
        static ListItem getInlineItem(String title, String subtitle) {
            ListItem listItem = new ListItem();
            listItem.type = LIST_ITEM_INLINE;
            listItem.title = title;
            listItem.subtitle = subtitle;
            return listItem;
        }

        @NonNull
        static ListItem getRegularItem(String title, String subtitle) {
            ListItem listItem = new ListItem();
            listItem.type = LIST_ITEM_REGULAR;
            listItem.title = title;
            listItem.subtitle = subtitle;
            return listItem;
        }

        @NonNull
        static ListItem getSelectableRegularItem(String title, String subtitle) {
            ListItem listItem = new ListItem();
            listItem.type = LIST_ITEM_REGULAR;
            listItem.flags |= LIST_ITEM_FLAG_SELECTABLE;
            listItem.title = title;
            listItem.subtitle = subtitle;
            return listItem;
        }

        @NonNull
        static ListItem getSelectableRegularItem(String title, String subtitle, View.OnClickListener actionListener) {
            ListItem listItem = new ListItem();
            listItem.type = LIST_ITEM_REGULAR;
            listItem.flags |= LIST_ITEM_FLAG_SELECTABLE;
            listItem.title = title;
            listItem.subtitle = subtitle;
            listItem.actionListener = actionListener;
            return listItem;
        }

        @NonNull
        @Override
        public String toString() {
            return "ListItem{" +
                    "type=" + type +
                    ", flags=" + flags +
                    ", title='" + title + '\'' +
                    ", subtitle='" + subtitle + '\'' +
                    '}';
        }
    }

    class AppInfoRecyclerAdapter extends RecyclerView.Adapter<AppInfoRecyclerAdapter.ViewHolder> {
        private List<ListItem> mAdapterList;

        AppInfoRecyclerAdapter() {
            mAdapterList = new ArrayList<>();
        }

        void setAdapterList(@NonNull List<ListItem> list) {
            mAdapterList = list;
            notifyDataSetChanged();
        }

        @Override
        public @ListItemType
        int getItemViewType(int position) {
            return mAdapterList.get(position).type;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, @ListItemType int viewType) {
            final View view;
            switch (viewType) {
                case AppInfoFragment.LIST_ITEM_GROUP_BEGIN:
                case AppInfoFragment.LIST_ITEM_REGULAR:
                default:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_icon_title_subtitle, parent, false);
                    break;
                case AppInfoFragment.LIST_ITEM_GROUP_END:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_divider_horizontal, parent, false);
                    break;
                case AppInfoFragment.LIST_ITEM_INLINE:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_title_subtitle_inline, parent, false);
                    break;
            }
            return new ViewHolder(view, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ListItem listItem = mAdapterList.get(position);
            holder.itemView.setClickable(false);
            holder.itemView.setFocusable(false);
            switch (listItem.type) {
                case AppInfoFragment.LIST_ITEM_GROUP_BEGIN:
                    holder.title.setText(listItem.title);
                    holder.title.setAllCaps(true);
                    holder.title.setTextSize(12f);
                    holder.title.setTextColor(mAccentColor);
                    int padding_small = mActivity.getResources().getDimensionPixelOffset(R.dimen.padding_small);
                    int padding_very_small = mActivity.getResources().getDimensionPixelOffset(R.dimen.padding_very_small);
                    int padding_medium = mActivity.getResources().getDimensionPixelOffset(R.dimen.padding_medium);
                    LinearLayoutCompat item_layout = holder.itemView.findViewById(R.id.item_layout);
                    item_layout.setPadding(padding_medium, padding_small, padding_medium, padding_very_small);
                    break;
                case AppInfoFragment.LIST_ITEM_GROUP_END:
                    break;
                case AppInfoFragment.LIST_ITEM_INLINE:
                    holder.title.setText(listItem.title);
                    holder.subtitle.setText(listItem.subtitle);
                    holder.subtitle.setTextIsSelectable((listItem.flags & AppInfoFragment.LIST_ITEM_FLAG_SELECTABLE) != 0);
                    if ((listItem.flags & AppInfoFragment.LIST_ITEM_FLAG_MONOSPACE) != 0)
                        holder.subtitle.setTypeface(Typeface.MONOSPACE);
                    else holder.subtitle.setTypeface(Typeface.DEFAULT);
                    break;
                case AppInfoFragment.LIST_ITEM_REGULAR:
                    holder.title.setText(listItem.title);
                    holder.subtitle.setText(listItem.subtitle);
                    holder.subtitle.setTextIsSelectable((listItem.flags & AppInfoFragment.LIST_ITEM_FLAG_SELECTABLE) != 0);
                    if ((listItem.flags & AppInfoFragment.LIST_ITEM_FLAG_MONOSPACE) != 0)
                        holder.subtitle.setTypeface(Typeface.MONOSPACE);
                    else holder.subtitle.setTypeface(Typeface.DEFAULT);
                    // FIXME: Load icon in background
                    if (listItem.icon != 0) holder.icon.setImageResource(listItem.icon);
                    // FIXME: Load action icon in background
                    if (listItem.actionIcon != 0)
                        holder.actionIcon.setImageResource(listItem.actionIcon);
                    if (listItem.actionListener != null) {
                        holder.actionIcon.setVisibility(View.VISIBLE);
                        holder.actionIcon.setOnClickListener(listItem.actionListener);
                    } else holder.actionIcon.setVisibility(View.GONE);
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return mAdapterList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView subtitle;
            ImageView icon;
            ImageView actionIcon;

            public ViewHolder(@NonNull View itemView, @ListItemType int viewType) {
                super(itemView);
                switch (viewType) {
                    case AppInfoFragment.LIST_ITEM_GROUP_BEGIN:
                        title = itemView.findViewById(R.id.item_title);
                        itemView.findViewById(R.id.item_subtitle).setVisibility(View.GONE);
                        itemView.findViewById(R.id.item_open).setVisibility(View.GONE);
                        itemView.findViewById(R.id.item_icon).setVisibility(View.INVISIBLE);
                        break;
                    case AppInfoFragment.LIST_ITEM_REGULAR:
                        title = itemView.findViewById(R.id.item_title);
                        subtitle = itemView.findViewById(R.id.item_subtitle);
                        actionIcon = itemView.findViewById(R.id.item_open);
                        icon = itemView.findViewById(R.id.item_icon);
                        break;
                    case AppInfoFragment.LIST_ITEM_GROUP_END:
                    default:
                        break;
                    case AppInfoFragment.LIST_ITEM_INLINE:
                        title = itemView.findViewById(R.id.item_title);
                        subtitle = itemView.findViewById(R.id.item_subtitle);
                        break;
                }
            }
        }
    }
}
