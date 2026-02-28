/*
 * Copyright (C) 2019 The Android Open Source Project
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

package io.github.muntashirakon.AppManager.overlays;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import android.os.Build;
import android.os.FabricatedOverlayInfo;
import android.os.FabricatedOverlayInternal;
import android.os.Handler;
import android.os.IBinder;
import android.os.IIdmap2;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;

import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.ipc.RemoteProcess;
import io.github.muntashirakon.AppManager.logs.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * To prevent idmap2d from continuously running, the idmap daemon will terminate after 10 seconds
 * without a transaction.
 **/
@RequiresApi(Build.VERSION_CODES.P)
public class IdmapDaemon {
    private static final String IDMAP_SERVICE = "idmap";
    private static Handler handler = new Handler(Looper.getMainLooper());
    private static final String TAG = IdmapDaemon.class.getSimpleName();
    // The amount of time in milliseconds to wait after a transaction to the idmap service is made
    // before stopping the service.
    private static final int SERVICE_TIMEOUT_MS = 10000;

    // The device may enter CPU sleep while waiting for the service startup, and in that mode
    // the uptime doesn't increment. Thus, we need to have two timeouts: a smaller one for the
    // uptime and a longer one for the wall time in case when the device never advances the uptime,
    // so the watchdog won't get triggered.

    // The amount of uptime in milliseconds to wait when attempting to connect to idmap service.
    private static final int SERVICE_CONNECT_UPTIME_TIMEOUT_MS = 5000;
    // The amount of wall time in milliseconds to wait.
    private static final int SERVICE_CONNECT_WALLTIME_TIMEOUT_MS = 30000;
    private static final int SERVICE_CONNECT_INTERVAL_SLEEP_MS = 5;

    private static final String IDMAP_DAEMON = "idmap2d";

    private static IdmapDaemon sInstance;
    private volatile IIdmap2 mService;
    private final AtomicInteger mOpenedCount = new AtomicInteger();
    private final Object mIdmapToken = new Object();

    /**
     * An {@link AutoCloseable} connection to the idmap service. When the connection is closed or
     * finalized, the idmap service will be stopped after a period of time unless another connection
     * to the service is open.
     **/
    private class Connection implements AutoCloseable {
        @Nullable
        private final IIdmap2 mIdmap2;
        private boolean mOpened = true;

        private Connection(IIdmap2 idmap2) {
            synchronized (mIdmapToken) {
                mOpenedCount.incrementAndGet();
                mIdmap2 = idmap2;
            }
        }


        @Override
        public void close() {
            synchronized (mIdmapToken) {
                if (!mOpened) {
                    return;
                }

                mOpened = false;
                if (mOpenedCount.decrementAndGet() != 0) {
                    // Only post the callback to stop the service if the service does not have an
                    // open connection.
                    return;
                }
                handler.postDelayed(() -> {
                    synchronized (mIdmapToken) {
                        // Only stop the service if the service does not have an open connection.
                        if (mService == null || mOpenedCount.get() != 0) {
                            return;
                        }

                        stopIdmapService();
                        mService = null;
                    }
                }, mIdmapToken, SERVICE_TIMEOUT_MS);
            }
        }

        @Nullable
        public IIdmap2 getIdmap2() {
            return mIdmap2;
        }
    }

    public static IdmapDaemon getInstance() {
        if (sInstance == null) {
            sInstance = new IdmapDaemon();
        }
        return sInstance;
    }

    public String createIdmap(@NonNull String targetPath, @NonNull String overlayPath,
                       @Nullable String overlayName, int policies, boolean enforce, int userId)
            throws TimeoutException, RemoteException {
        try (Connection c = connect()) {
            final IIdmap2 idmap2 = c.getIdmap2();
            if (idmap2 == null) {
                Log.w(TAG, "idmap2d service is not ready for createIdmap(\"" + targetPath
                        + "\", \"" + overlayPath + "\", \"" + overlayName + "\", " + policies + ", "
                        + enforce + ", " + userId + ")");
                return null;
            }

            return idmap2.createIdmap(targetPath, overlayPath, overlayName==null?"":overlayName,
                    policies, enforce, userId);
        }
    }

