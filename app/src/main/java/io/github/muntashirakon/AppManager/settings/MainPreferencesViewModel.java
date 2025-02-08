// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.app.Application;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.collection.ArrayMap;
import androidx.core.util.Pair;
import androidx.documentfile.provider.DocumentFileUtils;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.signing.Signer;
import io.github.muntashirakon.AppManager.changelog.Changelog;
import io.github.muntashirakon.AppManager.changelog.ChangelogParser;
import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.utils.AppDb;
import io.github.muntashirakon.AppManager.misc.DeviceInfo2;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.users.UserInfo;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.CpuUtils;
import io.github.muntashirakon.AppManager.utils.DigestUtils;
import io.github.muntashirakon.AppManager.utils.StorageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.lifecycle.SingleLiveEvent;

public class MainPreferencesViewModel extends AndroidViewModel implements Ops.AdbConnectionInterface {
    private final Object mRulesLock = new Object();
    private final MutableLiveData<List<UserInfo>> mSelectUsers = new SingleLiveEvent<>();
    private final MutableLiveData<Changelog> mChangeLog = new SingleLiveEvent<>();
    private final MutableLiveData<DeviceInfo2> mDeviceInfo = new SingleLiveEvent<>();
    private final MutableLiveData<String> mCustomCommand0 = new SingleLiveEvent<>();
    private final MutableLiveData<String> mCustomCommand1 = new SingleLiveEvent<>();
    private final MutableLiveData<Integer> mModeOfOpsStatus = new SingleLiveEvent<>();
    private final MutableLiveData<Boolean> mOperationCompletedLiveData = new SingleLiveEvent<>();
    private final MutableLiveData<ArrayMap<String, Uri>> mStorageVolumesLiveData = new SingleLiveEvent<>();
    private final MutableLiveData<String> mSigningKeySha256HashLiveData = new SingleLiveEvent<>();
    private final MutableLiveData<List<Pair<String, CharSequence>>> mPackageNameLabelPairLiveData = new SingleLiveEvent<>();
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(1);

    public MainPreferencesViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<UserInfo>> selectUsers() {
        return mSelectUsers;
    }

    public void loadAllUsers() {
        ThreadUtils.postOnBackgroundThread(() -> mSelectUsers.postValue(Users.getAllUsers()));
    }

    public LiveData<Changelog> getChangeLog() {
        return mChangeLog;
    }

