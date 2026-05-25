// SPDX-License-Identifier: Apache-2.0

package android.net.netstats;

import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM) // 15 r20
public class StatsResult implements Parcelable {
    public long rxBytes;
    public long rxPackets;
    public long txBytes;
    public long txPackets;

    protected StatsResult(Parcel in) {
        rxBytes = in.readLong();
        rxPackets = in.readLong();
        txBytes = in.readLong();
        txPackets = in.readLong();
    }

    public static final Creator<StatsResult> CREATOR = new Creator<StatsResult>() {
        @Override
        public StatsResult createFromParcel(Parcel in) {
            return new StatsResult(in);
        }

        @Override
        public StatsResult[] newArray(int size) {
            return new StatsResult[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(rxBytes);
        dest.writeLong(rxPackets);
        dest.writeLong(txBytes);
        dest.writeLong(txPackets);
    }
}
