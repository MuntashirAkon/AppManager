// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

import android.content.Context;

public class TestApplication extends AppManager {
    @Override
    public void onCreate() {
        try {
            super.onCreate();
        } catch (Throwable ignore) {
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        try {
            super.attachBaseContext(base);
        } catch (Throwable ignore) {
        }
    }
}
