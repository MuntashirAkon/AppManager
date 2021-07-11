// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server;

import android.app.ActivityThread;
import android.app.Application;
import android.content.Intent;

import io.github.muntashirakon.AppManager.server.common.FLog;

// Copyright 2017 Zheng Li
class BroadcastSender {
    static void sendBroadcast(Intent intent) {
        try {
            Application app = ActivityThread.currentApplication();
            if (app == null) {
                FLog.log("BroadcastSender: NullPointerException " + intent.toString());
                return;
            }
            app.sendBroadcast(intent);
        } catch (Exception e) {
            e.printStackTrace();
            FLog.log(e);
        }
    }
}
