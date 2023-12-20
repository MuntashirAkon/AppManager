// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.os.Build;

import androidx.annotation.RequiresApi;

public final class ManifestCompat {
    public static final class permission {
        public static final String TERMUX_RUN_COMMAND = "com.termux.permission.RUN_COMMAND";

        @RequiresApi(Build.VERSION_CODES.Q)
        public static final String ADJUST_RUNTIME_PERMISSIONS_POLICY = "android.permission.ADJUST_RUNTIME_PERMISSIONS_POLICY";
        public static final String CLEAR_APP_USER_DATA = "android.permission.CLEAR_APP_USER_DATA";
        @RequiresApi(Build.VERSION_CODES.N)
        public static final String CREATE_USERS = "android.permission.CREATE_USERS";
        public static final String DEVICE_POWER = "android.permission.DEVICE_POWER";
        public static final String FORCE_STOP_PACKAGES = "android.permission.FORCE_STOP_PACKAGES";
        public static final String GET_APP_OPS_STATS = "android.permission.GET_APP_OPS_STATS";
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        public static final String GET_HISTORICAL_APP_OPS_STATS = "android.permission.GET_HISTORICAL_APP_OPS_STATS";
        public static final String GET_RUNTIME_PERMISSIONS = "android.permission.GET_RUNTIME_PERMISSIONS";
        public static final String GRANT_RUNTIME_PERMISSIONS = "android.permission.GRANT_RUNTIME_PERMISSIONS";
        public static final String INJECT_EVENTS = "android.permission.INJECT_EVENTS";
        @RequiresApi(Build.VERSION_CODES.Q)
        public static final String INSTALL_EXISTING_PACKAGES = "com.android.permission.INSTALL_EXISTING_PACKAGES";
        public static final String INTERACT_ACROSS_USERS = "android.permission.INTERACT_ACROSS_USERS";
        public static final String INTERACT_ACROSS_USERS_FULL = "android.permission.INTERACT_ACROSS_USERS_FULL";
        @RequiresApi(Build.VERSION_CODES.P)
        public static final String INTERNAL_DELETE_CACHE_FILES = "android.permission.INTERNAL_DELETE_CACHE_FILES";
        @RequiresApi(Build.VERSION_CODES.M)
        public static final String KILL_UID = "android.permission.KILL_UID";
        @RequiresApi(Build.VERSION_CODES.N)
        public static final String MANAGE_APP_OPS_RESTRICTIONS = "android.permission.MANAGE_APP_OPS_RESTRICTIONS";
        @RequiresApi(Build.VERSION_CODES.P)
        public static final String MANAGE_APP_OPS_MODES = "android.permission.MANAGE_APP_OPS_MODES";
        @RequiresApi(Build.VERSION_CODES.Q)
        public static final String MANAGE_APPOPS = "android.permission.MANAGE_APPOPS";
        public static final String MANAGE_NETWORK_POLICY = "android.permission.MANAGE_NETWORK_POLICY";
        @RequiresApi(Build.VERSION_CODES.S)
        public static final String MANAGE_NOTIFICATION_LISTENERS = "android.permission.MANAGE_NOTIFICATION_LISTENERS";
        public static final String MANAGE_USERS = "android.permission.MANAGE_USERS";
        public static final String READ_PRIVILEGED_PHONE_STATE = "android.permission.READ_PRIVILEGED_PHONE_STATE";
        public static final String REAL_GET_TASKS = "android.permission.REAL_GET_TASKS";
        public static final String REVOKE_RUNTIME_PERMISSIONS = "android.permission.REVOKE_RUNTIME_PERMISSIONS";
        public static final String START_ANY_ACTIVITY = "android.permission.START_ANY_ACTIVITY";
        @RequiresApi(Build.VERSION_CODES.P)
        public static final String SUSPEND_APPS = "android.permission.SUSPEND_APPS";
        public static final String UPDATE_APP_OPS_STATS = "android.permission.UPDATE_APP_OPS_STATS";
        @RequiresApi(Build.VERSION_CODES.S)
        public static final String UPDATE_DOMAIN_VERIFICATION_USER_SELECTION = "android.permission.UPDATE_DOMAIN_VERIFICATION_USER_SELECTION";
        @RequiresApi(Build.VERSION_CODES.P)
        public static final String WATCH_APPOPS = "android.permission.WATCH_APPOPS";
    }
}
