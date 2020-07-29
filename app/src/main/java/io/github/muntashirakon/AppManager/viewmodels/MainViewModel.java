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
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
    private @MainActivity.Filter int mFilterFlags;
    private String searchQuery;
    private List<String> selectedPackages = new LinkedList<>();
    private List<ApplicationItem> selectedApplicationItems = new LinkedList<>();
    private int flagSigningInfo;
    public MainViewModel(@NonNull Application application) {
        super(application);
        Log.d("MVM", "New instance created");
        mPackageManager = application.getPackageManager();
        mHandler = new Handler(application.getMainLooper());
        mPackageObserver = new PackageIntentReceiver(this);
        mSortBy = (int) AppPref.get(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_ORDER_INT);
        mFilterFlags = (int) AppPref.get(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_FLAGS_INT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            flagSigningInfo = PackageManager.GET_SIGNING_CERTIFICATES;
        else flagSigningInfo = PackageManager.GET_SIGNATURES;
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

    public ApplicationItem deselect(@NonNull ApplicationItem item) {
        int i = applicationItems.indexOf(item);
        if (i == -1) return item;
        selectedPackages.remove(item.applicationInfo.packageName);
        selectedApplicationItems.remove(item);
        item.isSelected = false;
        applicationItems.set(i, item);
        return item;
    }

    public ApplicationItem select(@NonNull ApplicationItem item) {
        int i = applicationItems.indexOf(item);
        if (i == -1) return item;
        selectedPackages.add(item.applicationInfo.packageName);
        item.isSelected = true;
        applicationItems.set(i, item);
        selectedApplicationItems.add(item);
        return item;
    }

    public void clearSelection() {
        selectedPackages.clear();
        int i;
        for (ApplicationItem item: selectedApplicationItems) {
            i = applicationItems.indexOf(item);
            if (i == -1) continue;
            item.isSelected = false;
            applicationItems.set(i, item);
        }
        selectedApplicationItems.clear();
    }

    public List<String> getSelectedPackages() {
        return selectedPackages;
    }

    public List<ApplicationItem> getSelectedApplicationItems() {
        return selectedApplicationItems;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
        new Thread(this::filterItemsByFlags).start();
    }

    public void setSortBy(int sortBy) {
        if (mSortBy != sortBy) {
            new Thread(() -> {
                sortApplicationList(sortBy);
                filterItemsByFlags();
            }).start();
        }
        mSortBy = sortBy;
        AppPref.getInstance().setPref(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_ORDER_INT, mSortBy);
    }

    public int getFilterFlags() {
        return mFilterFlags;
    }

    public void addFilterFlag(@MainActivity.Filter int filterFlag) {
        mFilterFlags |= filterFlag;
        AppPref.getInstance().setPref(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_FLAGS_INT, mFilterFlags);
        new Thread(() -> {
            synchronized (applicationItems) {
                filterItemsByFlags();
            }
        }).start();
    }

    public void removeFilterFlag(@MainActivity.Filter int filterFlag) {
        mFilterFlags &= ~filterFlag;
        AppPref.getInstance().setPref(AppPref.PrefKey.PREF_MAIN_WINDOW_FILTER_FLAGS_INT, mFilterFlags);
        new Thread(() -> {
            synchronized (applicationItems) {
                filterItemsByFlags();
            }
        }).start();
    }

    @SuppressLint("PackageManagerGetSignatures")
    public void loadApplicationItems() {
        new Thread(() -> {
            synchronized (applicationItems) {
                applicationItems.clear();
                if (MainActivity.packageList != null) {
                    String[] packageList = MainActivity.packageList.split("[\\r\\n]+");
                    for (String packageName : packageList) {
                        ApplicationItem item = new ApplicationItem();
                        try {
                            PackageInfo packageInfo = mPackageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA | flagSigningInfo);
                            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
                            item.applicationInfo = applicationInfo;
                            item.star = (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
                            item.isUser = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
                            item.isDisabled = !applicationInfo.enabled;
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
                        item.star = (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
                        item.isUser = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
                        item.isDisabled = !applicationInfo.enabled;
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
                if (Build.VERSION.SDK_INT <= 25) loadPackageSize();
                sortApplicationList(mSortBy);
                filterItemsByFlags();
            }
        }).start();
    }

    private void filterItemsByQuery(@NonNull List<ApplicationItem> applicationItems) {
        List<ApplicationItem> filteredApplicationItems = new ArrayList<>();
        for (ApplicationItem item: applicationItems) {
            if (item.label.toLowerCase(Locale.ROOT).contains(searchQuery)
                    || item.applicationInfo.packageName.toLowerCase(Locale.ROOT).contains(searchQuery))
                filteredApplicationItems.add(item);
        }
        mHandler.post(() -> applicationItemsLiveData.postValue(filteredApplicationItems));
    }

    private void filterItemsByFlags() {
        if (mFilterFlags == MainActivity.FILTER_NO_FILTER) {
            if (!TextUtils.isEmpty(searchQuery)) {
                filterItemsByQuery(applicationItems);
            } else {
                mHandler.post(() -> applicationItemsLiveData.postValue(applicationItems));
            }
        } else {
            List<ApplicationItem> filteredApplicationItems = new ArrayList<>();
            if ((mFilterFlags & MainActivity.FILTER_APPS_WITH_RULES) != 0) {
                loadBlockingRules();
            }
            for (ApplicationItem item : applicationItems) {
                if ((mFilterFlags & MainActivity.FILTER_USER_APPS) != 0 && item.isUser) {
                    filteredApplicationItems.add(item);
                } else if ((mFilterFlags & MainActivity.FILTER_SYSTEM_APPS) != 0 && !item.isUser) {
                    filteredApplicationItems.add(item);
                } else if ((mFilterFlags & MainActivity.FILTER_DISABLED_APPS) != 0 && item.isDisabled) {
                    filteredApplicationItems.add(item);
                } else if ((mFilterFlags & MainActivity.FILTER_APPS_WITH_RULES) != 0 && item.blockedCount > 0) {
                    filteredApplicationItems.add(item);
                }
            }
            if (!TextUtils.isEmpty(searchQuery)) {
                filterItemsByQuery(filteredApplicationItems);
            } else {
                mHandler.post(() -> applicationItemsLiveData.postValue(filteredApplicationItems));
            }
        }
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

    private void updateInfoForUid(int uid, String action) {
        Log.d("updateInfoForUid", "Uid: " + uid);
        String[] packages;
        if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) packages = getPackagesForUid(uid);
        else packages = mPackageManager.getPackagesForUid(uid);
        updateInfoForPackages(packages, action);
    }

    private void updateInfoForPackages(@Nullable String[] packages, @NonNull String action) {
        Log.d("updateInfoForPackages", "packages: " + Arrays.toString(packages));
        if (packages == null || packages.length == 0) return;
        switch (action) {
            case Intent.ACTION_PACKAGE_REMOVED:
            case Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE:
                for (String packageName: packages) {
                    try {
                        mPackageManager.getApplicationInfo(packageName, 0);
                    } catch (PackageManager.NameNotFoundException e) {
                        removePackageFromApplicationItems(packageName);
                    }
                }
                filterItemsByFlags();
                break;
            case Intent.ACTION_PACKAGE_CHANGED:
                for (String packageName: packages) {
                    ApplicationItem item = getNewApplicationItem(packageName);
                    if (item != null) insertApplicationItemInApplicationItems(item);
                }
                sortApplicationList(mSortBy);
                filterItemsByFlags();
                break;
            case Intent.ACTION_PACKAGE_ADDED:
            case Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE:
                for (String packageName: packages) {
                    ApplicationItem item = getNewApplicationItem(packageName);
                    if (item != null) applicationItems.add(item);
                }
                sortApplicationList(mSortBy);
                filterItemsByFlags();
        }
    }

    private void removePackageFromApplicationItems(String packageName) {
        ApplicationItem item = getApplicationItemFromApplicationItems(packageName);
        if (item != null) applicationItems.remove(item);
    }

    private void insertApplicationItemInApplicationItems(ApplicationItem item) {
        for (int i = 0; i<applicationItems.size(); ++i) {
            if (applicationItems.get(i).applicationInfo.packageName.equals(item.applicationInfo.packageName)) {
                applicationItems.set(i, item);
            }
        }
    }

    @Nullable
    private ApplicationItem getNewApplicationItem(String packageName) {
        ApplicationItem item = new ApplicationItem();
        try {
            PackageInfo packageInfo = mPackageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA | flagSigningInfo);
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            item.applicationInfo = applicationInfo;
            item.label = applicationInfo.loadLabel(mPackageManager).toString();
            item.star = (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            item.isUser = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
            item.isDisabled = !applicationInfo.enabled;
            item.date = packageInfo.lastUpdateTime; // .firstInstallTime;
            item.sha = Utils.getIssuerAndAlg(packageInfo);
            if (Build.VERSION.SDK_INT >= 26) {
                item.size = (long) -1 * applicationInfo.targetSdkVersion;
            } else {  // 25 or less
                getSizeForPackage(item);
            }
            if (mSortBy == MainActivity.SORT_BY_BLOCKED_COMPONENTS && AppPref.isRootEnabled()) {
                try (ComponentsBlocker cb = ComponentsBlocker.getInstance(getApplication(), packageName, true)) {
                    item.blockedCount = cb.componentCount();
                }
            }
            return item;
        } catch (PackageManager.NameNotFoundException ignored) {}
        return null;
    }

    @Nullable
    private ApplicationItem getApplicationItemFromApplicationItems(String packageName) {
        for (ApplicationItem item: applicationItems) {
            if (item.applicationInfo.packageName.equals(packageName)) return item;
        }
        return null;
    }

    @NonNull
    private String[] getPackagesForUid(int uid) {
        List<String> packages = new LinkedList<>();
        for (ApplicationItem item: applicationItems)
            if (item.applicationInfo.uid == uid) packages.add(item.applicationInfo.packageName);
        return packages.toArray(new String[0]);
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
        public void onReceive(Context context, @NonNull Intent intent) {
            switch (Objects.requireNonNull(intent.getAction())) {
                case Intent.ACTION_PACKAGE_REMOVED:
                    if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) break;
                case Intent.ACTION_PACKAGE_ADDED:
                case Intent.ACTION_PACKAGE_CHANGED:
                    mModel.updateInfoForUid(intent.getIntExtra(Intent.EXTRA_UID, -1), intent.getAction());
                    break;
                case Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE:
                case Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE:
                    mModel.updateInfoForPackages(intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST), intent.getAction());
                    break;
                case Intent.ACTION_LOCALE_CHANGED:
                    mModel.loadApplicationItems();
            }
        }
    }
}
