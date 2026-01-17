// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.sun.security.provider.JavaKeyStoreProvider;

import androidx.annotation.Keep;

import com.topjohnwu.superuser.Shell;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.security.Security;

import dalvik.system.ZipPathValidator;
import io.github.muntashirakon.AppManager.misc.AMExceptionHandler;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;

public class AppManager extends Application {
    static {
        Shell.enableVerboseLogging = BuildConfig.DEBUG;
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // We don't rely on the system to detect a zip slip attack
            ZipPathValidator.clearCallback();
        }
    }

    @Keep
    @Override
    public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler(new AMExceptionHandler(this));
        AppearanceUtils.init(this);
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        Security.addProvider(new JavaKeyStoreProvider());
        Security.addProvider(new BouncyCastleProvider());
    }

    @Keep
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !Utils.isRoboUnitTest()) {
            HiddenApiBypass.addHiddenApiExemptions("L");
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            StaticDataset.cleanup();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        StaticDataset.cleanup();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        StaticDataset.cleanup();
    }
}
