/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util;

import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.CallbackRegistry.NotifierCallback;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.Executor;

/**
 * {@link ObservableServiceConnection} is a concrete implementation of {@link ServiceConnection}
 * that enables monitoring the status of a binder connection. It also aides in automatically
 * converting a proxy into an internal wrapper type.
 *
 * @param <T> The type of the wrapper over the resulting service.
 */
public class ObservableServiceConnection<T> implements ServiceConnection {
    /**
     * An interface for converting the service proxy into a given internal wrapper type.
     *
     * @param <T> The type of the wrapper over the resulting service.
     */
    public interface ServiceTransformer<T> {
        /**
         * Called to convert the service proxy to the wrapper type.
         *
         * @param service The service proxy to create the wrapper type from.
         * @return The wrapper type.
         */
        T convert(IBinder service);
    }

    /**
     * An interface for listening to the connection status.
     *
     * @param <T> The wrapper type.
     */
    public interface Callback<T> {
        /**
         * Invoked when the service has been successfully connected to.
         *
         * @param connection The {@link ObservableServiceConnection} instance that is now connected
         * @param service    The service proxy converted into the typed wrapper.
         */
        void onConnected(ObservableServiceConnection<T> connection, T service);

        /**
         * Invoked when the service has been disconnected.
         *
         * @param connection The {@link ObservableServiceConnection} that is now disconnected.
         * @param reason     The reason for the disconnection.
         */
        void onDisconnected(ObservableServiceConnection<T> connection,
                @DisconnectReason int reason);
    }

    /**
     * Default state, service has not yet disconnected.
     */
    public static final int DISCONNECT_REASON_NONE = 0;
    /**
     * Disconnection was due to the resulting binding being {@code null}.
     */
    public static final int DISCONNECT_REASON_NULL_BINDING = 1;
    /**
     * Disconnection was due to the remote end disconnecting.
     */
    public static final int DISCONNECT_REASON_DISCONNECTED = 2;
    /**
     * Disconnection due to the binder dying.
     */
    public static final int DISCONNECT_REASON_BINDING_DIED = 3;
    /**
     * Disconnection from an explicit unbinding.
     */
    public static final int DISCONNECT_REASON_UNBIND = 4;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            DISCONNECT_REASON_NONE,
            DISCONNECT_REASON_NULL_BINDING,
            DISCONNECT_REASON_DISCONNECTED,
            DISCONNECT_REASON_BINDING_DIED,
            DISCONNECT_REASON_UNBIND
    })
    public @interface DisconnectReason {
    }

    private final Object mLock = new Object();
    private final Context mContext;
    private final Executor mExecutor;
    private final ServiceTransformer<T> mTransformer;
    private final Intent mServiceIntent;
    private final int mFlags;

    @GuardedBy("mLock")
    private T mService;
    @GuardedBy("mLock")
    private boolean mBoundCalled = false;
    @GuardedBy("mLock")
    private int mLastDisconnectReason = DISCONNECT_REASON_NONE;

    private final CallbackRegistry<Callback<T>, ObservableServiceConnection<T>, T>
            mCallbackRegistry = new CallbackRegistry<>(
            new NotifierCallback<Callback<T>, ObservableServiceConnection<T>, T>() {
                    @Override
                    public void onNotifyCallback(Callback<T> callback,
                            ObservableServiceConnection<T> sender,
                            int disconnectReason, T service) {
                        mExecutor.execute(() -> {
                            synchronized (mLock) {
                                if (service != null) {
                                    callback.onConnected(sender, service);
                                } else if (mLastDisconnectReason != DISCONNECT_REASON_NONE) {
                                    callback.onDisconnected(sender, disconnectReason);
                                }
                            }
                        });
                    }
                });

    /**
     * Default constructor for {@link ObservableServiceConnection}.
     *
     * @param context     The context from which the service will be bound with.
     * @param executor    The executor for connection callbacks to be delivered on
     * @param transformer A {@link ObservableServiceConnection.ServiceTransformer} for transforming
     *                    the resulting service into a desired type.
     */
    public ObservableServiceConnection(@NonNull Context context,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull ServiceTransformer<T> transformer,
            Intent serviceIntent,
            int flags) {
        mContext = context;
        mExecutor = executor;
        mTransformer = transformer;
        mServiceIntent = serviceIntent;
        mFlags = flags;
    }

    /**
     * Initiate binding to the service.
     *
     * @return {@code true} if initiating binding succeed, {@code false} if the binding failed or
     * if this service is already bound. Regardless of the return value, you should later call
     * {@link #unbind()} to release the connection.
     */
    public boolean bind() {
        synchronized (mLock) {
            if (mBoundCalled) {
                return false;
            }
            final boolean bindResult =
                    mContext.bindService(mServiceIntent, mFlags, mExecutor, this);
            mBoundCalled = true;
            return bindResult;
        }
    }

    /**
     * Disconnect from the service if bound.
     */
    public void unbind() {
        onDisconnected(DISCONNECT_REASON_UNBIND);
    }

    /**
     * Adds a callback for receiving connection updates.
     *
     * @param callback The {@link Callback} to receive future updates.
     */
    public void addCallback(Callback<T> callback) {
        mCallbackRegistry.add(callback);
        mExecutor.execute(() -> {
            synchronized (mLock) {
                if (mService != null) {
                    callback.onConnected(this, mService);
                } else if (mLastDisconnectReason != DISCONNECT_REASON_NONE) {
                    callback.onDisconnected(this, mLastDisconnectReason);
                }
            }
        });
    }

    /**
     * Removes previously added callback from receiving future connection updates.
     *
     * @param callback The {@link Callback} to be removed.
     */
    public void removeCallback(Callback<T> callback) {
        synchronized (mLock) {
            mCallbackRegistry.remove(callback);
        }
    }

    private void onDisconnected(@DisconnectReason int reason) {
        synchronized (mLock) {
            if (!mBoundCalled) {
                return;
            }
            mBoundCalled = false;
            mLastDisconnectReason = reason;
            mContext.unbindService(this);
            mService = null;
            mCallbackRegistry.notifyCallbacks(this, reason, null);
        }
    }

    @Override
    public final void onServiceConnected(ComponentName name, IBinder service) {
        synchronized (mLock) {
            mService = mTransformer.convert(service);
            mLastDisconnectReason = DISCONNECT_REASON_NONE;
            mCallbackRegistry.notifyCallbacks(this, mLastDisconnectReason, mService);
        }
    }

    @Override
    public final void onServiceDisconnected(ComponentName name) {
        onDisconnected(DISCONNECT_REASON_DISCONNECTED);
    }

    @Override
    public final void onBindingDied(ComponentName name) {
        onDisconnected(DISCONNECT_REASON_BINDING_DIED);
    }

    @Override
    public final void onNullBinding(ComponentName name) {
        onDisconnected(DISCONNECT_REASON_NULL_BINDING);
    }
}
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
            mTrackerWindow.showOrUpdate(AccessibilityEvent.obtain(event));
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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                storageSettings = getString(event, "storage_settings_for_app");
            } else storageSettings = getString(event, "storage_label");
        } catch (Resources.NotFoundException e) {
            // Failed: non-AOSP device
            return false;
        }
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
