// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import io.github.muntashirakon.AppManager.logs.Log;

public final class ScreenLockChecker {
    public static final String TAG = ScreenLockChecker.class.getSimpleName();

    private static final int SECOND = 1000;
    private static final int MINUTE = 60 * SECOND;
    // This tracks the deltas between the actual options of 5s, 15s, 30s, 1m, 2m, 5m, 10m
    // It also includes an initial offset and some extra times (for safety)
    private static final int[] sCheckLockDelays = new int[]{SECOND, 5 * SECOND, 10 * SECOND, 20 * SECOND, 30 * SECOND,
            MINUTE, 3 * MINUTE, 5 * MINUTE, 10 * MINUTE, 30 * MINUTE};

    private final Context mContext;
    private final Timer mTimer = new Timer();
    @Nullable
    private final Runnable mRunnable;

    @Nullable
    private CheckLockTask mCheckLockTask;

    public ScreenLockChecker(@NonNull Context context, @Nullable Runnable runnable) {
        mContext = context.getApplicationContext();
        mRunnable = runnable;
    }

    public void checkLock() {
        checkLock(-1);
    }

    @WorkerThread
    private void checkLock(int delayIndex) {
        KeyguardManager keyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);

        final boolean isProtected = keyguardManager.isKeyguardSecure();
        final boolean isLocked = keyguardManager.isKeyguardLocked();
        final boolean isInteractive = powerManager.isInteractive();
        delayIndex = getSafeCheckLockDelay(delayIndex);
        Log.i(TAG, "checkLock: isProtected=%b, isLocked=%b, isInteractive=%b, delay=%d",
                isProtected, isLocked, isInteractive, sCheckLockDelays[delayIndex]);

        if (mCheckLockTask != null) {
            Log.i(TAG, "checkLock: cancelling CheckLockTask[%x]", System.identityHashCode(mCheckLockTask));
            mCheckLockTask.cancel();
        }

        if (isProtected && !isLocked && !isInteractive) {
            mCheckLockTask = new CheckLockTask(delayIndex);
            Log.i(TAG, "checkLock: scheduling CheckLockTask[%x] for %d ms", System.identityHashCode(mCheckLockTask), sCheckLockDelays[delayIndex]);
            mTimer.schedule(mCheckLockTask, sCheckLockDelays[delayIndex]);
        } else {
            Log.d(TAG, "checkLock: no need to schedule CheckLockTask");
            if (isProtected && isLocked) {
                if (mRunnable != null) {
                    mRunnable.run();
                }
            }
        }
    }

    private static int getSafeCheckLockDelay(final int delayIndex) {
        final int safeDelayIndex;
        if (delayIndex >= sCheckLockDelays.length) {
            safeDelayIndex = sCheckLockDelays.length - 1;
        } else safeDelayIndex = Math.max(delayIndex, 0);
        Log.v(TAG, "getSafeCheckLockDelay(%d) returns %d", delayIndex, safeDelayIndex);
        return safeDelayIndex;
    }

    private class CheckLockTask extends TimerTask {
        final int delayIndex;

        CheckLockTask(final int delayIndex) {
            this.delayIndex = delayIndex;
        }

        @Override
        public void run() {
            Log.i(TAG, "CLT.run [%x]: redirect intent to LockMonitor", System.identityHashCode(this));
            checkLock(getSafeCheckLockDelay(delayIndex + 1));
        }
    }
}