    public boolean removeIdmap(String overlayPath, int userId) throws TimeoutException, RemoteException {
        try (Connection c = connect()) {
            final IIdmap2 idmap2 = c.getIdmap2();
            if (idmap2 == null) {
                Log.w(TAG, "idmap2d service is not ready for removeIdmap(\"" + overlayPath
                        + "\", " + userId + ")");
                return false;
            }

            return idmap2.removeIdmap(overlayPath, userId);
        }
    }

    public boolean verifyIdmap(@NonNull String targetPath, @NonNull String overlayPath,
                        @Nullable String overlayName, int policies, boolean enforce, int userId)
            throws Exception {
        try (Connection c = connect()) {
            final IIdmap2 idmap2 = c.getIdmap2();
            if (idmap2 == null) {
                Log.w(TAG, "idmap2d service is not ready for verifyIdmap(\"" + targetPath
                        + "\", \"" + overlayPath + "\", \"" + overlayName + "\", " + policies + ", "
                        + enforce + ", " + userId + ")");
                return false;
            }

            return idmap2.verifyIdmap(targetPath, overlayPath, overlayName==null?"":overlayName,
                    policies, enforce, userId);
        }
    }

    public boolean idmapExists(String overlayPath, int userId) {
        try (Connection c = connect()) {
            final IIdmap2 idmap2 = c.getIdmap2();
            if (idmap2 == null) {
                Log.w(TAG, "idmap2d service is not ready for idmapExists(\"" + overlayPath
                        + "\", " + userId + ")");
                return false;
            }

            return new File(idmap2.getIdmapPath(overlayPath, userId)).isFile();
        } catch (Exception e) {
            Log.e(TAG, "failed to check if idmap exists for " + overlayPath, e);
            return false;
        }
    }

    public FabricatedOverlayInfo createFabricatedOverlay(@NonNull FabricatedOverlayInternal overlay) throws RemoteException{
        try (Connection c = connect()) {
            final IIdmap2 idmap2 = c.getIdmap2();
            if (idmap2 == null) {
                Log.w(TAG, "idmap2d service is not ready for createFabricatedOverlay()");
                return null;
            }
            return idmap2.createFabricatedOverlay(overlay);
        } catch (TimeoutException e) {
            Log.e(TAG, "failed to fabricate overlay " + overlay, e);
            throw new RemoteException(e.getMessage());
        }
    }

    public boolean deleteFabricatedOverlay(@NonNull String path) throws RemoteException {
        try (Connection c = connect()) {
            final IIdmap2 idmap2 = c.getIdmap2();
            if (idmap2 == null) {
                Log.w(TAG, "idmap2d service is not ready for deleteFabricatedOverlay(\"" + path
                        + "\")");
                return false;
            }

            return idmap2.deleteFabricatedOverlay(path);
        } catch (Exception e) {
            Log.e(TAG, "failed to delete fabricated overlay '" + path + "'", e);
            throw new RemoteException(e.getMessage());
        }
    }

    public synchronized List<FabricatedOverlayInfo> getFabricatedOverlayInfos() {
        final ArrayList<FabricatedOverlayInfo> allInfos = new ArrayList<>();
        Connection c = null;
        int iteratorId = -1;
        try {
            c = connect();
            final IIdmap2 service = c.getIdmap2();
            if (service == null) {
                Log.w(TAG, "idmap2d service is not ready for getFabricatedOverlayInfos()");
                return Collections.emptyList();
            }

            iteratorId = service.acquireFabricatedOverlayIterator();
            List<FabricatedOverlayInfo> infos;
            while (!(infos = service.nextFabricatedOverlayInfos(iteratorId)).isEmpty()) {
                allInfos.addAll(infos);
            }
            return allInfos;
        } catch (Exception e) {
            Log.e(TAG, "failed to get all fabricated overlays", e);
        } finally {
            try {
                if (c != null && c.getIdmap2() != null && iteratorId != -1) {
                    c.getIdmap2().releaseFabricatedOverlayIterator(iteratorId);
                }
            } catch (RemoteException e) {
                // ignore
            }
            if (c!=null) {
                c.close();
                }
        }
        return allInfos;
    }

