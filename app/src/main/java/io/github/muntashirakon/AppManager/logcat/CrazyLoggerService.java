// SPDX-License-Identifier: WTFPL AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import java.util.Random;

import io.github.muntashirakon.AppManager.types.ForegroundService;

// Copyright 2012 Nolan Lawson
public class CrazyLoggerService extends ForegroundService {
    public static final String TAG = CrazyLoggerService.class.getSimpleName();

    public static final int[] LOG_LEVELS = new int[]{Log.VERBOSE, Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR, Log.ASSERT};
    public static final String[] LOG_MESSAGES = new String[]{
            "Email: email@me.com",
            "FTP: ftp://website.com:21",
            "HTTP: https://website.com",
            "A simple log",
            "Another log"
    };

    private static final long INTERVAL = 300;
    private boolean mKill = false;

    public CrazyLoggerService() {
        super(TAG);
    }

    protected void onHandleIntent(Intent intent) {
        while (!mKill) {
            SystemClock.sleep(INTERVAL);
            if (new Random().nextInt(100) % 5 == 0) {
                Log.println(LOG_LEVELS[new Random().nextInt(6)], TAG, LOG_MESSAGES[new Random().nextInt(5)]);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mKill = true;
    }
}
