// SPDX-License-Identifier: Apache-2.0

package android.net;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import misc.utils.HiddenUtil;

public class NetworkTemplate implements Parcelable {

    // public static final int MATCH_MOBILE_ALL = 1;
    public static final int MATCH_WIFI = 4;

    /**
     * Template to match {@link ConnectivityManager#TYPE_MOBILE} networks with
     * the given IMSI.
     */
    public static NetworkTemplate buildTemplateMobileAll(String subscriberId) {
        return HiddenUtil.throwUOE(subscriberId);
    }

    /**
     * Template to match {@link ConnectivityManager#TYPE_MOBILE} networks,
     * regardless of IMSI.
     */
    public static NetworkTemplate buildTemplateMobileWildcard() {
        return HiddenUtil.throwUOE();
    }

    /**
     * Template to match all {@link ConnectivityManager#TYPE_WIFI} networks,
     * regardless of SSID.
     */
    public static NetworkTemplate buildTemplateWifiWildcard() {
        return HiddenUtil.throwUOE();
    }

    public NetworkTemplate(int matchRule, @Nullable String subscriberId, @Nullable String networkId) {
        HiddenUtil.throwUOE(matchRule, subscriberId, networkId);
    }

    private NetworkTemplate(Parcel in) {
        HiddenUtil.throwUOE(in);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        HiddenUtil.throwUOE(dest, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<NetworkTemplate> CREATOR = HiddenUtil.creator();
}