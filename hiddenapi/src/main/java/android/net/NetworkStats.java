// SPDX-License-Identifier: Apache-2.0

package android.net;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import misc.utils.HiddenUtil;

/**
 * Collection of active network statistics. Can contain summary details across
 * all interfaces, or details with per-UID granularity. Internally stores data
 * as a large table, closely matching {@code /proc/} data format.
 */
public class NetworkStats implements Parcelable {
    public static class Entry {
        public String iface;
        public int uid;
        public int set;
        public int tag;
        public long rxBytes;
        public long rxPackets;
        public long txBytes;
        public long txPackets;
        public long operations;

        public Entry() {
            HiddenUtil.throwUOE();
        }
    }

    public NetworkStats(Parcel parcel) {
        HiddenUtil.throwUOE(parcel);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
    }

    /**
     * Return specific stats entry.
     */
    public Entry getValues(int i, @Nullable Entry recycle) {
        return HiddenUtil.throwUOE(i, recycle);
    }

    public int size() {
        return HiddenUtil.throwUOE();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<NetworkStats> CREATOR = HiddenUtil.creator();
}