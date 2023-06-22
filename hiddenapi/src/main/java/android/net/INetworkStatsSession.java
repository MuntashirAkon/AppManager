// SPDX-License-Identifier: Apache-2.0

package android.net;

import android.os.Build;
import android.os.IInterface;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

public interface INetworkStatsSession extends IInterface {
    /**
     * Return device aggregated network layer usage summary for traffic that matches template.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    NetworkStats getDeviceSummaryForNetwork(NetworkTemplate template, long start, long end) throws RemoteException;

    /**
     * Return network layer usage summary for traffic that matches template.
     */
    NetworkStats getSummaryForNetwork(NetworkTemplate template, long start, long end) throws RemoteException;
//    /** Return historical network layer stats for traffic that matches template. */
//    NetworkStatsHistory getHistoryForNetwork(NetworkTemplate template, int fields) throws RemoteException;
//    /**
//     * Return historical network layer stats for traffic that matches template, start and end
//     * timestamp.
//     */
//    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
//    NetworkStatsHistory getHistoryIntervalForNetwork(NetworkTemplate template, int fields, long start, long end) throws RemoteException;

    /**
     * Return network layer usage summary per UID for traffic that matches template.
     *
     * <p>The resulting {@code NetworkStats#getElapsedRealtime()} contains time delta between
     * {@code start} and {@code end}.
     *
     * @param template    - a predicate to filter netstats.
     * @param start       - start of the range, timestamp in milliseconds since the epoch.
     * @param end         - end of the range, timestamp in milliseconds since the epoch.
     * @param includeTags - includes data usage tags if true.
     */
    NetworkStats getSummaryForAllUid(NetworkTemplate template, long start, long end, boolean includeTags) throws RemoteException;

    /**
     * Return network layer usage summary per UID for tagged traffic that matches template.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    NetworkStats getTaggedSummaryForAllUid(NetworkTemplate template, long start, long end);
//    /** Return historical network layer stats for specific UID traffic that matches template. */
//    NetworkStatsHistory getHistoryForUid(NetworkTemplate template, int uid, int set, int tag, int fields) throws RemoteException;
//    /** Return historical network layer stats for specific UID traffic that matches template. */
//    @RequiresApi(Build.VERSION_CODES.M)
//    NetworkStatsHistory getHistoryIntervalForUid(NetworkTemplate template, int uid, int set, int tag, int fields, long start, long end) throws RemoteException;

    /**
     * Return array of uids that have stats and are accessible to the calling user
     */
    @RequiresApi(Build.VERSION_CODES.M)
    int[] getRelevantUids() throws RemoteException;

    void close() throws RemoteException;
}