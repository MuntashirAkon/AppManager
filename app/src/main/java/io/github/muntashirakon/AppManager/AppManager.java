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

package io.github.muntashirakon.AppManager;

import android.app.Application;
import android.content.Context;
import android.content.pm.IPackageManager;

import com.topjohnwu.superuser.Shell;
import com.yariksoffice.lingver.Lingver;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.toybox.ToyboxInitializer;
import me.weishu.reflection.Reflection;

public class AppManager extends Application {
    private static AppManager instance;

    static {
        Shell.enableVerboseLogging = BuildConfig.DEBUG;
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setInitializers(ToyboxInitializer.class)
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10));
    }

    @NonNull
    public static AppManager getInstance() {
        return instance;
    }

    @NonNull
    public static Context getContext() {
        return instance.getBaseContext();
    }

    public static IPackageManager getIPackageManager() {
        return IPackageManager.Stub.asInterface(ProxyBinder.getService("package"));
    }

    private static boolean isAuthenticated = false;
    public static boolean isAuthenticated() {
        return isAuthenticated;
    }

    public static void setIsAuthenticated(boolean isAuthenticated) {
        AppManager.isAuthenticated = isAuthenticated;
    }

    @Override
    public void onCreate() {
        instance = this;
        super.onCreate();
        Lingver.init(instance, LangUtils.getLocaleByLanguage(instance));
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Reflection.unseal(base);
    }
}
