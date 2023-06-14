// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.smt;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.PendingIntentCompat;

import java.util.concurrent.atomic.AtomicInteger;


// https://github.com/BLuFeNiX/SMTShell-API/blob/241a3deccea2e63ecabfe9b410db34088cd7365e/api/src/main/java/net/blufenix/smtshell/api/SMTShellAPI.java
public class SMTShellAPI {

    // requests
    public static final String ACTION_SHELL_COMMAND = "smtshell.intent.action.SHELL_COMMAND";
    public static final String EXTRA_COMMAND = "smtshell.intent.extra.COMMAND";
    public static final String EXTRA_REQUEST_ID = "smtshell.intent.extra.REQUEST_ID";
    public static final String EXTRA_CALLBACK_PKG = "smtshell.intent.extra.CALLBACK_PKG"; // IntentSender
    public static final String PKG_NAME_SMT = "com.samsung.SMT"; // send requests to this pkg

    public static final String ACTION_API_PING = "smtshell.intent.action.API_PING";

    // results
    public static final String ACTION_SHELL_RESULT = "smtshell.intent.action.SHELL_RESULT";
    public static final String EXTRA_STDOUT = "smtshell.intent.extra.STDOUT";
    public static final String EXTRA_STDERR = "smtshell.intent.extra.STDERR";
    public static final String EXTRA_EXIT_CODE = "smtshell.intent.extra.EXIT_CODE";

    public static final String ACTION_API_READY = "smtshell.intent.action.API_READY";
    public static final String PERMISSION_RECEIVER_GUARD = "android.permission.REBOOT";

    // start at a number a user is unlikely to use for themselves,
    //  in case they manually call the API without the wrapper
    private static final AtomicInteger REQUEST_ID = new AtomicInteger(1000000);
    static int nextId() {
        return REQUEST_ID.getAndIncrement();
    }

    public static void executeCommand(@NonNull Context context, @NonNull String cmd, @Nullable CommandCallback cb) {
        // setup intent
        int requestId = nextId();
        Intent intent = createIntent(ACTION_SHELL_COMMAND, requestId);
        intent.putExtra(EXTRA_COMMAND, cmd);

        if (cb != null) {
            // specify that the request has a sender
            setSender(context, intent);
            // setup receiver
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (requestId == intent.getIntExtra(EXTRA_REQUEST_ID, -1)) {
                        context.unregisterReceiver(this);
                        cb.onComplete(
                                intent.getStringExtra(EXTRA_STDOUT),
                                intent.getStringExtra(EXTRA_STDERR),
                                intent.getIntExtra(EXTRA_EXIT_CODE, -1)
                        );
                    }
                }
            }, new IntentFilter(ACTION_SHELL_RESULT), PERMISSION_RECEIVER_GUARD, null);
        }

        // send command
        context.sendBroadcast(intent);
    }

    public static void ping(@NonNull Context context) {
        Intent intent = createIntent(ACTION_API_PING, -1);
        setSender(context, intent);
        context.sendBroadcast(intent);
    }

    @NonNull
    static Intent createIntent(@NonNull String action, int requestId) {
        Intent intent = new Intent(action);
        intent.setPackage(PKG_NAME_SMT);
        intent.putExtra(EXTRA_REQUEST_ID, requestId);
        return intent;
    }

    // used to prove where the intent came from, so other packages can't request things on our behalf
    static void setSender(@NonNull Context context, @NonNull Intent intent) {
        @SuppressLint("WrongConstant")
        PendingIntent self = PendingIntentCompat.getBroadcast(context, 0, new Intent(), 0, false);
        if (self != null) {
            intent.putExtra(EXTRA_CALLBACK_PKG, self.getIntentSender());
        }
    }

    public interface CommandCallback {
        void onComplete(String stdout, String stderr, int exitCode);
    }
}