/*
 * Copyright (c) 2021 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.logcat;

import android.content.Intent;
import android.util.Log;

import java.util.Random;

import io.github.muntashirakon.AppManager.types.ForegroundService;

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
    private boolean kill = false;

    public CrazyLoggerService() {
        super(TAG);
    }

    protected void onHandleIntent(Intent intent) {
        while (!kill) {
            try {
                Thread.sleep(INTERVAL);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted", e);
            }
            if (new Random().nextInt(100) % 5 == 0) {
                Log.println(LOG_LEVELS[new Random().nextInt(6)], TAG, LOG_MESSAGES[new Random().nextInt(5)]);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        kill = true;
    }
}
