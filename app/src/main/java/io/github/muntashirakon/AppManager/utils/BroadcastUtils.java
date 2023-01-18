// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.types.PackageChangeReceiver;

public class BroadcastUtils {
    public static void sendPackageAdded(@NonNull Context context, String[] packageNames) {
        Intent intent = new Intent(PackageChangeReceiver.ACTION_PACKAGE_ADDED);
        intent.setPackage(context.getPackageName());
        intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, packageNames);
        context.sendBroadcast(intent);
    }

    public static void sendPackageAltered(@NonNull Context context, String[] packageNames) {
        Intent intent = new Intent(PackageChangeReceiver.ACTION_PACKAGE_ALTERED);
        intent.setPackage(context.getPackageName());
        intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, packageNames);
        context.sendBroadcast(intent);
    }

    public static void sendPackageRemoved(@NonNull Context context, String[] packageNames) {
        Intent intent = new Intent(PackageChangeReceiver.ACTION_PACKAGE_REMOVED);
        intent.setPackage(context.getPackageName());
        intent.putExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST, packageNames);
        context.sendBroadcast(intent);
    }
}
