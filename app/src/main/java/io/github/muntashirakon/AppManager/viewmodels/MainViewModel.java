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
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
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
    private static Collator sCollator = Collator.getInstance();

    private PackageManager mPackageManager;
    private PackageIntentReceiver mPackageObserver;
    private Handler mHandler;
    private @MainActivity.SortOrder int mSortBy;
    public MainViewModel(@NonNull Application application) {
        super(application);
        mPackageManager = application.getPackageManager();
        mHandler = new Handler(application.getMainLooper());
        mPackageObserver = new PackageIntentReceiver(this);
        mSortBy = (int) AppPref.get(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_ORDER_INT);
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

    public void setSortBy(int sortBy) {
        if (mSortBy != sortBy) {
            new Thread(() -> {
                sortApplicationList(sortBy);
                mHandler.post(() -> applicationItemsLiveData.postValue(applicationItems));
            }).start();
        }
        mSortBy = sortBy;
        AppPref.getInstance().setPref(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_ORDER_INT, mSortBy);
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
            sortApplicationList(mSortBy);
            mHandler.post(() -> applicationItemsLiveData.postValue(applicationItems));
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

    private void sortApplicationList(@MainActivity.SortOrder int sortBy) {
        final boolean isRootEnabled = AppPref.isRootEnabled();
        if (sortBy != MainActivity.SORT_BY_APP_LABEL) sortApplicationList(MainActivity.SORT_BY_APP_LABEL);
        if (sortBy == MainActivity.SORT_BY_BLOCKED_COMPONENTS && isRootEnabled) loadBlockingRules();
        Collections.sort(applicationItems, (o1, o2) -> {
            switch (sortBy) {
                case MainActivity.SORT_BY_APP_LABEL:
                    return sCollator.compare(o1.label, o2.label);
                case MainActivity.SORT_BY_PACKAGE_NAME:
                    return o1.applicationInfo.packageName.compareTo(o2.applicationInfo.packageName);
                case MainActivity.SORT_BY_DOMAIN:
                    boolean isSystem1 = (o1.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    boolean isSystem2 = (o2.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    return Utils.compareBooleans(isSystem1, isSystem2);
                case MainActivity.SORT_BY_LAST_UPDATE:
                    // Sort in decreasing order
                    return -o1.date.compareTo(o2.date);
                case MainActivity.SORT_BY_APP_SIZE_OR_SDK:
                    return -o1.size.compareTo(o2.size);
                case MainActivity.SORT_BY_SHARED_ID:
                    return o2.applicationInfo.uid - o1.applicationInfo.uid;
                case MainActivity.SORT_BY_SHA:
                    try {
                        return o1.sha.compareTo(o2.sha);
                    } catch (NullPointerException ignored) {}
                    break;
                case MainActivity.SORT_BY_BLOCKED_COMPONENTS:
                    if (isRootEnabled)
                        return -o1.blockedCount.compareTo(o2.blockedCount);
                    break;
                case MainActivity.SORT_BY_DISABLED_APP:
                    return Utils.compareBooleans(o1.applicationInfo.enabled, o2.applicationInfo.enabled);
            }
            return 0;
        });
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
