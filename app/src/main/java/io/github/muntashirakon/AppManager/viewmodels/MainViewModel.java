package io.github.muntashirakon.AppManager.viewmodels;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.os.Build;
import android.os.Handler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.github.muntashirakon.AppManager.activities.MainActivity;
import io.github.muntashirakon.AppManager.storage.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.types.ApplicationItem;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.Tuple;
import io.github.muntashirakon.AppManager.utils.Utils;

public class MainViewModel extends AndroidViewModel {
    private PackageManager mPackageManager;
    private PackageIntentReceiver mPackageObserver;
    private Handler mHandler;
    public MainViewModel(@NonNull Application application) {
        super(application);
        mPackageManager = application.getPackageManager();
        mHandler = new Handler(application.getMainLooper());
        mPackageObserver = new PackageIntentReceiver(this);
    }

    private MutableLiveData<List<ApplicationItem>> applicationItemsLiveData;
    final private List<ApplicationItem> applicationItems = new ArrayList<>();
    @NonNull
    public LiveData<List<ApplicationItem>> getApplicationItems() {
        if (applicationItemsLiveData == null) {
            applicationItemsLiveData = new MutableLiveData<>();
            loadApplicationItems();
        }
        return applicationItemsLiveData;
    }

    @SuppressLint("PackageManagerGetSignatures")
    public void loadApplicationItems() {
        new Thread(() -> {
            String pName;
            final boolean isRootEnabled = AppPref.isRootEnabled();
            int flagSigningInfo;
            applicationItems.clear();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                flagSigningInfo = PackageManager.GET_SIGNING_CERTIFICATES;
            else flagSigningInfo = PackageManager.GET_SIGNATURES;
            if (MainActivity.packageList != null) {
                String[] aList = MainActivity.packageList.split("[\\r\\n]+");
                for (String s : aList) {
                    ApplicationItem item = new ApplicationItem();
                    if (s.endsWith("*")) {
                        item.star = true;
                        pName = s.substring(0, s.length() - 1);
                    } else pName = s;
                    try {
                        PackageInfo packageInfo = mPackageManager.getPackageInfo(pName, PackageManager.GET_META_DATA | flagSigningInfo);
                        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
                        item.applicationInfo = applicationInfo;
                        item.label = applicationInfo.loadLabel(mPackageManager).toString();
                        item.date = packageInfo.lastUpdateTime; // .firstInstallTime;
                        item.sha = Utils.getIssuerAndAlg(packageInfo);
                        if (Build.VERSION.SDK_INT >= 26) {
                            item.size = (long) -1 * applicationInfo.targetSdkVersion;
                        }
                        applicationItems.add(item);
                    } catch (PackageManager.NameNotFoundException ignored) {}
                }
            } else {
                List<ApplicationInfo> applicationInfoList = mPackageManager.getInstalledApplications(PackageManager.GET_META_DATA);
                for (ApplicationInfo applicationInfo : applicationInfoList) {
                    ApplicationItem item = new ApplicationItem();
                    item.applicationInfo = applicationInfo;
                    item.star = ((applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
                    item.label = applicationInfo.loadLabel(mPackageManager).toString();
                    if (Build.VERSION.SDK_INT >= 26) {
                        item.size = (long) -1 * applicationInfo.targetSdkVersion;
                    }
                    try {
                        PackageInfo packageInfo = mPackageManager.getPackageInfo(applicationInfo.packageName, flagSigningInfo);
                        item.sha = Utils.getIssuerAndAlg(packageInfo);
                        item.date = packageInfo.lastUpdateTime; // .firstInstallTime;
                    } catch (PackageManager.NameNotFoundException e) {
                        item.date = 0L;
                        item.sha = new Tuple<>("?", "?");
                    }
                    item.blockedCount = 0;
                    applicationItems.add(item);
                }
            }
            mHandler.post(() -> applicationItemsLiveData.postValue(applicationItems));
            if (isRootEnabled) loadBlockingRules();  // FIXME: Run this if only sort by blocking rules is requested
            if (Build.VERSION.SDK_INT <= 25) loadPackageSize();
        }).start();
    }

    private void loadBlockingRules() {
        for (int i = 0; i<applicationItems.size(); ++i) {
            ApplicationItem applicationItem = applicationItems.get(i);
            try (ComponentsBlocker cb = ComponentsBlocker.getInstance(getApplication(), applicationItem.applicationInfo.packageName, true)) {
                applicationItem.blockedCount = cb.componentCount();
            }
            applicationItems.set(i, applicationItem);
        }
        mHandler.post(() -> applicationItemsLiveData.postValue(applicationItems));
    }

    private void loadPackageSize() {
        for (int i = 0; i<applicationItems.size(); ++i)
            applicationItems.set(i, getSizeForPackage(applicationItems.get(i)));
        mHandler.post(() -> applicationItemsLiveData.postValue(applicationItems));
    }

    private ApplicationItem getSizeForPackage(@NonNull final ApplicationItem item) {
        try {
            @SuppressWarnings("JavaReflectionMemberAccess")
            Method getPackageSizeInfo = PackageManager.class.getMethod("getPackageSizeInfo",
                    String.class, IPackageStatsObserver.class);

            getPackageSizeInfo.invoke(mPackageManager, item.applicationInfo.packageName, new IPackageStatsObserver.Stub() {
                @Override
                public void onGetStatsCompleted(final PackageStats pStats, final boolean succeeded) {
                    if (succeeded) {
                        item.size = pStats.codeSize + pStats.cacheSize + pStats.dataSize
                                + pStats.externalCodeSize + pStats.externalCacheSize
                                + pStats.externalDataSize + pStats.externalMediaSize
                                + pStats.externalObbSize;
                    } else item.size = -1L;
                }
            });
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return item;
    }

    @Override
    protected void onCleared() {
        if (mPackageObserver != null) getApplication().unregisterReceiver(mPackageObserver);
        super.onCleared();
    }

    public static class PackageIntentReceiver extends BroadcastReceiver {

        final MainViewModel mModel;

        public PackageIntentReceiver(@NonNull MainViewModel model) {
            mModel = model;
            IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
            filter.addDataScheme("package");
            mModel.getApplication().registerReceiver(this, filter);
            // Register for events related to sdcard installation.
            IntentFilter sdFilter = new IntentFilter();
            sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
            sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            mModel.getApplication().registerReceiver(this, sdFilter);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            mModel.loadApplicationItems();
        }
    }
}
