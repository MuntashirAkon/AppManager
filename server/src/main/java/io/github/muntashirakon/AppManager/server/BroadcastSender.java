/*
 * Copyright (C) 2020 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.server;

import android.app.ActivityThread;
import android.app.Application;
import android.content.Intent;

import io.github.muntashirakon.AppManager.server.common.FLog;

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
