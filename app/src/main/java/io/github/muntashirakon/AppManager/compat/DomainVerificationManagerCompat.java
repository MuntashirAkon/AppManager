// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.content.Context;
import android.content.pm.verify.domain.DomainVerificationUserState;
import android.content.pm.verify.domain.IDomainVerificationManager;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import io.github.muntashirakon.AppManager.ipc.ProxyBinder;

@RequiresApi(Build.VERSION_CODES.S)
public class DomainVerificationManagerCompat {
    @Nullable
    public static DomainVerificationUserState getDomainVerificationUserState(String packageName, int userId) {
        try {
            return getDomainVerificationManager().getDomainVerificationUserState(packageName, userId);
        } catch (RemoteException ignore) {
        }
        return null;
    }

    @RequiresPermission(ManifestCompat.permission.UPDATE_DOMAIN_VERIFICATION_USER_SELECTION)
    public static void setDomainVerificationLinkHandlingAllowed(String packageName, boolean allowed, int userId)
            throws RemoteException {
        getDomainVerificationManager().setDomainVerificationLinkHandlingAllowed(packageName, allowed, userId);
    }

    public static IDomainVerificationManager getDomainVerificationManager() {
        return IDomainVerificationManager.Stub.asInterface(ProxyBinder.getService(Context.DOMAIN_VERIFICATION_SERVICE));
    }
}
