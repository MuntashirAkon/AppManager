// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.github.muntashirakon.AppManager.self.filecache.InternalCacheCleanerService;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            InternalCacheCleanerService.scheduleAlarm(context.getApplicationContext());
        }
    }
}
