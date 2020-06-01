package io.github.muntashirakon.AppManager.activities;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

import androidx.appcompat.app.AppCompatActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.MenuItemCreator;
import io.github.muntashirakon.AppManager.utils.Tuple;
import io.github.muntashirakon.AppManager.utils.Utils;

public class AppInfoActivity extends AppCompatActivity {
    public static final String EXTRA_PACKAGE_NAME = "pkg";

    private static final String UID_STATS_PATH = "/proc/uid_stat/";
    private static final String UID_STATS_TR = "tcp_rcv";
    private static final String UID_STATS_RC = "tcp_snd";

    private PackageManager mPackageManager;
    private String mPackageName;
    private PackageInfo mPackageInfo;
    private PackageStats mPackageStats;
    private String mMainActivity = "";
    private final AppCompatActivity mActivity = this;

    @SuppressLint("SimpleDateFormat")
    private SimpleDateFormat mDateFormatter = new SimpleDateFormat("EE LLL dd yyyy kk:mm:ss");
    private MenuItemCreator mMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setContentView(R.layout.activity_app_info);
        mPackageName = getIntent().getStringExtra(AppInfoActivity.EXTRA_PACKAGE_NAME);
        if (mPackageName == null) {
            Toast.makeText(this, getString(R.string.empty_package_name), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mPackageManager = getPackageManager();
        mPackageInfo = getPackageInfo(mPackageName);
        if (mPackageInfo == null) {
            Toast.makeText(this, mPackageName + ": " + getString(R.string.app_not_installed), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (mPackageInfo.activities != null) {
            mMainActivity = mPackageInfo.activities[0].name;
        }

        // MenuItemCreator instance
        mMenu = new MenuItemCreator(this, R.id.details_container);
        // Load headers
        setHeaderView();
        // Load package size info
        getPackageSizeInfo();
        // FIXME: Set click events by generating layouts dynamically
        setClickEvents();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_refresh_detail) {
            Toast.makeText(this, getString(R.string.refresh), Toast.LENGTH_SHORT).show();
            this.recreate();
            return true;
        } else if (id == R.id.action_show_details) {
            Intent appDetailsIntent = new Intent(this, AppDetailsActivity.class);
            appDetailsIntent.putExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME, mPackageName);
            startActivity(appDetailsIntent);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_app_info_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Set views up to details_container.
     */
    @SuppressLint("SetTextI18n")
    private void setHeaderView() {
        ApplicationInfo applicationInfo = mPackageInfo.applicationInfo;

        int accentColor = mMenu.getSystemColor(android.R.attr.colorActivatedHighlight);

        // Set Application Name, aka Label
        TextView labelView = findViewById(R.id.label);
        CharSequence label = applicationInfo.loadLabel(mPackageManager);
        labelView.setText(label);

        // Set Package Name
        TextView packageNameView = findViewById(R.id.packageName);
        packageNameView.setText(mPackageName);

        // Set App Icon
        ImageView iconView = findViewById(R.id.icon);
        iconView.setImageDrawable(applicationInfo.loadIcon(mPackageManager));

        // Set App Version
        TextView versionView = findViewById(R.id.version);
        versionView.setText(getString(R.string.version) + " " + mPackageInfo.versionName + " (" + mPackageInfo.versionCode + ")");

        // Set App Type: User or System, if System find out whether it's updated
        TextView isSystemAppView = findViewById(R.id.isSystem);
        if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0){
            if ((applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) isSystemAppView.setText(R.string.system_app_updated);
            else isSystemAppView.setText(R.string.system_app);
        } else isSystemAppView.setText(R.string.user_app);
        // FIXME: Fix these things
        if ((applicationInfo.flags & ApplicationInfo.FLAG_HAS_CODE) == 0)isSystemAppView.setText(isSystemAppView.getText()+" + Code");
        if ((applicationInfo.flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0)isSystemAppView.setText(isSystemAppView.getText()+" + XLdalvik");

        // Paths
        mMenu.titleColor = accentColor;
        mMenu.addMenuItemWithIconTitleSubtitle(getString(R.string.paths), true);
        mMenu.titleColor = MenuItemCreator.NO_COLOR;
        mMenu.addMenuItemWithIconTitleSubtitle(getString(R.string.apk_path), applicationInfo.sourceDir, MenuItemCreator.SELECTABLE);
        mMenu.addMenuItemWithIconTitleSubtitle(getString(R.string.data_path), applicationInfo.dataDir, MenuItemCreator.SELECTABLE);
        mMenu.addDivider();

        // SDK
        mMenu.titleColor = accentColor;
        mMenu.addMenuItemWithIconTitleSubtitle(getString(R.string.sdk), true);
        mMenu.titleColor = MenuItemCreator.NO_COLOR;
        mMenu.addMenuItemWithIconTitleSubtitle(getString(R.string.sdk_max), "" + applicationInfo.targetSdkVersion, MenuItemCreator.SELECTABLE);
        mMenu.addMenuItemWithIconTitleSubtitle(getString(R.string.sdk_min), Build.VERSION.SDK_INT > 23 ? "" + applicationInfo.minSdkVersion : "N/A", MenuItemCreator.SELECTABLE);

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

        // TODO: Should be monospaced
        if(flags.length() != 0)
            mMenu.addMenuItemWithIconTitleSubtitle(getString(R.string.sdk_flags), flags, MenuItemCreator.SELECTABLE);
        mMenu.addDivider();

        mMenu.titleColor = accentColor;
        mMenu.addMenuItemWithIconTitleSubtitle(getString(R.string.more_info), true);
        mMenu.titleColor = MenuItemCreator.NO_COLOR;
        mMenu.addMenuItemWithIconTitleSubtitle(getString(R.string.date_installed), getTime(mPackageInfo.firstInstallTime), MenuItemCreator.NO_ACTION);
        mMenu.addMenuItemWithIconTitleSubtitle(getString(R.string.date_updated), getTime(mPackageInfo.lastUpdateTime), MenuItemCreator.NO_ACTION);
        String installerPackageName = mPackageManager.getInstallerPackageName(mPackageName);
        if (installerPackageName != null) {
            String applicationLabel;
            try {
                applicationLabel = getPackageManager().getApplicationInfo(installerPackageName, 0).loadLabel(getPackageManager()).toString();
            } catch (PackageManager.NameNotFoundException e) {
                applicationLabel = installerPackageName;
            }
            mMenu.addMenuItemWithIconTitleSubtitle(getString(R.string.installer_app), applicationLabel, MenuItemCreator.SELECTABLE);
        }
        // Use red color if FLAG_LARGE_HEAP enabled FIXME: find a suitable place for this
        if ((applicationInfo.flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0)
            mMenu.subtitleColor = Color.RED;
        mMenu.addMenuItemWithIconTitleSubtitle(getString(R.string.user_id), "" + applicationInfo.uid, MenuItemCreator.SELECTABLE);
        if (mPackageInfo.sharedUserId != null)
            mMenu.addMenuItemWithIconTitleSubtitle(getString(R.string.shared_user_id), "" + mPackageInfo.sharedUserId, MenuItemCreator.SELECTABLE);
        mMenu.subtitleColor = MenuItemCreator.NO_COLOR;
        if (mMainActivity.length() != 0)
            mMenu.addMenuItemWithIconTitleSubtitle(getString(R.string.main_activity), mMainActivity, MenuItemCreator.SELECTABLE);
        mMenu.addDivider();

        // Netstat
        mMenu.titleColor = accentColor;
        mMenu.addMenuItemWithIconTitleSubtitle(getString(R.string.netstats_msg), true);
        mMenu.titleColor = MenuItemCreator.NO_COLOR;

        Tuple<String, String> uidNetStats = getNetStats(applicationInfo.uid);
        mMenu.addMenuItemWithIconTitleSubtitle(getString(R.string.netstats_transmitted), uidNetStats.getFirst(), MenuItemCreator.SELECTABLE);
        mMenu.addMenuItemWithIconTitleSubtitle(getString(R.string.netstats_received), uidNetStats.getSecond(), MenuItemCreator.SELECTABLE);
        mMenu.addDivider();
    }

    /**
     * Load package sizes and update views if success.
     */
    private void getPackageSizeInfo() {
        try {
            @SuppressWarnings("JavaReflectionMemberAccess")
            Method getPackageSizeInfo = PackageManager.class.getMethod(
                    "getPackageSizeInfo", String.class, IPackageStatsObserver.class);

            getPackageSizeInfo.invoke(mPackageManager, mPackageName, new IPackageStatsObserver.Stub() {
                @Override
                public void onGetStatsCompleted(final PackageStats pStats, boolean succeeded) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mPackageStats = pStats;
                            onPackageStatsLoaded();
                        }
                    });
                }
            });
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
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

    private void setClickEvents(){
        // Set events here for now, this is a hack!
        findViewById(R.id.details_uninstall).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
                uninstallIntent.setData(Uri.parse("package:" + mPackageName));
                startActivity(uninstallIntent);
            }
        });
        findViewById(R.id.details_view_in_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent infoIntent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                infoIntent.addCategory(Intent.CATEGORY_DEFAULT);
                infoIntent.setData(Uri.parse("package:" + mPackageName));
                startActivity(infoIntent);
            }
        });
        findViewById(R.id.details_manifest).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mActivity, ManifestViewerActivity.class);
                intent.putExtra(ManifestViewerActivity.EXTRA_PACKAGE_NAME, mPackageName);
                startActivity(intent);
            }
        });
        findViewById(R.id.details_exodus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent newIntent = new Intent(AppInfoActivity.this, ClassListingActivity.class);
                    File file = new File(mPackageManager.getPackageInfo(mPackageName, 0).applicationInfo.publicSourceDir);
                    newIntent.setDataAndType(Uri.fromFile(file), MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"));
                    newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    newIntent.putExtra(ClassListingActivity.EXTRA_PACKAGE_NAME, mPackageName);
                    startActivity(newIntent);
                } catch (ActivityNotFoundException | PackageManager.NameNotFoundException e) {
                    Toast.makeText(mActivity, e.toString(), Toast.LENGTH_LONG).show();
                }
            }
        });
        findViewById(R.id.details_fdroid).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClassName("org.fdroid.fdroid", "org.fdroid.fdroid.views.AppDetailsActivity");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("appid", mPackageName);
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(mActivity, e.toString(), Toast.LENGTH_LONG).show();
                }
            }
        });

    }

    /**
     * Get package info.
     * @param packageName Package name (e.g. com.android.wallpaper)
     * @return Null or PackageInfo
     */
    @SuppressLint("PackageManagerGetSignatures")
    private PackageInfo getPackageInfo(String packageName) {
        try {
            return mPackageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS
                    | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                    | PackageManager.GET_SERVICES | PackageManager.GET_URI_PERMISSION_PATTERNS
                    | PackageManager.GET_SIGNATURES | PackageManager.GET_CONFIGURATIONS | PackageManager.GET_SHARED_LIBRARY_FILES);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
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
