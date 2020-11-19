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

package io.github.muntashirakon.AppManager.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.types.IconLoaderThread;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class MainRecyclerAdapter extends RecyclerView.Adapter<MainRecyclerAdapter.ViewHolder>
        implements SectionIndexer {
    static final String sections = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    @SuppressLint("SimpleDateFormat")
    static final DateFormat sSimpleDateFormat = new SimpleDateFormat("dd/MM/yyyy"); // hh:mm:ss");

    private final MainActivity mActivity;
    private final PackageManager mPackageManager;
    private String mSearchQuery;
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

    void clearSelection() {
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

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Cancel an existing icon loading operation
        if (holder.iconLoader != null) holder.iconLoader.interrupt();
        final ApplicationItem item = mAdapterList.get(position);
        // Add click listeners
        holder.itemView.setOnClickListener(v -> {
            // Click listener: 1) If app not installed, display a toast message saying that it's
            // not installed, 2) If installed, load the App Details page, 3) If selection mode
            // is on, select/deselect the current item instead of 1 & 2.
            if (mActivity.mModel.getSelectedPackages().size() == 0) {
                if (!item.isInstalled)
                    Toast.makeText(mActivity, R.string.app_not_installed, Toast.LENGTH_SHORT).show();
                else {
                    Intent appDetailsIntent = new Intent(mActivity, AppDetailsActivity.class);
                    appDetailsIntent.putExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME, item.packageName);
                    if (item.userHandles.length > 0) {
                        appDetailsIntent.putExtra(AppDetailsActivity.EXTRA_USER_HANDLE, item.userHandles[0]);
                    }
                    mActivity.startActivity(appDetailsIntent);
                }
            } else toggleSelection(item, position);
        });
        holder.itemView.setOnLongClickListener(v -> {
            // Long click listener: Select/deselect an app. Turn selection mode on if this is
            // the first item in the selection list
            toggleSelection(item, position);
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
        String lastUpdateDate = sSimpleDateFormat.format(new Date(item.lastUpdateTime));
        if (item.firstInstallTime == item.lastUpdateTime)
            holder.date.setText(lastUpdateDate);
        else {
            long days = TimeUnit.DAYS.convert(item.lastUpdateTime - item.firstInstallTime, TimeUnit.MILLISECONDS);
            SpannableString ssDate = new SpannableString(mActivity.getResources()
                    .getQuantityString(R.plurals.main_list_date_days, (int) days, lastUpdateDate, days));
            ssDate.setSpan(new RelativeSizeSpan(.8f), 10, ssDate.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            holder.date.setText(ssDate);
        }
        // Set date color to orange if app can read logs (and accepted)
        if (mPackageManager.checkPermission(Manifest.permission.READ_LOGS, item.packageName)
                == PackageManager.PERMISSION_GRANTED)
            holder.date.setTextColor(mColorOrange);
        else holder.date.setTextColor(mColorSecondary);
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
        if (Build.VERSION.SDK_INT >= 23) {
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
        if ((item.flags & ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0)
            holder.size.setTextColor(mColorOrange);
        else holder.size.setTextColor(mColorSecondary);
        // Check for backup
        if (item.metadata != null) {
            holder.backupIndicator.setVisibility(View.VISIBLE);
            holder.backupInfo.setVisibility(View.VISIBLE);
            holder.backupInfoExt.setVisibility(View.VISIBLE);
            holder.backupIndicator.setText(R.string.backup);
            MetadataManager.Metadata metadata = item.metadata;
            long days = TimeUnit.DAYS.convert(System.currentTimeMillis() -
                    metadata.backupTime, TimeUnit.MILLISECONDS);
            holder.backupInfo.setText(String.format("%s: %s, %s %s",
                    mActivity.getString(R.string.backup), mActivity.getResources()
                            .getQuantityString(R.plurals.usage_days, (int) days, days),
                    mActivity.getString(R.string.version), metadata.versionName));
            StringBuilder extBulder = new StringBuilder();
            if (metadata.flags.backupSource()) extBulder.append("apk");
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

    public void toggleSelection(@NonNull ApplicationItem item, int position) {
        if (mActivity.mModel.getSelectedPackages().contains(item.packageName)) {
            mAdapterList.set(position, mActivity.mModel.deselect(item));
        } else {
            mAdapterList.set(position, mActivity.mModel.select(item));
        }
        notifyItemChanged(position);
        mActivity.handleSelection();
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public int getItemCount() {
        return mAdapterList.size();
    }

    @Override
    public int getPositionForSection(int section) {
        for (int i = 0; i < getItemCount(); i++) {
            String item = mAdapterList.get(i).label;
            if (item.length() > 0) {
                if (item.charAt(0) == sections.charAt(section))
                    return i;
            }
        }
        return 0;
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