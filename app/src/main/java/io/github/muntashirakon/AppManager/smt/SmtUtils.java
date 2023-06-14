// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.smt;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.core.content.pm.PackageInfoCompat;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.runner.Runner;

public class SmtUtils {
    public static final String TAG = SmtUtils.class.getSimpleName();

    @AnyThread
    public static boolean canAccessSmtShell(@NonNull Context context) {
        try {
            // https://blog.flanker017.me/text-to-speech-speaks-pwned/
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(SMTShellAPI.PKG_NAME_SMT, 0);
            long versionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (versionCode >= 300200007) {
                    Log.d(TAG, "SMT does not work since 3.0.02.7 in Android P and later");
                    return false;
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                if (versionCode >= 300000101) {
                    Log.d(TAG, "SMT does not work since 3.0.00.101 in Android O_MR1 and earlier");
                    return false;
                }
            } else return false; // Lollipop unsupported
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "SMT does not exist. Probably not a Samsung device.");
            return false;
        }
        Log.d(TAG, "Found a supported SMT version. Checking if SMTShell is alive...");
        CountDownLatch smtApiCheck = new CountDownLatch(1);
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (SMTShellAPI.ACTION_API_READY.equals(intent.getAction())) {
                    // SMTShell is present
                    smtApiCheck.countDown();
                }
            }
        };
        try {
            context.registerReceiver(broadcastReceiver, new IntentFilter(SMTShellAPI.ACTION_API_READY));
            SMTShellAPI.ping(context);
            // Wait at most 5 seconds
            try {
                if (smtApiCheck.await(5, TimeUnit.SECONDS)) {
                    Log.d(TAG, "SMTShell is present and alive.");
                    return true;
                }
            } catch (InterruptedException ignore) {
            }
        } finally {
            context.unregisterReceiver(broadcastReceiver);
        }
        return false;
    }

    /**
     * Run a command in SMTShell. Make sure to check for SMTShell using {@link #canAccessSmtShell(Context)}, otherwise
     * this might get stuck forever.
     */
    @NonNull
    public static Runner.Result runCommand(@NonNull Context context, @NonNull String command) {
        AtomicReference<Runner.Result> resultRef = new AtomicReference<>();
        CountDownLatch resultWatcher = new CountDownLatch(1);
        Log.d(TAG, "COMMAND: " + command);
        SMTShellAPI.executeCommand(context, command, (stdout, stderr, exitCode) -> {
            Log.d(TAG, "STDOUT: " + stdout);
            Log.d(TAG, "STDERR: " + stderr);
            Log.d(TAG, "EXIT_CODE: " + exitCode);
            Runner.Result result = new Runner.Result(
                    stdout != null ? Arrays.asList(stdout.split("\n")) : Collections.emptyList(),
                    stderr != null ? Arrays.asList(stderr.split("\n")) : Collections.emptyList(),
                    exitCode);
            resultRef.set(result);
            resultWatcher.countDown();
        });
        try {
            resultWatcher.await();
        } catch (InterruptedException ignore) {
        }
        Runner.Result result = resultRef.get();
        return result != null ? result : new Runner.Result();
    }
}
