package io.github.muntashirakon.AppManager.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.text.format.Formatter;
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
import com.google.android.material.progressindicator.ProgressIndicator;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.activities.AppDetailsActivity;
import io.github.muntashirakon.AppManager.activities.ClassListingActivity;
import io.github.muntashirakon.AppManager.activities.ManifestViewerActivity;
import io.github.muntashirakon.AppManager.activities.SharedPrefsActivity;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.storage.compontents.TrackerComponentUtils;
import io.github.muntashirakon.AppManager.types.ScrollSafeSwipeRefreshLayout;
import io.github.muntashirakon.AppManager.usage.AppUsageStatsManager;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.ListItemCreator;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.RunnerUtils;
import io.github.muntashirakon.AppManager.utils.Tuple;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.AppManager.viewmodels.AppDetailsViewModel;

public class AppInfoFragment extends Fragment
        implements ScrollSafeSwipeRefreshLayout.OnRefreshListener {
    private static final String UID_STATS_PATH = "/proc/uid_stat/";
    private static final String UID_STATS_TR = "tcp_rcv";
    private static final String UID_STATS_RC = "tcp_snd";

    private static final String PACKAGE_NAME_FDROID = "org.fdroid.fdroid";
    private static final String PACKAGE_NAME_AURORA_DROID = "com.aurora.adroid";
    private static final String PACKAGE_NAME_AURORA_STORE = "com.aurora.store";
    private static final String ACTIVITY_NAME_FDROID = "org.fdroid.fdroid.views.AppDetailsActivity";
    private static final String ACTIVITY_NAME_AURORA_DROID = "com.aurora.adroid.ui.activity.DetailsActivity";
    private static final String ACTIVITY_NAME_AURORA_STORE = "com.aurora.store.ui.details.DetailsActivity";

    private static final String MIME_TSV = "text/tab-separated-values";

    private static final int REQUEST_CODE_BATCH_EXPORT = 441;

    private PackageManager mPackageManager;
    private String mPackageName;
    private PackageInfo mPackageInfo;
    private PackageStats mPackageStats;
    private AppDetailsActivity mActivity;
    private ApplicationInfo mApplicationInfo;
    private LinearLayout mHorizontalLayout;
    private ChipGroup mTagCloud;
    @SuppressLint("SimpleDateFormat")
    private SimpleDateFormat mDateFormatter = new SimpleDateFormat("EE LLL dd yyyy kk:mm:ss");
    private ScrollSafeSwipeRefreshLayout mSwipeRefresh;
    private int mAccentColor;
    private CharSequence mPackageLabel;
    private ProgressIndicator mProgressIndicator;
    private AppDetailsViewModel mainModel;
    private View view;
    // Headers
    private TextView labelView;
    private TextView packageNameView;
    private ImageView iconView;
    private TextView versionView;
    private boolean isExternalApk;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        mActivity = (AppDetailsActivity) requireActivity();
        mainModel = mActivity.model;
        mPackageName = mainModel.getPackageName();
        if (mPackageName == null) {
            mainModel.setPackageInfo(false);
            mPackageName = mainModel.getPackageName();
        }
        isExternalApk = mainModel.getIsExternalApk();
        mPackageManager = mActivity.getPackageManager();
        mAccentColor = Utils.getThemeColor(mActivity, android.R.attr.colorAccent);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.pager_app_info, container, false);
        // Swipe refresh
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setColorSchemeColors(mAccentColor);
        mSwipeRefresh.setProgressBackgroundColorSchemeColor(Utils.getThemeColor(mActivity, android.R.attr.colorPrimary));
        mSwipeRefresh.setOnRefreshListener(this);
        mHorizontalLayout = view.findViewById(R.id.horizontal_layout);
        // Progress indicator
        mProgressIndicator = view.findViewById(R.id.progress_linear);
        // Header
        mTagCloud = view.findViewById(R.id.tag_cloud);
        labelView = view.findViewById(R.id.label);
        packageNameView = view.findViewById(R.id.packageName);
        iconView = view.findViewById(R.id.icon);
        versionView = view.findViewById(R.id.version);
        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        if (!mainModel.getIsExternalApk()) inflater.inflate(R.menu.fragment_app_info_actions, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh_detail:
                mainModel.setIsPackageChanged();
                return true;
            case R.id.action_share_apk:
                new Thread(() -> {
                    try {
                        File tmpApkSource = IOUtils.getSharableApk(new File(mApplicationInfo.sourceDir));
                        runOnUiThread(() -> {
                            Intent intent = ShareCompat.IntentBuilder.from(mActivity)
                                    .setStream(FileProvider.getUriForFile(mActivity, BuildConfig.APPLICATION_ID + ".provider", tmpApkSource))
                                    .setType("application/vnd.android.package-archive")
                                    .getIntent()
                                    .setAction(Intent.ACTION_SEND)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(intent);
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> Toast.makeText(mActivity, getString(R.string.failed_to_extract_apk_file), Toast.LENGTH_SHORT).show());
                    }
                }).start();
                return true;
            case R.id.action_view_settings:
                Intent infoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                infoIntent.addCategory(Intent.CATEGORY_DEFAULT);
                infoIntent.setData(Uri.parse("package:" + mPackageName));
                startActivity(infoIntent);
                return true;
            case R.id.action_export_blocking_rules:
                @SuppressLint("SimpleDateFormat")
                String fileName = "app_manager_rules_export-" + (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime())) + ".am.tsv";
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(MIME_TSV);
                intent.putExtra(Intent.EXTRA_TITLE, fileName);
                startActivityForResult(intent, REQUEST_CODE_BATCH_EXPORT);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_CODE_BATCH_EXPORT) {
                if (data != null) {
                    RulesTypeSelectionDialogFragment dialogFragment = new RulesTypeSelectionDialogFragment();
                    Bundle args = new Bundle();
                    ArrayList<String> packages = new ArrayList<>();
                    packages.add(mPackageName);
                    args.putInt(RulesTypeSelectionDialogFragment.ARG_MODE, RulesTypeSelectionDialogFragment.MODE_EXPORT);
                    args.putParcelable(RulesTypeSelectionDialogFragment.ARG_URI, data.getData());
                    args.putStringArrayList(RulesTypeSelectionDialogFragment.ARG_PKG, packages);
                    dialogFragment.setArguments(args);
                    dialogFragment.show(mActivity.getSupportFragmentManager(), RulesTypeSelectionDialogFragment.TAG);
                }
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mainModel.getIsPackageChanged().observe(this, isPackageChanged -> {
            //noinspection ConstantConditions
            if (isPackageChanged && mainModel.getIsPackageExist().getValue()) getPackageInfo();
        });
        // First load
        mainModel.setIsPackageChanged();
    }

    @Override
    public void onRefresh() {
        mainModel.setIsPackageChanged();
        mSwipeRefresh.setRefreshing(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mActivity.searchView != null) mActivity.searchView.setVisibility(View.GONE);
    }

    @Override
    public void onDestroy() {
        IOUtils.deleteDir(mActivity.getExternalCacheDir());
        super.onDestroy();
    }

    /**
     * Set views up to details_container.
     */
    private void setHeaders() {
        // Set Application Name, aka Label
        runOnUiThread(() -> labelView.setText(mPackageLabel));

        // Set Package Name
        runOnUiThread(() -> packageNameView.setText(mPackageName));

        // Set App Icon
        final Drawable appIcon = mApplicationInfo.loadIcon(mPackageManager);
        runOnUiThread(() -> iconView.setImageDrawable(appIcon));

        // Set App Version
        runOnUiThread(() -> versionView.setText(String.format(getString(R.string.version_name_with_code), mPackageInfo.versionName, PackageUtils.getVersionCode(mPackageInfo))));

        // Tag cloud //
        List<String> componentList = new ArrayList<>();
        // Add activities
        if (mPackageInfo.activities != null) {
            String activityName;
            for (ActivityInfo activityInfo : mPackageInfo.activities) {
                if (activityInfo.targetActivity != null) activityName = activityInfo.targetActivity;
                else activityName = activityInfo.name;
                if (TrackerComponentUtils.isTracker(activityName)) componentList.add(activityName);
            }
        }
        // Add others
        if (mPackageInfo.services != null) {
            for (ComponentInfo componentInfo : mPackageInfo.services)
                if (TrackerComponentUtils.isTracker(componentInfo.name)) componentList.add(componentInfo.name);
        }
        if (mPackageInfo.receivers != null) {
            for (ComponentInfo componentInfo : mPackageInfo.receivers)
                if (TrackerComponentUtils.isTracker(componentInfo.name)) componentList.add(componentInfo.name);
        }
        if (mPackageInfo.providers != null) {
            for (ComponentInfo componentInfo : mPackageInfo.providers)
                if (TrackerComponentUtils.isTracker(componentInfo.name)) componentList.add(componentInfo.name);
        }
        runOnUiThread(() -> {
            mTagCloud.removeAllViews();
            // Add tracker chip
            if (!componentList.isEmpty()) {
                Chip chip = new Chip(mActivity);
                chip.setText(String.format(getString(R.string.no_of_trackers), componentList.size()));
                chip.setChipBackgroundColorResource(R.color.red);
                mTagCloud.addView(chip);
            }
            if ((mApplicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                addChip(R.string.system_app);
                if ((mApplicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
                    addChip(R.string.updated_app);
            } else if (!mainModel.getIsExternalApk()) addChip(R.string.user_app);
            if ((mApplicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                addChip(R.string.debuggable);
            if ((mApplicationInfo.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0)
                addChip(R.string.test_only);
            if ((mApplicationInfo.flags & ApplicationInfo.FLAG_HAS_CODE) == 0)
                addChip(R.string.no_code);
            if ((mApplicationInfo.flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0)
                addChip(R.string.requested_large_heap, R.color.red);
            if ((mApplicationInfo.flags & ApplicationInfo.FLAG_STOPPED) != 0)
                addChip(R.string.stopped, R.color.stopped);
            if (!mApplicationInfo.enabled) addChip(R.string.disabled_app, R.color.disabled_user);
        });
    }

    private void setHorizontalView() {
        mHorizontalLayout.removeAllViews();
        if (!mainModel.getIsExternalApk()) {
            // Set open
            final Intent launchIntentForPackage = mPackageManager.getLaunchIntentForPackage(mPackageName);
            if (launchIntentForPackage != null) {
                addToHorizontalLayout(R.string.launch_app, R.drawable.ic_open_in_new_black_24dp)
                        .setOnClickListener(v -> startActivity(launchIntentForPackage));
            }
            // Set uninstall
            addToHorizontalLayout(R.string.uninstall, R.drawable.ic_delete_black_24dp).setOnClickListener(v -> {
                final boolean isSystemApp = (mApplicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (AppPref.isRootEnabled()) {
                    new MaterialAlertDialogBuilder(mActivity, R.style.AppTheme_AlertDialog)
                            .setTitle(mPackageLabel)
                            .setMessage(isSystemApp ?
                                    R.string.uninstall_system_app_message : R.string.uninstall_app_message)
                            .setPositiveButton(R.string.uninstall, (dialog, which) -> new Thread(() -> {
                                // Try without root first then with root
                                if (RunnerUtils.uninstallPackage(mPackageName).isSuccessful()) {
                                    runOnUiThread(() -> {
                                        Toast.makeText(mActivity, String.format(getString(R.string.uninstalled_successfully), mPackageLabel), Toast.LENGTH_LONG).show();
                                        mActivity.finish();
                                    });
                                } else {
                                    runOnUiThread(() -> Toast.makeText(mActivity, String.format(getString(R.string.failed_to_uninstall), mPackageLabel), Toast.LENGTH_LONG).show());
                                }
                            }).start())
                            .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                                if (dialog != null) dialog.cancel();
                            })
                            .show();
                } else {
                    Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
                    uninstallIntent.setData(Uri.parse("package:" + mPackageName));
                    startActivity(uninstallIntent);
                }
            });
            // Enable/disable app (root only)
            if (AppPref.isRootEnabled() || AppPref.isAdbEnabled()) {
                if (mApplicationInfo.enabled) {
                    // Disable app
                    addToHorizontalLayout(R.string.disable, R.drawable.ic_block_black_24dp).setOnClickListener(v -> new Thread(() -> {
                        if (!RunnerUtils.disablePackage(mPackageName).isSuccessful()) {
                            runOnUiThread(() -> Toast.makeText(mActivity, String.format(getString(R.string.failed_to_disable), mPackageLabel), Toast.LENGTH_LONG).show());
                        }
                    }).start());
                } else {
                    // Enable app
                    addToHorizontalLayout(R.string.enable, R.drawable.ic_baseline_get_app_24).setOnClickListener(v -> new Thread(() -> {
                        if (!RunnerUtils.enablePackage(mPackageName).isSuccessful()) {
                            runOnUiThread(() -> Toast.makeText(mActivity, String.format(getString(R.string.failed_to_enable), mPackageLabel), Toast.LENGTH_LONG).show());
                        }
                    }).start());
                }
                // Force stop
                if ((mApplicationInfo.flags & ApplicationInfo.FLAG_STOPPED) == 0) {
                    addToHorizontalLayout(R.string.force_stop, R.drawable.ic_baseline_power_settings_new_24).setOnClickListener(v -> new Thread(() -> {
                        if (RunnerUtils.forceStopPackage(mPackageName).isSuccessful()) {
                            // Refresh
                            runOnUiThread(() -> mainModel.setIsPackageChanged());
                        } else {
                            runOnUiThread(() -> Toast.makeText(mActivity, String.format(getString(R.string.failed_to_stop), mPackageLabel), Toast.LENGTH_LONG).show());
                        }
                    }).start());
                }
                // Clear data
                addToHorizontalLayout(R.string.clear_data, R.drawable.ic_delete_black_24dp).setOnClickListener(v -> {
                    if (RunnerUtils.clearPackageData(mPackageName).isSuccessful()) {
                        runOnUiThread(() -> mainModel.setIsPackageChanged());
                    }
                });
                // Clear cache
                if (AppPref.isRootEnabled()) {
                    addToHorizontalLayout(R.string.clear_cache, R.drawable.ic_delete_black_24dp).setOnClickListener(v -> {
                        StringBuilder command = new StringBuilder(String.format("rm -rf %s/cache %s/code_cache",
                                mApplicationInfo.dataDir, mApplicationInfo.dataDir));
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            if (!mApplicationInfo.dataDir.equals(mApplicationInfo.deviceProtectedDataDir)) {
                                command.append(String.format(" %s/cache %s/code_cache",
                                        mApplicationInfo.deviceProtectedDataDir, mApplicationInfo.deviceProtectedDataDir));
                            }
                        }
                        File[] cacheDirs = mActivity.getExternalCacheDirs();
                        for (File cacheDir : cacheDirs) {
                            String extCache = cacheDir.getAbsolutePath().replace(mActivity.getPackageName(), mPackageName);
                            command.append(" ").append(extCache);
                        }
                        if (Runner.runCommand(command.toString()).isSuccessful()) {
                            runOnUiThread(() -> mainModel.setIsPackageChanged());
                        }
                    });
                }
            }  // End root only
        } else {
            PackageInfo packageInfo = null;
            try {
                packageInfo = mPackageManager.getPackageInfo(mPackageName, 0);
            } catch (PackageManager.NameNotFoundException ignore) {}
            if (packageInfo == null) {
                // App not installed
                addToHorizontalLayout(R.string.install, R.drawable.ic_baseline_get_app_24)
                        .setOnClickListener(v -> new Thread(() -> {
                    try {
                        File tmpApkSource = IOUtils.getSharableApk(new File(mApplicationInfo.sourceDir));
                        runOnUiThread(() -> {
                            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            intent.setDataAndType(FileProvider.getUriForFile(mActivity,
                                    BuildConfig.APPLICATION_ID + ".provider", tmpApkSource),
                                    MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"));
                            startActivity(intent);
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> Toast.makeText(mActivity, getString(R.string.failed_to_extract_apk_file), Toast.LENGTH_SHORT).show());
                    }
                }).start());
            } else {
                // App is installed
                long installedVersionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
                long thisVersionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? mPackageInfo.getLongVersionCode() : mPackageInfo.versionCode;
                if (installedVersionCode < thisVersionCode) {  // FIXME: Check for signature
                    addToHorizontalLayout(R.string.update, R.drawable.ic_baseline_get_app_24)
                            .setOnClickListener(v -> new Thread(() -> {
                        try {
                            File tmpApkSource = IOUtils.getSharableApk(new File(mApplicationInfo.sourceDir));
                            runOnUiThread(() -> {
                                Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                intent.setDataAndType(FileProvider.getUriForFile(mActivity,
                                        BuildConfig.APPLICATION_ID + ".provider", tmpApkSource),
                                        MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"));
                                startActivity(intent);
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                            runOnUiThread(() -> Toast.makeText(mActivity, getString(R.string.failed_to_extract_apk_file), Toast.LENGTH_SHORT).show());
                        }
                    }).start());
                }
            }
        }
        // Set manifest
        addToHorizontalLayout(R.string.manifest, R.drawable.ic_tune_black_24dp).setOnClickListener(v -> {
            Intent intent = new Intent(mActivity, ManifestViewerActivity.class);
            if (!mainModel.getIsExternalApk()) intent.putExtra(ManifestViewerActivity.EXTRA_PACKAGE_NAME, mPackageName);
            else {
                File file = new File(mApplicationInfo.publicSourceDir);
                intent.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"));
            }
            startActivity(intent);
        });
        // Set exodus
        addToHorizontalLayout(R.string.exodus, R.drawable.ic_frost_classysharkexodus_black_24dp).setOnClickListener(v -> {
            Intent intent = new Intent(mActivity, ClassListingActivity.class);
            File file = new File(mApplicationInfo.publicSourceDir);
            intent.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"));
            startActivity(intent);
        });
        // Root only features
        if (!mainModel.getIsExternalApk() && AppPref.isRootEnabled()) {
            // Shared prefs (root only)
            List<String> sharedPrefs;
            sharedPrefs = getSharedPrefs(mApplicationInfo.dataDir);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                sharedPrefs.addAll(getSharedPrefs(mApplicationInfo.deviceProtectedDataDir));
            }
            if (!sharedPrefs.isEmpty()) {
                CharSequence[] sharedPrefs2 = new CharSequence[sharedPrefs.size()];
                for (int i = 0; i < sharedPrefs.size(); ++i) {
                    sharedPrefs2[i] = new File(sharedPrefs.get(i)).getName();
                }
                addToHorizontalLayout(R.string.shared_prefs, R.drawable.ic_view_list_black_24dp)
                        .setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity, R.style.AppTheme_AlertDialog)
                                .setTitle(R.string.shared_prefs)
                                .setItems(sharedPrefs2, (dialog, which) -> {
                                    Intent intent = new Intent(mActivity, SharedPrefsActivity.class);
                                    intent.putExtra(SharedPrefsActivity.EXTRA_PREF_LOCATION, sharedPrefs.get(which));
                                    intent.putExtra(SharedPrefsActivity.EXTRA_PREF_LABEL, mPackageLabel);
                                    startActivity(intent);
                                })
                                .setNegativeButton(android.R.string.ok, null)
                                .show());
            }
            // Databases (root only)
            List<String> databases;
            databases = getDatabases(mApplicationInfo.dataDir);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                databases.addAll(getDatabases(mApplicationInfo.deviceProtectedDataDir));
            }
            if (!databases.isEmpty()) {
                CharSequence[] databases2 = new CharSequence[databases.size()];
                for (int i = 0; i < databases.size(); ++i) {
                    databases2[i] = databases.get(i);
                }
                addToHorizontalLayout(R.string.databases, R.drawable.ic_assignment_black_24dp)
                        .setOnClickListener(v -> new MaterialAlertDialogBuilder(mActivity, R.style.AppTheme_AlertDialog)
                                .setTitle(R.string.databases)
                                .setItems(databases2, null)  // TODO
                                .setNegativeButton(android.R.string.ok, null)
                                .show());
            }
        }  // End root only features
        // Set F-Droid or Aurora Droid
        try {
            if(!mPackageManager.getApplicationInfo(PACKAGE_NAME_AURORA_DROID, 0).enabled)
                throw new PackageManager.NameNotFoundException();
            addToHorizontalLayout(R.string.aurora, R.drawable.ic_frost_auroradroid_black_24dp)
                    .setOnClickListener(v -> {
                        Intent intent = new Intent();
                        intent.setClassName(PACKAGE_NAME_AURORA_DROID, ACTIVITY_NAME_AURORA_DROID);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra("INTENT_PACKAGE_NAME", mPackageName);
                        try {
                            startActivity(intent);
                        } catch (Exception ignored) {}
                    });
        } catch (PackageManager.NameNotFoundException e) {
            try {
                if(!mPackageManager.getApplicationInfo(PACKAGE_NAME_FDROID, 0).enabled)
                    throw new PackageManager.NameNotFoundException();
                addToHorizontalLayout(R.string.fdroid, R.drawable.ic_frost_fdroid_black_24dp)
                        .setOnClickListener(v -> {
                            Intent intent = new Intent();
                            intent.setClassName(PACKAGE_NAME_FDROID, ACTIVITY_NAME_FDROID);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.putExtra("appid", mPackageName);
                            try {
                                startActivity(intent);
                            } catch (Exception ignored) {}
                        });
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        // Set Aurora Store
        try {
            if(!mPackageManager.getApplicationInfo(PACKAGE_NAME_AURORA_STORE, 0).enabled)
                throw new PackageManager.NameNotFoundException();
            addToHorizontalLayout(R.string.store, R.drawable.ic_frost_aurorastore_black_24dp)
                    .setOnClickListener(v -> {
                        Intent intent = new Intent();
                        intent.setClassName(PACKAGE_NAME_AURORA_STORE, ACTIVITY_NAME_AURORA_STORE);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra("INTENT_PACKAGE_NAME", mPackageName);
                        try {
                            startActivity(intent);
                        } catch (Exception ignored) {}
                    });
        } catch (PackageManager.NameNotFoundException ignored) {}
    }

    private void setPathsAndDirectories() {
        final ListItemCreator creator = new ListItemCreator(mActivity, R.id.layout_paths_and_directories, true);
        // Paths and directories
        creator.addItemWithTitle(getString(R.string.paths_and_directories), true);
        creator.item_title.setTextColor(mAccentColor);
        // Source directory (apk path)
        creator.addItemWithTitleSubtitle(getString(R.string.source_dir), mApplicationInfo.sourceDir, ListItemCreator.SELECTABLE);
        openAsFolderInFM(creator, (new File(mApplicationInfo.sourceDir)).getParent());
        // Public source directory
        if (!mApplicationInfo.publicSourceDir.equals(mApplicationInfo.sourceDir)) {
            creator.addItemWithTitleSubtitle(getString(R.string.public_source_dir), mApplicationInfo.publicSourceDir, ListItemCreator.SELECTABLE);
            openAsFolderInFM(creator, (new File(mApplicationInfo.publicSourceDir)).getParent());
        }
        // Data dir
        creator.addItemWithTitleSubtitle(getString(R.string.data_dir), mApplicationInfo.dataDir, ListItemCreator.SELECTABLE);
        openAsFolderInFM(creator, mApplicationInfo.dataDir);
        // Device-protected data dir
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            creator.addItemWithTitleSubtitle(getString(R.string.dev_protected_data_dir), mApplicationInfo.deviceProtectedDataDir, ListItemCreator.SELECTABLE);
            openAsFolderInFM(creator, mApplicationInfo.deviceProtectedDataDir);
        }
        // External data dirs
        File[] dataDirs = mActivity.getExternalCacheDirs();
        if (dataDirs != null) {
            List<String> extDataDirs = new ArrayList<>();
            for (File dataDir : dataDirs) {
                //noinspection ConstantConditions
                extDataDirs.add((new File(dataDir.getParent())).getParent() + "/" + mPackageName);
            }
            if (extDataDirs.size() == 1) {
                if (new File(extDataDirs.get(0)).exists()) {
                    creator.addItemWithTitleSubtitle(getString(R.string.external_data_dir), extDataDirs.get(0), ListItemCreator.SELECTABLE);
                    openAsFolderInFM(creator, extDataDirs.get(0));
                }
            } else {
                for (int i = 0; i < extDataDirs.size(); ++i) {
                    if (new File(extDataDirs.get(i)).exists()) {
                        creator.addItemWithTitleSubtitle(String.format(getString(R.string.external_multiple_data_dir), i), extDataDirs.get(i), ListItemCreator.SELECTABLE);
                        openAsFolderInFM(creator, extDataDirs.get(i));
                    }
                }
            }
        }
        // Native JNI library dir
        File nativeLib = new File(mApplicationInfo.nativeLibraryDir);
        if (nativeLib.exists()) {
            creator.addItemWithTitleSubtitle(getString(R.string.native_library_dir), mApplicationInfo.nativeLibraryDir, ListItemCreator.SELECTABLE);
            openAsFolderInFM(creator, mApplicationInfo.nativeLibraryDir);
        }
        creator.addDivider();
    }

    private void setMoreInfo() {
        ListItemCreator creator = new ListItemCreator(mActivity, R.id.layout_more_info, true);
        // Set more info
        creator.addItemWithTitle(getString(R.string.more_info), true);
        creator.item_title.setTextColor(mAccentColor);

        // SDK
        final StringBuilder sdk = new StringBuilder();
        sdk.append(getString(R.string.sdk_max)).append(": ").append(mApplicationInfo.targetSdkVersion);
        if (Build.VERSION.SDK_INT > 23)
            sdk.append(", ").append(getString(R.string.sdk_min)).append(": ").append(mApplicationInfo.minSdkVersion);
        creator.addItemWithTitleSubtitle(getString(R.string.sdk), sdk.toString(), ListItemCreator.SELECTABLE);

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

        if(flags.length() != 0) {
            creator.addItemWithTitleSubtitle(getString(R.string.sdk_flags), flags.toString(), ListItemCreator.SELECTABLE);
            creator.item_subtitle.setTypeface(Typeface.MONOSPACE);
        }
        if (isExternalApk) return;

        creator.addItemWithTitleSubtitle(getString(R.string.date_installed), getTime(mPackageInfo.firstInstallTime), ListItemCreator.NO_ACTION);
        creator.addItemWithTitleSubtitle(getString(R.string.date_updated), getTime(mPackageInfo.lastUpdateTime), ListItemCreator.NO_ACTION);
        if(!mPackageName.equals(mApplicationInfo.processName))
            creator.addItemWithTitleSubtitle(getString(R.string.process_name), mApplicationInfo.processName, ListItemCreator.NO_ACTION);
        String installerPackageName = null;
        try {
            installerPackageName = mPackageManager.getInstallerPackageName(mPackageName);
        } catch (IllegalArgumentException ignore) {}
        if (installerPackageName != null) {
            String applicationLabel = mApplicationInfo.loadLabel(mPackageManager).toString();
            creator.addItemWithTitleSubtitle(getString(R.string.installer_app), applicationLabel, ListItemCreator.SELECTABLE);
        }
        creator.addItemWithTitleSubtitle(getString(R.string.user_id), Integer.toString(mApplicationInfo.uid), ListItemCreator.SELECTABLE);
        if (mPackageInfo.sharedUserId != null)
            creator.addItemWithTitleSubtitle(getString(R.string.shared_user_id), mPackageInfo.sharedUserId, ListItemCreator.SELECTABLE);
        // Main activity
        final Intent launchIntentForPackage = mPackageManager.getLaunchIntentForPackage(mPackageName);
        if (launchIntentForPackage != null) {
            final ComponentName launchComponentName = launchIntentForPackage.getComponent();
            if (launchComponentName != null) {
                final String mainActivity = launchIntentForPackage.getComponent().getClassName();
                creator.addItemWithTitleSubtitle(getString(R.string.main_activity), mainActivity, ListItemCreator.SELECTABLE);
                creator.setOpen(view -> startActivity(launchIntentForPackage));
            }
        }
        creator.addDivider();
    }

    private void setDataUsage() {
        try {
            // Net statistics
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if ((Boolean) AppPref.get(AppPref.PrefKey.PREF_USAGE_ACCESS_ENABLED_BOOL)) {
                    final Tuple<Tuple<Long, Long>, Tuple<Long, Long>> dataUsage = AppUsageStatsManager
                            .getWifiMobileUsageForPackage(mActivity, mPackageName,
                                    io.github.muntashirakon.AppManager.usage.Utils.USAGE_LAST_BOOT);
                    runOnUiThread(() -> {
                        ListItemCreator creator = new ListItemCreator(mActivity, R.id.layout_data_usage, true);
                        creator.addItemWithTitle(getString(R.string.netstats_msg), true);
                        creator.item_title.setTextColor(mAccentColor);
                        creator.addInlineItem(getString(R.string.netstats_transmitted), getReadableSize(dataUsage.getFirst().getFirst() + dataUsage.getSecond().getFirst()));
                        creator.addInlineItem(getString(R.string.netstats_received), getReadableSize(dataUsage.getFirst().getSecond() + dataUsage.getSecond().getSecond()));
                        creator.addDivider();
                    });
                }
            } else {
                final Tuple<String, String> uidNetStats = getNetStats(mApplicationInfo.uid);
                runOnUiThread(() -> {
                    ListItemCreator creator = new ListItemCreator(mActivity, R.id.layout_data_usage, true);
                    creator.addItemWithTitle(getString(R.string.netstats_msg), true);
                    creator.item_title.setTextColor(mAccentColor);
                    creator.addInlineItem(getString(R.string.netstats_transmitted), uidNetStats.getFirst());
                    creator.addInlineItem(getString(R.string.netstats_received), uidNetStats.getSecond());
                    creator.addDivider();
                });
            }
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(mActivity, R.string.failed_to_get_data_usage_information, Toast.LENGTH_LONG).show());
            e.printStackTrace();
        }
    }

    private void setVerticalView()  {
        runOnUiThread(this::setMoreInfo);
        if (isExternalApk) return;
        runOnUiThread(this::setPathsAndDirectories);
        setDataUsage();
        // Storage and Cache
        if ((Boolean) AppPref.get(AppPref.PrefKey.PREF_USAGE_ACCESS_ENABLED_BOOL)) setStorageAndCache();
    }

    private List<String> getSharedPrefs(@NonNull String sourceDir) {
        File sharedPath = new File(sourceDir + "/shared_prefs");
        return Runner.runCommand(String.format("ls %s/*.xml", sharedPath.getAbsolutePath())).getOutputAsList();
    }

    private List<String> getDatabases(@NonNull String sourceDir) {
        File sharedPath = new File(sourceDir + "/databases");
        // FIXME: SQLite db doesn't necessarily have .db extension
        return Runner.runCommand(String.format("ls %s/*.db", sharedPath.getAbsolutePath())).getOutputAsList();
    }

    private void openAsFolderInFM(@NonNull ListItemCreator creator, String dir) {
        creator.setOpen(view -> {
            Intent openFile = new Intent(Intent.ACTION_VIEW);
            openFile.setDataAndType(Uri.parse(dir), "resource/folder");
            openFile.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (openFile.resolveActivityInfo(mPackageManager, 0) != null)
                startActivity(openFile);
        });
    }

    private void addChip(@StringRes int resId, @ColorRes int color) {
        Chip chip = new Chip(mActivity);
        chip.setText(resId);
        chip.setChipBackgroundColorResource(color);
        mTagCloud.addView(chip);
    }

    private void addChip(@StringRes int resId) {
        Chip chip = new Chip(mActivity);
        chip.setText(resId);
        mTagCloud.addView(chip);
    }

    @NonNull
    private View addToHorizontalLayout(@StringRes int stringResId, @DrawableRes int iconResId) {
        View view = getLayoutInflater().inflate(R.layout.item_app_info_actions, mHorizontalLayout, false);
        TextView textView = view.findViewById(R.id.item_text);
        textView.setText(stringResId);
        textView.setCompoundDrawablesWithIntrinsicBounds(null, mActivity.getDrawable(iconResId), null, null);
        mHorizontalLayout.addView(view);
        return view;
    }

    /**
     * Load package sizes and update views if success.
     */
    private void setStorageAndCache() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            try {
                Method getPackageSizeInfo = mPackageManager.getClass().getMethod(
                        "getPackageSizeInfo", String.class, IPackageStatsObserver.class);

                getPackageSizeInfo.invoke(mPackageManager, mPackageName, new IPackageStatsObserver.Stub() {
                    @Override
                    public void onGetStatsCompleted(final PackageStats pStats, boolean succeeded) {
                        mActivity.runOnUiThread(() -> {
                            mPackageStats = pStats;
                            setStorageInfo(mPackageStats.codeSize
                                            + mPackageStats.externalCodeSize, mPackageStats.dataSize
                                            + mPackageStats.externalDataSize, mPackageStats.cacheSize
                                            + mPackageStats.externalCacheSize, mPackageStats.externalObbSize,
                                    mPackageStats.externalMediaSize);
                        });
                    }
                });
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        } else {
            if (!Utils.checkUsageStatsPermission(mActivity)) {
                runOnUiThread(() -> new MaterialAlertDialogBuilder(mActivity, R.style.AppTheme_AlertDialog)
                        .setTitle(R.string.grant_usage_access)
                        .setMessage(R.string.grant_usage_acess_message)
                        .setPositiveButton(R.string.go, (dialog, which) -> startActivityForResult(new Intent(
                                Settings.ACTION_USAGE_ACCESS_SETTINGS), 0))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setNeutralButton(R.string.never_ask, (dialog, which) ->
                                AppPref.getInstance().setPref(AppPref.PrefKey.PREF_USAGE_ACCESS_ENABLED_BOOL, false))
                        .setCancelable(false)
                        .show());
                return;
            }
            try {
                StorageStatsManager storageStatsManager = (StorageStatsManager) mActivity.getSystemService(Context.STORAGE_STATS_SERVICE);
                StorageStats storageStats = storageStatsManager.queryStatsForPackage(mApplicationInfo.storageUuid, mPackageName, Process.myUserHandle());
                // TODO: List obb and media size
                long cacheSize = storageStats.getCacheBytes();
                runOnUiThread(() -> setStorageInfo(storageStats.getAppBytes(), storageStats.getDataBytes() - cacheSize, cacheSize, 0, 0));
            } catch (IOException | PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void setStorageInfo(long codeSize, long dataSize, long cacheSize, long obbSize, long mediaSize) {
        ListItemCreator creator = new ListItemCreator(mActivity, R.id.layout_storage_and_cache, true);
        creator.addItemWithTitle(getString(R.string.storage_and_cache), true);
        creator.item_title.setTextColor(mAccentColor);
        // Code size
        creator.addInlineItem(getString(R.string.app_size), getReadableSize(codeSize));
        // Data size
        creator.addInlineItem(getString(R.string.data_size), getReadableSize(dataSize));
        // Cache size
        creator.addInlineItem(getString(R.string.cache_size), getReadableSize(cacheSize));
        // OBB size
        if (obbSize != 0) creator.addInlineItem(getString(R.string.obb_size), getReadableSize(obbSize));
        // Media size
        if (mediaSize != 0) creator.addInlineItem(getString(R.string.media_size), getReadableSize(mediaSize));
        creator.addInlineItem(getString(R.string.total_size), getReadableSize(codeSize
                + dataSize + cacheSize + obbSize + mediaSize));
        creator.addDivider();
    }

    /**
     * Get package info.
     */
    private void getPackageInfo() {
        mProgressIndicator.show();
        new Thread(() -> {
            mPackageName = mainModel.getPackageName();
            mPackageInfo = mainModel.getPackageInfo();
            if (mPackageInfo == null) return;
            mApplicationInfo = mPackageInfo.applicationInfo;
            mPackageLabel = mApplicationInfo.loadLabel(mPackageManager);
            // (Re)load views
            runOnUiThread(this::setHorizontalView);
            setVerticalView();
            setHeaders();
            runOnUiThread(() -> mProgressIndicator.hide());
        }).start();
    }

    /**
     * Get Unix time to formatted time.
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
    private Tuple<String, String> getNetStats(int uid) {
        Tuple<String, String> tuple = new Tuple<>(getReadableSize(0), getReadableSize(0));
        File uidStatsDir = new File(UID_STATS_PATH + uid);

        if (uidStatsDir.exists() && uidStatsDir.isDirectory()) {
            for (File child : Objects.requireNonNull(uidStatsDir.listFiles())) {
                if (child.getName().equals(UID_STATS_TR))
                    tuple.setFirst(getReadableSize(Long.parseLong(Utils.getFileContent(child, "-1"))));
                else if (child.getName().equals(UID_STATS_RC))
                    tuple.setSecond(getReadableSize(Long.parseLong(Utils.getFileContent(child, "-1"))));
            }
        }
        return tuple;
    }

    /**
     * Format sizes (bytes to B, KB, MB etc.).
     * @param size Size in Bytes
     * @return Formatted size
     */
    private String getReadableSize(long size) {
        return Formatter.formatFileSize(mActivity, size);
    }

    private void runOnUiThread(Runnable runnable) {
        mActivity.runOnUiThread(runnable);
    }
}
