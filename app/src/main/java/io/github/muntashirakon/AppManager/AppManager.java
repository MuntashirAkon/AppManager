// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.sun.security.provider.JavaKeyStoreProvider;

import androidx.annotation.NonNull;

import com.google.android.material.color.DynamicColors;
import com.topjohnwu.superuser.Shell;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.security.Security;

import io.github.muntashirakon.AppManager.misc.AMExceptionHandler;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;

public class AppManager extends Application {
    private static AppManager instance;

    static {
        Shell.enableVerboseLogging = BuildConfig.DEBUG;
        Shell.setDefaultBuilder(Shell.Builder.create()
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

    @Override
    public void onCreate() {
        instance = this;
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler(new AMExceptionHandler(this));
        DynamicColors.applyToActivitiesIfAvailable(this);
        AppearanceUtils.init(this);
        Security.addProvider(new JavaKeyStoreProvider());
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !Utils.isRoboUnitTest()) {
            HiddenApiBypass.addHiddenApiExemptions("L");
        }
    }
}
