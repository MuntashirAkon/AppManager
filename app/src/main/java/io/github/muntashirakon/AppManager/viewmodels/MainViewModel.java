package io.github.muntashirakon.AppManager.viewmodels;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;

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

    private MutableLiveData<List<ApplicationItem>> applicationItems;
    @NonNull
    public LiveData<List<ApplicationItem>> getApplicationItems() {
        if (applicationItems == null) {
            applicationItems = new MutableLiveData<>();
            loadInBackground();
        }
        return applicationItems;
    }

    @SuppressLint("PackageManagerGetSignatures")
    public void loadInBackground() {
        new Thread(() -> {
            List<ApplicationItem> itemList = new ArrayList<>();
            String pName;
            final boolean isRootEnabled = AppPref.isRootEnabled();
            if (MainActivity.packageList != null) {
                String[] aList = MainActivity.packageList.split("[\\r\\n]+");
                for (String s : aList) {
                    ApplicationItem item = new ApplicationItem();
                    if (s.endsWith("*")) {
                        item.star = true;
                        pName = s.substring(0, s.length() - 1);
                    } else pName = s;
                    try {
                        ApplicationInfo applicationInfo = mPackageManager.getApplicationInfo(pName, PackageManager.GET_META_DATA);
                        item.applicationInfo = applicationInfo;
                        item.label = applicationInfo.loadLabel(mPackageManager).toString();
                        item.date = mPackageManager.getPackageInfo(applicationInfo.packageName, 0).lastUpdateTime; // .firstInstallTime;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            item.sha = Utils.getIssuerAndAlg(mPackageManager.getPackageInfo(applicationInfo.packageName, PackageManager.GET_SIGNING_CERTIFICATES));
                        } else {
                            item.sha = Utils.getIssuerAndAlg(mPackageManager.getPackageInfo(applicationInfo.packageName, PackageManager.GET_SIGNATURES));
                        }
                        if (Build.VERSION.SDK_INT >= 26) {
                            item.size = (long) -1 * applicationInfo.targetSdkVersion;
                        }
                        if (isRootEnabled) {
                            try (ComponentsBlocker cb = ComponentsBlocker.getInstance(getApplication(), pName, true)) {
                                item.blockedCount = cb.componentCount();
                            }
                        }
                        itemList.add(item);
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            item.sha = Utils.getIssuerAndAlg(mPackageManager.getPackageInfo(applicationInfo.packageName, PackageManager.GET_SIGNING_CERTIFICATES));
                        } else {
                            item.sha = Utils.getIssuerAndAlg(mPackageManager.getPackageInfo(applicationInfo.packageName, PackageManager.GET_SIGNATURES));
                        }
                        item.date = mPackageManager.getPackageInfo(applicationInfo.packageName, 0).lastUpdateTime; // .firstInstallTime;
                    } catch (PackageManager.NameNotFoundException e) {
                        item.date = 0L;
                        item.sha = new Tuple<>("?", "?");
                    }
                    if (isRootEnabled) {
                        try (ComponentsBlocker cb = ComponentsBlocker.getInstance(getApplication(), applicationInfo.packageName, true)) {
                            item.blockedCount = cb.componentCount();
                        }
                    }
                    itemList.add(item);
                }
            }
            mHandler.post(() -> applicationItems.postValue(itemList));
        }).start();
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
            mModel.loadInBackground();
        }
    }
}
