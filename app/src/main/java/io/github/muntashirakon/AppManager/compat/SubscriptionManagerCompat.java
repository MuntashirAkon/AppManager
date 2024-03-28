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
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ExUtils;

public class SubscriptionManagerCompat {
    public static final String TAG = SubscriptionManagerCompat.class.getSimpleName();

    @SuppressWarnings("deprecation")
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    @Nullable
    public static List<SubscriptionInfo> getActiveSubscriptionInfoList() {
        try {
            ISub sub = getSub();
            if (sub == null) {
                return null;
            }
            int uid = Users.getSelfOrRemoteUid();
            String callingPackage = SelfPermissions.getCallingPackage(uid);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    return sub.getActiveSubscriptionInfoList(callingPackage, null);
                } catch (NoSuchMethodError e) {
                    // Google Pixel
                    return sub.getActiveSubscriptionInfoList(callingPackage, null, true);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return sub.getActiveSubscriptionInfoList(callingPackage, null);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return sub.getActiveSubscriptionInfoList(callingPackage);
            }
            return sub.getActiveSubscriptionInfoList();
        } catch (RemoteException e) {
            return ExUtils.rethrowFromSystemServer(e);
        }
    }

    @SuppressWarnings("deprecation")
    @Nullable
    public static String getSubscriberIdForSubscriber(long subId) {
        try {
            IPhoneSubInfo sub = getPhoneSubInfo();
            if (sub == null) {
                return null;
            }
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
        } catch (RemoteException e) {
            return ExUtils.rethrowFromSystemServer(e);
        } catch (NullPointerException ignore) {
        }
        return null;
    }

    @Nullable
    private static ISub getSub() {
        try {
            return ISub.Stub.asInterface(ProxyBinder.getService("isub"));
        } catch (NullPointerException e) {
            Log.i(TAG, "No isub, Huawei GSI?", e);
            return null;
        }
    }

    @Nullable
    private static IPhoneSubInfo getPhoneSubInfo() {
        try {
            return IPhoneSubInfo.Stub.asInterface(ProxyBinder.getService("iphonesubinfo"));
        } catch (NullPointerException e) {
            Log.i(TAG, "No iphonesubinfo, Huawei GSI?", e);
            return null;
        }
    }
}
