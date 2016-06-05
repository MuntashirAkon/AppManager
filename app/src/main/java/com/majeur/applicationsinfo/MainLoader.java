package com.majeur.applicationsinfo;

import android.content.AsyncTaskLoader;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.List;

public class MainLoader extends AsyncTaskLoader<List<ApplicationItem>> {

    private List<ApplicationItem> mData;
    private PackageIntentReceiver mPackageObserver;
    private PackageManager mPackageManager;

    public MainLoader(Context context) {
        super(context);

        mPackageManager = getContext().getPackageManager();
    }

    @Override
    public List<ApplicationItem> loadInBackground() {
        List<ApplicationInfo> applicationInfos = mPackageManager.getInstalledApplications(PackageManager.GET_META_DATA);

        List<ApplicationItem> itemList = new ArrayList<>(applicationInfos.size());

        for (ApplicationInfo applicationInfo : applicationInfos) {

            ApplicationItem item = new ApplicationItem();
            item.applicationInfo = applicationInfo;
            item.label = applicationInfo.loadLabel(mPackageManager).toString();
            try {
                item.date = mPackageManager.getPackageInfo(applicationInfo.packageName, 0).firstInstallTime;
            } catch (PackageManager.NameNotFoundException e) {
                item.date = 0L;
            }
            itemList.add(item);
        }

        return itemList;
    }

    /**
     * Called when there is new data to deliver to the client.  The
     * super class will take care of delivering it; the implementation
     * here just adds a little more logic.
     */
    @Override
    public void deliverResult(List<ApplicationItem> data) {
        if (isReset()) {
            // An async query came in while the loader is stopped.  We
            // don't need the result.
            if (data != null) {
                onReleaseResources(data);
            }
        }
        List<ApplicationItem> olddata = mData;
        mData = data;

        if (isStarted()) {
            // If the Loader is currently started, we can immediately
            // deliver its results.
            super.deliverResult(data);
        }

        // At this point we can release the resources associated with
        // 'olddata' if needed; now that the new result is delivered we
        // know that it is no longer in use.
        if (olddata != null) {
            onReleaseResources(olddata);
        }
    }

    /**
     * Handles a request to start the Loader.
     */
    @Override
    protected void onStartLoading() {
        if (mData != null) {
            // If we currently have a result available, deliver it
            // immediately.
            deliverResult(mData);
        }

        // Start watching for changes in the app data.
        if (mPackageObserver == null) {
            mPackageObserver = new PackageIntentReceiver(this);
        }

        if (takeContentChanged() || mData == null) {
            // If the data has changed since the last time it was loaded
            // or is not currently available, start a load.
            forceLoad();
        }
    }

    /**
     * Handles a request to stop the Loader.
     */
    @Override
    protected void onStopLoading() {
        // Attempt to cancel the current load task if possible.
        cancelLoad();
    }

    /**
     * Handles a request to cancel a load.
     */
    @Override
    public void onCanceled(List<ApplicationItem> data) {
        super.onCanceled(data);

        // At this point we can release the resources associated with 'data'
        // if needed.
        onReleaseResources(data);
    }

    /**
     * Handles a request to completely reset the Loader.
     */
    @Override
    protected void onReset() {
        super.onReset();

        // Ensure the loader is stopped
        onStopLoading();

        // At this point we can release the resources associated with 'data'
        // if needed.
        if (mData != null) {
            onReleaseResources(mData);
            mData = null;
        }

        // Stop monitoring for changes.
        if (mPackageObserver != null) {
            getContext().unregisterReceiver(mPackageObserver);
            mPackageObserver = null;
        }
    }

    /**
     * Helper function to take care of releasing resources associated
     * with an actively loaded data set.
     */
    protected void onReleaseResources(List<ApplicationItem> data) {
        // For a simple List<> there is nothing to do.  For something
        // like a Cursor, we would close it here.
    }


    /**
     * Helper class to look for interesting changes to the installed apps
     * so that the loader can be updated.
     */
    public static class PackageIntentReceiver extends BroadcastReceiver {

        final MainLoader mLoader;

        public PackageIntentReceiver(MainLoader loader) {
            mLoader = loader;
            IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
            filter.addDataScheme("package");
            mLoader.getContext().registerReceiver(this, filter);
            // Register for events related to sdcard installation.
            IntentFilter sdFilter = new IntentFilter();
            sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
            sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            mLoader.getContext().registerReceiver(this, sdFilter);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            mLoader.onContentChanged();
        }
    }

}