    public String dumpIdmap(@NonNull String overlayPath) {
        try (Connection c = connect()) {
            final IIdmap2 service = c.getIdmap2();
            if (service == null) {
                final String dumpText = "idmap2d service is not ready for dumpIdmap()";
                Log.w(TAG, dumpText);
                return dumpText;
            }
            String dump = service.dumpIdmap(overlayPath);
            return dump==null?"":dump;
        } catch (Exception e) {
            Log.e(TAG, "failed to dump idmap", e);
            return null;
        }
    }

    @Nullable
    private IBinder getIdmapService() throws TimeoutException, RemoteException {
        try {
            LocalServices.getAmService().setprop("ctl.start", IDMAP_DAEMON);
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to enable idmap2 daemon", e);
            if (Objects.requireNonNull(e.getMessage()).contains("failed to set system property")) {
                return null;
            }
        }

        long uptimeMillis = SystemClock.uptimeMillis();
        final long endUptimeMillis = uptimeMillis + SERVICE_CONNECT_UPTIME_TIMEOUT_MS;
        long walltimeMillis = SystemClock.elapsedRealtime();
        final long endWalltimeMillis = walltimeMillis + SERVICE_CONNECT_WALLTIME_TIMEOUT_MS;

        do {
            final IBinder binder = LocalServices.getAmService().getService(IDMAP_SERVICE);
            if (binder != null) {
                binder.linkToDeath(
                        () -> Log.w(TAG,
                                String.format("AM Debug service '%s' died", IDMAP_SERVICE)), 0);
                return new ProxyBinder(binder);
            }
            SystemClock.sleep(SERVICE_CONNECT_INTERVAL_SLEEP_MS);
        } while ((uptimeMillis = SystemClock.uptimeMillis()) <= endUptimeMillis
                && (walltimeMillis = SystemClock.elapsedRealtime()) <= endWalltimeMillis);

        throw new TimeoutException(
                String.format("Failed to connect to '%s' in %d/%d ms (spent %d/%d ms)",
                        IDMAP_SERVICE, SERVICE_CONNECT_UPTIME_TIMEOUT_MS,
                        SERVICE_CONNECT_WALLTIME_TIMEOUT_MS,
                        uptimeMillis - endUptimeMillis + SERVICE_CONNECT_UPTIME_TIMEOUT_MS,
                        walltimeMillis - endWalltimeMillis + SERVICE_CONNECT_WALLTIME_TIMEOUT_MS));
    }

    private static void stopIdmapService() {
        try {
            LocalServices.getAmService().setprop("ctl.stop", IDMAP_DAEMON);
        } catch (RuntimeException | RemoteException e) {
            // If the idmap daemon cannot be disabled for some reason, it is okay
            // since we already finished invoking idmap.
            Log.w(TAG, "Failed to disable idmap2 daemon", e);
        }
    }

    @NonNull
    private Connection connect() throws TimeoutException, RemoteException {
        synchronized (mIdmapToken) {
            handler.removeCallbacksAndMessages(mIdmapToken);
            if (mService != null) {
                // Not enough time has passed to stop the idmap service. Reuse the existing
                // interface.
                return new Connection(mService);
            }

            IBinder binder = getIdmapService();
            if (binder == null) {
                return new Connection(null);
            }

            mService = IIdmap2.Stub.asInterface(binder);
            return new Connection(mService);
        }
    }
}
