// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import io.github.muntashirakon.AppManager.self.filecache.InternalCacheCleanerService;
import io.github.muntashirakon.AppManager.servermanager.WifiWaitService;
import io.github.muntashirakon.AppManager.settings.Ops;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (Ops.getMode().equals(Ops.MODE_ADB_WIFI)) {
                // Connect ADB
                Intent serviceIntent = new Intent(context, WifiWaitService.class);
                ContextCompat.startForegroundService(context, serviceIntent);
            }
            // Schedule cache cleaning
            InternalCacheCleanerService.scheduleAlarm(context.getApplicationContext());
        }
    }
}
