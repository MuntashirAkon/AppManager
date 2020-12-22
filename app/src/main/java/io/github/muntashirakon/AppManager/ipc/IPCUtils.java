package io.github.muntashirakon.AppManager.ipc;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.io.File;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.IAMService;
import io.github.muntashirakon.AppManager.logs.Log;

public final class IPCUtils {
    private static final String TAG = "IPCUtils";

    private static IAMService amService;
    private static final AMServiceConnection conn = new AMServiceConnection();

    static class AMServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.e(TAG, "service onServiceConnected");
            amService = IAMService.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.e(TAG, "service onServiceDisconnected");
        }

        @Override
        public void onBindingDied(ComponentName name) {
            amService = null;
        }

        @Override
        public void onNullBinding(ComponentName name) {
            amService = null;
        }
    }

    public static void startDaemon(Context context) {
        if (amService == null) {
            Log.e(TAG, "Launching service...");
            Intent intent = new Intent(context, AMService.class);
            RootService.bind(intent, conn);
            // Wait for service to be bound
            int i = 0;
            while (amService == null) {  // No workaround for spin-lock
                try {
                    if (i % 20 == 0) {
                        Log.i(TAG, "Waiting for AMService to be bound");
                    }
                    Thread.sleep(100);
                    if (i > 1000)
                        break;
                    ++i;
                } catch (InterruptedException e) {
                    Log.e(TAG, "startDaemon: interrupted", e);
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public static IAMService getAmService(Context context) {
        if (amService == null) {
            startDaemon(context);
        }
        return amService;
    }

    public static void stopDaemon(Context context) {
        Intent intent = new Intent(context, AMService.class);
        // Use stop here instead of unbind because AIDLService is running as a daemon.
        // To verify whether the daemon actually works, kill the app and try testing the
        // daemon again. You should get the same PID as last time (as it was re-using the
        // previous daemon process), and in AIDLService, onRebind should be called.
        // Note: re-running the app in Android Studio is not the same as kill + relaunch.
        // The root service will kill itself when it detects the client APK has updated.
        RootService.stop(intent);
        amService = null;
    }
}
