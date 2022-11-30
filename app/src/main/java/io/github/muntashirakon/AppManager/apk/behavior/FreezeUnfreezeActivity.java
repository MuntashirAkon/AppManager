// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior;

import android.app.Application;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

import static io.github.muntashirakon.AppManager.utils.FileUtils.dimBitmap;
import static io.github.muntashirakon.AppManager.utils.FileUtils.getBitmapFromDrawable;

public class FreezeUnfreezeActivity extends BaseActivity {
    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        FreezeUnfreezeViewModel viewModel = new ViewModelProvider(this).get(FreezeUnfreezeViewModel.class);
        FreezeUnfreeze.ShortcutInfo shortcutInfo = FreezeUnfreeze.getShortcutInfo(getIntent());
        if (shortcutInfo != null) {
            viewModel.checkFrozen(shortcutInfo);
        } else {
            finishAndRemoveTask();
            return;
        }
        viewModel.isFrozenLiveData.observe(this, frozen -> {
            if (frozen == null) {
                finishAndRemoveTask();
                return;
            }
            Intent intent = FreezeUnfreeze.getShortcutIntent(this, shortcutInfo);

            // Set action for shortcut
            intent.setAction(Intent.ACTION_CREATE_SHORTCUT);

            ShortcutInfoCompat shortcutInfoCompat = new ShortcutInfoCompat.Builder(this, shortcutInfo.shortcutId)
                    .setShortLabel(shortcutInfo.getLabel())
                    .setLongLabel(shortcutInfo.getLabel())
                    .setIcon(IconCompat.createWithBitmap(shortcutInfo.getIcon()))
                    .setIntent(intent)
                    .build();

            ShortcutManagerCompat.updateShortcuts(this, Collections.singletonList(shortcutInfoCompat));

            // Launch app if requested
            if (!frozen && (shortcutInfo.flags & FreezeUnfreeze.FLAG_ON_UNFREEZE_OPEN_APP) != 0) {
                FreezeUnfreeze.launchApp(this, shortcutInfo);
            }
            finishAndRemoveTask();
        });
    }

    public static class FreezeUnfreezeViewModel extends AndroidViewModel {
        private final MutableLiveData<Boolean> isFrozenLiveData = new MutableLiveData<>();
        private final ExecutorService executor = Executors.newFixedThreadPool(1);

        public FreezeUnfreezeViewModel(@NonNull Application application) {
            super(application);
        }

        public void checkFrozen(@NonNull FreezeUnfreeze.ShortcutInfo shortcutInfo) {
            executor.submit(() -> {
                try {
                    ApplicationInfo applicationInfo = PackageManagerCompat.getApplicationInfo(shortcutInfo.packageName, PackageUtils.flagMatchUninstalled | PackageUtils.flagDisabledComponents, shortcutInfo.userId);
                    Bitmap icon = getBitmapFromDrawable(applicationInfo.loadIcon(getApplication().getPackageManager()));
                    shortcutInfo.setLabel(applicationInfo.loadLabel(getApplication().getPackageManager()).toString());
                    boolean isFrozen = FreezeUtils.isFrozen(applicationInfo);
                    if (isFrozen) {
                        FreezeUtils.unfreeze(shortcutInfo.packageName, shortcutInfo.userId);
                    } else {
                        FreezeUtils.freeze(shortcutInfo.packageName, shortcutInfo.userId);
                        dimBitmap(icon);
                    }
                    shortcutInfo.setIcon(icon);
                    isFrozenLiveData.postValue(!isFrozen);
                } catch (RemoteException e) {
                    e.printStackTrace();
                    isFrozenLiveData.postValue(null);
                }
            });
        }
    }
}