    public void loadChangeLog() {
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                Changelog changelog = new ChangelogParser(getApplication(), R.raw.changelog).parse();
                mChangeLog.postValue(changelog);
            } catch (IOException | XmlPullParserException e) {
                e.printStackTrace();
            }
        });
    }

    public LiveData<DeviceInfo2> getDeviceInfo() {
        return mDeviceInfo;
    }

    public void loadDeviceInfo(@NonNull DeviceInfo2 di) {
        ThreadUtils.postOnBackgroundThread(() -> {
            di.loadInfo();
            mDeviceInfo.postValue(di);
        });
    }

    public void reloadApps() {
        ThreadUtils.postOnBackgroundThread(() -> {
            PowerManager.WakeLock wakeLock = CpuUtils.getPartialWakeLock("appDbUpdater");
            try {
                wakeLock.acquire();
                AppDb appDb = new AppDb();
                appDb.deleteAllApplications();
                appDb.deleteAllBackups();
                appDb.loadInstalledOrBackedUpApplications(getApplication());
            } finally {
                CpuUtils.releaseWakeLock(wakeLock);
            }
        });
    }

    public MutableLiveData<String> getCustomCommand0() {
        return mCustomCommand0;
    }

    public MutableLiveData<String> getCustomCommand1() {
        return mCustomCommand1;
    }

    public void loadCustomCommands() {
        mExecutor.submit(() -> {
            try {
                ServerConfig.init(getApplication(), UserHandleHidden.myUserId());
                mCustomCommand0.postValue(ServerConfig.getServerRunnerCommand(0));
                mCustomCommand1.postValue(ServerConfig.getServerRunnerCommand(1));
            } catch (Throwable e) {
                e.printStackTrace();
                mCustomCommand0.postValue(null);
                mCustomCommand1.postValue(null);
            }
        });
    }

    public LiveData<Integer> getModeOfOpsStatus() {
        return mModeOfOpsStatus;
    }

    public void setModeOfOps() {
        mExecutor.submit(() -> {
            int status = Ops.init(getApplication(), true);
            mModeOfOpsStatus.postValue(status);
        });
    }

    public LiveData<Boolean> getOperationCompletedLiveData() {
        return mOperationCompletedLiveData;
    }

    public void applyAllRules() {
        ThreadUtils.postOnBackgroundThread(() -> {
            synchronized (mRulesLock) {
                // TODO: 13/8/22 Synchronise in ComponentsBlocker instead of here
                ComponentsBlocker.applyAllRules(getApplication(), UserHandleHidden.myUserId());
            }
        });
    }

    public void removeAllRules() {
        ThreadUtils.postOnBackgroundThread(() -> {
            int[] userHandles = Users.getUsersIds();
            List<String> packages = ComponentUtils.getAllPackagesWithRules(getApplication());
            for (int userHandle : userHandles) {
                for (String packageName : packages) {
                    ComponentUtils.removeAllRules(packageName, userHandle);
                }
            }
            mOperationCompletedLiveData.postValue(true);
        });
    }

    public LiveData<ArrayMap<String, Uri>> getStorageVolumesLiveData() {
        return mStorageVolumesLiveData;
    }

    public void loadStorageVolumes() {
        ThreadUtils.postOnBackgroundThread(() -> {
            ArrayMap<String, Uri> locations = StorageUtils.getAllStorageLocations(getApplication());
            ArrayMap<String, Uri> newLocations = new ArrayMap<>(locations.size());
            PackageManager pm = getApplication().getPackageManager();
            for (int i = 0; i < locations.size(); ++i) {
                Uri uri = locations.valueAt(i);
                String authority = uri.getAuthority();
                if (authority != null) {
                    ResolveInfo resolveInfo = DocumentFileUtils.getUriSource(getApplication(), uri);
                    String readableName = resolveInfo != null
                            ? resolveInfo.loadLabel(pm).toString()
                            : locations.keyAt(i);
                    newLocations.put(readableName, locations.valueAt(i));
                } else newLocations.put(locations.keyAt(i), locations.valueAt(i));
            }
            mStorageVolumesLiveData.postValue(newLocations);
        });
    }

    public LiveData<String> getSigningKeySha256HashLiveData() {
        return mSigningKeySha256HashLiveData;
    }

    public void loadSigningKeySha256Hash() {
        mExecutor.submit(() -> {
            String hash = null;
            try {
                KeyStoreManager keyStoreManager = KeyStoreManager.getInstance();
                if (keyStoreManager.containsKey(Signer.SIGNING_KEY_ALIAS)) {
                    KeyPair keyPair = keyStoreManager.getKeyPair(Signer.SIGNING_KEY_ALIAS);
                    if (keyPair != null) {
                        Certificate certificate = keyPair.getCertificate();
                        hash = DigestUtils.getHexDigest(DigestUtils.SHA_256, certificate.getEncoded());
                        try {
                            keyPair.destroy();
                        } catch (Exception ignore) {
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            mSigningKeySha256HashLiveData.postValue(hash);
        });
    }

    public LiveData<List<Pair<String, CharSequence>>> getPackageNameLabelPairLiveData() {
        return mPackageNameLabelPairLiveData;
    }

    public void loadPackageNameLabelPair() {
        mExecutor.submit(() -> {
            List<App> appList = new AppDb().getAllApplications();
            Map<String, CharSequence> packageNameLabelMap = new HashMap<>(appList.size());
            for (App app : appList) {
                packageNameLabelMap.put(app.packageName, app.packageLabel);
            }
            List<Pair<String, CharSequence>> appInfo = new ArrayList<>();
            for (String packageName : packageNameLabelMap.keySet()) {
                appInfo.add(new Pair<>(packageName, packageNameLabelMap.get(packageName)));
            }
            Collections.sort(appInfo, (o1, o2) -> o1.second.toString().compareTo(o2.second.toString()));
            mPackageNameLabelPairLiveData.postValue(appInfo);
        });
    }

    @RequiresApi(Build.VERSION_CODES.R)
    public void autoConnectWirelessDebugging() {
        mExecutor.submit(() -> {
            int status = Ops.autoConnectWirelessDebugging(getApplication());
            mModeOfOpsStatus.postValue(status);
        });
    }

    @Override
    public void connectAdb(int port) {
        mExecutor.submit(() -> {
            int status = Ops.connectAdb(getApplication(), port, Ops.STATUS_FAILURE);
            mModeOfOpsStatus.postValue(status);
        });
    }

    @Override
    @RequiresApi(Build.VERSION_CODES.R)
    public void pairAdb() {
        mExecutor.submit(() -> {
            int status = Ops.pairAdb(getApplication());
            mModeOfOpsStatus.postValue(status);
        });
    }

    @Override
    public void onStatusReceived(int status) {
        mModeOfOpsStatus.postValue(status);
    }
}
