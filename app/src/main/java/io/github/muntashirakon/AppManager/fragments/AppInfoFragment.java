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
import android.util.Log;
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
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
    private static final int REQUEST_CODE_INSTALL_PKG  = 815;

    private PackageManager mPackageManager;
    private String mPackageName;
    private PackageInfo mPackageInfo;
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
    private AppInfoRecyclerAdapter adapter;
    // Headers
    private TextView labelView;
    private TextView packageNameView;
    private ImageView iconView;
    private TextView versionView;

    private boolean isExternalApk;
    private boolean isRootEnabled;
    private boolean isAdbEnabled;

    private final List<ListItem> mListItems = new ArrayList<>();

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
        isRootEnabled = AppPref.isRootEnabled();
        isAdbEnabled = AppPref.isAdbEnabled();
        mPackageManager = mActivity.getPackageManager();
        mAccentColor = Utils.getThemeColor(mActivity, android.R.attr.colorAccent);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.pager_app_info, container, false);
        // Swipe refresh
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setColorSchemeColors(mAccentColor);
        mSwipeRefresh.setProgressBackgroundColorSchemeColor(Utils.getThemeColor(mActivity, android.R.attr.colorPrimary));
        mSwipeRefresh.setOnRefreshListener(this);
        // Recycler view
        RecyclerView recyclerView = view.findViewById(android.R.id.list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(mActivity));
        adapter = new AppInfoRecyclerAdapter();
        recyclerView.setAdapter(adapter);
        // Horizontal view
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
        if (requestCode == REQUEST_CODE_BATCH_EXPORT && resultCode == Activity.RESULT_OK) {
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
        } else if (requestCode == REQUEST_CODE_INSTALL_PKG) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(mActivity, String.format(getString(R.string.package_name_is_installed_successfully), mPackageLabel), Toast.LENGTH_SHORT).show();
            } else if (resultCode == Activity.RESULT_FIRST_USER) {
                Toast.makeText(mActivity, String.format(getString(R.string.failed_to_install_package_name), mPackageLabel), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mainModel.getIsPackageChanged().observe(this, isPackageChanged -> {
            //noinspection ConstantConditions
            if (isPackageChanged && mainModel.getIsPackageExist().getValue()) {
                Log.e("AppInfo", "Package Changed");
                getPackageInfo();
            }
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
            if (!componentList.isEmpty())
                addChip(getResources().getQuantityString(R.plurals.no_of_trackers, componentList.size(), componentList.size()), R.color.red);
            if ((mApplicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                addChip(R.string.system_app);
                if ((mApplicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
                    addChip(R.string.updated_app);
            } else if (!mainModel.getIsExternalApk()) addChip(R.string.user_app);
            int countSplits = 0;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                    && mApplicationInfo.splitNames != null) {
                countSplits = mApplicationInfo.splitNames.length;
            }
            if (countSplits > 0) addChip(getResources().getQuantityString(R.plurals.no_of_splits, countSplits, countSplits));
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
            // Set disable
            if (isRootEnabled || isAdbEnabled) {
                if (mApplicationInfo.enabled) {
                    addToHorizontalLayout(R.string.disable, R.drawable.ic_block_black_24dp).setOnClickListener(v -> new Thread(() -> {
                        if (!RunnerUtils.disablePackage(mPackageName).isSuccessful()) {
                            runOnUiThread(() -> Toast.makeText(mActivity, String.format(getString(R.string.failed_to_disable), mPackageLabel), Toast.LENGTH_LONG).show());
                        }
                    }).start());
                }
            }
            // Set uninstall
            addToHorizontalLayout(R.string.uninstall, R.drawable.ic_delete_black_24dp).setOnClickListener(v -> {
                final boolean isSystemApp = (mApplicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (isRootEnabled) {
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
            if (isRootEnabled || isAdbEnabled) {
                if (!mApplicationInfo.enabled) {
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
                if (isRootEnabled) {
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
                            // TODO: Replace with installer session for >= M
                            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
                            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
                            intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, BuildConfig.APPLICATION_ID);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                intent.setDataAndType(FileProvider.getUriForFile(mActivity,
                                        BuildConfig.APPLICATION_ID + ".provider", tmpApkSource),
                                        MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"));
                            } else {
                                intent.setDataAndType(Uri.fromFile(tmpApkSource), MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"));
                            }
                            startActivityForResult(intent, REQUEST_CODE_INSTALL_PKG);
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
                                intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
                                intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
                                intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, BuildConfig.APPLICATION_ID);
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    intent.setDataAndType(FileProvider.getUriForFile(mActivity,
                                            BuildConfig.APPLICATION_ID + ".provider", tmpApkSource),
                                            MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"));
                                } else {
                                    intent.setDataAndType(Uri.fromFile(tmpApkSource), MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"));
                                }
                                startActivityForResult(intent, REQUEST_CODE_INSTALL_PKG);
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
            if (mainModel.getIsExternalApk() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mApplicationInfo.splitNames != null)) {
                File file = new File(mApplicationInfo.publicSourceDir);
                intent.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"));
            } else intent.putExtra(ManifestViewerActivity.EXTRA_PACKAGE_NAME, mPackageName);
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
        if (!mainModel.getIsExternalApk() && isRootEnabled) {
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
        // Paths and directories
        mListItems.add(ListItem.getGroupHeader(getString(R.string.paths_and_directories)));
        // Source directory (apk path)
        mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.source_dir), mApplicationInfo.sourceDir,
                openAsFolderInFM(new File(mApplicationInfo.sourceDir).getParent())));
        // Split source directories
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mApplicationInfo.splitNames != null) {
            int countSplits = mApplicationInfo.splitNames.length;
            for (int i = 0; i<countSplits; ++i) {
                mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.split_no, (i+1),
                        mApplicationInfo.splitNames[i]), mApplicationInfo.splitSourceDirs[i],
                        openAsFolderInFM(new File(mApplicationInfo.splitSourceDirs[i]).getParent())));
            }
        }
        // Public source directory
        if (!mApplicationInfo.publicSourceDir.equals(mApplicationInfo.sourceDir)) {
            mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.public_source_dir),
                    mApplicationInfo.publicSourceDir, openAsFolderInFM((new File(
                            mApplicationInfo.publicSourceDir)).getParent())));
        }
        // Public split source directories
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mApplicationInfo.splitNames != null) {
            int countSplits = mApplicationInfo.splitNames.length;
            for (int i = 0; i<countSplits; ++i) {
                if (!mApplicationInfo.splitPublicSourceDirs[i].equals(mApplicationInfo.splitSourceDirs[i])) {
                    mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.public_split_no,
                            (i + 1), mApplicationInfo.splitNames[i]), mApplicationInfo.splitPublicSourceDirs[i],
                            openAsFolderInFM(new File(mApplicationInfo.splitPublicSourceDirs[i]).getParent())));
                }
            }
        }
        // Data dir
        mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.data_dir), mApplicationInfo.dataDir, openAsFolderInFM(mApplicationInfo.dataDir)));
        // Device-protected data dir
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mListItems.add(ListItem.getSelectableRegularItem(getString(R.string.dev_protected_data_dir),
                    mApplicationInfo.deviceProtectedDataDir, openAsFolderInFM(mApplicationInfo.deviceProtectedDataDir)));
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

    private void setMoreInfo() {
        // Set more info
        mListItems.add(ListItem.getGroupHeader(getString(R.string.more_info)));

        // SDK
        final StringBuilder sdk = new StringBuilder();
        sdk.append(getString(R.string.sdk_max)).append(": ").append(mApplicationInfo.targetSdkVersion);
        if (Build.VERSION.SDK_INT > 23)
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

        if(flags.length() != 0) {
            ListItem flagsItem = ListItem.getSelectableRegularItem(getString(R.string.sdk_flags), flags.toString());
            flagsItem.flags |= LIST_ITEM_FLAG_MONOSPACE;
            mListItems.add(flagsItem);
        }
        if (isExternalApk) return;

        mListItems.add(ListItem.getRegularItem(getString(R.string.date_installed), getTime(mPackageInfo.firstInstallTime)));
        mListItems.add(ListItem.getRegularItem(getString(R.string.date_updated), getTime(mPackageInfo.lastUpdateTime)));
        if(!mPackageName.equals(mApplicationInfo.processName))
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
        } catch (IllegalArgumentException ignore) {}
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

    private void setDataUsage() {
        try {
            // Net statistics
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if ((Boolean) AppPref.get(AppPref.PrefKey.PREF_USAGE_ACCESS_ENABLED_BOOL)) {
                    try {
                        final Tuple<Tuple<Long, Long>, Tuple<Long, Long>> dataUsage = AppUsageStatsManager
                                .getWifiMobileUsageForPackage(mActivity, mPackageName,
                                        io.github.muntashirakon.AppManager.usage.Utils.USAGE_LAST_BOOT);
                        setDataUsageHelper(getReadableSize(dataUsage.getFirst().getFirst() + dataUsage.getSecond().getFirst()),
                                getReadableSize(dataUsage.getFirst().getSecond() + dataUsage.getSecond().getSecond()));
                    } catch (SecurityException e) {
                        runOnUiThread(() -> Toast.makeText(mActivity, R.string.failed_to_get_data_usage_information, Toast.LENGTH_SHORT).show());
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else {
                final Tuple<String, String> uidNetStats = getNetStats(mApplicationInfo.uid);
                setDataUsageHelper(uidNetStats.getFirst(), uidNetStats.getSecond());
            }
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(mActivity, R.string.failed_to_get_data_usage_information, Toast.LENGTH_LONG).show());
            e.printStackTrace();
        }
    }

    private void setDataUsageHelper(String txData, String rxData) {
        mListItems.add(ListItem.getGroupHeader(getString(R.string.data_usage_msg)));
        mListItems.add(ListItem.getInlineItem(getString(R.string.data_transmitted), txData));
        mListItems.add(ListItem.getInlineItem(getString(R.string.data_received), rxData));
        mListItems.add(ListItem.getGroupDivider());
    }

    private void setVerticalView()  {
        synchronized (mListItems) {
            mListItems.clear();
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

    private List<String> getSharedPrefs(@NonNull String sourceDir) {
        File sharedPath = new File(sourceDir + "/shared_prefs");
        return Runner.runCommand(String.format("ls %s/*.xml", sharedPath.getAbsolutePath())).getOutputAsList();
    }

    private List<String> getDatabases(@NonNull String sourceDir) {
        File sharedPath = new File(sourceDir + "/databases");
        // FIXME: SQLite db doesn't necessarily have .db extension
        return Runner.runCommand(String.format("ls %s/*.db", sharedPath.getAbsolutePath())).getOutputAsList();
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

    private void addChip(@StringRes int resId, @ColorRes int color) {
        Chip chip = new Chip(mActivity);
        chip.setText(resId);
        chip.setChipBackgroundColorResource(color);
        mTagCloud.addView(chip);
    }

    private void addChip(CharSequence text, @ColorRes int color) {
        Chip chip = new Chip(mActivity);
        chip.setText(text);
        chip.setChipBackgroundColorResource(color);
        mTagCloud.addView(chip);
    }

    private void addChip(@StringRes int resId) {
        Chip chip = new Chip(mActivity);
        chip.setText(resId);
        mTagCloud.addView(chip);
    }

    private void addChip(CharSequence text) {
        Chip chip = new Chip(mActivity);
        chip.setText(text);
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
                setStorageInfo(storageStats.getAppBytes(), storageStats.getDataBytes() - cacheSize, cacheSize, 0, 0);
            } catch (IOException | PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void setStorageInfo(long codeSize, long dataSize, long cacheSize, long obbSize, long mediaSize) {
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
            setHeaders();
            runOnUiThread(this::setHorizontalView);
            setVerticalView();
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

    @IntDef(value = {
            LIST_ITEM_GROUP_BEGIN,
            LIST_ITEM_GROUP_END,
            LIST_ITEM_REGULAR,
            LIST_ITEM_INLINE
    })
    private @interface ListItemType {}
    private static final int LIST_ITEM_GROUP_BEGIN = 0;  // Group header
    private static final int LIST_ITEM_GROUP_END = 1;  // Group divider
    private static final int LIST_ITEM_REGULAR = 2;
    private static final int LIST_ITEM_INLINE = 3;

    @IntDef(flag = true, value = {
            LIST_ITEM_FLAG_SELECTABLE,
            LIST_ITEM_FLAG_MONOSPACE
    })
    private @interface ListItemFlag {}
    private static final int LIST_ITEM_FLAG_SELECTABLE = 1;
    private static final int LIST_ITEM_FLAG_MONOSPACE = 1 << 1;

    static class ListItem {
        @ListItemType int type;
        @ListItemFlag int flags = 0;
        String title;
        String subtitle;
        @DrawableRes int icon = 0;
        @DrawableRes int actionIcon = 0;
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
        public @ListItemType int getItemViewType(int position) {
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
                    if ((listItem.flags & AppInfoFragment.LIST_ITEM_FLAG_SELECTABLE) != 0)
                        holder.subtitle.setTextIsSelectable(true);
                    else holder.subtitle.setTextIsSelectable(false);
                    if ((listItem.flags & AppInfoFragment.LIST_ITEM_FLAG_MONOSPACE) != 0)
                        holder.subtitle.setTypeface(Typeface.MONOSPACE);
                    else holder.subtitle.setTypeface(Typeface.DEFAULT);
                    break;
                case AppInfoFragment.LIST_ITEM_REGULAR:
                    holder.title.setText(listItem.title);
                    holder.subtitle.setText(listItem.subtitle);
                    if ((listItem.flags & AppInfoFragment.LIST_ITEM_FLAG_SELECTABLE) != 0)
                        holder.subtitle.setTextIsSelectable(true);
                    else holder.subtitle.setTextIsSelectable(false);
                    if ((listItem.flags & AppInfoFragment.LIST_ITEM_FLAG_MONOSPACE) != 0)
                        holder.subtitle.setTypeface(Typeface.MONOSPACE);
                    else holder.subtitle.setTypeface(Typeface.DEFAULT);
                    // FIXME: Load icon in background
                    if (listItem.icon != 0) holder.icon.setImageResource(listItem.icon);
                    // FIXME: Load action icon in background
                    if (listItem.actionIcon != 0) holder.actionIcon.setImageResource(listItem.actionIcon);
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
