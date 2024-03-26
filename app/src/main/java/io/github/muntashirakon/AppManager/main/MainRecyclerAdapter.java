// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;
import static io.github.muntashirakon.AppManager.utils.UIUtils.displayLongToast;

import android.Manifest;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandleHidden;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.users.UserInfo;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.dialog.SearchableItemsDialogBuilder;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.widget.MultiSelectionView;

public class MainRecyclerAdapter extends MultiSelectionView.Adapter<MainRecyclerAdapter.ViewHolder>
        implements SectionIndexer {
    private static final String sSections = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final MainActivity mActivity;
    private final PackageManager mPackageManager;
    private String mSearchQuery;
    @GuardedBy("mAdapterList")
    private final List<ApplicationItem> mAdapterList = new ArrayList<>();

    private final int mColorGreen;
    private final int mColorOrange;
    private final int mColorPrimary;
    private final int mColorSecondary;
    private final int mQueryStringHighlight;

    MainRecyclerAdapter(@NonNull MainActivity activity) {
        super();
        mActivity = activity;
        mPackageManager = activity.getPackageManager();

        mColorGreen = ContextCompat.getColor(mActivity, io.github.muntashirakon.ui.R.color.stopped);
        mColorOrange = ContextCompat.getColor(mActivity, io.github.muntashirakon.ui.R.color.orange);
        mColorPrimary = ContextCompat.getColor(mActivity, io.github.muntashirakon.ui.R.color.textColorPrimary);
        mColorSecondary = ContextCompat.getColor(mActivity, io.github.muntashirakon.ui.R.color.textColorSecondary);
        mQueryStringHighlight = ColorCodes.getQueryStringHighlightColor(mActivity);
    }

    @GuardedBy("mAdapterList")
    @UiThread
    void setDefaultList(List<ApplicationItem> list) {
        if (mActivity.viewModel == null) return;
        synchronized (mAdapterList) {
            mSearchQuery = mActivity.viewModel.getSearchQuery();
            AdapterUtils.notifyDataSetChanged(this, mAdapterList, list);
            notifySelectionChange();
        }
    }

    @GuardedBy("mAdapterList")
    @Override
    public void cancelSelection() {
        super.cancelSelection();
        mActivity.viewModel.cancelSelection();
    }

    @Override
    public int getSelectedItemCount() {
        if (mActivity.viewModel == null) return 0;
        return mActivity.viewModel.getSelectedPackages().size();
    }

    @Override
    protected int getTotalItemCount() {
        if (mActivity.viewModel == null) return 0;
        return mActivity.viewModel.getApplicationItemCount();
    }

    @GuardedBy("mAdapterList")
    @Override
    protected boolean isSelected(int position) {
        synchronized (mAdapterList) {
            return mAdapterList.get(position).isSelected;
        }
    }

    @GuardedBy("mAdapterList")
    @Override
    protected void select(int position) {
        synchronized (mAdapterList) {
            mAdapterList.set(position, mActivity.viewModel.select(mAdapterList.get(position)));
        }
    }

    @GuardedBy("mAdapterList")
    @Override
    protected void deselect(int position) {
        synchronized (mAdapterList) {
            mAdapterList.set(position, mActivity.viewModel.deselect(mAdapterList.get(position)));
        }
    }

    @GuardedBy("mAdapterList")
    @Override
    public void toggleSelection(int position) {
        synchronized (mAdapterList) {
            super.toggleSelection(position);
        }
    }

    @GuardedBy("mAdapterList")
    @Override
    public void selectAll() {
        synchronized (mAdapterList) {
            super.selectAll();
        }
    }

    @GuardedBy("mAdapterList")
    @Override
    public void selectRange(int firstPosition, int secondPosition) {
        synchronized (mAdapterList) {
            super.selectRange(firstPosition, secondPosition);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_main, parent, false);
        return new ViewHolder(view);
    }

    @GuardedBy("mAdapterList")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final ApplicationItem item;
        synchronized (mAdapterList) {
            item = mAdapterList.get(position);
        }
        // Add click listeners
        MaterialCardView cardView = holder.itemView;
        cardView.setOnClickListener(v -> {
            // If selection mode is on, select/deselect the current item instead of the default behaviour
            if (isInSelectionMode()) {
                toggleSelection(position);
                return;
            }
            handleClick(item);
        });
        cardView.setOnLongClickListener(v -> {
            // Long click listener: Select/deselect an app.
            // 1) Turn selection mode on if this is the first item in the selection list
            // 2) Select between last selection position and this position (inclusive) if selection mode is on
            synchronized (mAdapterList) {
                ApplicationItem lastSelectedItem = mActivity.viewModel.getLastSelectedPackage();
                int lastSelectedItemPosition = lastSelectedItem == null ? -1 : mAdapterList.indexOf(lastSelectedItem);
                if (lastSelectedItemPosition >= 0) {
                    // Select from last selection to this selection
                    selectRange(lastSelectedItemPosition, position);
                } else toggleSelection(position);
            }
            return true;
        });
        holder.icon.setOnClickListener(v -> toggleSelection(position));
        // Divider colors: disabled > regular
        if (!item.isInstalled) {
            cardView.setStrokeColor(ColorCodes.getAppUninstalledIndicatorColor(mActivity));
        } else if (item.isDisabled) {
            cardView.setStrokeColor(ColorCodes.getAppDisabledIndicatorColor(mActivity));
        } else if ((item.flags & ApplicationInfo.FLAG_STOPPED) != 0) { // Force-stopped: Dark cyan
            cardView.setStrokeColor(ColorCodes.getAppForceStoppedIndicatorColor(mActivity));
        } else {
            cardView.setStrokeColor(Color.TRANSPARENT);
        }
        // Add yellow star if the app is in debug mode
        holder.debugIcon.setVisibility(item.debuggable ? View.VISIBLE : View.INVISIBLE);
        // Set version name
        holder.version.setText(item.versionName);
        // Set date and (if available,) days between first install and last update
        String lastUpdateDate = DateUtils.formatDate(mActivity, item.lastUpdateTime);
        if (item.firstInstallTime == item.lastUpdateTime) {
            holder.date.setText(lastUpdateDate);
        } else {
            long days = TimeUnit.DAYS.convert(item.lastUpdateTime - item.firstInstallTime, TimeUnit.MILLISECONDS);
            SpannableString ssDate = new SpannableString(mActivity.getResources()
                    .getQuantityString(R.plurals.main_list_date_days, (int) days, lastUpdateDate, days));
            ssDate.setSpan(new RelativeSizeSpan(.8f), lastUpdateDate.length(),
                    ssDate.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            holder.date.setText(ssDate);
        }
        // Set date color to orange if app can read logs (and accepted)
        if (mPackageManager.checkPermission(Manifest.permission.READ_LOGS, item.packageName)
                == PackageManager.PERMISSION_GRANTED) {
            holder.date.setTextColor(mColorOrange);
        } else holder.date.setTextColor(mColorSecondary);
        if (item.isInstalled) {
            // Set kernel user ID
            String sharedId;
            if (item.userIds.length > 1) {
                int appId = UserHandleHidden.getAppId(item.uid);
                sharedId = item.userIds.length + "+" + appId;
            } else sharedId = String.valueOf(item.uid);
            holder.sharedId.setText(sharedId);
            // Set kernel user ID text color to orange if the package is shared
            if (item.sharedUserId != null) {
                holder.sharedId.setTextColor(mColorOrange);
            } else holder.sharedId.setTextColor(mColorSecondary);
        } else holder.sharedId.setText("");
        if (item.sha != null) {
            // Set issuer
            String issuer;
            try {
                issuer = "CN=" + (item.sha.first).split("CN=", 2)[1];
            } catch (ArrayIndexOutOfBoundsException e) {
                issuer = item.sha.first;
            }
            holder.issuer.setVisibility(View.VISIBLE);
            holder.issuer.setText(issuer);
            // Set signature type
            holder.sha.setVisibility(View.VISIBLE);
            holder.sha.setText(item.sha.second);
        } else {
            holder.issuer.setVisibility(View.GONE);
            holder.sha.setVisibility(View.GONE);
        }
        // Load app icon
        holder.icon.setTag(item.packageName);
        ImageLoader.getInstance().displayImage(item.packageName, item, holder.icon);
        // Set app label
        if (!TextUtils.isEmpty(mSearchQuery) && item.label.toLowerCase(Locale.ROOT).contains(mSearchQuery)) {
            // Highlight searched query
            holder.label.setText(UIUtils.getHighlightedText(item.label, mSearchQuery, mQueryStringHighlight));
        } else holder.label.setText(item.label);
        // Set app label color to red if clearing user data not allowed
        if (item.isInstalled && (item.flags & ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA) == 0) {
            holder.label.setTextColor(Color.RED);
        } else holder.label.setTextColor(mColorPrimary);
        // Set package name
        if (!TextUtils.isEmpty(mSearchQuery) && item.packageName.toLowerCase(Locale.ROOT).contains(mSearchQuery)) {
            // Highlight searched query
            holder.packageName.setText(UIUtils.getHighlightedText(item.packageName, mSearchQuery, mQueryStringHighlight));
        } else holder.packageName.setText(item.packageName);
        // Set package name color to orange if the app has known tracker components
        if (item.trackerCount > 0)
            holder.packageName.setTextColor(ColorCodes.getComponentTrackerIndicatorColor(mActivity));
        else holder.packageName.setTextColor(mColorSecondary);
        // Set version (along with HW accelerated, debug and test only flags)
        CharSequence version = holder.version.getText();
        if (item.isInstalled && (item.flags & ApplicationInfo.FLAG_HARDWARE_ACCELERATED) == 0)
            version = "_" + version;
        if ((item.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) version = "debug" + version;
        if ((item.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0) version = "~" + version;
        holder.version.setText(version);
        // Set version color to dark cyan if the app is inactive
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            UsageStatsManager mUsageStats;
            mUsageStats = mActivity.getSystemService(UsageStatsManager.class);
            if (mUsageStats != null && mUsageStats.isAppInactive(item.packageName))
                holder.version.setTextColor(mColorGreen);
            else holder.version.setTextColor(mColorSecondary);
        }
        // Set app type: system or user app (along with large heap, suspended, multi-arch,
        // has code, vm safe mode)
        String isSystemApp;
        if (item.isInstalled) {
            if ((item.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                isSystemApp = mActivity.getString(R.string.system);
            else isSystemApp = mActivity.getString(R.string.user);
            if ((item.flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0) isSystemApp += "#";
            if ((item.flags & ApplicationInfo.FLAG_SUSPENDED) != 0) isSystemApp += "°";
            if ((item.flags & ApplicationInfo.FLAG_MULTIARCH) != 0) isSystemApp += "X";
            if ((item.flags & ApplicationInfo.FLAG_HAS_CODE) == 0) isSystemApp += "0";
            if ((item.flags & ApplicationInfo.FLAG_VM_SAFE_MODE) != 0) isSystemApp += "?";
            holder.isSystemApp.setText(isSystemApp);
            // Set app type text color to magenta if the app is persistent
            if ((item.flags & ApplicationInfo.FLAG_PERSISTENT) != 0)
                holder.isSystemApp.setTextColor(Color.MAGENTA);
            else holder.isSystemApp.setTextColor(mColorSecondary);
        } else {
            holder.isSystemApp.setText("-");
            holder.isSystemApp.setTextColor(mColorSecondary);
        }
        // Set SDK
        if (item.isInstalled) {
            holder.size.setText(String.format(Locale.ROOT, "SDK %d", item.sdk));
        } else holder.size.setText("-");
        // Set SDK color to orange if the app is using cleartext (e.g. HTTP) traffic
        if ((item.flags & ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0) {
            holder.size.setTextColor(mColorOrange);
        } else holder.size.setTextColor(mColorSecondary);
        // Check for backup
        if (item.backup != null) {
            holder.backupIndicator.setVisibility(View.VISIBLE);
            holder.backupInfo.setVisibility(View.VISIBLE);
            holder.backupInfoExt.setVisibility(View.VISIBLE);
            holder.backupIndicator.setText(R.string.backup);
            int indicatorColor;
            if (item.isInstalled) {
                if (item.backup.versionCode >= item.versionCode) {
                    // Up-to-date backup
                    indicatorColor = ColorCodes.getBackupLatestIndicatorColor(mActivity);
                } else {
                    // Outdated backup
                    indicatorColor = ColorCodes.getBackupOutdatedIndicatorColor(mActivity);
                }
            } else {
                // App not installed
                indicatorColor = ColorCodes.getBackupUninstalledIndicatorColor(mActivity);
            }
            holder.backupIndicator.setTextColor(indicatorColor);
            Backup backup = item.backup;
            long days = TimeUnit.DAYS.convert(System.currentTimeMillis() -
                    backup.backupTime, TimeUnit.MILLISECONDS);
            holder.backupInfo.setText(String.format("%s: %s, %s %s",
                    mActivity.getString(R.string.latest_backup), mActivity.getResources()
                            .getQuantityString(R.plurals.usage_days, (int) days, days),
                    mActivity.getString(R.string.version), backup.versionName));
            StringBuilder extBuilder = new StringBuilder();
            if (backup.getFlags().backupApkFiles()) extBuilder.append("apk");
            if (backup.getFlags().backupData()) {
                if (extBuilder.length() > 0) extBuilder.append("+");
                extBuilder.append("data");
            }
            if (backup.hasRules) {
                if (extBuilder.length() > 0) extBuilder.append("+");
                extBuilder.append("rules");
            }
            holder.backupInfoExt.setText(extBuilder.toString());
        } else {
            holder.backupIndicator.setVisibility(View.GONE);
            holder.backupInfo.setVisibility(View.GONE);
            holder.backupInfoExt.setVisibility(View.GONE);
        }
        super.onBindViewHolder(holder, position);
    }

    @GuardedBy("mAdapterList")
    @Override
    public long getItemId(int position) {
        synchronized (mAdapterList) {
            return mAdapterList.get(position).hashCode();
        }
    }

    @GuardedBy("mAdapterList")
    @Override
    public int getItemCount() {
        synchronized (mAdapterList) {
            return mAdapterList.size();
        }
    }

    @GuardedBy("mAdapterList")
    @Override
    public int getPositionForSection(int section) {
        synchronized (mAdapterList) {
            for (int i = 0; i < getItemCount(); i++) {
                String item = mAdapterList.get(i).label;
                if (!item.isEmpty()) {
                    if (item.charAt(0) == sSections.charAt(section))
                        return i;
                }
            }
            return 0;
        }
    }

    @Override
    public int getSectionForPosition(int i) {
        return 0;
    }

    @Override
    public Object[] getSections() {
        String[] sectionsArr = new String[sSections.length()];
        for (int i = 0; i < sSections.length(); i++)
            sectionsArr[i] = String.valueOf(sSections.charAt(i));

        return sectionsArr;
    }

    private void handleClick(@NonNull ApplicationItem item) {
        if (!item.isInstalled || item.userIds.length == 0) {
            // The app should not be installed. But make sure this is really true. (For current user only)
            ApplicationInfo info;
            try {
                info = PackageManagerCompat.getApplicationInfo(item.packageName, MATCH_UNINSTALLED_PACKAGES
                                | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES,
                        UserHandleHidden.myUserId());
            } catch (RemoteException | PackageManager.NameNotFoundException e) {
                Toast.makeText(mActivity, R.string.app_not_installed, Toast.LENGTH_SHORT).show();
                return;
            }
            // 1. Check if the app was really uninstalled.
            if (ApplicationInfoCompat.isInstalled(info)) {
                // The app is already installed, and we were wrong to assume that it was installed.
                // Update data before opening it.
                item.isInstalled = true;
                item.userIds = new int[]{UserHandleHidden.myUserId()};
                Intent intent = AppDetailsActivity.getIntent(mActivity, item.packageName, UserHandleHidden.myUserId());
                mActivity.startActivity(intent);
                return;
            }
            // 2. If the app can be installed, offer it to install again.
            if (FeatureController.isInstallerEnabled()) {
                if (ApplicationInfoCompat.isSystemApp(info) && SelfPermissions.canInstallExistingPackages()) {
                    // Install existing app instead of installing as an update
                    mActivity.startActivity(PackageInstallerActivity.getLaunchableInstance(mActivity, item.packageName));
                    return;
                }
                // Otherwise, try with APK files
                // FIXME: 1/4/23 Include splits
                if (Paths.exists(info.publicSourceDir)) {
                    mActivity.startActivity(PackageInstallerActivity.getLaunchableInstance(mActivity,
                            Uri.fromFile(new File(info.publicSourceDir))));
                    return;
                }
            }
            // 3. The app might be uninstalled without clearing data
            if (ApplicationInfoCompat.isSystemApp(info)) {
                // The app is a system app, there's no point in asking to uninstall it again
                Toast.makeText(mActivity, R.string.app_not_installed, Toast.LENGTH_SHORT).show();
                return;
            }
            new MaterialAlertDialogBuilder(mActivity)
                    .setTitle(mActivity.getString(R.string.uninstall_app, item.label))
                    .setMessage(R.string.uninstall_app_again_message)
                    .setNegativeButton(R.string.no, null)
                    .setPositiveButton(R.string.yes, (dialog, which) -> ThreadUtils.postOnBackgroundThread(() -> {
                        PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
                        installer.setAppLabel(item.label);
                        boolean uninstalled = installer.uninstall(item.packageName, UserHandleHidden.myUserId(), false);
                        ThreadUtils.postOnMainThread(() -> {
                            if (uninstalled) {
                                displayLongToast(R.string.uninstalled_successfully, item.label);
                            } else {
                                displayLongToast(R.string.failed_to_uninstall, item.label);
                            }
                        });
                    }))
                    .show();
            return;
        }
        // The app is installed
        if (item.userIds.length == 1) {
            int[] userHandles = Users.getUsersIds();
            if (ArrayUtils.contains(userHandles, item.userIds[0])) {
                Intent intent = AppDetailsActivity.getIntent(mActivity, item.packageName, item.userIds[0]);
                mActivity.startActivity(intent);
                return;
            }
            // Outside our jurisdiction
            Toast.makeText(mActivity, R.string.app_not_installed, Toast.LENGTH_SHORT).show();
            return;
        }
        // More than a user, ask the user to select one
        CharSequence[] userNames = new String[item.userIds.length];
        List<UserInfo> users = Users.getUsers();
        for (UserInfo info : users) {
            for (int i = 0; i < item.userIds.length; ++i) {
                if (info.id == item.userIds[i]) {
                    userNames[i] = info.toLocalizedString(mActivity);
                }
            }
        }
        new SearchableItemsDialogBuilder<>(mActivity, userNames)
                .setTitle(R.string.select_user)
                .setOnItemClickListener((dialog, which, item1) -> {
                    Intent intent = AppDetailsActivity.getIntent(mActivity, item.packageName, item.userIds[which]);
                    mActivity.startActivity(intent);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static class ViewHolder extends MultiSelectionView.ViewHolder {
        MaterialCardView itemView;
        AppCompatImageView icon;
        AppCompatImageView debugIcon;
        TextView label;
        TextView packageName;
        TextView version;
        TextView isSystemApp;
        TextView date;
        TextView size;
        TextView sharedId;
        TextView issuer;
        TextView sha;
        TextView backupIndicator;
        TextView backupInfo;
        TextView backupInfoExt;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = (MaterialCardView) itemView;
            icon = itemView.findViewById(R.id.icon);
            debugIcon = itemView.findViewById(R.id.favorite_icon);
            label = itemView.findViewById(R.id.label);
            packageName = itemView.findViewById(R.id.packageName);
            version = itemView.findViewById(R.id.version);
            isSystemApp = itemView.findViewById(R.id.isSystem);
            date = itemView.findViewById(R.id.date);
            size = itemView.findViewById(R.id.size);
            sharedId = itemView.findViewById(R.id.shareid);
            issuer = itemView.findViewById(R.id.issuer);
            sha = itemView.findViewById(R.id.sha);
            backupIndicator = itemView.findViewById(R.id.backup_indicator);
            backupInfo = itemView.findViewById(R.id.backup_info);
            backupInfoExt = itemView.findViewById(R.id.backup_info_ext);
        }
    }
}