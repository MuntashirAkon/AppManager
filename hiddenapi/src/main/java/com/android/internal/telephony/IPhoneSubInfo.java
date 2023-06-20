// SPDX-License-Identifier: Apache-2.0

package com.android.internal.telephony;

import android.os.Build;
import android.os.IInterface;
import android.os.RemoteException;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;

public interface IPhoneSubInfo extends IInterface {
    /**
     * @deprecated Replaced by {@link #getSubscriberIdForSubscriber(int)} in SDK 22 (Android Lollipop MR1)
     */
    @Deprecated
    String getSubscriberIdForSubscriber(long subId) throws RemoteException;

    /**
     * @deprecated Replaced by {@link #getSubscriberIdForSubscriber(int, String)} in SDK 23 (Android M)
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    @Deprecated
    String getSubscriberIdForSubscriber(int subId) throws RemoteException;

    /**
     * @deprecated Replaced by {@link #getSubscriberIdForSubscriber(int, String, String)} in SDK 30 (Android R)
     */
    @RequiresApi(Build.VERSION_CODES.M)
    @Deprecated
    String getSubscriberIdForSubscriber(int subId, String callingPackage) throws RemoteException;

    /**
     * Retrieves the unique subscriber ID of a given subId, e.g., IMSI for GSM phones.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    String getSubscriberIdForSubscriber(int subId, String callingPackage, @Nullable String callingFeatureId) throws RemoteException;

    abstract class Stub {
        public static IPhoneSubInfo asInterface(android.os.IBinder obj) {
            return HiddenUtil.throwUOE(obj);
        }
    }
}
