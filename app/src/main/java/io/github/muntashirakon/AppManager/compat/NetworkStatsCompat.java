// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.net.INetworkStatsService;
import android.net.INetworkStatsSession;
import android.net.NetworkStats;
import android.net.NetworkTemplate;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.users.Users;

public class NetworkStatsCompat implements AutoCloseable {
    private final NetworkTemplate mTemplate;
    private final long mStartTime;
    private final long mEndTime;

    @Nullable
    private INetworkStatsSession mSession;
    private int mIndex;
    @Nullable
    private NetworkStats mSummary;
    @Nullable
    private NetworkStats.Entry mSummaryEntry;

    NetworkStatsCompat(@NonNull NetworkTemplate template, int flags, long startTime, long endTime,
                       @NonNull INetworkStatsService statsService) throws RemoteException, SecurityException {
        mTemplate = template;
        mStartTime = startTime;
        mEndTime = endTime;
        String callingPackage = SelfPermissions.getCallingPackage(Users.getSelfOrRemoteUid());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            mSession = statsService.openSessionForUsageStats(flags, callingPackage);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mSession = statsService.openSessionForUsageStats(callingPackage);
        } else mSession = statsService.openSession();
    }

    public boolean hasNextEntry() {
        return mSummary != null && mIndex < mSummary.size();
    }

    @Nullable
    public NetworkStats.Entry getNextEntry(boolean recycle) {
        if (mSummary == null) {
            return null;
        }
        mSummaryEntry = mSummary.getValues(mIndex, recycle ? mSummaryEntry : null);
        ++mIndex;
        return mSummaryEntry;
    }

    void startSummaryEnumeration() throws RemoteException {
        if (mSession != null) {
            mSummary = mSession.getSummaryForAllUid(mTemplate, mStartTime, mEndTime, false);
        }
        mIndex = 0;
    }

    @Override
    public void close() {
        if (mSession != null) {
            try {
                mSession.close();
            } catch (RemoteException e) {
                e.printStackTrace();
                // Otherwise, meh
            }
        }
        mSession = null;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }
}
