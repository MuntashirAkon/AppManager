package io.github.muntashirakon.AppManager.activities;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.FileUtils;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.classysharkandroid.utils.IOUtils;
import com.jaredrummler.android.shell.Shell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.ListItemCreator;
import io.github.muntashirakon.AppManager.utils.Tuple;
import io.github.muntashirakon.AppManager.utils.Utils;

import static io.github.muntashirakon.AppManager.utils.IOUtils.deleteDir;

public class AppInfoActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {
    public static final String EXTRA_PACKAGE_NAME = "pkg";

    private static final String UID_STATS_PATH = "/proc/uid_stat/";
    private static final String UID_STATS_TR = "tcp_rcv";
    private static final String UID_STATS_RC = "tcp_snd";

    private static final String PACKAGE_NAME_FDROID = "org.fdroid.fdroid";
    private static final String PACKAGE_NAME_AURORA_DROID = "com.aurora.adroid";

    private PackageManager mPackageManager;
    private String mPackageName;
    private PackageInfo mPackageInfo;
    private PackageStats mPackageStats;
    private final AppCompatActivity mActivity = this;
    private ApplicationInfo mApplicationInfo;
    private LinearLayout mHorizontalLayout;
    private ChipGroup mTagCloud;

    @SuppressLint("SimpleDateFormat")
    private SimpleDateFormat mDateFormatter = new SimpleDateFormat("EE LLL dd yyyy kk:mm:ss");
    private ListItemCreator mList;
    private SwipeRefreshLayout mSwipeRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_info);
        mPackageName = getIntent().getStringExtra(AppInfoActivity.EXTRA_PACKAGE_NAME);
        if (mPackageName == null) {
            Toast.makeText(this, getString(R.string.empty_package_name), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        mSwipeRefresh = findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setOnRefreshListener(this);
        mPackageManager = getPackageManager();
        mHorizontalLayout = findViewById(R.id.horizontal_layout);
        mTagCloud = findViewById(R.id.tag_cloud);
        getPackageInfoOrFinish(mPackageName);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_refresh_detail:
                Toast.makeText(this, getString(R.string.refresh), Toast.LENGTH_SHORT).show();
                getPackageInfoOrFinish(mPackageName);
                return true;
            case R.id.action_share_apk:
                try {
                    File apkSource = new File(mApplicationInfo.sourceDir);
                    File tmpApkSource = File.createTempFile(mApplicationInfo.packageName, ".apk", getExternalCacheDir());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        FileUtils.copy(new FileInputStream(apkSource), new FileOutputStream(tmpApkSource));
                    } else {
                        IOUtils.copy(new FileInputStream(apkSource), new FileOutputStream(tmpApkSource));
                    }
                    Intent intent = ShareCompat.IntentBuilder.from(this)
                            .setStream(FileProvider.getUriForFile(
                                    this, BuildConfig.APPLICATION_ID + ".provider", tmpApkSource))
                            .setType("application/vnd.android.package-archive")
                            .getIntent()
                            .setAction(Intent.ACTION_SEND)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, getString(R.string.failed_to_extract_apk_file), Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
                return true;
            case R.id.action_show_details:
                Intent appDetailsIntent = new Intent(this, AppDetailsActivity.class);
                appDetailsIntent.putExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME, mPackageName);
                startActivity(appDetailsIntent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            File dir = getExternalCacheDir();
            deleteDir(dir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_app_info_actions, menu);
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onRefresh() {
        getPackageInfoOrFinish(mPackageName);
        mSwipeRefresh.setRefreshing(false);
    }

    /**
     * Set views up to details_container.
     */
    private void setHeaderView() {
        int accentColor = Utils.getThemeColor(this, android.R.attr.colorAccent);

        // Set Application Name, aka Label
        TextView labelView = findViewById(R.id.label);
        CharSequence label = mApplicationInfo.loadLabel(mPackageManager);
        labelView.setText(label);

        // Set Package Name
        TextView packageNameView = findViewById(R.id.packageName);
        packageNameView.setText(mPackageName);

        // Set App Icon
        ImageView iconView = findViewById(R.id.icon);
        iconView.setImageDrawable(mApplicationInfo.loadIcon(mPackageManager));

        // Set App Version
        TextView versionView = findViewById(R.id.version);
        versionView.setText(String.format(getString(R.string.version), mPackageInfo.versionName,
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ?
                            mPackageInfo.getLongVersionCode() : mPackageInfo.versionCode)));

        // Tag cloud //
        mTagCloud.removeAllViews();
        if ((mApplicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
            addChip(R.string.system_app);
            if ((mApplicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
                addChip(R.string.updated_app);
        } else addChip(R.string.user_app);
        if ((mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            addChip(R.string.debuggable);
        if ((mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0)
            addChip(R.string.test_only);
        if ((mApplicationInfo.flags & ApplicationInfo.FLAG_HAS_CODE) == 0)
            addChip(R.string.no_code);
        if ((mApplicationInfo.flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0)
            addChip(R.string.requested_large_heap, Color.RED);

        // Horizontal layout //
        mHorizontalLayout.removeAllViews();
        // Set uninstall
        addToHorizontalLayout(R.string.uninstall, R.drawable.ic_delete_black_24dp).setOnClickListener(v -> {
                Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
                uninstallIntent.setData(Uri.parse("package:" + mPackageName));
                startActivity(uninstallIntent);
        });
        // Set app info (open in settings)
        addToHorizontalLayout(R.string.app_info, R.drawable.ic_info_outline_black_24dp).setOnClickListener(v -> {
            Intent infoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            infoIntent.addCategory(Intent.CATEGORY_DEFAULT);
            infoIntent.setData(Uri.parse("package:" + mPackageName));
            startActivity(infoIntent);
        });
        // Set manifest
        addToHorizontalLayout(R.string.manifest, R.drawable.ic_tune_black_24dp).setOnClickListener(v -> {
            Intent intent = new Intent(mActivity, ManifestViewerActivity.class);
            intent.putExtra(ManifestViewerActivity.EXTRA_PACKAGE_NAME, mPackageName);
            startActivity(intent);
        });
        // Set exodus
        addToHorizontalLayout(R.string.exodus, R.drawable.ic_frost_classysharkexodus_black_24dp).setOnClickListener(v -> {
            try {
                Intent newIntent = new Intent(AppInfoActivity.this, ClassListingActivity.class);
                File file = new File(mPackageManager.getPackageInfo(mPackageName, 0).applicationInfo.publicSourceDir);
                newIntent.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"));
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                newIntent.putExtra(ClassListingActivity.EXTRA_PACKAGE_NAME, mPackageName);
                startActivity(newIntent);
            } catch (PackageManager.NameNotFoundException e) {
                Toast.makeText(mActivity, e.toString(), Toast.LENGTH_LONG).show();
            }
        });
        // Shared prefs
        List<String> sharedPrefs;
        sharedPrefs = getSharedPrefs(mApplicationInfo.dataDir);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sharedPrefs.addAll(getSharedPrefs(mApplicationInfo.deviceProtectedDataDir));
        }
        if (!sharedPrefs.isEmpty()) {
            CharSequence[] sharedPrefs2 = new CharSequence[sharedPrefs.size()];
            for (int i = 0; i<sharedPrefs.size(); ++i) {
                // TODO: Strip directory names
                sharedPrefs2[i] = sharedPrefs.get(i);
            }
            addToHorizontalLayout(R.string.shared_prefs, R.drawable.ic_view_list_black_24dp)
                .setOnClickListener(v -> new AlertDialog.Builder(this, R.style.CustomDialog)
                    .setTitle(R.string.shared_prefs)
                    .setItems(sharedPrefs2, (dialog, which) -> {
                        Intent intent = new Intent(this, SharedPrefsActivity.class);
                        intent.putExtra(SharedPrefsActivity.EXTRA_PREF_LOCATION, sharedPrefs.get(which));
                        intent.putExtra(SharedPrefsActivity.EXTRA_PREF_LABEL, label);
                        startActivity(intent);
                    })
                    .setNegativeButton(android.R.string.ok, null)
                    .show());
        }
        // Databases
        List<String> databases;
        databases = getDatabases(mApplicationInfo.dataDir);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            databases.addAll(getDatabases(mApplicationInfo.deviceProtectedDataDir));
        }
        if (!databases.isEmpty()) {
            CharSequence[] databases2 = new CharSequence[databases.size()];
            for (int i = 0; i<databases.size(); ++i) {
                databases2[i] = databases.get(i);
            }
            addToHorizontalLayout(R.string.databases, R.drawable.ic_assignment_black_24dp).setOnClickListener(v -> {
                new AlertDialog.Builder(this, R.style.CustomDialog)
                        .setTitle(R.string.databases)
                        .setItems(databases2, null)  // TODO
                        .setNegativeButton(android.R.string.ok, null)
                        .show();
            });
        }
        // Set F-Droid or Aurora Droid
        try {
            if(!mPackageManager.getApplicationInfo(PACKAGE_NAME_FDROID, 0).enabled)
                throw new PackageManager.NameNotFoundException();
            addToHorizontalLayout(R.string.fdroid, R.drawable.ic_frost_fdroid_black_24dp)
                    .setOnClickListener(v -> {
                Intent intent = new Intent();
                intent.setClassName(PACKAGE_NAME_FDROID, "org.fdroid.fdroid.views.AppDetailsActivity");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("appid", mPackageName);
                try {
                    startActivity(intent);
                } catch (Exception ignored) {}
            });
        } catch (PackageManager.NameNotFoundException e) {
            try {
                if(!mPackageManager.getApplicationInfo(PACKAGE_NAME_AURORA_DROID, 0).enabled)
                    throw new PackageManager.NameNotFoundException();
                addToHorizontalLayout(R.string.aurora, R.drawable.ic_frost_auroradroid_black_24dp)
                        .setOnClickListener(v -> {
                            Intent intent = new Intent();
                            intent.setClassName(PACKAGE_NAME_AURORA_DROID, "com.aurora.adroid.ui.activity.DetailsActivity");
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.putExtra("INTENT_PACKAGE_NAME", mPackageName);
                            try {
                                startActivity(intent);
                            } catch (Exception ignored) {}
                        });
            } catch (PackageManager.NameNotFoundException ignored) {}
        }

        // Vertical layout //
        // Paths
        mList.addMenuItemWithIconTitleSubtitle(getString(R.string.paths_and_directories), true);
        mList.item_title.setTextColor(accentColor);
        // Source directory (apk path)
        mList.addMenuItemWithIconTitleSubtitle(getString(R.string.source_dir), mApplicationInfo.sourceDir, ListItemCreator.SELECTABLE);
        openAsFolderInFM((new File(mApplicationInfo.sourceDir)).getParent());
        // Public source directory
        if (!mApplicationInfo.publicSourceDir.equals(mApplicationInfo.sourceDir)) {
            mList.addMenuItemWithIconTitleSubtitle(getString(R.string.public_source_dir), mApplicationInfo.publicSourceDir, ListItemCreator.SELECTABLE);
            openAsFolderInFM((new File(mApplicationInfo.publicSourceDir)).getParent());
        }
        // Data dir
        mList.addMenuItemWithIconTitleSubtitle(getString(R.string.data_dir), mApplicationInfo.dataDir, ListItemCreator.SELECTABLE);
        openAsFolderInFM(mApplicationInfo.dataDir);
        // Device-protected data dir
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mList.addMenuItemWithIconTitleSubtitle(getString(R.string.dev_protected_data_dir), mApplicationInfo.deviceProtectedDataDir, ListItemCreator.NO_ACTION);
            openAsFolderInFM(mApplicationInfo.deviceProtectedDataDir);
        }
        // Native JNI library dir
        File nativeLib = new File(mApplicationInfo.nativeLibraryDir);
        if (nativeLib.exists()) {
            mList.addMenuItemWithIconTitleSubtitle(getString(R.string.native_library_dir), mApplicationInfo.nativeLibraryDir, ListItemCreator.NO_ACTION);
            openAsFolderInFM(mApplicationInfo.nativeLibraryDir);
        }
        mList.addDivider();

        // SDK
        mList.addMenuItemWithIconTitleSubtitle(getString(R.string.sdk), true);
        mList.item_title.setTextColor(accentColor);
        mList.addMenuItemWithIconTitleSubtitle(getString(R.string.sdk_max), "" + mApplicationInfo.targetSdkVersion, ListItemCreator.SELECTABLE);
        mList.addMenuItemWithIconTitleSubtitle(getString(R.string.sdk_min), Build.VERSION.SDK_INT > 23 ? "" + mApplicationInfo.minSdkVersion : "N/A", ListItemCreator.SELECTABLE);

        // Set Flags
        String flags = "";
        if ((mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            flags += "FLAG_DEBUGGABLE";
        if ((mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0)
            flags += (flags.length() == 0 ? "" : "|" ) + "FLAG_TEST_ONLY";
        if ((mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_MULTIARCH) != 0)
            flags += (flags.length() == 0 ? "" : "|" ) + "FLAG_MULTIARCH";
        if ((mPackageInfo.applicationInfo.flags & ApplicationInfo.FLAG_HARDWARE_ACCELERATED) == 0)
            flags += (flags.length() == 0 ? "" : "|" ) + "FLAG_HARDWARE_ACCELERATED";

        if(flags.length() != 0) {
            mList.addMenuItemWithIconTitleSubtitle(getString(R.string.sdk_flags), flags, ListItemCreator.SELECTABLE);
            mList.item_subtitle.setTypeface(Typeface.MONOSPACE);
        }
        mList.addDivider();

        mList.addMenuItemWithIconTitleSubtitle(getString(R.string.more_info), true);
        mList.item_title.setTextColor(accentColor);
        mList.addMenuItemWithIconTitleSubtitle(getString(R.string.date_installed), getTime(mPackageInfo.firstInstallTime), ListItemCreator.NO_ACTION);
        mList.addMenuItemWithIconTitleSubtitle(getString(R.string.date_updated), getTime(mPackageInfo.lastUpdateTime), ListItemCreator.NO_ACTION);
        if(!mPackageName.equals(mApplicationInfo.processName))
            mList.addMenuItemWithIconTitleSubtitle(getString(R.string.process_name), mApplicationInfo.processName, ListItemCreator.NO_ACTION);
        String installerPackageName = mPackageManager.getInstallerPackageName(mPackageName);
        if (installerPackageName != null) {
            String applicationLabel;
            try {
                applicationLabel = getPackageManager().getApplicationInfo(installerPackageName, 0).loadLabel(getPackageManager()).toString();
            } catch (PackageManager.NameNotFoundException e) {
                applicationLabel = installerPackageName;
            }
            mList.addMenuItemWithIconTitleSubtitle(getString(R.string.installer_app), applicationLabel, ListItemCreator.SELECTABLE);
        }
        mList.addMenuItemWithIconTitleSubtitle(getString(R.string.user_id), Integer.toString(mApplicationInfo.uid), ListItemCreator.SELECTABLE);
        if (mPackageInfo.sharedUserId != null)
            mList.addMenuItemWithIconTitleSubtitle(getString(R.string.shared_user_id), mPackageInfo.sharedUserId, ListItemCreator.SELECTABLE);
        // Main activity
        final Intent launchIntentForPackage = mPackageManager.getLaunchIntentForPackage(mPackageName);
        if (launchIntentForPackage != null) {
            final ComponentName launchComponentName = launchIntentForPackage.getComponent();
            if (launchComponentName != null) {
                final String mainActivity = launchIntentForPackage.getComponent().getClassName();
                mList.addMenuItemWithIconTitleSubtitle(getString(R.string.main_activity), mainActivity, ListItemCreator.SELECTABLE);
                mList.setOpen(view -> startActivity(launchIntentForPackage));
            }
        }
        mList.addDivider();

        // Netstat
        mList.addMenuItemWithIconTitleSubtitle(getString(R.string.netstats_msg), true);
        mList.item_title.setTextColor(accentColor);

        Tuple<String, String> uidNetStats = getNetStats(mApplicationInfo.uid);
        mList.addMenuItemWithIconTitleSubtitle(getString(R.string.netstats_transmitted), uidNetStats.getFirst(), ListItemCreator.SELECTABLE);
        mList.addMenuItemWithIconTitleSubtitle(getString(R.string.netstats_received), uidNetStats.getSecond(), ListItemCreator.SELECTABLE);
        mList.addDivider();
    }

    private List<String> getSharedPrefs(@NonNull String sourceDir) {
        File sharedPath = new File(sourceDir + "/shared_prefs");
        return Shell.SU.run(String.format("ls %s/*.xml", sharedPath.getAbsolutePath())).stdout;
    }

    private List<String> getDatabases(@NonNull String sourceDir) {
        File sharedPath = new File(sourceDir + "/databases");
        // FIXME: SQLite db doesn't necessarily have .db extension
        return Shell.SU.run(String.format("ls %s/*.db", sharedPath.getAbsolutePath())).stdout;
    }

    private void openAsFolderInFM(String dir) {
        mList.setOpen(view -> {
            Intent openFile = new Intent(Intent.ACTION_VIEW);
            openFile.setDataAndType(Uri.parse(dir), "resource/folder");
            if (openFile.resolveActivityInfo(mPackageManager, 0) != null)
                startActivity(openFile);
        });
    }

    private void addChip(@StringRes int resId, int color) {
        Chip chip = new Chip(this);
        chip.setText(resId);
        chip.setTextColor(color);
        mTagCloud.addView(chip);
    }

    private void addChip(@StringRes int resId) {
        Chip chip = new Chip(this);
        chip.setText(resId);
        mTagCloud.addView(chip);
    }

    @NonNull
    private View addToHorizontalLayout(@StringRes int stringResId, @DrawableRes int iconResId) {
        View view = getLayoutInflater().inflate(R.layout.item_app_info_actions, mHorizontalLayout, false);
        TextView textView = view.findViewById(R.id.item_text);
        textView.setText(stringResId);
        textView.setCompoundDrawablesWithIntrinsicBounds(null, getDrawable(iconResId), null, null);
        mHorizontalLayout.addView(view);
        return view;
    }

    /**
     * Load package sizes and update views if success.
     */
    private void getPackageSizeInfo() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            try {
                @SuppressWarnings("JavaReflectionMemberAccess")
                Method getPackageSizeInfo = PackageManager.class.getMethod(
                        "getPackageSizeInfo", String.class, IPackageStatsObserver.class);

                getPackageSizeInfo.invoke(mPackageManager, mPackageName, new IPackageStatsObserver.Stub() {
                    @Override
                    public void onGetStatsCompleted(final PackageStats pStats, boolean succeeded) {
                        mActivity.runOnUiThread(() -> {
                            mPackageStats = pStats;
                            onPackageStatsLoaded();
                        });
                    }
                });
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    private void onPackageStatsLoaded() {
        if (mPackageStats == null)
            return;

        TextView sizeCodeView = findViewById(R.id.size_code);
        sizeCodeView.setText(getReadableSize(mPackageStats.codeSize));

        TextView sizeCacheView = findViewById(R.id.size_cache);
        sizeCacheView.setText(getReadableSize(mPackageStats.cacheSize));

        TextView sizeDataView = findViewById(R.id.size_data);
        sizeDataView.setText(getReadableSize(mPackageStats.dataSize));

        TextView sizeExtCodeView = findViewById(R.id.size_ext_code);
        sizeExtCodeView.setText(getReadableSize(mPackageStats.externalCodeSize));

        TextView sizeExtCacheView = findViewById(R.id.size_ext_cache);
        sizeExtCacheView.setText(getReadableSize(mPackageStats.externalCacheSize));

        TextView sizeExtDataView = findViewById(R.id.size_ext_data);
        sizeExtDataView.setText(getReadableSize(mPackageStats.externalDataSize));

        TextView sizeObb = findViewById(R.id.size_ext_obb);
        sizeObb.setText(getReadableSize(mPackageStats.externalObbSize));

        TextView sizeMedia = findViewById(R.id.size_ext_media);
        sizeMedia.setText(getReadableSize(mPackageStats.externalMediaSize));
    }

    /**
     * Get package info.
     * @param packageName Package name (e.g. com.android.wallpaper)
     */
    @SuppressLint("PackageManagerGetSignatures")
    private void getPackageInfoOrFinish(String packageName) {
        try {
            final int signingCertFlag;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                signingCertFlag = PackageManager.GET_SIGNING_CERTIFICATES;
            } else {
                signingCertFlag = PackageManager.GET_SIGNATURES;
            }
            mPackageInfo = mPackageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS
                    | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                    | PackageManager.GET_SERVICES | PackageManager.GET_URI_PERMISSION_PATTERNS
                    | signingCertFlag | PackageManager.GET_CONFIGURATIONS | PackageManager.GET_SHARED_LIBRARY_FILES);

            if (mPackageInfo == null) {
                Toast.makeText(this, mPackageName + ": " + getString(R.string.app_not_installed), Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            mApplicationInfo = mPackageInfo.applicationInfo;

            // ListItemCreator instance
            mList = new ListItemCreator(this, R.id.details_container, true);
            // Load headers
            setHeaderView();
            // Load package size info
            getPackageSizeInfo();
        } catch (PackageManager.NameNotFoundException e) {
            finish();
        }
    }

    /**
     * Get Unix time to formatted time.
     * @param time Unix time
     * @return Formatted time
     */
    public String getTime(long time) {
        Date date = new Date(time);
        return mDateFormatter.format(date);
    }

    /**
     * Get network stats.
     * TODO: Doesn't work in newer Android versions
     * @param uid Application UID
     * @return A tuple consisting of transmitted and received data
     */
    private Tuple<String, String> getNetStats(int uid) {
        Tuple<String, String> tuple = new Tuple<>(getReadableSize(0), getReadableSize(0));
        File uidStatsDir = new File(UID_STATS_PATH + uid);

        if (uidStatsDir.exists() && uidStatsDir.isDirectory()) {
            for (File child : Objects.requireNonNull(uidStatsDir.listFiles())) {
                if (child.getName().equals(UID_STATS_TR))
                    tuple.setFirst(getReadableSize(Long.parseLong(Utils.getFileContent(child))));
                else if (child.getName().equals(UID_STATS_RC))
                    tuple.setSecond(getReadableSize(Long.parseLong(Utils.getFileContent(child))));
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
        return Formatter.formatFileSize(this, size);
    }
}
