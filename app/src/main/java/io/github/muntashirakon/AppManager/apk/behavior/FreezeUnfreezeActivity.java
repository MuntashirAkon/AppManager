// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_DISABLED_COMPONENTS;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getBitmapFromDrawable;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getDimmedBitmap;

import android.app.Application;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.util.Pair;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.utils.NotificationUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class FreezeUnfreezeActivity extends BaseActivity {
    private FreezeUnfreezeViewModel mViewModel;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(this).get(FreezeUnfreezeViewModel.class);
        if (!SelfPermissions.canFreezeUnfreezePackages()) {
            UIUtils.displayShortToast(R.string.only_works_in_root_or_adb_mode);
            finishAndRemoveTask();
            return;
        }
        FreezeUnfreeze.ShortcutInfo i = FreezeUnfreeze.getShortcutInfo(getIntent());
        if (i != null) {
            hideNotification(i);
            mViewModel.addToPendingShortcuts(i);
            mViewModel.checkNextFrozen();
        } else {
            finishAndRemoveTask();
            return;
        }
        mViewModel.mIsFrozenLiveData.observe(this, shortcutInfoBooleanPair -> {
            if (shortcutInfoBooleanPair == null) {
                // End of queue reached
                finishAndRemoveTask();
                return;
            }
            FreezeUnfreeze.ShortcutInfo shortcutInfo = shortcutInfoBooleanPair.first;
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
            if (!shortcutInfoBooleanPair.second && (shortcutInfo.flags & FreezeUnfreeze.FLAG_ON_UNFREEZE_OPEN_APP) != 0) {
                FreezeUnfreeze.launchApp(this, shortcutInfo);
            }
            mViewModel.checkNextFrozen();
        });
        mViewModel.mOpenAppOrFreeze.observe(this, shortcutInfo -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.freeze_unfreeze)
                .setMessage(R.string.choose_what_to_do)
                .setPositiveButton(R.string.open, (dialog, which) -> FreezeUnfreeze.launchApp(this, shortcutInfo))
                .setNegativeButton(R.string.freeze, (dialog, which) -> mViewModel.freezeFinal(shortcutInfo))
                .setOnDismissListener(v -> mViewModel.checkNextFrozen())
                .show());
    }

    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!SelfPermissions.canFreezeUnfreezePackages()) {
            UIUtils.displayShortToast(R.string.only_works_in_root_or_adb_mode);
            finishAndRemoveTask();
            return;
        }
        FreezeUnfreeze.ShortcutInfo shortcutInfo = FreezeUnfreeze.getShortcutInfo(getIntent());
        if (mViewModel != null && shortcutInfo != null) {
            hideNotification(shortcutInfo);
            mViewModel.addToPendingShortcuts(shortcutInfo);
        }
    }

    private void hideNotification(@Nullable FreezeUnfreeze.ShortcutInfo shortcutInfo) {
        if (shortcutInfo == null) return;
        int notificationId = shortcutInfo.hashCode();
        NotificationUtils.getFreezeUnfreezeNotificationManager(this).cancel(notificationId);
    }

    public static class FreezeUnfreezeViewModel extends AndroidViewModel {
        private final MutableLiveData<Pair<FreezeUnfreeze.ShortcutInfo, Boolean>> mIsFrozenLiveData = new MutableLiveData<>();
        private final MutableLiveData<FreezeUnfreeze.ShortcutInfo> mOpenAppOrFreeze = new MutableLiveData<>();
        private final Queue<FreezeUnfreeze.ShortcutInfo> mPendingShortcuts = new LinkedList<>();

        public FreezeUnfreezeViewModel(@NonNull Application application) {
            super(application);
        }

        public void addToPendingShortcuts(@NonNull FreezeUnfreeze.ShortcutInfo shortcutInfo) {
            synchronized (mPendingShortcuts) {
                mPendingShortcuts.add(shortcutInfo);
            }
        }

        public void checkNextFrozen() {
            ThreadUtils.postOnBackgroundThread(() -> {
                FreezeUnfreeze.ShortcutInfo shortcutInfo;
                synchronized (mPendingShortcuts) {
                    shortcutInfo = mPendingShortcuts.poll();
                }
                if (shortcutInfo == null) {
                    mIsFrozenLiveData.postValue(null);
                    return;
                }
                boolean forceFreeze = (shortcutInfo.getPrivateFlags() & FreezeUnfreeze.PRIVATE_FLAG_FREEZE_FORCE) != 0;
                try {
                    ApplicationInfo applicationInfo = PackageManagerCompat.getApplicationInfo(shortcutInfo.packageName,
                            MATCH_UNINSTALLED_PACKAGES | MATCH_DISABLED_COMPONENTS
                                    | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, shortcutInfo.userId);
                    Bitmap icon = getBitmapFromDrawable(applicationInfo.loadIcon(getApplication().getPackageManager()));
                    shortcutInfo.setLabel(applicationInfo.loadLabel(getApplication().getPackageManager()).toString());
                    boolean isFrozen = !forceFreeze && FreezeUtils.isFrozen(applicationInfo);
                    if (isFrozen) {
                        FreezeUtils.unfreeze(shortcutInfo.packageName, shortcutInfo.userId);
                        shortcutInfo.setIcon(icon);
                    } else {
                        shortcutInfo.setIcon(getDimmedBitmap(icon));
                        if (!forceFreeze && (shortcutInfo.flags & FreezeUnfreeze.FLAG_ON_UNFREEZE_OPEN_APP) != 0) {
                            // Ask whether to open or freeze the app
                            mOpenAppOrFreeze.postValue(shortcutInfo);
                            return;
                        }
                        FreezeUtils.freeze(shortcutInfo.packageName, shortcutInfo.userId);
                    }
                    mIsFrozenLiveData.postValue(new Pair<>(shortcutInfo, !isFrozen));
                } catch (RemoteException | PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
            });
        }

        public void freezeFinal(FreezeUnfreeze.ShortcutInfo shortcutInfo) {
            ThreadUtils.postOnBackgroundThread(() -> {
                try {
                    FreezeUtils.freeze(shortcutInfo.packageName, shortcutInfo.userId);
                    mIsFrozenLiveData.postValue(new Pair<>(shortcutInfo, true));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            });
        }
    }
}
