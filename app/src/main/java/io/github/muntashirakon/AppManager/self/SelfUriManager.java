// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import android.net.Uri;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class SelfUriManager {
    public static final String APP_MANAGER_SCHEME = "app-manager";
    public static final String SETTINGS_HOST = "settings";
    public static final String DETAILS_HOST = "details";

    @Nullable
    public static UserPackagePair getUserPackagePairFromUri(@Nullable Uri detailsUri) {
        // Required format app-manager://details?id=<pkg>&user=<user_id>
        if (detailsUri == null || !APP_MANAGER_SCHEME.equals(detailsUri.getScheme())
                || !DETAILS_HOST.equals(detailsUri.getHost())) {
            return null;
        }
        String pkg = detailsUri.getQueryParameter("id");
        String userIdStr = detailsUri.getQueryParameter("user");
        if (pkg != null && PackageUtils.validateName(pkg.trim())) {
            int userId;
            if (userIdStr != null && TextUtils.isDigitsOnly(userIdStr)) {
                userId = Integer.parseInt(userIdStr);
            } else userId = UserHandleHidden.myUserId();
            return new UserPackagePair(pkg, userId);
        }
        return null;
    }
}
