// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.IPackageInstaller;
import android.content.pm.PackageInstaller;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

/**
 * Keeps enough ownership information to abandon sessions orphaned by process death.
 */
final class PackageInstallerSessionRegistry {
    static final String PREFS_NAME = "package_installer_sessions";
    static final String KEY_PREFIX = "session_";

    private static final String TAG = PackageInstallerSessionRegistry.class.getSimpleName();
    private static final String PROCESS_TOKEN = UUID.randomUUID().toString();

    interface SessionBackend {
        @NonNull
        Set<Integer> getSessionIds() throws Exception;

        void abandonSession(int sessionId) throws Exception;
    }

    private PackageInstallerSessionRegistry() {
    }

    static synchronized void cleanupOrphanedSessions(@NonNull IPackageInstaller packageInstaller) {
        Context context = ContextUtils.getContext();
        cleanupOrphanedSessions(context, new SessionBackend() {
            @NonNull
            @Override
            public Set<Integer> getSessionIds() throws Exception {
                Set<Integer> sessionIds = new HashSet<>();
                for (PackageInstaller.SessionInfo sessionInfo : packageInstaller.getMySessions(
                        context.getPackageName(), UserHandleHidden.myUserId()).getList()) {
                    sessionIds.add(sessionInfo.getSessionId());
                }
                return sessionIds;
            }

            @Override
            public void abandonSession(int sessionId) throws Exception {
                packageInstaller.abandonSession(sessionId);
            }
        });
    }

    static synchronized void cleanupOrphanedSessions(@NonNull Context context,
                                                     @NonNull SessionBackend backend) {
        SharedPreferences preferences = getPreferences(context);
        Map<String, ?> records = preferences.getAll();
        if (records.isEmpty()) {
            return;
        }
        Set<Integer> liveSessionIds;
        try {
            liveSessionIds = backend.getSessionIds();
        } catch (Exception e) {
            Log.w(TAG, "Could not query package installer sessions.", e);
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        boolean changed = false;
        for (Map.Entry<String, ?> record : records.entrySet()) {
            String key = record.getKey();
            if (!key.startsWith(KEY_PREFIX) || PROCESS_TOKEN.equals(record.getValue())) {
                continue;
            }
            int sessionId;
            try {
                sessionId = Integer.parseInt(key.substring(KEY_PREFIX.length()));
            } catch (NumberFormatException e) {
                editor.remove(key);
                changed = true;
                continue;
            }
            if (!liveSessionIds.contains(sessionId)) {
                editor.remove(key);
                changed = true;
                continue;
            }
            try {
                backend.abandonSession(sessionId);
                editor.remove(key);
                changed = true;
                Log.i(TAG, "Abandoned orphaned package installer session %d.", sessionId);
            } catch (Exception e) {
                Log.w(TAG, "Could not abandon orphaned package installer session %d.", e, sessionId);
            }
        }
        if (changed && !editor.commit()) {
            Log.w(TAG, "Could not persist package installer session cleanup.");
        }
    }

    static synchronized void record(int sessionId) {
        if (sessionId < 0) {
            return;
        }
        boolean persisted = getPreferences(ContextUtils.getContext()).edit()
                .putString(KEY_PREFIX + sessionId, PROCESS_TOKEN)
                .commit();
        if (!persisted) {
            Log.w(TAG, "Could not persist package installer session %d.", sessionId);
        }
    }

    static synchronized void forget(int sessionId) {
        if (sessionId < 0) {
            return;
        }
        getPreferences(ContextUtils.getContext()).edit().remove(KEY_PREFIX + sessionId).commit();
    }

    @NonNull
    static String getProcessToken() {
        return PROCESS_TOKEN;
    }

    @NonNull
    static SharedPreferences getPreferences(@NonNull Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
