// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.accessibility.activity.TrackerWindow;
import io.github.muntashirakon.AppManager.utils.ResourceUtil;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;

public class NoRootAccessibilityService extends BaseAccessibilityService {
    private static final CharSequence SETTING_PACKAGE = "com.android.settings";
    private static final CharSequence INSTALLER_PACKAGE = "com.android.packageinstaller";

    private final AccessibilityMultiplexer mMultiplexer = AccessibilityMultiplexer.getInstance();
    private PackageManager mPm;
    private int mTries = 1;
    @Nullable
    private TrackerWindow mTrackerWindow;

    @Override
    public void onCreate() {
        super.onCreate();
        mPm = AppearanceUtils.getSystemContext(this).getPackageManager();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (mMultiplexer.isLeadingActivityTracker()) {
            if (mTrackerWindow == null) {
                mTrackerWindow = new TrackerWindow(this);
            }
            mTrackerWindow.showOrUpdate(event);
        } else if (mTrackerWindow != null) {
            mTrackerWindow.dismiss();
            mTrackerWindow = null;
        }
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence packageName = event.getPackageName();
        if (INSTALLER_PACKAGE.equals(packageName)) {
            automateInstallationUninstallation(event);
            return;
        }
        if (SETTING_PACKAGE.equals(packageName)) {
            // Clear data/cache, force-stop
            if (event.getClassName().equals("com.android.settings.applications.InstalledAppDetailsTop")) {
                AccessibilityNodeInfo node = findViewByText(getString(event, "force_stop"), true);
                if (mMultiplexer.isForceStopEnabled()) {
                    if (node != null) {
                        if (node.isEnabled()) {
                            mTries = 0;
                            performViewClick(node);
                        } else if (mTries > 0 && navigateToStorageAndCache(event)) {
                            // Hack to enable force-stop when it is disabled due to Android bug
                            performBackClick();
                            --mTries;
                        } else performBackClick();
                        node.recycle();
                    } else performBackClick();
                } else if (mMultiplexer.isNavigateToStorageAndCache()) {
                    SystemClock.sleep(1000);
                    navigateToStorageAndCache(event);
                }
            } else if (event.getClassName().equals("com.android.settings.SubSettings")
                    || getString(event, "storage_settings").equals(event.getText().toString())) {
                if (mMultiplexer.isClearDataEnabled()) {
                    performViewClick(findViewByText(getString(event, "clear_user_data_text"), true));
                }
                if (mMultiplexer.isClearCacheEnabled()) {
                    mMultiplexer.enableClearCache(false);
                    AccessibilityNodeInfo node = findViewByText(getString(event, "clear_cache_btn_text"), true);
                    if (node != null) {
                        if (node.isEnabled()) {
                            performViewClick(node);
                        }
                        performBackClick();
                        performBackClick();
                        node.recycle();
                    }
                }
            } else if (event.getClassName().equals("androidx.appcompat.app.AlertDialog")) {
                if (mMultiplexer.isForceStopEnabled() && findViewByText(getString(event, "force_stop_dlg_title")) != null) {
                    mMultiplexer.enableForceStop(false);
                    mTries = 1; // Restore tries
                    performViewClick(findViewByText(getString(event, "dlg_ok"), true));
                    performBackClick();
                }
                if (mMultiplexer.isClearDataEnabled() && findViewByText(getString(event, "clear_data_dlg_title")) != null) {
                    mMultiplexer.enableClearData(false);
                    performViewClick(findViewByText(getString(event, "dlg_ok"), true));
                    performBackClick();
                    performBackClick();
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (mTrackerWindow != null) {
            mTrackerWindow.dismiss();
            mTrackerWindow = null;
        }
        return super.onUnbind(intent);
    }

    private void automateInstallationUninstallation(@NonNull AccessibilityEvent event) {
        if (event.getClassName().equals("android.app.Dialog")) {
            if (mMultiplexer.isInstallEnabled()) {
                // Install
                performViewClick(findViewByText(getString(event, "install"), true)); // install_text
            }
        } else if (event.getClassName().equals("com.android.packageinstaller.UninstallerActivity")) {
            if (mMultiplexer.isUninstallEnabled()) {
                // uninstall
                performViewClick(findViewByText(getString(event, "ok"), true)); // dlg_ok
            }
        }
    }

    private boolean navigateToStorageAndCache(AccessibilityEvent event) {
        String storageSettings;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            storageSettings = getString(event, "storage_settings_for_app");
        } else storageSettings = getString(event, "storage_label");
        SystemClock.sleep(500); // It may take a few moments to initialise the Recycler/List views
        AccessibilityNodeInfo storageNode = findViewByTextRecursive(getRootInActiveWindow(), storageSettings);
        if (storageNode != null) {
            mMultiplexer.enableNavigateToStorageAndCache(false);  // prevent infinite loop
            performViewClick(storageNode);
            storageNode.recycle();
            return true;
        }
        // Failed
        performBackClick();
        return false;
    }

    /**
     * Return the string value associated with a particular resource ID. It will be stripped of any styled text information.
     *
     * @param stringRes The desired resource identifier.
     * @return String The string data associated with the resource, stripped of styled text information.
     * @throws Resources.NotFoundException Throws NotFoundException if the given ID or package does not exist.
     */
    private String getString(@NonNull AccessibilityEvent event, @NonNull String stringRes)
            throws Resources.NotFoundException {
        CharSequence packageName = event.getPackageName();
        CharSequence className = event.getClassName();
        if (TextUtils.isEmpty(packageName)) {
            throw new Resources.NotFoundException("Empty package name");
        }
        ResourceUtil resUtil = new ResourceUtil();
        if (!TextUtils.isEmpty(className)) {
            if (!resUtil.loadResources(mPm, packageName.toString(), className.toString())
                    && !resUtil.loadResources(mPm, packageName.toString())
                    && !resUtil.loadAndroidResources()) {
                throw new Resources.NotFoundException("Couldn't load resources for package: " + packageName
                        + ", class: " + className);
            }
        } else if (!resUtil.loadResources(mPm, packageName.toString()) && !resUtil.loadAndroidResources()) {
            throw new Resources.NotFoundException("Couldn't load resources for package: " + packageName);
        }
        return resUtil.getString(stringRes);
    }
}
