// SPDX-License-Identifier: Apache-2.0

package com.android.internal.telephony;

import android.os.Build;
import android.os.IInterface;
import android.os.RemoteException;
import android.telephony.SubscriptionInfo;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.List;

import misc.utils.HiddenUtil;

public interface ISub extends IInterface {
    /**
     * @deprecated Replaced with {@link #getActiveSubscriptionInfoList(String)} in API 23 (Android M)
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    @Deprecated
    List<SubscriptionInfo> getActiveSubscriptionInfoList() throws RemoteException;

    /**
     * @deprecated Replaced with {@link #getActiveSubscriptionInfoList(String, String)} in API 30 (Android R)
     */
    @RequiresApi(Build.VERSION_CODES.M)
    @Deprecated
    List<SubscriptionInfo> getActiveSubscriptionInfoList(String callingPackage) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.R)
    List<SubscriptionInfo> getActiveSubscriptionInfoList(String callingPackage, @Nullable String callingFeatureId) throws RemoteException;

    /**
     * Added in Google Pixel stock ROM
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    List<SubscriptionInfo> getActiveSubscriptionInfoList(String callingPackage, @Nullable String callingFeatureId, boolean allUsers) throws RemoteException;

    abstract class Stub {
        public static ISub asInterface(android.os.IBinder obj) {
            return HiddenUtil.throwUOE(obj);
        }
    }
}
