// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.os.Build;
import android.os.RemoteException;
import android.telephony.SubscriptionInfo;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.internal.telephony.IPhoneSubInfo;
import com.android.internal.telephony.ISub;

import java.util.List;

import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.users.Users;

public class SubscriptionManagerCompat {
    @SuppressWarnings("deprecation")
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    @Nullable
    public static List<SubscriptionInfo> getActiveSubscriptionInfoList() {
        try {
            ISub sub = getSub();
            int uid = Users.getSelfOrRemoteUid();
            String callingPackage = SelfPermissions.getCallingPackage(uid);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return sub.getActiveSubscriptionInfoList(callingPackage, null);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return sub.getActiveSubscriptionInfoList(callingPackage);
            }
            return sub.getActiveSubscriptionInfoList();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    @Nullable
    public static String getSubscriberIdForSubscriber(long subId) {
        try {
            IPhoneSubInfo sub = getPhoneSubInfo();
            int uid = Users.getSelfOrRemoteUid();
            String callingPackage = SelfPermissions.getCallingPackage(uid);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return sub.getSubscriberIdForSubscriber((int) subId, callingPackage, null);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return sub.getSubscriberIdForSubscriber((int) subId, callingPackage);
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1) {
                return sub.getSubscriberIdForSubscriber((int) subId);
            }
            return sub.getSubscriberIdForSubscriber(subId);
        } catch (RemoteException | NullPointerException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static ISub getSub() {
        return ISub.Stub.asInterface(ProxyBinder.getService("isub"));
    }

    private static IPhoneSubInfo getPhoneSubInfo() {
        return IPhoneSubInfo.Stub.asInterface(ProxyBinder.getService("iphonesubinfo"));
    }
}
