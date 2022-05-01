// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager;

import android.app.Application;
import android.content.Context;
import android.content.pm.IPackageManager;
import android.os.Build;
import android.sun.security.provider.JavaKeyStoreProvider;

import androidx.annotation.NonNull;
import androidx.room.Room;

import com.google.android.material.color.DynamicColors;
import com.topjohnwu.superuser.Shell;
import com.yariksoffice.lingver.Lingver;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.security.Security;

import io.github.muntashirakon.AppManager.db.AMDatabase;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class AppManager extends Application {
    private static AppManager instance;
    private static AMDatabase db;

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

    public static IPackageManager getIPackageManager() {
        return IPackageManager.Stub.asInterface(ProxyBinder.getService("package"));
    }

    @NonNull
    public static synchronized AMDatabase getDb() {
        if (db == null) {
            db = Room.databaseBuilder(getContext(), AMDatabase.class, "am")
                    .addMigrations(AMDatabase.MIGRATION_1_2, AMDatabase.MIGRATION_2_3, AMDatabase.MIGRATION_3_4,
                            AMDatabase.MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return db;
    }

    @Override
    public void onCreate() {
        instance = this;
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
        Lingver.init(instance, LangUtils.getLocaleByLanguage(instance));
        Security.addProvider(new JavaKeyStoreProvider());
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L");
        }
    }
}
