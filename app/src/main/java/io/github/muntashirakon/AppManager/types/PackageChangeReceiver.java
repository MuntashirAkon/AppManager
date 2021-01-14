/*
 * Copyright (C) 2021 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.types;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.BuildConfig;

public abstract class PackageChangeReceiver extends BroadcastReceiver {
    public static final String ACTION_PACKAGE_ALTERED = BuildConfig.APPLICATION_ID + ".action.PACKAGE_ALTERED";

    public PackageChangeReceiver(@NonNull Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        context.registerReceiver(this, filter);
        // Other filters
        IntentFilter sdFilter = new IntentFilter();
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        sdFilter.addAction(Intent.ACTION_LOCALE_CHANGED);
        sdFilter.addAction(ACTION_PACKAGE_ALTERED);
        context.registerReceiver(this, sdFilter);
    }

    protected abstract void onPackageChanged(@Nullable Integer uid, @Nullable String[] packages);

    @Override
    public void onReceive(Context context, @NonNull Intent intent) {
        switch (Objects.requireNonNull(intent.getAction())) {
            case Intent.ACTION_PACKAGE_REMOVED:
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
            case Intent.ACTION_PACKAGE_ADDED:
            case Intent.ACTION_PACKAGE_CHANGED:
                int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
                if (uid != -1) onPackageChanged(uid, null);
                return;
            case Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE:
            case Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE:
                String[] packages = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
                onPackageChanged(null, packages);
                return;
            case Intent.ACTION_LOCALE_CHANGED:
                onPackageChanged(null, null);
        }
    }
}