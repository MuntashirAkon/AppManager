/*
 * Copyright (c) 2021 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.details.info;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import io.github.muntashirakon.AppManager.backup.BackupUtils;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.details.AppDetailsViewModel;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.NetworkPolicyManagerCompat;
import io.github.muntashirakon.AppManager.utils.*;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppInfoViewModel extends AndroidViewModel {
    private final MutableLiveData<CharSequence> packageLabel = new MutableLiveData<>();
    private final MutableLiveData<TagCloud> tagCloud = new MutableLiveData<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    @Nullable
    private AppDetailsViewModel mainModel;

    public AppInfoViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
        super.onCleared();
    }

    public void setMainModel(@NonNull AppDetailsViewModel mainModel) {
        this.mainModel = mainModel;
    }

    public MutableLiveData<CharSequence> getPackageLabel() {
        return packageLabel;
    }

    public MutableLiveData<TagCloud> getTagCloud() {
        return tagCloud;
    }

    public void loadPackageLabel() {
        if (mainModel != null) {
            packageLabel.postValue(mainModel.getPackageInfo().applicationInfo.loadLabel(getApplication()
                    .getPackageManager()));
        }
    }

    public void loadTagCloud() {
        if (mainModel == null) return;
        PackageInfo packageInfo = mainModel.getPackageInfo();
        String packageName = packageInfo.packageName;
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        TagCloud tagCloud = new TagCloud();
        tagCloud.trackerComponents = ComponentUtils.getTrackerComponentsForPackageInfo(packageInfo);
        tagCloud.isSystemApp = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        tagCloud.isSystemlessPath = !mainModel.getIsExternalApk() && AppPref.isRootEnabled()
                && MagiskUtils.isSystemlessPath(PackageUtils.getHiddenCodePathOrDefault(packageName,
                applicationInfo.publicSourceDir));
        tagCloud.isUpdatedSystemApp = (applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        tagCloud.splitCount = mainModel.getSplitCount();
        tagCloud.isDebuggable = (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        tagCloud.isTestOnly = (applicationInfo.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0;
        tagCloud.hasCode = (applicationInfo.flags & ApplicationInfo.FLAG_HAS_CODE) != 0;
        tagCloud.hasRequestedLargeHeap = (applicationInfo.flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0;
        tagCloud.isRunning = !mainModel.getIsExternalApk() && PackageUtils.hasRunningServices(packageName);
        tagCloud.isForceStopped = (applicationInfo.flags & ApplicationInfo.FLAG_STOPPED) != 0;
        tagCloud.isAppEnabled = applicationInfo.enabled;
        tagCloud.isMagiskHideEnabled = !mainModel.getIsExternalApk() && AppPref.isRootEnabled() && MagiskUtils.isHidden(packageName);
        tagCloud.hasKeyStoreItems = KeyStoreUtils.hasKeyStore(applicationInfo.uid);
        tagCloud.hasMasterKeyInKeyStore = KeyStoreUtils.hasMasterKey(applicationInfo.uid);
        MetadataManager.Metadata[] metadata = MetadataManager.getMetadata(packageName);
        String[] readableBackupNames = new String[metadata.length];
        for (int i = 0; i < metadata.length; ++i) {
            String backupName = BackupUtils.getShortBackupName(metadata[i].backupName);
            int userHandle = metadata[i].userHandle;
            readableBackupNames[i] = backupName == null ? "Base backup for user " + userHandle : backupName + " for user " + userHandle;
        }
        tagCloud.readableBackupNames = readableBackupNames;
        if (!mainModel.getIsExternalApk() && PermissionUtils.hasDumpPermission()) {
            String targetString = "user," + packageName + "," + applicationInfo.uid;
            Runner.Result result = Runner.runCommand(new String[]{"dumpsys", "deviceidle", "whitelist"});
            tagCloud.isBatteryOptimized = !result.isSuccessful() || !result.getOutput().contains(targetString);
        } else {
            tagCloud.isBatteryOptimized = true;
        }
        if (!mainModel.getIsExternalApk() && LocalServer.isAMServiceAlive()) {
            tagCloud.netPolicies = NetworkPolicyManagerCompat.getUidPolicy(applicationInfo.uid);
        } else {
            tagCloud.netPolicies = 0;
        }
        this.tagCloud.postValue(tagCloud);
    }

    public static class TagCloud {
        public HashMap<String, RulesStorageManager.Type> trackerComponents;
        public boolean isSystemApp;
        public boolean isSystemlessPath;
        public boolean isUpdatedSystemApp;
        public int splitCount;
        public boolean isDebuggable;
        public boolean isTestOnly;
        public boolean hasCode;
        public boolean hasRequestedLargeHeap;
        public boolean isRunning;
        public boolean isForceStopped;
        public boolean isAppEnabled;
        public boolean isMagiskHideEnabled;
        public boolean hasKeyStoreItems;
        public boolean hasMasterKeyInKeyStore;
        public String[] readableBackupNames;
        public boolean isBatteryOptimized;
        public int netPolicies;
    }
}
