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

package io.github.muntashirakon.AppManager.utils;

import android.os.Build;
import android.os.HandlerThread;
import android.os.Process;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.io.ProxyFile;

@RequiresApi(Build.VERSION_CODES.O)
public class SsaidSettings {
    @SuppressWarnings("FieldCanBeLocal")
    private final Object lock = new Object();
    private final int uid;
    private final String packageName;
    private final SettingsState settingsState;

    @WorkerThread
    public SsaidSettings(String packageName, int uid) {
        this.uid = uid;
        this.packageName = packageName;
        HandlerThread thread = new HandlerThread("SSAID", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        int ssaidKey = SettingsState.makeKey(SettingsState.SETTINGS_TYPE_SSAID, 0);
        settingsState = new SettingsState(AppManager.getContext(), lock,
                new ProxyFile(OsEnvironment.getUserSystemDirectory(Users.getUserHandle(uid)),
                        "settings_ssaid.xml"), ssaidKey, SettingsState.MAX_BYTES_PER_APP_PACKAGE_UNLIMITED,
                thread.getLooper());
    }

    public String getSsaid() {
        return settingsState.getSettingLocked(packageName.equals("android") ? "userkey" : String.valueOf(uid)).getValue();
    }

//    public String setSsaid() {
//        //
//    }
}
