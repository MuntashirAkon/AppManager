// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.types.IconLoaderThread;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class MainRecyclerAdapter extends RecyclerView.Adapter<MainRecyclerAdapter.ViewHolder>
        implements SectionIndexer {
    static final String sections = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final MainActivity mActivity;
    private final PackageManager mPackageManager;
    private String mSearchQuery;
    @GuardedBy("mAdapterList")
    private final List<ApplicationItem> mAdapterList = new ArrayList<>();

    private static int mColorTransparent;
    private static int mColorSemiTransparent;
    private static int mColorHighlight;
    private static int mColorDisabled;
    private static int mColorStopped;
    private static int mColorOrange;
    private static int mColorPrimary;
    private static int mColorSecondary;
    private static int mColorRed;

    MainRecyclerAdapter(@NonNull MainActivity activity) {
        mActivity = activity;
        mPackageManager = activity.getPackageManager();

        mColorTransparent = Color.TRANSPARENT;
        mColorSemiTransparent = ContextCompat.getColor(mActivity, R.color.semi_transparent);
        mColorHighlight = ContextCompat.getColor(mActivity, R.color.highlight);
        mColorDisabled = ContextCompat.getColor(mActivity, R.color.disabled_user);
        mColorStopped = ContextCompat.getColor(mActivity, R.color.stopped);
        mColorOrange = ContextCompat.getColor(mActivity, R.color.orange);
        mColorPrimary = ContextCompat.getColor(mActivity, R.color.textColorPrimary);
        mColorSecondary = ContextCompat.getColor(mActivity, R.color.textColorSecondary);
        mColorRed = ContextCompat.getColor(mActivity, R.color.red);
    }

    @GuardedBy("mAdapterList")
    void setDefaultList(List<ApplicationItem> list) {
        new Thread(() -> {
            synchronized (mAdapterList) {
                mAdapterList.clear();
                mAdapterList.addAll(list);
                mSearchQuery = mActivity.mModel.getSearchQuery();
                mActivity.runOnUiThread(this::notifyDataSetChanged);
            }
        }).start();
    }

    @GuardedBy("mAdapterList")
    @WorkerThread
    void clearSelection() {
        if (mActivity.mModel == null) return;
        synchronized (mAdapterList) {
            final List<Integer> itemIds = new ArrayList<>();
            int itemId;
            for (ApplicationItem applicationItem : mActivity.mModel.getSelectedApplicationItems()) {
                itemId = mAdapterList.indexOf(applicationItem);
                if (itemId == -1) continue;
                applicationItem.isSelected = false;
                mAdapterList.set(itemId, applicationItem);
                itemIds.add(itemId);
            }
            mActivity.runOnUiThread(() -> {
                for (int id : itemIds) notifyItemChanged(id);
            });
            mActivity.mModel.clearSelection();
        }
    }

    @GuardedBy("mAdapterList")
    @UiThread
    void selectAll() {
        synchronized (mAdapterList) {
            for (int i = 0; i < mAdapterList.size(); ++i) {
                mAdapterList.set(i, mActivity.mModel.select(mAdapterList.get(i)));
                notifyItemChanged(i);
            }
            mActivity.handleSelection();
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
        // Cancel an existing icon loading operation
        if (holder.iconLoader != null) holder.iconLoader.interrupt();
        final ApplicationItem item;
        synchronized (mAdapterList) {
            item = mAdapterList.get(position);
        }
        // Add click listeners
        holder.itemView.setOnClickListener(v -> {
            // Click listener:
            // 1) If the app is not installed:
            //    i.  Display a toast message saying that it's not installed if it's a backup-only app
            //    ii. Offer to install the app if it can be installed
            // 2) If installed, load the App Details page
            // 3) If selection mode is on, select/deselect the current item instead of 1 & 2.
            if (mActivity.mModel.getSelectedPackages().size() == 0) {
                if (!item.isInstalled) {
                    try {
                        @SuppressLint("WrongConstant")
                        ApplicationInfo info = mPackageManager.getApplicationInfo(item.packageName, PackageUtils.flagMatchUninstalled);
                        if (info.publicSourceDir != null && new File(info.publicSourceDir).exists()
                                && FeatureController.isInstallerEnabled()) {
                            Intent intent = new Intent(mActivity, PackageInstallerActivity.class);
                            intent.setData(Uri.fromFile(new File(info.publicSourceDir)));
                            mActivity.startActivity(intent);
                        }
                    } catch (PackageManager.NameNotFoundException ignore) {
                    }
                    Toast.makeText(mActivity, R.string.app_not_installed, Toast.LENGTH_SHORT).show();
                } else {
                    Intent appDetailsIntent = new Intent(mActivity, AppDetailsActivity.class);
                    appDetailsIntent.putExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME, item.packageName);
                    if (item.userHandles.length > 0) {
                        if (item.userHandles.length > 1) {
                            String[] userNames = new String[item.userHandles.length];
                            for (int i = 0; i < item.userHandles.length; ++i) {
                                userNames[i] = mActivity.getString(R.string.user_with_id, item.userHandles[i]);
                            }
                            List<UserInfo> users = Users.getUsers();
                            if (users != null) {
                                for (UserInfo info : users) {
                                    for (int i = 0; i < item.userHandles.length; ++i) {
                                        if (info.id == item.userHandles[i]) {
                                            userNames[i] = info.name == null ? String.valueOf(info.id) : info.name;
                                        }
                                    }
                                }
                            }
                            new MaterialAlertDialogBuilder(mActivity)
                                    .setTitle(R.string.select_user)
                                    .setItems(userNames, (dialog, which) -> {
                                        appDetailsIntent.putExtra(AppDetailsActivity.EXTRA_USER_HANDLE, item.userHandles[which]);
                                        mActivity.startActivity(appDetailsIntent);
                                        dialog.dismiss();
                                    })
                                    .setNegativeButton(R.string.cancel, null)
                                    .show();
                        } else {
                            appDetailsIntent.putExtra(AppDetailsActivity.EXTRA_USER_HANDLE, item.userHandles[0]);
                            mActivity.startActivity(appDetailsIntent);
                        }
                    } else mActivity.startActivity(appDetailsIntent);
                }
            } else toggleSelection(item, position);
        });
        holder.itemView.setOnLongClickListener(v -> {
            // Long click listener: Select/deselect an app.
            // 1) Turn selection mode on if this is the first item in the selection list
            // 2) Select between last selection position and this position (inclusive) if selection mode is on
            ApplicationItem lastSelectedItem = mActivity.mModel.getLastSelectedPackage();
            int lastSelectedItemPosition = lastSelectedItem == null ? -1 : mAdapterList.indexOf(lastSelectedItem);
            if (lastSelectedItemPosition >= 0) {
                // Select from last selection to this selection
                selectRange(lastSelectedItemPosition, position);
            } else toggleSelection(item, position);
            return true;
        });
        holder.icon.setOnClickListener(v -> toggleSelection(item, position));
        // Alternate background colors: selected > disabled > regular
        if (item.isSelected) holder.mainView.setBackgroundColor(mColorHighlight);
        else if (item.isDisabled) holder.mainView.setBackgroundColor(mColorDisabled);
        else {
            holder.mainView.setBackgroundColor(position % 2 == 0 ? mColorSemiTransparent : mColorTransparent);
        }
        // Add yellow star if the app is in debug mode
        holder.favorite_icon.setVisibility(item.debuggable ? View.VISIBLE : View.INVISIBLE);
        // Set version name
        holder.version.setText(item.versionName);
        // Set date and (if available,) days between first install and last update
        String lastUpdateDate = DateUtils.formatDate(item.lastUpdateTime);
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
            holder.sharedId.setText(String.valueOf(item.uid));
            // Set kernel user ID text color to orange if the package is shared
            if (item.sharedUserId != null) holder.sharedId.setTextColor(mColorOrange);
            else holder.sharedId.setTextColor(mColorSecondary);
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
        holder.iconLoader = new IconLoaderThread(holder.icon, item);
        holder.iconLoader.start();
        // Set app label
        if (!TextUtils.isEmpty(mSearchQuery) && item.label.toLowerCase(Locale.ROOT).contains(mSearchQuery)) {
            // Highlight searched query
            holder.label.setText(UIUtils.getHighlightedText(item.label, mSearchQuery, mColorRed));
        } else holder.label.setText(item.label);
        // Set app label color to red if clearing user data not allowed
        if (item.isInstalled && (item.flags & ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA) == 0)
            holder.label.setTextColor(Color.RED);
        else holder.label.setTextColor(mColorPrimary);
        // Set package name
        if (!TextUtils.isEmpty(mSearchQuery) && item.packageName.toLowerCase(Locale.ROOT).contains(mSearchQuery)) {
            // Highlight searched query
            holder.packageName.setText(UIUtils.getHighlightedText(item.packageName, mSearchQuery, mColorRed));
        } else holder.packageName.setText(item.packageName);
        // Set package name color to dark cyan if the app is in stopped/force closed state
        if ((item.flags & ApplicationInfo.FLAG_STOPPED) != 0)
            holder.packageName.setTextColor(mColorStopped);
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
                holder.version.setTextColor(mColorStopped);
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
            holder.size.setText(String.format(Locale.getDefault(), "SDK %d", item.sdk));
        } else holder.size.setText("-");
        // Set SDK color to orange if the app is using cleartext (e.g. HTTP) traffic
        if ((item.flags & ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0) {
            holder.size.setTextColor(mColorOrange);
        } else holder.size.setTextColor(mColorSecondary);
        // Check for backup
        if (item.metadata != null) {
            holder.backupIndicator.setVisibility(View.VISIBLE);
            holder.backupInfo.setVisibility(View.VISIBLE);
            holder.backupInfoExt.setVisibility(View.VISIBLE);
            holder.backupIndicator.setText(R.string.backup);
            int indicatorColor;
            if (item.isInstalled) {
                if (item.metadata.versionCode >= item.versionCode) {
                    // Up-to-date backup
                    indicatorColor = mColorStopped;
                } else {
                    // Outdated backup
                    indicatorColor = mColorOrange;
                }
            } else {
                // App not installed
                indicatorColor = mColorRed;
            }
            holder.backupIndicator.setTextColor(indicatorColor);
            MetadataManager.Metadata metadata = item.metadata;
            long days = TimeUnit.DAYS.convert(System.currentTimeMillis() -
                    metadata.backupTime, TimeUnit.MILLISECONDS);
            holder.backupInfo.setText(String.format("%s: %s, %s %s",
                    mActivity.getString(R.string.latest_backup), mActivity.getResources()
                            .getQuantityString(R.plurals.usage_days, (int) days, days),
                    mActivity.getString(R.string.version), metadata.versionName));
            StringBuilder extBulder = new StringBuilder();
            if (metadata.flags.backupApkFiles()) extBulder.append("apk");
            if (metadata.flags.backupData()) {
                if (extBulder.length() > 0) extBulder.append("+");
                extBulder.append("data");
            }
            if (metadata.hasRules) {
                if (extBulder.length() > 0) extBulder.append("+");
                extBulder.append("rules");
            }
            holder.backupInfoExt.setText(extBulder.toString());
        } else {
            holder.backupIndicator.setVisibility(View.GONE);
            holder.backupInfo.setVisibility(View.GONE);
            holder.backupInfoExt.setVisibility(View.GONE);
        }
    }

    @GuardedBy("mAdapterList")
    @UiThread
    public void toggleSelection(@NonNull ApplicationItem item, int position) {
        synchronized (mAdapterList) {
            if (mActivity.mModel.getSelectedPackages().containsKey(item.packageName)) {
                mAdapterList.set(position, mActivity.mModel.deselect(item));
            } else {
                mAdapterList.set(position, mActivity.mModel.select(item));
            }
        }
        notifyItemChanged(position);
        mActivity.handleSelection();
    }

    @GuardedBy("mAdapterList")
    @UiThread
    public void selectRange(int firstPosition, int secondPosition) {
        int beginPosition = Math.min(firstPosition, secondPosition);
        int endPosition = Math.max(firstPosition, secondPosition);
        synchronized (mAdapterList) {
            for (int position = beginPosition; position <= endPosition; ++position) {
                mAdapterList.set(position, mActivity.mModel.select(mAdapterList.get(position)));
            }
        }
        notifyItemRangeChanged(beginPosition, endPosition - beginPosition + 1);
        mActivity.handleSelection();
    }

    @GuardedBy("mAdapterList")
    @Override
    public long getItemId(int i) {
        synchronized (mAdapterList) {
            return mAdapterList.get(i).hashCode();
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
                if (item.length() > 0) {
                    if (item.charAt(0) == sections.charAt(section))
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
        String[] sectionsArr = new String[sections.length()];
        for (int i = 0; i < sections.length(); i++)
            sectionsArr[i] = "" + sections.charAt(i);

        return sectionsArr;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View mainView;
        ImageView icon;
        ImageView favorite_icon;
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
        IconLoaderThread iconLoader;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            mainView = itemView.findViewById(R.id.main_view);
            icon = itemView.findViewById(R.id.icon);
            favorite_icon = itemView.findViewById(R.id.favorite_icon);
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