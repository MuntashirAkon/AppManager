// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.appops;

import android.Manifest;
import android.annotation.SuppressLint;
import android.os.Build;
import android.os.UserManager;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.HashSet;

import io.github.muntashirakon.AppManager.utils.ArrayUtils;

/**
 * API for interacting with "application operation" tracking.
 *
 * <p>This API is not generally intended for third party application developers; most
 * features are only available to system applications.
 */
// Copyright 2016 The Android Open Source Project
@SuppressWarnings("unused")
public class AppOpsManager {
    @SuppressLint("NewApi")
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {
            MODE_ALLOWED,
            MODE_IGNORED,
            MODE_ERRORED,
            MODE_DEFAULT,
            MODE_FOREGROUND,
            MODE_ASK
    })
    public @interface Mode {
    }

    /**
     * Result from {@link android.app.AppOpsManager#checkOp},
     * {@link android.app.AppOpsManager#noteOp}, {@link android.app.AppOpsManager#startOp}:
     * the given caller is allowed to perform the given operation.
     */
    public static final int MODE_ALLOWED = 0;
    /**
     * Result from {@link android.app.AppOpsManager#checkOp},
     * {@link android.app.AppOpsManager#noteOp}, {@link android.app.AppOpsManager#startOp}:
     * the given caller is not allowed to perform the given operation, and this attempt should
     * <em>silently fail</em> (it should not cause the app to crash).
     */
    public static final int MODE_IGNORED = 1;
    /**
     * Result from {@link android.app.AppOpsManager#checkOpNoThrow},
     * {@link android.app.AppOpsManager#noteOpNoThrow},
     * {@link android.app.AppOpsManager#startOpNoThrow}: the given caller is not allowed to perform
     * the given operation, and this attempt should cause it to have a fatal error,
     * typically a {@link SecurityException}.
     */
    public static final int MODE_ERRORED = 1 << 1;
    /**
     * Result from {@link android.app.AppOpsManager#checkOp},
     * {@link android.app.AppOpsManager#noteOp}, {@link android.app.AppOpsManager#startOp}:
     * the given caller should use its default security check. This mode is not normally used;
     * it should only be used with appop permissions, and callers must explicitly check for it and
     * deal with it.
     */
    public static final int MODE_DEFAULT = 3;
    /**
     * Special mode that means "allow only when app is in foreground."  This is <b>not</b>
     * returned from {@link android.app.AppOpsManager#unsafeCheckOp},
     * {@link android.app.AppOpsManager#noteOp}, {@link android.app.AppOpsManager#startOp}.
     * Rather, {@link android.app.AppOpsManager#unsafeCheckOp} will always return
     * {@link #MODE_ALLOWED} (because it is always possible for it to be ultimately allowed,
     * depending on the app's background state), and {@link android.app.AppOpsManager#noteOp} and
     * {@link android.app.AppOpsManager#startOp} will return {@link #MODE_ALLOWED} when the app
     * being checked is currently in the foreground, otherwise {@link #MODE_IGNORED}.
     *
     * <p>The only place you will this normally see this value is through
     * {@link android.app.AppOpsManager#unsafeCheckOpRaw}, which returns the actual raw mode
     * of the op.  Note that because you can't know the current state of the app being checked
     * (and it can change at any point), you can only treat the result here as an indication that
     * it will vary between {@link #MODE_ALLOWED} and {@link #MODE_IGNORED} depending on changes
     * in the background state of the app.  You thus must always use
     * {@link android.app.AppOpsManager#noteOp} or {@link android.app.AppOpsManager#startOp} to do
     * the actual check for access to the op.</p>
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static final int MODE_FOREGROUND = 1 << 2;
    /**
     * Special mode for MIUI
     */
    public static final int MODE_ASK = 5;

    public static final String[] MODE_NAMES = new String[]{
            "allow",        // MODE_ALLOWED
            "ignore",       // MODE_IGNORED
            "deny",         // MODE_ERRORED
            "default",      // MODE_DEFAULT
            "foreground",   // MODE_FOREGROUND
            "ask",          // MODE_ASK (MIUI)
    };

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            UID_STATE_PERSISTENT,
            UID_STATE_TOP,
            UID_STATE_FOREGROUND_SERVICE_LOCATION,
            UID_STATE_FOREGROUND_SERVICE,
            UID_STATE_FOREGROUND,
            UID_STATE_BACKGROUND,
            UID_STATE_CACHED
    })
    public @interface UidState {
    }

    /**
     * Uid state: The UID is a foreground persistent app. The lower the UID
     * state the more important the UID is for the user.
     */
    public static final int UID_STATE_PERSISTENT = 100;

    /**
     * Uid state: The UID is top foreground app. The lower the UID
     * state the more important the UID is for the user.
     */
    public static final int UID_STATE_TOP = 200;

    /**
     * Uid state: The UID is running a foreground service of location type.
     * The lower the UID state the more important the UID is for the user.
     * This uid state is a counterpart to PROCESS_STATE_FOREGROUND_SERVICE_LOCATION which has been
     * deprecated.
     *
     * @deprecated
     */
    @Deprecated
    public static final int UID_STATE_FOREGROUND_SERVICE_LOCATION = 300;

    /**
     * Uid state: The UID is running a foreground service. The lower the UID
     * state the more important the UID is for the user.
     */
    public static final int UID_STATE_FOREGROUND_SERVICE = 400;

    /**
     * Uid state: The UID is a foreground app. The lower the UID
     * state the more important the UID is for the user.
     */
    public static final int UID_STATE_FOREGROUND = 500;

    /**
     * The max, which is min priority, UID state for which any app op
     * would be considered as performed in the foreground.
     */
    public static final int UID_STATE_MAX_LAST_NON_RESTRICTED = UID_STATE_FOREGROUND;

    /**
     * Uid state: The UID is a background app. The lower the UID
     * state the more important the UID is for the user.
     */
    public static final int UID_STATE_BACKGROUND = 600;

    /**
     * Uid state: The UID is a cached app. The lower the UID
     * state the more important the UID is for the user.
     */
    public static final int UID_STATE_CACHED = 700;

    /**
     * Uid state: The UID state with the highest priority.
     */
    public static final int MAX_PRIORITY_UID_STATE = UID_STATE_PERSISTENT;

    /**
     * Uid state: The UID state with the lowest priority.
     */
    public static final int MIN_PRIORITY_UID_STATE = UID_STATE_CACHED;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {
            OP_FLAG_SELF,
            OP_FLAG_TRUSTED_PROXY,
            OP_FLAG_UNTRUSTED_PROXY,
            OP_FLAG_TRUSTED_PROXIED,
            OP_FLAG_UNTRUSTED_PROXIED
    })
    public @interface OpFlags {
    }

    /**
     * Flag: non proxy operations. These are operations
     * performed on behalf of the app itself and not on behalf of
     * another one.
     */
    public static final int OP_FLAG_SELF = 0x1;

    /**
     * Flag: trusted proxy operations. These are operations
     * performed on behalf of another app by a trusted app.
     * Which is work a trusted app blames on another app.
     */
    public static final int OP_FLAG_TRUSTED_PROXY = 1 << 1;

    /**
     * Flag: untrusted proxy operations. These are operations
     * performed on behalf of another app by an untrusted app.
     * Which is work an untrusted app blames on another app.
     */
    public static final int OP_FLAG_UNTRUSTED_PROXY = 1 << 2;

    /**
     * Flag: trusted proxied operations. These are operations
     * performed by a trusted other app on behalf of an app.
     * Which is work an app was blamed for by a trusted app.
     */
    public static final int OP_FLAG_TRUSTED_PROXIED = 1 << 3;

    /**
     * Flag: untrusted proxied operations. These are operations
     * performed by an untrusted other app on behalf of an app.
     * Which is work an app was blamed for by an untrusted app.
     */
    public static final int OP_FLAG_UNTRUSTED_PROXIED = 1 << 4;

    // when adding one of these:
    //  - increment _NUM_OP
    //  - define an OPSTR_* constant (marked as @SystemApi)
    //  - add rows to sOpToSwitch, sOpToString, sOpNames, sOpToPerms, sOpDefault
    //  - add descriptive strings to Settings/res/values/arrays.xml
    //  - add the op to the appropriate template in AppOpsState.OpsTemplate (settings app)

    // From frameworks/base/core/proto/android/app/enums.proto
    public static final int OP_NONE = -1;
    public static final int OP_COARSE_LOCATION = 0;
    public static final int OP_FINE_LOCATION = 1;
    public static final int OP_GPS = 2;
    public static final int OP_VIBRATE = 3;
    public static final int OP_READ_CONTACTS = 4;
    public static final int OP_WRITE_CONTACTS = 5;
    public static final int OP_READ_CALL_LOG = 6;
    public static final int OP_WRITE_CALL_LOG = 7;
    public static final int OP_READ_CALENDAR = 8;
    public static final int OP_WRITE_CALENDAR = 9;
    public static final int OP_WIFI_SCAN = 10;
    public static final int OP_POST_NOTIFICATION = 11;
    public static final int OP_NEIGHBORING_CELLS = 12;
    public static final int OP_CALL_PHONE = 13;
    public static final int OP_READ_SMS = 14;
    public static final int OP_WRITE_SMS = 15;
    public static final int OP_RECEIVE_SMS = 16;
    public static final int OP_RECEIVE_EMERGENCY_SMS = 17;
    public static final int OP_RECEIVE_MMS = 18;
    public static final int OP_RECEIVE_WAP_PUSH = 19;
    public static final int OP_SEND_SMS = 20;
    public static final int OP_READ_ICC_SMS = 21;
    public static final int OP_WRITE_ICC_SMS = 22;
    public static final int OP_WRITE_SETTINGS = 23;
    public static final int OP_SYSTEM_ALERT_WINDOW = 24;
    public static final int OP_ACCESS_NOTIFICATIONS = 25;
    public static final int OP_CAMERA = 26;
    public static final int OP_RECORD_AUDIO = 27;
    public static final int OP_PLAY_AUDIO = 28;
    public static final int OP_READ_CLIPBOARD = 29;
    public static final int OP_WRITE_CLIPBOARD = 30;
    public static final int OP_TAKE_MEDIA_BUTTONS = 31;
    public static final int OP_TAKE_AUDIO_FOCUS = 32;
    public static final int OP_AUDIO_MASTER_VOLUME = 33;
    public static final int OP_AUDIO_VOICE_VOLUME = 34;
    public static final int OP_AUDIO_RING_VOLUME = 35;
    public static final int OP_AUDIO_MEDIA_VOLUME = 36;
    public static final int OP_AUDIO_ALARM_VOLUME = 37;
    public static final int OP_AUDIO_NOTIFICATION_VOLUME = 38;
    public static final int OP_AUDIO_BLUETOOTH_VOLUME = 39;
    public static final int OP_WAKE_LOCK = 40;
    public static final int OP_MONITOR_LOCATION = 41;
    public static final int OP_MONITOR_HIGH_POWER_LOCATION = 42;
    public static final int OP_GET_USAGE_STATS = 43;
    public static final int OP_MUTE_MICROPHONE = 44;
    public static final int OP_TOAST_WINDOW = 45;
    public static final int OP_PROJECT_MEDIA = 46;
    public static final int OP_ACTIVATE_VPN = 47;
    public static final int OP_WRITE_WALLPAPER = 48;
    public static final int OP_ASSIST_STRUCTURE = 49;
    public static final int OP_ASSIST_SCREENSHOT = 50;
    public static final int OP_READ_PHONE_STATE = 51;
    public static final int OP_ADD_VOICEMAIL = 52;
    public static final int OP_USE_SIP = 53;
    public static final int OP_PROCESS_OUTGOING_CALLS = 54;
    public static final int OP_USE_FINGERPRINT = 55;
    public static final int OP_BODY_SENSORS = 56;
    public static final int OP_READ_CELL_BROADCASTS = 57;
    public static final int OP_MOCK_LOCATION = 58;
    public static final int OP_READ_EXTERNAL_STORAGE = 59;
    public static final int OP_WRITE_EXTERNAL_STORAGE = 60;
    public static final int OP_TURN_SCREEN_ON = 61;
    public static final int OP_GET_ACCOUNTS = 62;
    public static final int OP_RUN_IN_BACKGROUND = 63;
    public static final int OP_AUDIO_ACCESSIBILITY_VOLUME = 64;
    public static final int OP_READ_PHONE_NUMBERS = 65;
    public static final int OP_REQUEST_INSTALL_PACKAGES = 66;
    public static final int OP_PICTURE_IN_PICTURE = 67;
    public static final int OP_INSTANT_APP_START_FOREGROUND = 68;
    public static final int OP_ANSWER_PHONE_CALLS = 69;
    public static final int OP_RUN_ANY_IN_BACKGROUND = 70;
    public static final int OP_CHANGE_WIFI_STATE = 71;
    public static final int OP_REQUEST_DELETE_PACKAGES = 72;
    public static final int OP_BIND_ACCESSIBILITY_SERVICE = 73;
    public static final int OP_ACCEPT_HANDOVER = 74;
    public static final int OP_MANAGE_IPSEC_TUNNELS = 75;
    public static final int OP_START_FOREGROUND = 76;
    public static final int OP_BLUETOOTH_SCAN = 77;
    public static final int OP_USE_BIOMETRIC = 78;
    public static final int OP_ACTIVITY_RECOGNITION = 79;
    public static final int OP_SMS_FINANCIAL_TRANSACTIONS = 80;
    public static final int OP_READ_MEDIA_AUDIO = 81;
    public static final int OP_WRITE_MEDIA_AUDIO = 82;
    public static final int OP_READ_MEDIA_VIDEO = 83;
    public static final int OP_WRITE_MEDIA_VIDEO = 84;
    public static final int OP_READ_MEDIA_IMAGES = 85;
    public static final int OP_WRITE_MEDIA_IMAGES = 86;
    public static final int OP_LEGACY_STORAGE = 87;
    public static final int OP_ACCESS_ACCESSIBILITY = 88;
    public static final int OP_READ_DEVICE_IDENTIFIERS = 89;
    public static final int OP_ACCESS_MEDIA_LOCATION = 90;
    public static final int OP_QUERY_ALL_PACKAGES = 91;
    public static final int OP_MANAGE_EXTERNAL_STORAGE = 92;
    public static final int OP_INTERACT_ACROSS_PROFILES = 93;
    public static final int OP_ACTIVATE_PLATFORM_VPN = 94;
    public static final int OP_LOADER_USAGE_STATS = 95;
    public static final int OP_DEPRECATED_1 = 96;
    public static final int OP_AUTO_REVOKE_PERMISSIONS_IF_UNUSED = 97;
    public static final int OP_AUTO_REVOKE_MANAGED_BY_INSTALLER = 98;
    public static final int OP_NO_ISOLATED_STORAGE = 99;
    public static final int OP_PHONE_CALL_MICROPHONE = 100;
    public static final int OP_PHONE_CALL_CAMERA = 101;
    public static final int OP_RECORD_AUDIO_HOTWORD = 102;
    public static final int OP_MANAGE_ONGOING_CALLS = 103;
    public static final int OP_MANAGE_CREDENTIALS = 104;
    public static final int OP_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER = 105;
    public static final int OP_RECORD_AUDIO_OUTPUT = 106;
    public static final int OP_SCHEDULE_EXACT_ALARM = 107;
    public static final int OP_FINE_LOCATION_SOURCE = 108;
    public static final int OP_COARSE_LOCATION_SOURCE = 109;
    public static final int OP_MANAGE_MEDIA = 110;
    public static final int OP_BLUETOOTH_CONNECT = 111;
    public static final int _NUM_OP;  // fetched using reflection

    // MIUI app ops taken from framework.jar
    public static final int MIUI_OP_START = 10000;
    public static final int OP_WIFI_CHANGE = 10001;
    public static final int OP_BLUETOOTH_CHANGE = 10002;
    public static final int OP_DATA_CONNECT_CHANGE = 10003;
    public static final int OP_SEND_MMS = 10004;
    public static final int OP_READ_MMS = 10005;
    public static final int OP_WRITE_MMS = 10006;
    public static final int OP_BOOT_COMPLETED = 10007;
    public static final int OP_AUTO_START = 10008;
    public static final int OP_NFC_CHANGE = 10009;
    public static final int OP_DELETE_SMS = 10010;
    public static final int OP_DELETE_MMS = 10011;
    public static final int OP_DELETE_CONTACTS = 10012;
    public static final int OP_DELETE_CALL_LOG = 10013;
    public static final int OP_EXACT_ALARM = 10014;
    public static final int OP_ACCESS_XIAOMI_ACCOUNT = 10015;
    public static final int OP_NFC = 10016;
    public static final int OP_INSTALL_SHORTCUT = 10017;
    public static final int OP_READ_NOTIFICATION_SMS = 10018;
    public static final int OP_GET_TASKS = 10019;
    public static final int OP_SHOW_WHEN_LOCKED = 10020;
    public static final int OP_BACKGROUND_START_ACTIVITY = 10021;
    public static final int OP_GET_INSTALLED_APPS = 10022;
    public static final int OP_SERVICE_FOREGROUND = 10023;
    public static final int OP_GET_ANONYMOUS_ID = 10024;
    public static final int OP_GET_UDEVICE_ID = 10025;
    public static final int OP_DEAMON_NOTIFICATION = 10026;
    public static final int OP_BACKGROUND_LOCATION = 10027;
    public static final int OP_READ_SMS_REAL = 10028;
    // MIUI 12 start?
    public static final int OP_READ_CONTACTS_REAL = 10029;
    public static final int OP_READ_CALENDAR_REAL = 10030;
    public static final int OP_READ_CALL_LOG_REAL = 10031;
    public static final int OP_READ_PHONE_STATE_REAL = 10032;
    // MIUI 12
    public static final int OP_POST_LOCAL_NOTIFICATION = 10033;
    public static final int OP_ACCESS_GALLERY = 10034;
    public static final int OP_ACCESS_SOCIALITY = 10035;
    public static final int OP_ACCESS_LOCATION_REAL = 10036;
    public static final int MIUI_OP_END = 10037;

    public static final String OPSTR_COARSE_LOCATION = "android:coarse_location";
    public static final String OPSTR_FINE_LOCATION = "android:fine_location";
    public static final String OPSTR_MONITOR_LOCATION = "android:monitor_location";
    public static final String OPSTR_MONITOR_HIGH_POWER_LOCATION = "android:monitor_location_high_power";
    public static final String OPSTR_GET_USAGE_STATS = "android:get_usage_stats";
    public static final String OPSTR_ACTIVATE_VPN = "android:activate_vpn";
    public static final String OPSTR_READ_CONTACTS = "android:read_contacts";
    public static final String OPSTR_WRITE_CONTACTS = "android:write_contacts";
    public static final String OPSTR_READ_CALL_LOG = "android:read_call_log";
    public static final String OPSTR_WRITE_CALL_LOG = "android:write_call_log";
    public static final String OPSTR_READ_CALENDAR = "android:read_calendar";
    public static final String OPSTR_WRITE_CALENDAR = "android:write_calendar";
    public static final String OPSTR_CALL_PHONE = "android:call_phone";
    public static final String OPSTR_READ_SMS = "android:read_sms";
    public static final String OPSTR_RECEIVE_SMS = "android:receive_sms";
    public static final String OPSTR_RECEIVE_MMS = "android:receive_mms";
    public static final String OPSTR_RECEIVE_WAP_PUSH = "android:receive_wap_push";
    public static final String OPSTR_SEND_SMS = "android:send_sms";
    public static final String OPSTR_CAMERA = "android:camera";
    public static final String OPSTR_RECORD_AUDIO = "android:record_audio";
    public static final String OPSTR_READ_PHONE_STATE = "android:read_phone_state";
    public static final String OPSTR_ADD_VOICEMAIL = "android:add_voicemail";
    public static final String OPSTR_USE_SIP = "android:use_sip";
    public static final String OPSTR_PROCESS_OUTGOING_CALLS = "android:process_outgoing_calls";
    public static final String OPSTR_USE_FINGERPRINT = "android:use_fingerprint";
    public static final String OPSTR_BODY_SENSORS = "android:body_sensors";
    public static final String OPSTR_READ_CELL_BROADCASTS = "android:read_cell_broadcasts";
    public static final String OPSTR_MOCK_LOCATION = "android:mock_location";
    public static final String OPSTR_READ_EXTERNAL_STORAGE = "android:read_external_storage";
    public static final String OPSTR_WRITE_EXTERNAL_STORAGE = "android:write_external_storage";
    public static final String OPSTR_SYSTEM_ALERT_WINDOW = "android:system_alert_window";
    public static final String OPSTR_WRITE_SETTINGS = "android:write_settings";
    public static final String OPSTR_GET_ACCOUNTS = "android:get_accounts";
    public static final String OPSTR_READ_PHONE_NUMBERS = "android:read_phone_numbers";
    public static final String OPSTR_PICTURE_IN_PICTURE = "android:picture_in_picture";
    public static final String OPSTR_INSTANT_APP_START_FOREGROUND = "android:instant_app_start_foreground";
    public static final String OPSTR_ANSWER_PHONE_CALLS = "android:answer_phone_calls";
    public static final String OPSTR_ACCEPT_HANDOVER = "android:accept_handover";
    public static final String OPSTR_GPS = "android:gps";
    public static final String OPSTR_VIBRATE = "android:vibrate";
    public static final String OPSTR_WIFI_SCAN = "android:wifi_scan";
    public static final String OPSTR_POST_NOTIFICATION = "android:post_notification";
    public static final String OPSTR_NEIGHBORING_CELLS = "android:neighboring_cells";
    public static final String OPSTR_WRITE_SMS = "android:write_sms";
    public static final String OPSTR_RECEIVE_EMERGENCY_BROADCAST = "android:receive_emergency_broadcast";
    public static final String OPSTR_READ_ICC_SMS = "android:read_icc_sms";
    public static final String OPSTR_WRITE_ICC_SMS = "android:write_icc_sms";
    public static final String OPSTR_ACCESS_NOTIFICATIONS = "android:access_notifications";
    public static final String OPSTR_PLAY_AUDIO = "android:play_audio";
    public static final String OPSTR_READ_CLIPBOARD = "android:read_clipboard";
    public static final String OPSTR_WRITE_CLIPBOARD = "android:write_clipboard";
    public static final String OPSTR_TAKE_MEDIA_BUTTONS = "android:take_media_buttons";
    public static final String OPSTR_TAKE_AUDIO_FOCUS = "android:take_audio_focus";
    public static final String OPSTR_AUDIO_MASTER_VOLUME = "android:audio_master_volume";
    public static final String OPSTR_AUDIO_VOICE_VOLUME = "android:audio_voice_volume";
    public static final String OPSTR_AUDIO_RING_VOLUME = "android:audio_ring_volume";
    public static final String OPSTR_AUDIO_MEDIA_VOLUME = "android:audio_media_volume";
    public static final String OPSTR_AUDIO_ALARM_VOLUME = "android:audio_alarm_volume";
    public static final String OPSTR_AUDIO_NOTIFICATION_VOLUME = "android:audio_notification_volume";
    public static final String OPSTR_AUDIO_BLUETOOTH_VOLUME = "android:audio_bluetooth_volume";
    public static final String OPSTR_WAKE_LOCK = "android:wake_lock";
    public static final String OPSTR_MUTE_MICROPHONE = "android:mute_microphone";
    public static final String OPSTR_TOAST_WINDOW = "android:toast_window";
    public static final String OPSTR_PROJECT_MEDIA = "android:project_media";
    public static final String OPSTR_WRITE_WALLPAPER = "android:write_wallpaper";
    public static final String OPSTR_ASSIST_STRUCTURE = "android:assist_structure";
    public static final String OPSTR_ASSIST_SCREENSHOT = "android:assist_screenshot";
    public static final String OPSTR_TURN_SCREEN_ON = "android:turn_screen_on";
    public static final String OPSTR_RUN_IN_BACKGROUND = "android:run_in_background";
    public static final String OPSTR_AUDIO_ACCESSIBILITY_VOLUME = "android:audio_accessibility_volume";
    public static final String OPSTR_REQUEST_INSTALL_PACKAGES = "android:request_install_packages";
    public static final String OPSTR_RUN_ANY_IN_BACKGROUND = "android:run_any_in_background";
    public static final String OPSTR_CHANGE_WIFI_STATE = "android:change_wifi_state";
    public static final String OPSTR_REQUEST_DELETE_PACKAGES = "android:request_delete_packages";
    public static final String OPSTR_BIND_ACCESSIBILITY_SERVICE = "android:bind_accessibility_service";
    public static final String OPSTR_MANAGE_IPSEC_TUNNELS = "android:manage_ipsec_tunnels";
    public static final String OPSTR_START_FOREGROUND = "android:start_foreground";
    public static final String OPSTR_BLUETOOTH_SCAN = "android:bluetooth_scan";
    public static final String OPSTR_USE_BIOMETRIC = "android:use_biometric";
    public static final String OPSTR_ACTIVITY_RECOGNITION = "android:activity_recognition";
    public static final String OPSTR_SMS_FINANCIAL_TRANSACTIONS = "android:sms_financial_transactions";
    public static final String OPSTR_READ_MEDIA_AUDIO = "android:read_media_audio";
    public static final String OPSTR_WRITE_MEDIA_AUDIO = "android:write_media_audio";
    public static final String OPSTR_READ_MEDIA_VIDEO = "android:read_media_video";
    public static final String OPSTR_WRITE_MEDIA_VIDEO = "android:write_media_video";
    public static final String OPSTR_READ_MEDIA_IMAGES = "android:read_media_images";
    public static final String OPSTR_WRITE_MEDIA_IMAGES = "android:write_media_images";
    public static final String OPSTR_LEGACY_STORAGE = "android:legacy_storage";
    public static final String OPSTR_ACCESS_MEDIA_LOCATION = "android:access_media_location";
    public static final String OPSTR_ACCESS_ACCESSIBILITY = "android:access_accessibility";
    public static final String OPSTR_READ_DEVICE_IDENTIFIERS = "android:read_device_identifiers";
    public static final String OPSTR_QUERY_ALL_PACKAGES = "android:query_all_packages";
    public static final String OPSTR_MANAGE_EXTERNAL_STORAGE = "android:manage_external_storage";
    public static final String OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED = "android:auto_revoke_permissions_if_unused";
    public static final String OPSTR_AUTO_REVOKE_MANAGED_BY_INSTALLER = "android:auto_revoke_managed_by_installer";
    public static final String OPSTR_INTERACT_ACROSS_PROFILES = "android:interact_across_profiles";
    public static final String OPSTR_ACTIVATE_PLATFORM_VPN = "android:activate_platform_vpn";
    public static final String OPSTR_LOADER_USAGE_STATS = "android:loader_usage_stats";
    public static final String OPSTR_NO_ISOLATED_STORAGE = "android:no_isolated_storage";
    public static final String OPSTR_PHONE_CALL_MICROPHONE = "android:phone_call_microphone";
    public static final String OPSTR_PHONE_CALL_CAMERA = "android:phone_call_camera";
    public static final String OPSTR_RECORD_AUDIO_HOTWORD = "android:record_audio_hotword";
    public static final String OPSTR_MANAGE_ONGOING_CALLS = "android:manage_on_going_calls";
    public static final String OPSTR_MANAGE_CREDENTIALS = "android:manage_credentials";
    public static final String OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER = "android:use_icc_auth_with_device_identifier";
    public static final String OPSTR_RECORD_AUDIO_OUTPUT = "android:record_audio_output";
    public static final String OPSTR_SCHEDULE_EXACT_ALARM = "android:schedule_exact_alarm";
    public static final String OPSTR_FINE_LOCATION_SOURCE = "android:fine_location_source";
    public static final String OPSTR_COARSE_LOCATION_SOURCE = "android:coarse_location_source";
    public static final String OPSTR_MANAGE_MEDIA = "android:manage_media";
    public static final String OPSTR_BLUETOOTH_CONNECT = "android:bluetooth_connect";

    private static final int[] RUNTIME_AND_APPOP_PERMISSIONS_OPS = {
            // RUNTIME PERMISSIONS
            // Contacts
            OP_READ_CONTACTS,
            OP_WRITE_CONTACTS,
            OP_GET_ACCOUNTS,
            // Calendar
            OP_READ_CALENDAR,
            OP_WRITE_CALENDAR,
            // SMS
            OP_SEND_SMS,
            OP_RECEIVE_SMS,
            OP_READ_SMS,
            OP_RECEIVE_WAP_PUSH,
            OP_RECEIVE_MMS,
            OP_READ_CELL_BROADCASTS,
            // Storage
            OP_READ_EXTERNAL_STORAGE,
            OP_WRITE_EXTERNAL_STORAGE,
            OP_ACCESS_MEDIA_LOCATION,
            // Location
            OP_COARSE_LOCATION,
            OP_FINE_LOCATION,
            // Phone
            OP_READ_PHONE_STATE,
            OP_READ_PHONE_NUMBERS,
            OP_CALL_PHONE,
            OP_READ_CALL_LOG,
            OP_WRITE_CALL_LOG,
            OP_ADD_VOICEMAIL,
            OP_USE_SIP,
            OP_PROCESS_OUTGOING_CALLS,
            OP_ANSWER_PHONE_CALLS,
            OP_ACCEPT_HANDOVER,
            // Microphone
            OP_RECORD_AUDIO,
            // Camera
            OP_CAMERA,
            // Body sensors
            OP_BODY_SENSORS,
            // Activity recognition
            OP_ACTIVITY_RECOGNITION,
            // Aural
            OP_READ_MEDIA_AUDIO,
            OP_WRITE_MEDIA_AUDIO,
            // Visual
            OP_READ_MEDIA_VIDEO,
            OP_WRITE_MEDIA_VIDEO,
            OP_READ_MEDIA_IMAGES,
            OP_WRITE_MEDIA_IMAGES,
            // APPOP PERMISSIONS
            OP_ACCESS_NOTIFICATIONS,
            OP_SYSTEM_ALERT_WINDOW,
            OP_WRITE_SETTINGS,
            OP_REQUEST_INSTALL_PACKAGES,
            OP_START_FOREGROUND,
            OP_SMS_FINANCIAL_TRANSACTIONS,
            OP_MANAGE_IPSEC_TUNNELS,
            OP_INSTANT_APP_START_FOREGROUND,
            OP_MANAGE_EXTERNAL_STORAGE,
            OP_INTERACT_ACROSS_PROFILES,
            OP_LOADER_USAGE_STATS,
            OP_MANAGE_ONGOING_CALLS,
            OP_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER,
            OP_SCHEDULE_EXACT_ALARM,
            OP_MANAGE_MEDIA,
            OP_BLUETOOTH_CONNECT,  // Missing in the original file
    };

    /**
     * This maps each operation to the operation that serves as the
     * switch to determine whether it is allowed.  Generally this is
     * a 1:1 mapping, but for some things (like location) that have
     * multiple low-level operations being tracked that should be
     * presented to the user as one switch then this can be used to
     * make them all controlled by the same single operation.
     */
    private static final int[] sOpToSwitch = new int[]{
            OP_COARSE_LOCATION,                 // COARSE_LOCATION
            OP_COARSE_LOCATION,                 // FINE_LOCATION
            OP_COARSE_LOCATION,                 // GPS
            OP_VIBRATE,                         // VIBRATE
            OP_READ_CONTACTS,                   // READ_CONTACTS
            OP_WRITE_CONTACTS,                  // WRITE_CONTACTS
            OP_READ_CALL_LOG,                   // READ_CALL_LOG
            OP_WRITE_CALL_LOG,                  // WRITE_CALL_LOG
            OP_READ_CALENDAR,                   // READ_CALENDAR
            OP_WRITE_CALENDAR,                  // WRITE_CALENDAR
            OP_COARSE_LOCATION,                 // WIFI_SCAN
            OP_POST_NOTIFICATION,               // POST_NOTIFICATION
            OP_COARSE_LOCATION,                 // NEIGHBORING_CELLS
            OP_CALL_PHONE,                      // CALL_PHONE
            OP_READ_SMS,                        // READ_SMS
            OP_WRITE_SMS,                       // WRITE_SMS
            OP_RECEIVE_SMS,                     // RECEIVE_SMS
            OP_RECEIVE_SMS,                     // RECEIVE_EMERGECY_SMS
            OP_RECEIVE_MMS,                     // RECEIVE_MMS
            OP_RECEIVE_WAP_PUSH,                // RECEIVE_WAP_PUSH
            OP_SEND_SMS,                        // SEND_SMS
            OP_READ_SMS,                        // READ_ICC_SMS
            OP_WRITE_SMS,                       // WRITE_ICC_SMS
            OP_WRITE_SETTINGS,                  // WRITE_SETTINGS
            OP_SYSTEM_ALERT_WINDOW,             // SYSTEM_ALERT_WINDOW
            OP_ACCESS_NOTIFICATIONS,            // ACCESS_NOTIFICATIONS
            OP_CAMERA,                          // CAMERA
            OP_RECORD_AUDIO,                    // RECORD_AUDIO
            OP_PLAY_AUDIO,                      // PLAY_AUDIO
            OP_READ_CLIPBOARD,                  // READ_CLIPBOARD
            OP_WRITE_CLIPBOARD,                 // WRITE_CLIPBOARD
            OP_TAKE_MEDIA_BUTTONS,              // TAKE_MEDIA_BUTTONS
            OP_TAKE_AUDIO_FOCUS,                // TAKE_AUDIO_FOCUS
            OP_AUDIO_MASTER_VOLUME,             // AUDIO_MASTER_VOLUME
            OP_AUDIO_VOICE_VOLUME,              // AUDIO_VOICE_VOLUME
            OP_AUDIO_RING_VOLUME,               // AUDIO_RING_VOLUME
            OP_AUDIO_MEDIA_VOLUME,              // AUDIO_MEDIA_VOLUME
            OP_AUDIO_ALARM_VOLUME,              // AUDIO_ALARM_VOLUME
            OP_AUDIO_NOTIFICATION_VOLUME,       // AUDIO_NOTIFICATION_VOLUME
            OP_AUDIO_BLUETOOTH_VOLUME,          // AUDIO_BLUETOOTH_VOLUME
            OP_WAKE_LOCK,                       // WAKE_LOCK
            OP_COARSE_LOCATION,                 // MONITOR_LOCATION
            OP_COARSE_LOCATION,                 // MONITOR_HIGH_POWER_LOCATION
            OP_GET_USAGE_STATS,                 // GET_USAGE_STATS
            OP_MUTE_MICROPHONE,                 // MUTE_MICROPHONE
            OP_TOAST_WINDOW,                    // TOAST_WINDOW
            OP_PROJECT_MEDIA,                   // PROJECT_MEDIA
            OP_ACTIVATE_VPN,                    // ACTIVATE_VPN
            OP_WRITE_WALLPAPER,                 // WRITE_WALLPAPER
            OP_ASSIST_STRUCTURE,                // ASSIST_STRUCTURE
            OP_ASSIST_SCREENSHOT,               // ASSIST_SCREENSHOT
            OP_READ_PHONE_STATE,                // READ_PHONE_STATE
            OP_ADD_VOICEMAIL,                   // ADD_VOICEMAIL
            OP_USE_SIP,                         // USE_SIP
            OP_PROCESS_OUTGOING_CALLS,          // PROCESS_OUTGOING_CALLS
            OP_USE_FINGERPRINT,                 // USE_FINGERPRINT
            OP_BODY_SENSORS,                    // BODY_SENSORS
            OP_READ_CELL_BROADCASTS,            // READ_CELL_BROADCASTS
            OP_MOCK_LOCATION,                   // MOCK_LOCATION
            OP_READ_EXTERNAL_STORAGE,           // READ_EXTERNAL_STORAGE
            OP_WRITE_EXTERNAL_STORAGE,          // WRITE_EXTERNAL_STORAGE
            OP_TURN_SCREEN_ON,                  // TURN_SCREEN_ON
            OP_GET_ACCOUNTS,                    // GET_ACCOUNTS
            OP_RUN_IN_BACKGROUND,               // RUN_IN_BACKGROUND
            OP_AUDIO_ACCESSIBILITY_VOLUME,      // AUDIO_ACCESSIBILITY_VOLUME
            OP_READ_PHONE_NUMBERS,              // READ_PHONE_NUMBERS
            OP_REQUEST_INSTALL_PACKAGES,        // REQUEST_INSTALL_PACKAGES
            OP_PICTURE_IN_PICTURE,              // ENTER_PICTURE_IN_PICTURE_ON_HIDE
            OP_INSTANT_APP_START_FOREGROUND,    // INSTANT_APP_START_FOREGROUND
            OP_ANSWER_PHONE_CALLS,              // ANSWER_PHONE_CALLS
            OP_RUN_ANY_IN_BACKGROUND,           // OP_RUN_ANY_IN_BACKGROUND
            OP_CHANGE_WIFI_STATE,               // OP_CHANGE_WIFI_STATE
            OP_REQUEST_DELETE_PACKAGES,         // OP_REQUEST_DELETE_PACKAGES
            OP_BIND_ACCESSIBILITY_SERVICE,      // OP_BIND_ACCESSIBILITY_SERVICE
            OP_ACCEPT_HANDOVER,                 // ACCEPT_HANDOVER
            OP_MANAGE_IPSEC_TUNNELS,            // MANAGE_IPSEC_HANDOVERS
            OP_START_FOREGROUND,                // START_FOREGROUND
            OP_COARSE_LOCATION,                 // BLUETOOTH_SCAN
            OP_USE_BIOMETRIC,                   // BIOMETRIC
            OP_ACTIVITY_RECOGNITION,            // ACTIVITY_RECOGNITION
            OP_SMS_FINANCIAL_TRANSACTIONS,      // SMS_FINANCIAL_TRANSACTIONS
            OP_READ_MEDIA_AUDIO,                // READ_MEDIA_AUDIO
            OP_WRITE_MEDIA_AUDIO,               // WRITE_MEDIA_AUDIO
            OP_READ_MEDIA_VIDEO,                // READ_MEDIA_VIDEO
            OP_WRITE_MEDIA_VIDEO,               // WRITE_MEDIA_VIDEO
            OP_READ_MEDIA_IMAGES,               // READ_MEDIA_IMAGES
            OP_WRITE_MEDIA_IMAGES,              // WRITE_MEDIA_IMAGES
            OP_LEGACY_STORAGE,                  // LEGACY_STORAGE
            OP_ACCESS_ACCESSIBILITY,            // ACCESS_ACCESSIBILITY
            OP_READ_DEVICE_IDENTIFIERS,         // READ_DEVICE_IDENTIFIERS
            OP_ACCESS_MEDIA_LOCATION,           // ACCESS_MEDIA_LOCATION
            OP_QUERY_ALL_PACKAGES,              // QUERY_ALL_PACKAGES
            OP_MANAGE_EXTERNAL_STORAGE,         // MANAGE_EXTERNAL_STORAGE
            OP_INTERACT_ACROSS_PROFILES,        //INTERACT_ACROSS_PROFILES
            OP_ACTIVATE_PLATFORM_VPN,           // ACTIVATE_PLATFORM_VPN
            OP_LOADER_USAGE_STATS,              // LOADER_USAGE_STATS
            OP_DEPRECATED_1,                    // deprecated
            OP_AUTO_REVOKE_PERMISSIONS_IF_UNUSED, //AUTO_REVOKE_PERMISSIONS_IF_UNUSED
            OP_AUTO_REVOKE_MANAGED_BY_INSTALLER, //OP_AUTO_REVOKE_MANAGED_BY_INSTALLER
            OP_NO_ISOLATED_STORAGE,             // NO_ISOLATED_STORAGE
            OP_PHONE_CALL_MICROPHONE,           // OP_PHONE_CALL_MICROPHONE
            OP_PHONE_CALL_CAMERA,               // OP_PHONE_CALL_CAMERA
            OP_RECORD_AUDIO_HOTWORD,            // RECORD_AUDIO_HOTWORD
            OP_MANAGE_ONGOING_CALLS,            // MANAGE_ONGOING_CALLS
            OP_MANAGE_CREDENTIALS,              // MANAGE_CREDENTIALS
            OP_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER, // USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER
            OP_RECORD_AUDIO_OUTPUT,             // RECORD_AUDIO_OUTPUT
            OP_SCHEDULE_EXACT_ALARM,            // SCHEDULE_EXACT_ALARM
            OP_FINE_LOCATION,                   // FINE_LOCATION_SOURCE
            OP_COARSE_LOCATION,                 // COARSE_LOCATION_SOURCE
            OP_MANAGE_MEDIA,                    // MANAGE_MEDIA
            OP_BLUETOOTH_CONNECT,               // BLUETOOTH_CONNECT
    };

    /**
     * Map MIUI app operations. This is a 1:1 mapping.
     */
    public static final int[] sMiuiOpToSwitch = new int[]{
            OP_WIFI_CHANGE,
            OP_BLUETOOTH_CHANGE,
            OP_DATA_CONNECT_CHANGE,
            OP_SEND_MMS,
            OP_READ_MMS,
            OP_WRITE_MMS,
            OP_BOOT_COMPLETED,
            OP_AUTO_START,
            OP_NFC_CHANGE,
            OP_DELETE_SMS,
            OP_DELETE_MMS,
            OP_DELETE_CONTACTS,
            OP_DELETE_CALL_LOG,
            OP_EXACT_ALARM,
            OP_ACCESS_XIAOMI_ACCOUNT,
            OP_NFC,
            OP_INSTALL_SHORTCUT,
            OP_READ_NOTIFICATION_SMS,
            OP_GET_TASKS,
            OP_SHOW_WHEN_LOCKED,
            OP_BACKGROUND_START_ACTIVITY,
            OP_GET_INSTALLED_APPS,
            OP_SERVICE_FOREGROUND,
            OP_GET_ANONYMOUS_ID,
            OP_GET_UDEVICE_ID,
            OP_DEAMON_NOTIFICATION,
            OP_BACKGROUND_LOCATION,
            OP_READ_SMS_REAL,
            OP_READ_CONTACTS_REAL,
            OP_READ_CALENDAR_REAL,
            OP_READ_CALL_LOG_REAL,
            OP_READ_PHONE_STATE_REAL,
            OP_POST_LOCAL_NOTIFICATION,
            OP_ACCESS_GALLERY,
            OP_ACCESS_SOCIALITY,
            OP_ACCESS_LOCATION_REAL,
    };

    /**
     * This maps each operation to the public string constant for it.
     */
    private static final String[] sOpToString = new String[]{
            OPSTR_COARSE_LOCATION,
            OPSTR_FINE_LOCATION,
            OPSTR_GPS,
            OPSTR_VIBRATE,
            OPSTR_READ_CONTACTS,
            OPSTR_WRITE_CONTACTS,
            OPSTR_READ_CALL_LOG,
            OPSTR_WRITE_CALL_LOG,
            OPSTR_READ_CALENDAR,
            OPSTR_WRITE_CALENDAR,
            OPSTR_WIFI_SCAN,
            OPSTR_POST_NOTIFICATION,
            OPSTR_NEIGHBORING_CELLS,
            OPSTR_CALL_PHONE,
            OPSTR_READ_SMS,
            OPSTR_WRITE_SMS,
            OPSTR_RECEIVE_SMS,
            OPSTR_RECEIVE_EMERGENCY_BROADCAST,
            OPSTR_RECEIVE_MMS,
            OPSTR_RECEIVE_WAP_PUSH,
            OPSTR_SEND_SMS,
            OPSTR_READ_ICC_SMS,
            OPSTR_WRITE_ICC_SMS,
            OPSTR_WRITE_SETTINGS,
            OPSTR_SYSTEM_ALERT_WINDOW,
            OPSTR_ACCESS_NOTIFICATIONS,
            OPSTR_CAMERA,
            OPSTR_RECORD_AUDIO,
            OPSTR_PLAY_AUDIO,
            OPSTR_READ_CLIPBOARD,
            OPSTR_WRITE_CLIPBOARD,
            OPSTR_TAKE_MEDIA_BUTTONS,
            OPSTR_TAKE_AUDIO_FOCUS,
            OPSTR_AUDIO_MASTER_VOLUME,
            OPSTR_AUDIO_VOICE_VOLUME,
            OPSTR_AUDIO_RING_VOLUME,
            OPSTR_AUDIO_MEDIA_VOLUME,
            OPSTR_AUDIO_ALARM_VOLUME,
            OPSTR_AUDIO_NOTIFICATION_VOLUME,
            OPSTR_AUDIO_BLUETOOTH_VOLUME,
            OPSTR_WAKE_LOCK,
            OPSTR_MONITOR_LOCATION,
            OPSTR_MONITOR_HIGH_POWER_LOCATION,
            OPSTR_GET_USAGE_STATS,
            OPSTR_MUTE_MICROPHONE,
            OPSTR_TOAST_WINDOW,
            OPSTR_PROJECT_MEDIA,
            OPSTR_ACTIVATE_VPN,
            OPSTR_WRITE_WALLPAPER,
            OPSTR_ASSIST_STRUCTURE,
            OPSTR_ASSIST_SCREENSHOT,
            OPSTR_READ_PHONE_STATE,
            OPSTR_ADD_VOICEMAIL,
            OPSTR_USE_SIP,
            OPSTR_PROCESS_OUTGOING_CALLS,
            OPSTR_USE_FINGERPRINT,
            OPSTR_BODY_SENSORS,
            OPSTR_READ_CELL_BROADCASTS,
            OPSTR_MOCK_LOCATION,
            OPSTR_READ_EXTERNAL_STORAGE,
            OPSTR_WRITE_EXTERNAL_STORAGE,
            OPSTR_TURN_SCREEN_ON,
            OPSTR_GET_ACCOUNTS,
            OPSTR_RUN_IN_BACKGROUND,
            OPSTR_AUDIO_ACCESSIBILITY_VOLUME,
            OPSTR_READ_PHONE_NUMBERS,
            OPSTR_REQUEST_INSTALL_PACKAGES,
            OPSTR_PICTURE_IN_PICTURE,
            OPSTR_INSTANT_APP_START_FOREGROUND,
            OPSTR_ANSWER_PHONE_CALLS,
            OPSTR_RUN_ANY_IN_BACKGROUND,
            OPSTR_CHANGE_WIFI_STATE,
            OPSTR_REQUEST_DELETE_PACKAGES,
            OPSTR_BIND_ACCESSIBILITY_SERVICE,
            OPSTR_ACCEPT_HANDOVER,
            OPSTR_MANAGE_IPSEC_TUNNELS,
            OPSTR_START_FOREGROUND,
            OPSTR_BLUETOOTH_SCAN,
            OPSTR_USE_BIOMETRIC,
            OPSTR_ACTIVITY_RECOGNITION,
            OPSTR_SMS_FINANCIAL_TRANSACTIONS,
            OPSTR_READ_MEDIA_AUDIO,
            OPSTR_WRITE_MEDIA_AUDIO,
            OPSTR_READ_MEDIA_VIDEO,
            OPSTR_WRITE_MEDIA_VIDEO,
            OPSTR_READ_MEDIA_IMAGES,
            OPSTR_WRITE_MEDIA_IMAGES,
            OPSTR_LEGACY_STORAGE,
            OPSTR_ACCESS_ACCESSIBILITY,
            OPSTR_READ_DEVICE_IDENTIFIERS,
            OPSTR_ACCESS_MEDIA_LOCATION,
            OPSTR_QUERY_ALL_PACKAGES,
            OPSTR_MANAGE_EXTERNAL_STORAGE,
            OPSTR_INTERACT_ACROSS_PROFILES,
            OPSTR_ACTIVATE_PLATFORM_VPN,
            OPSTR_LOADER_USAGE_STATS,
            "", // deprecated
            OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED,
            OPSTR_AUTO_REVOKE_MANAGED_BY_INSTALLER,
            OPSTR_NO_ISOLATED_STORAGE,
            OPSTR_PHONE_CALL_MICROPHONE,
            OPSTR_PHONE_CALL_CAMERA,
            OPSTR_RECORD_AUDIO_HOTWORD,
            OPSTR_MANAGE_ONGOING_CALLS,
            OPSTR_MANAGE_CREDENTIALS,
            OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER,
            OPSTR_RECORD_AUDIO_OUTPUT,
            OPSTR_SCHEDULE_EXACT_ALARM,
            OPSTR_FINE_LOCATION_SOURCE,
            OPSTR_COARSE_LOCATION_SOURCE,
            OPSTR_MANAGE_MEDIA,
            OPSTR_BLUETOOTH_CONNECT,
    };

    /**
     * This provides a simple name for each operation to be used
     * in debug output.
     */
    private static final String[] sOpNames = new String[]{
            "COARSE_LOCATION",
            "FINE_LOCATION",
            "GPS",
            "VIBRATE",
            "READ_CONTACTS",
            "WRITE_CONTACTS",
            "READ_CALL_LOG",
            "WRITE_CALL_LOG",
            "READ_CALENDAR",
            "WRITE_CALENDAR",
            "WIFI_SCAN",
            "POST_NOTIFICATION",
            "NEIGHBORING_CELLS",
            "CALL_PHONE",
            "READ_SMS",
            "WRITE_SMS",
            "RECEIVE_SMS",
            "RECEIVE_EMERGECY_SMS",
            "RECEIVE_MMS",
            "RECEIVE_WAP_PUSH",
            "SEND_SMS",
            "READ_ICC_SMS",
            "WRITE_ICC_SMS",
            "WRITE_SETTINGS",
            "SYSTEM_ALERT_WINDOW",
            "ACCESS_NOTIFICATIONS",
            "CAMERA",
            "RECORD_AUDIO",
            "PLAY_AUDIO",
            "READ_CLIPBOARD",
            "WRITE_CLIPBOARD",
            "TAKE_MEDIA_BUTTONS",
            "TAKE_AUDIO_FOCUS",
            "AUDIO_MASTER_VOLUME",
            "AUDIO_VOICE_VOLUME",
            "AUDIO_RING_VOLUME",
            "AUDIO_MEDIA_VOLUME",
            "AUDIO_ALARM_VOLUME",
            "AUDIO_NOTIFICATION_VOLUME",
            "AUDIO_BLUETOOTH_VOLUME",
            "WAKE_LOCK",
            "MONITOR_LOCATION",
            "MONITOR_HIGH_POWER_LOCATION",
            "GET_USAGE_STATS",
            "MUTE_MICROPHONE",
            "TOAST_WINDOW",
            "PROJECT_MEDIA",
            "ACTIVATE_VPN",
            "WRITE_WALLPAPER",
            "ASSIST_STRUCTURE",
            "ASSIST_SCREENSHOT",
            "READ_PHONE_STATE",
            "ADD_VOICEMAIL",
            "USE_SIP",
            "PROCESS_OUTGOING_CALLS",
            "USE_FINGERPRINT",
            "BODY_SENSORS",
            "READ_CELL_BROADCASTS",
            "MOCK_LOCATION",
            "READ_EXTERNAL_STORAGE",
            "WRITE_EXTERNAL_STORAGE",
            "TURN_ON_SCREEN",
            "GET_ACCOUNTS",
            "RUN_IN_BACKGROUND",
            "AUDIO_ACCESSIBILITY_VOLUME",
            "READ_PHONE_NUMBERS",
            "REQUEST_INSTALL_PACKAGES",
            "PICTURE_IN_PICTURE",
            "INSTANT_APP_START_FOREGROUND",
            "ANSWER_PHONE_CALLS",
            "RUN_ANY_IN_BACKGROUND",
            "CHANGE_WIFI_STATE",
            "REQUEST_DELETE_PACKAGES",
            "BIND_ACCESSIBILITY_SERVICE",
            "ACCEPT_HANDOVER",
            "MANAGE_IPSEC_TUNNELS",
            "START_FOREGROUND",
            "BLUETOOTH_SCAN",
            "USE_BIOMETRIC",
            "ACTIVITY_RECOGNITION",
            "SMS_FINANCIAL_TRANSACTIONS",
            "READ_MEDIA_AUDIO",
            "WRITE_MEDIA_AUDIO",
            "READ_MEDIA_VIDEO",
            "WRITE_MEDIA_VIDEO",
            "READ_MEDIA_IMAGES",
            "WRITE_MEDIA_IMAGES",
            "LEGACY_STORAGE",
            "ACCESS_ACCESSIBILITY",
            "READ_DEVICE_IDENTIFIERS",
            "ACCESS_MEDIA_LOCATION",
            "QUERY_ALL_PACKAGES",
            "MANAGE_EXTERNAL_STORAGE",
            "INTERACT_ACROSS_PROFILES",
            "ACTIVATE_PLATFORM_VPN",
            "LOADER_USAGE_STATS",
            "deprecated",
            "AUTO_REVOKE_PERMISSIONS_IF_UNUSED",
            "AUTO_REVOKE_MANAGED_BY_INSTALLER",
            "NO_ISOLATED_STORAGE",
            "PHONE_CALL_MICROPHONE",
            "PHONE_CALL_CAMERA",
            "RECORD_AUDIO_HOTWORD",
            "MANAGE_ONGOING_CALLS",
            "MANAGE_CREDENTIALS",
            "USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER",
            "RECORD_AUDIO_OUTPUT",
            "SCHEDULE_EXACT_ALARM",
            "FINE_LOCATION_SOURCE",
            "COARSE_LOCATION_SOURCE",
            "MANAGE_MEDIA",
            "BLUETOOTH_CONNECT"
    };

    /**
     * This provides a simple name for each MIUI app operation to be used
     * in debug output.
     */
    public static final String[] sMiuiOpNames = new String[]{
            "WIFI_CHANGE",
            "BLUETOOTH_CHANGE",
            "DATA_CONNECT_CHANGE",
            "SEND_MMS",
            "READ_MMS",
            "WRITE_MMS",
            "BOOT_COMPLETED",
            "AUTO_START",
            "NFC_CHANGE",
            "DELETE_SMS",
            "DELETE_MMS",
            "DELETE_CONTACTS",
            "DELETE_CALL_LOG",
            "EXACT_ALARM",
            "ACCESS_XIAOMI_ACCOUNT",
            "NFC",
            "INSTALL_SHORTCUT",
            "READ_NOTIFICATION_SMS",
            "GET_TASKS",
            "SHOW_WHEN_LOCKED",
            "BACKGROUND_START_ACTIVITY",
            "GET_INSTALLED_APPS",
            "SERVICE_FOREGROUND",
            "GET_ANONYMOUS_ID",
            "GET_UDEVICE_ID",
            "DEAMON_NOTIFICATION",
            "BACKGROUND_LOCATION",
            "READ_SMS_REAL",
            "READ_CONTACTS_REAL",
            "READ_CALENDAR_REAL",
            "READ_CALL_LOG_REAL",
            "READ_PHONE_STATE_REAL",
            "POST_LOCAL_NOTIFICATION",
            "ACCESS_GALLERY",
            "ACCESS_SOCIALITY",
            "ACCESS_LOCATION_REAL",
    };

    /**
     * This optionally maps a permission to an operation.  If there
     * is no permission associated with an operation, it is null.
     */
    private static final String[] sOpPerms = new String[]{
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            null, // no permission for gps
            android.Manifest.permission.VIBRATE,  // Normal
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.WRITE_CONTACTS,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.WRITE_CALL_LOG,
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.WRITE_CALENDAR,
            android.Manifest.permission.ACCESS_WIFI_STATE,  // Normal
            null, // no permission required for notifications
            null, // neighboring cells shares the coarse location perm
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.READ_SMS,
            null, // no permission required for writing sms
            android.Manifest.permission.RECEIVE_SMS,
            "android.permission.RECEIVE_EMERGENCY_BROADCAST",
            android.Manifest.permission.RECEIVE_MMS,
            android.Manifest.permission.RECEIVE_WAP_PUSH,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_SMS,
            null, // no permission required for writing icc sms
            android.Manifest.permission.WRITE_SETTINGS,
            android.Manifest.permission.SYSTEM_ALERT_WINDOW,
            "android.permission.ACCESS_NOTIFICATIONS",
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            null, // no permission for playing audio
            null, // no permission for reading clipboard
            null, // no permission for writing clipboard
            null, // no permission for taking media buttons
            null, // no permission for taking audio focus
            null, // no permission for changing global volume
            null, // no permission for changing voice volume
            null, // no permission for changing ring volume
            null, // no permission for changing media volume
            null, // no permission for changing alarm volume
            null, // no permission for changing notification volume
            null, // no permission for changing bluetooth volume
            android.Manifest.permission.WAKE_LOCK,  // Normal
            null, // no permission for generic location monitoring
            null, // no permission for high power location monitoring
            "android.permission.PACKAGE_USAGE_STATS",
            null, // no permission for muting/unmuting microphone
            null, // no permission for displaying toasts
            null, // no permission for projecting media
            null, // no permission for activating vpn
            null, // no permission for supporting wallpaper
            null, // no permission for receiving assist structure
            null, // no permission for receiving assist screenshot
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ADD_VOICEMAIL,
            Manifest.permission.USE_SIP,
            "android.permission.PROCESS_OUTGOING_CALLS",
            "android.permission.USE_FINGERPRINT",  // Normal
            Manifest.permission.BODY_SENSORS,
            "android.permission.READ_CELL_BROADCASTS",
            null,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            null, // no permission for turning the screen on
            Manifest.permission.GET_ACCOUNTS,
            null, // no permission for running in background
            null, // no permission for changing accessibility volume
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.REQUEST_INSTALL_PACKAGES",
            null, // no permission for entering picture-in-picture on hide
            "android.permission.INSTANT_APP_FOREGROUND_SERVICE",
            "android.permission.ANSWER_PHONE_CALLS",
            null, // no permission for OP_RUN_ANY_IN_BACKGROUND
            Manifest.permission.CHANGE_WIFI_STATE,  // Normal
            "android.permission.REQUEST_DELETE_PACKAGES",  // Normal
            Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
            "android.permission.ACCEPT_HANDOVER",
            "android.permission.MANAGE_IPSEC_TUNNELS",
            "android.permission.FOREGROUND_SERVICE",  // Normal
            null, // no permission for OP_BLUETOOTH_SCAN
            "android.permission.USE_BIOMETRIC",  // Normal
            "android.permission.ACTIVITY_RECOGNITION",
            "android.permission.SMS_FINANCIAL_TRANSACTIONS",
            null,
            null, // no permission for OP_WRITE_MEDIA_AUDIO
            null,
            null, // no permission for OP_WRITE_MEDIA_VIDEO
            null,
            null, // no permission for OP_WRITE_MEDIA_IMAGES
            null, // no permission for OP_LEGACY_STORAGE
            null, // no permission for OP_ACCESS_ACCESSIBILITY
            null, // no direct permission for OP_READ_DEVICE_IDENTIFIERS
            "android.permission.ACCESS_MEDIA_LOCATION",
            null, // no permission for OP_QUERY_ALL_PACKAGES
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.INTERACT_ACROSS_PROFILES",
            null, // no permission for OP_ACTIVATE_PLATFORM_VPN
            "android.permission.LOADER_USAGE_STATS",
            null, // deprecated operation
            null, // no permission for OP_AUTO_REVOKE_PERMISSIONS_IF_UNUSED
            null, // no permission for OP_AUTO_REVOKE_MANAGED_BY_INSTALLER
            null, // no permission for OP_NO_ISOLATED_STORAGE
            null, // no permission for OP_PHONE_CALL_MICROPHONE
            null, // no permission for OP_PHONE_CALL_CAMERA
            null, // no permission for OP_RECORD_AUDIO_HOTWORD
            "android.permission.MANAGE_ONGOING_CALLS",
            null, // no permission for OP_MANAGE_CREDENTIALS
            "android.permission.USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER",
            null, // no permission for OP_RECORD_AUDIO_OUTPUT
            "android.permission.SCHEDULE_EXACT_ALARM",
            null, // no permission for OP_FINE_LOCATION_SOURCE
            null, // no permission for OP_COARSE_LOCATION_SOURCE
            "android.permission.MANAGE_MEDIA",
            "android.permission.BLUETOOTH_CONNECT",
    };

    /**
     * Specifies whether an Op should be restricted by a user restriction.
     * Each Op should be filled with a restriction string from UserManager or
     * null to specify it is not affected by any user restriction.
     */
    private static final String[] sOpRestrictions = new String[]{
            UserManager.DISALLOW_SHARE_LOCATION, //COARSE_LOCATION
            UserManager.DISALLOW_SHARE_LOCATION, //FINE_LOCATION
            UserManager.DISALLOW_SHARE_LOCATION, //GPS
            null, //VIBRATE
            null, //READ_CONTACTS
            null, //WRITE_CONTACTS
            UserManager.DISALLOW_OUTGOING_CALLS, //READ_CALL_LOG
            UserManager.DISALLOW_OUTGOING_CALLS, //WRITE_CALL_LOG
            null, //READ_CALENDAR
            null, //WRITE_CALENDAR
            UserManager.DISALLOW_SHARE_LOCATION, //WIFI_SCAN
            null, //POST_NOTIFICATION
            null, //NEIGHBORING_CELLS
            null, //CALL_PHONE
            UserManager.DISALLOW_SMS, //READ_SMS
            UserManager.DISALLOW_SMS, //WRITE_SMS
            UserManager.DISALLOW_SMS, //RECEIVE_SMS
            null, //RECEIVE_EMERGENCY_SMS
            UserManager.DISALLOW_SMS, //RECEIVE_MMS
            null, //RECEIVE_WAP_PUSH
            UserManager.DISALLOW_SMS, //SEND_SMS
            UserManager.DISALLOW_SMS, //READ_ICC_SMS
            UserManager.DISALLOW_SMS, //WRITE_ICC_SMS
            null, //WRITE_SETTINGS
            UserManager.DISALLOW_CREATE_WINDOWS, //SYSTEM_ALERT_WINDOW
            null, //ACCESS_NOTIFICATIONS
            "no_camera", // UserManager.DISALLOW_CAMERA, //CAMERA
            "no_record_audio", // UserManager.DISALLOW_RECORD_AUDIO, //RECORD_AUDIO
            null, //PLAY_AUDIO
            null, //READ_CLIPBOARD
            null, //WRITE_CLIPBOARD
            null, //TAKE_MEDIA_BUTTONS
            null, //TAKE_AUDIO_FOCUS
            UserManager.DISALLOW_ADJUST_VOLUME, //AUDIO_MASTER_VOLUME
            UserManager.DISALLOW_ADJUST_VOLUME, //AUDIO_VOICE_VOLUME
            UserManager.DISALLOW_ADJUST_VOLUME, //AUDIO_RING_VOLUME
            UserManager.DISALLOW_ADJUST_VOLUME, //AUDIO_MEDIA_VOLUME
            UserManager.DISALLOW_ADJUST_VOLUME, //AUDIO_ALARM_VOLUME
            UserManager.DISALLOW_ADJUST_VOLUME, //AUDIO_NOTIFICATION_VOLUME
            UserManager.DISALLOW_ADJUST_VOLUME, //AUDIO_BLUETOOTH_VOLUME
            null, //WAKE_LOCK
            UserManager.DISALLOW_SHARE_LOCATION, //MONITOR_LOCATION
            UserManager.DISALLOW_SHARE_LOCATION, //MONITOR_HIGH_POWER_LOCATION
            null, //GET_USAGE_STATS
            UserManager.DISALLOW_UNMUTE_MICROPHONE, // MUTE_MICROPHONE
            UserManager.DISALLOW_CREATE_WINDOWS, // TOAST_WINDOW
            null, //PROJECT_MEDIA
            null, // ACTIVATE_VPN
            "no_wallpaper", // UserManager.DISALLOW_WALLPAPER, // WRITE_WALLPAPER
            null, // ASSIST_STRUCTURE
            null, // ASSIST_SCREENSHOT
            null, // READ_PHONE_STATE
            null, // ADD_VOICEMAIL
            null, // USE_SIP
            null, // PROCESS_OUTGOING_CALLS
            null, // USE_FINGERPRINT
            null, // BODY_SENSORS
            null, // READ_CELL_BROADCASTS
            null, // MOCK_LOCATION
            null, // READ_EXTERNAL_STORAGE
            null, // WRITE_EXTERNAL_STORAGE
            null, // TURN_ON_SCREEN
            null, // GET_ACCOUNTS
            null, // RUN_IN_BACKGROUND
            UserManager.DISALLOW_ADJUST_VOLUME, //AUDIO_ACCESSIBILITY_VOLUME
            null, // READ_PHONE_NUMBERS
            null, // REQUEST_INSTALL_PACKAGES
            null, // ENTER_PICTURE_IN_PICTURE_ON_HIDE
            null, // INSTANT_APP_START_FOREGROUND
            null, // ANSWER_PHONE_CALLS
            null, // OP_RUN_ANY_IN_BACKGROUND
            null, // OP_CHANGE_WIFI_STATE
            null, // REQUEST_DELETE_PACKAGES
            null, // OP_BIND_ACCESSIBILITY_SERVICE
            null, // ACCEPT_HANDOVER
            null, // MANAGE_IPSEC_TUNNELS
            null, // START_FOREGROUND
            null, // maybe should be UserManager.DISALLOW_SHARE_LOCATION, //BLUETOOTH_SCAN
            null, // USE_BIOMETRIC
            null, // ACTIVITY_RECOGNITION
            UserManager.DISALLOW_SMS, // SMS_FINANCIAL_TRANSACTIONS
            null, // READ_MEDIA_AUDIO
            null, // WRITE_MEDIA_AUDIO
            null, // READ_MEDIA_VIDEO
            null, // WRITE_MEDIA_VIDEO
            null, // READ_MEDIA_IMAGES
            null, // WRITE_MEDIA_IMAGES
            null, // LEGACY_STORAGE
            null, // ACCESS_ACCESSIBILITY
            null, // READ_DEVICE_IDENTIFIERS
            null, // ACCESS_MEDIA_LOCATION
            null, // QUERY_ALL_PACKAGES
            null, // MANAGE_EXTERNAL_STORAGE
            null, // INTERACT_ACROSS_PROFILES
            null, // ACTIVATE_PLATFORM_VPN
            null, // LOADER_USAGE_STATS
            null, // deprecated operation
            null, // AUTO_REVOKE_PERMISSIONS_IF_UNUSED
            null, // AUTO_REVOKE_MANAGED_BY_INSTALLER
            null, // NO_ISOLATED_STORAGE
            null, // PHONE_CALL_MICROPHONE
            null, // PHONE_CALL_MICROPHONE
            null, // RECORD_AUDIO_HOTWORD
            null, // MANAGE_ONGOING_CALLS
            null, // MANAGE_CREDENTIALS
            null, // USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER
            null, // RECORD_AUDIO_OUTPUT
            null, // SCHEDULE_EXACT_ALARM
            null, // FINE_LOCATION_SOURCE
            null, // COARSE_LOCATION_SOURCE
            null, // MANAGE_MEDIA
            null, // BLUETOOTH_CONNECT
    };

    /**
     * In which cases should an app be allowed to bypass the user restriction for a certain app-op.
     */
    private static final RestrictionBypass[] sOpAllowSystemRestrictionBypass = new RestrictionBypass[]{
            new RestrictionBypass(true, false), //COARSE_LOCATION
            new RestrictionBypass(true, false), //FINE_LOCATION
            null, //GPS
            null, //VIBRATE
            null, //READ_CONTACTS
            null, //WRITE_CONTACTS
            null, //READ_CALL_LOG
            null, //WRITE_CALL_LOG
            null, //READ_CALENDAR
            null, //WRITE_CALENDAR
            new RestrictionBypass(true, false), //WIFI_SCAN
            null, //POST_NOTIFICATION
            null, //NEIGHBORING_CELLS
            null, //CALL_PHONE
            null, //READ_SMS
            null, //WRITE_SMS
            null, //RECEIVE_SMS
            null, //RECEIVE_EMERGECY_SMS
            null, //RECEIVE_MMS
            null, //RECEIVE_WAP_PUSH
            null, //SEND_SMS
            null, //READ_ICC_SMS
            null, //WRITE_ICC_SMS
            null, //WRITE_SETTINGS
            new RestrictionBypass(true, false), //SYSTEM_ALERT_WINDOW
            null, //ACCESS_NOTIFICATIONS
            null, //CAMERA
            new RestrictionBypass(false, true), //RECORD_AUDIO
            null, //PLAY_AUDIO
            null, //READ_CLIPBOARD
            null, //WRITE_CLIPBOARD
            null, //TAKE_MEDIA_BUTTONS
            null, //TAKE_AUDIO_FOCUS
            null, //AUDIO_MASTER_VOLUME
            null, //AUDIO_VOICE_VOLUME
            null, //AUDIO_RING_VOLUME
            null, //AUDIO_MEDIA_VOLUME
            null, //AUDIO_ALARM_VOLUME
            null, //AUDIO_NOTIFICATION_VOLUME
            null, //AUDIO_BLUETOOTH_VOLUME
            null, //WAKE_LOCK
            null, //MONITOR_LOCATION
            null, //MONITOR_HIGH_POWER_LOCATION
            null, //GET_USAGE_STATS
            null, //MUTE_MICROPHONE
            new RestrictionBypass(true, false), //TOAST_WINDOW
            null, //PROJECT_MEDIA
            null, //ACTIVATE_VPN
            null, //WALLPAPER
            null, //ASSIST_STRUCTURE
            null, //ASSIST_SCREENSHOT
            null, //READ_PHONE_STATE
            null, //ADD_VOICEMAIL
            null, // USE_SIP
            null, // PROCESS_OUTGOING_CALLS
            null, // USE_FINGERPRINT
            null, // BODY_SENSORS
            null, // READ_CELL_BROADCASTS
            null, // MOCK_LOCATION
            null, // READ_EXTERNAL_STORAGE
            null, // WRITE_EXTERNAL_STORAGE
            null, // TURN_ON_SCREEN
            null, // GET_ACCOUNTS
            null, // RUN_IN_BACKGROUND
            null, // AUDIO_ACCESSIBILITY_VOLUME
            null, // READ_PHONE_NUMBERS
            null, // REQUEST_INSTALL_PACKAGES
            null, // ENTER_PICTURE_IN_PICTURE_ON_HIDE
            null, // INSTANT_APP_START_FOREGROUND
            null, // ANSWER_PHONE_CALLS
            null, // OP_RUN_ANY_IN_BACKGROUND
            null, // OP_CHANGE_WIFI_STATE
            null, // OP_REQUEST_DELETE_PACKAGES
            null, // OP_BIND_ACCESSIBILITY_SERVICE
            null, // ACCEPT_HANDOVER
            null, // MANAGE_IPSEC_HANDOVERS
            null, // START_FOREGROUND
            new RestrictionBypass(true, false), // BLUETOOTH_SCAN
            null, // USE_BIOMETRIC
            null, // ACTIVITY_RECOGNITION
            null, // SMS_FINANCIAL_TRANSACTIONS
            null, // READ_MEDIA_AUDIO
            null, // WRITE_MEDIA_AUDIO
            null, // READ_MEDIA_VIDEO
            null, // WRITE_MEDIA_VIDEO
            null, // READ_MEDIA_IMAGES
            null, // WRITE_MEDIA_IMAGES
            null, // LEGACY_STORAGE
            null, // ACCESS_ACCESSIBILITY
            null, // READ_DEVICE_IDENTIFIERS
            null, // ACCESS_MEDIA_LOCATION
            null, // QUERY_ALL_PACKAGES
            null, // MANAGE_EXTERNAL_STORAGE
            null, // INTERACT_ACROSS_PROFILES
            null, // ACTIVATE_PLATFORM_VPN
            null, // LOADER_USAGE_STATS
            null, // deprecated operation
            null, // AUTO_REVOKE_PERMISSIONS_IF_UNUSED
            null, // AUTO_REVOKE_MANAGED_BY_INSTALLER
            null, // NO_ISOLATED_STORAGE
            null, // PHONE_CALL_MICROPHONE
            null, // PHONE_CALL_CAMERA
            null, // RECORD_AUDIO_HOTWORD
            null, // MANAGE_ONGOING_CALLS
            null, // MANAGE_CREDENTIALS
            null, // USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER
            null, // RECORD_AUDIO_OUTPUT
            null, // SCHEDULE_EXACT_ALARM
            null, // FINE_LOCATION_SOURCE
            null, // COARSE_LOCATION_SOURCE
            null, // MANAGE_MEDIA
            null, // BLUETOOTH_CONNECT
    };

    /**
     * This specifies the default mode for each operation.
     */
    private static final int[] sOpDefaultMode = new int[]{
            MODE_ALLOWED, // COARSE_LOCATION
            MODE_ALLOWED, // FINE_LOCATION
            MODE_ALLOWED, // GPS
            MODE_ALLOWED, // VIBRATE
            MODE_ALLOWED, // READ_CONTACTS
            MODE_ALLOWED, // WRITE_CONTACTS
            MODE_ALLOWED, // READ_CALL_LOG
            MODE_ALLOWED, // WRITE_CALL_LOG
            MODE_ALLOWED, // READ_CALENDAR
            MODE_ALLOWED, // WRITE_CALENDAR
            MODE_ALLOWED, // WIFI_SCAN
            MODE_ALLOWED, // POST_NOTIFICATION
            MODE_ALLOWED, // NEIGHBORING_CELLS
            MODE_ALLOWED, // CALL_PHONE
            MODE_ALLOWED, // READ_SMS
            MODE_IGNORED, // WRITE_SMS
            MODE_ALLOWED, // RECEIVE_SMS
            MODE_ALLOWED, // RECEIVE_EMERGENCY_BROADCAST
            MODE_ALLOWED, // RECEIVE_MMS
            MODE_ALLOWED, // RECEIVE_WAP_PUSH
            MODE_ALLOWED, // SEND_SMS
            MODE_ALLOWED, // READ_ICC_SMS
            MODE_ALLOWED, // WRITE_ICC_SMS
            MODE_DEFAULT, // WRITE_SETTINGS
            getSystemAlertWindowDefault(), // SYSTEM_ALERT_WINDOW
            MODE_ALLOWED, // ACCESS_NOTIFICATIONS
            MODE_ALLOWED, // CAMERA
            MODE_ALLOWED, // RECORD_AUDIO
            MODE_ALLOWED, // PLAY_AUDIO
            MODE_ALLOWED, // READ_CLIPBOARD
            MODE_ALLOWED, // WRITE_CLIPBOARD
            MODE_ALLOWED, // TAKE_MEDIA_BUTTONS
            MODE_ALLOWED, // TAKE_AUDIO_FOCUS
            MODE_ALLOWED, // AUDIO_MASTER_VOLUME
            MODE_ALLOWED, // AUDIO_VOICE_VOLUME
            MODE_ALLOWED, // AUDIO_RING_VOLUME
            MODE_ALLOWED, // AUDIO_MEDIA_VOLUME
            MODE_ALLOWED, // AUDIO_ALARM_VOLUME
            MODE_ALLOWED, // AUDIO_NOTIFICATION_VOLUME
            MODE_ALLOWED, // AUDIO_BLUETOOTH_VOLUME
            MODE_ALLOWED, // WAKE_LOCK
            MODE_ALLOWED, // MONITOR_LOCATION
            MODE_ALLOWED, // MONITOR_HIGH_POWER_LOCATION
            MODE_DEFAULT, // GET_USAGE_STATS
            MODE_ALLOWED, // MUTE_MICROPHONE
            MODE_ALLOWED, // TOAST_WINDOW
            MODE_IGNORED, // PROJECT_MEDIA
            MODE_IGNORED, // ACTIVATE_VPN
            MODE_ALLOWED, // WRITE_WALLPAPER
            MODE_ALLOWED, // ASSIST_STRUCTURE
            MODE_ALLOWED, // ASSIST_SCREENSHOT
            MODE_ALLOWED, // READ_PHONE_STATE
            MODE_ALLOWED, // ADD_VOICEMAIL
            MODE_ALLOWED, // USE_SIP
            MODE_ALLOWED, // PROCESS_OUTGOING_CALLS
            MODE_ALLOWED, // USE_FINGERPRINT
            MODE_ALLOWED, // BODY_SENSORS
            MODE_ALLOWED, // READ_CELL_BROADCASTS
            MODE_ERRORED, // MOCK_LOCATION
            MODE_ALLOWED, // READ_EXTERNAL_STORAGE
            MODE_ALLOWED, // WRITE_EXTERNAL_STORAGE
            MODE_ALLOWED, // TURN_SCREEN_ON
            MODE_ALLOWED, // GET_ACCOUNTS
            MODE_ALLOWED, // RUN_IN_BACKGROUND
            MODE_ALLOWED, // AUDIO_ACCESSIBILITY_VOLUME
            MODE_ALLOWED, // READ_PHONE_NUMBERS
            MODE_DEFAULT, // REQUEST_INSTALL_PACKAGES
            MODE_ALLOWED, // PICTURE_IN_PICTURE
            MODE_DEFAULT, // INSTANT_APP_START_FOREGROUND
            MODE_ALLOWED, // ANSWER_PHONE_CALLS
            MODE_ALLOWED, // RUN_ANY_IN_BACKGROUND
            MODE_ALLOWED, // CHANGE_WIFI_STATE
            MODE_ALLOWED, // REQUEST_DELETE_PACKAGES
            MODE_ALLOWED, // BIND_ACCESSIBILITY_SERVICE
            MODE_ALLOWED, // ACCEPT_HANDOVER
            MODE_ERRORED, // MANAGE_IPSEC_TUNNELS
            MODE_ALLOWED, // START_FOREGROUND
            MODE_ALLOWED, // BLUETOOTH_SCAN
            MODE_ALLOWED, // USE_BIOMETRIC
            MODE_ALLOWED, // ACTIVITY_RECOGNITION
            MODE_DEFAULT, // SMS_FINANCIAL_TRANSACTIONS
            MODE_ALLOWED, // READ_MEDIA_AUDIO
            MODE_ERRORED, // WRITE_MEDIA_AUDIO
            MODE_ALLOWED, // READ_MEDIA_VIDEO
            MODE_ERRORED, // WRITE_MEDIA_VIDEO
            MODE_ALLOWED, // READ_MEDIA_IMAGES
            MODE_ERRORED, // WRITE_MEDIA_IMAGES
            MODE_DEFAULT, // LEGACY_STORAGE
            MODE_ALLOWED, // ACCESS_ACCESSIBILITY
            MODE_ERRORED, // READ_DEVICE_IDENTIFIERS
            MODE_ALLOWED, // ALLOW_MEDIA_LOCATION
            MODE_DEFAULT, // QUERY_ALL_PACKAGES
            MODE_DEFAULT, // MANAGE_EXTERNAL_STORAGE
            MODE_DEFAULT, // INTERACT_ACROSS_PROFILES
            MODE_IGNORED, // ACTIVATE_PLATFORM_VPN
            MODE_DEFAULT, // LOADER_USAGE_STATS
            MODE_IGNORED, // deprecated operation
            MODE_DEFAULT, // OP_AUTO_REVOKE_PERMISSIONS_IF_UNUSED
            MODE_ALLOWED, // OP_AUTO_REVOKE_MANAGED_BY_INSTALLER
            MODE_ERRORED, // OP_NO_ISOLATED_STORAGE
            MODE_ALLOWED, // PHONE_CALL_MICROPHONE
            MODE_ALLOWED, // PHONE_CALL_CAMERA
            MODE_ALLOWED, // OP_RECORD_AUDIO_HOTWORD
            MODE_DEFAULT, // MANAGE_ONGOING_CALLS
            MODE_DEFAULT, // MANAGE_CREDENTIALS
            MODE_DEFAULT, // USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER
            MODE_ALLOWED, // RECORD_AUDIO_OUTPUT
            MODE_DEFAULT, // SCHEDULE_EXACT_ALARM
            MODE_ALLOWED, // FINE_LOCATION_SOURCE
            MODE_ALLOWED, // COARSE_LOCATION_SOURCE
            MODE_DEFAULT, // MANAGE_MEDIA
            MODE_ALLOWED, // BLUETOOTH_CONNECT
    };


    private static int getSystemAlertWindowDefault() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return MODE_IGNORED;
        return MODE_DEFAULT;
    }

    /**
     * This specifies whether each option is allowed to be reset
     * when resetting all app preferences.  Disable reset for
     * app ops that are under strong control of some part of the
     * system (such as OP_WRITE_SMS, which should be allowed only
     * for whichever app is selected as the current SMS app).
     */
    private static final boolean[] sOpDisableReset = new boolean[]{
            false, // COARSE_LOCATION
            false, // FINE_LOCATION
            false, // GPS
            false, // VIBRATE
            false, // READ_CONTACTS
            false, // WRITE_CONTACTS
            false, // READ_CALL_LOG
            false, // WRITE_CALL_LOG
            false, // READ_CALENDAR
            false, // WRITE_CALENDAR
            false, // WIFI_SCAN
            false, // POST_NOTIFICATION
            false, // NEIGHBORING_CELLS
            false, // CALL_PHONE
            true, // READ_SMS
            true, // WRITE_SMS
            true, // RECEIVE_SMS
            false, // RECEIVE_EMERGENCY_BROADCAST
            false, // RECEIVE_MMS
            true, // RECEIVE_WAP_PUSH
            true, // SEND_SMS
            false, // READ_ICC_SMS
            false, // WRITE_ICC_SMS
            false, // WRITE_SETTINGS
            false, // SYSTEM_ALERT_WINDOW
            false, // ACCESS_NOTIFICATIONS
            false, // CAMERA
            false, // RECORD_AUDIO
            false, // PLAY_AUDIO
            false, // READ_CLIPBOARD
            false, // WRITE_CLIPBOARD
            false, // TAKE_MEDIA_BUTTONS
            false, // TAKE_AUDIO_FOCUS
            false, // AUDIO_MASTER_VOLUME
            false, // AUDIO_VOICE_VOLUME
            false, // AUDIO_RING_VOLUME
            false, // AUDIO_MEDIA_VOLUME
            false, // AUDIO_ALARM_VOLUME
            false, // AUDIO_NOTIFICATION_VOLUME
            false, // AUDIO_BLUETOOTH_VOLUME
            false, // WAKE_LOCK
            false, // MONITOR_LOCATION
            false, // MONITOR_HIGH_POWER_LOCATION
            false, // GET_USAGE_STATS
            false, // MUTE_MICROPHONE
            false, // TOAST_WINDOW
            false, // PROJECT_MEDIA
            false, // ACTIVATE_VPN
            false, // WRITE_WALLPAPER
            false, // ASSIST_STRUCTURE
            false, // ASSIST_SCREENSHOT
            false, // READ_PHONE_STATE
            false, // ADD_VOICEMAIL
            false, // USE_SIP
            false, // PROCESS_OUTGOING_CALLS
            false, // USE_FINGERPRINT
            false, // BODY_SENSORS
            true, // READ_CELL_BROADCASTS
            false, // MOCK_LOCATION
            false, // READ_EXTERNAL_STORAGE
            false, // WRITE_EXTERNAL_STORAGE
            false, // TURN_SCREEN_ON
            false, // GET_ACCOUNTS
            false, // RUN_IN_BACKGROUND
            false, // AUDIO_ACCESSIBILITY_VOLUME
            false, // READ_PHONE_NUMBERS
            false, // REQUEST_INSTALL_PACKAGES
            false, // PICTURE_IN_PICTURE
            false, // INSTANT_APP_START_FOREGROUND
            false, // ANSWER_PHONE_CALLS
            false, // RUN_ANY_IN_BACKGROUND
            false, // CHANGE_WIFI_STATE
            false, // REQUEST_DELETE_PACKAGES
            false, // BIND_ACCESSIBILITY_SERVICE
            false, // ACCEPT_HANDOVER
            false, // MANAGE_IPSEC_TUNNELS
            false, // START_FOREGROUND
            false, // BLUETOOTH_SCAN
            false, // USE_BIOMETRIC
            false, // ACTIVITY_RECOGNITION
            false, // SMS_FINANCIAL_TRANSACTIONS
            false, // READ_MEDIA_AUDIO
            false, // WRITE_MEDIA_AUDIO
            false, // READ_MEDIA_VIDEO
            false, // WRITE_MEDIA_VIDEO
            false, // READ_MEDIA_IMAGES
            false, // WRITE_MEDIA_IMAGES
            true,  // LEGACY_STORAGE
            false, // ACCESS_ACCESSIBILITY
            false, // READ_DEVICE_IDENTIFIERS
            false, // ACCESS_MEDIA_LOCATION
            false, // QUERY_ALL_PACKAGES
            false, // MANAGE_EXTERNAL_STORAGE
            false, // INTERACT_ACROSS_PROFILES
            false, // ACTIVATE_PLATFORM_VPN
            false, // LOADER_USAGE_STATS
            false, // deprecated operation
            false, // AUTO_REVOKE_PERMISSIONS_IF_UNUSED
            false, // AUTO_REVOKE_MANAGED_BY_INSTALLER
            true, // NO_ISOLATED_STORAGE
            false, // PHONE_CALL_MICROPHONE
            false, // PHONE_CALL_CAMERA
            false, // RECORD_AUDIO_HOTWORD
            true, // MANAGE_ONGOING_CALLS
            false, // MANAGE_CREDENTIALS
            true, // USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER
            false, // RECORD_AUDIO_OUTPUT
            false, // SCHEDULE_EXACT_ALARM
            false, // FINE_LOCATION_SOURCE
            false, // COARSE_LOCATION_SOURCE
            false, // MANAGE_MEDIA
            false, // BLUETOOTH_CONNECT
    };

    /**
     * Mapping from an app op name to the app op code.
     */
    private static final HashMap<String, Integer> sOpStrToOp = new HashMap<>();

    /**
     * Mapping from a permission to the corresponding app op.
     */
    private static final HashMap<String, Integer> sPermToOp = new HashMap<>();

    /**
     * Some ops doesn't have any permissions associated with them and are enabled by default.
     * We are interest in the parents of these ops.
     */
    @Deprecated
    public static int[] sOpsWithNoPerm;

    static {
        // Get _NUM_OP
        int numOp = 112;  // Should be the same as the latest _NUM_OP
        try {
            //noinspection JavaReflectionMemberAccess
            numOp = android.app.AppOpsManager.class.getField("_NUM_OP").getInt(null);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
        _NUM_OP = numOp;

        // Checks
        if (sOpToSwitch.length < _NUM_OP) {
            throw new IllegalStateException("sOpToSwitch length " + sOpToSwitch.length + " should be " + _NUM_OP);
        }
        if (sOpToString.length < _NUM_OP) {
            throw new IllegalStateException("sOpToString length " + sOpToString.length + " should be " + _NUM_OP);
        }
        if (sOpNames.length < _NUM_OP) {
            throw new IllegalStateException("sOpNames length " + sOpNames.length + " should be " + _NUM_OP);
        }
        if (sOpPerms.length < _NUM_OP) {
            throw new IllegalStateException("sOpPerms length " + sOpPerms.length + " should be " + _NUM_OP);
        }
        if (sOpDefaultMode.length < _NUM_OP) {
            throw new IllegalStateException("sOpDefaultMode length " + sOpDefaultMode.length + " should be " + _NUM_OP);
        }
        if (sOpDisableReset.length < _NUM_OP) {
            throw new IllegalStateException("sOpDisableReset length " + sOpDisableReset.length + " should be " + _NUM_OP);
        }
        if (sOpRestrictions.length < _NUM_OP) {
            throw new IllegalStateException("sOpRestrictions length " + sOpRestrictions.length + " should be " + _NUM_OP);
        }
        if (sOpAllowSystemRestrictionBypass.length < _NUM_OP) {
            throw new IllegalStateException("sOpAllowSystemRestrictionsBypass length " + sOpRestrictions.length + " should be " + _NUM_OP);
        }

        // Init sOpWithoutPerm
        HashSet<Integer> opWithoutPerm = new HashSet<>();
        for (int op = 0; op < _NUM_OP; op++) {
            if (sOpToString[op] != null) {
                sOpStrToOp.put(sOpToString[op], op);
            }
            if (sOpPerms[op] == null) {
                opWithoutPerm.add(sOpToSwitch[op]);
            }
            sOpsWithNoPerm = ArrayUtils.convertToIntArray(opWithoutPerm);
        }
        for (int op : RUNTIME_AND_APPOP_PERMISSIONS_OPS) {
            if (sOpPerms[op] != null) {
                sPermToOp.put(sOpPerms[op], op);
            }
        }
    }

    /**
     * Retrieve the op switch that controls the given operation.
     */
    public static int opToSwitch(int op) {
        if (op > 10000 && op < 10033) {
            // MIUI app operations
            return sMiuiOpToSwitch[op - 10000];
        }
        return sOpToSwitch[op];
    }

    /**
     * Retrieve a non-localized name for the operation, for debugging output.
     */
    public static String opToName(int op) {
        if (op == OP_NONE) return "NONE";
        else if (op > MIUI_OP_START && op < MIUI_OP_END) {
            // MIUI app operations
            return sMiuiOpNames[op - MIUI_OP_START - 1];
        }
        return op < sOpNames.length ? sOpNames[op] : ("Unknown(" + op + ")");
    }

    /**
     * Retrieve a non-localized public name for the operation.
     */
    @NonNull
    public static String opToPublicName(int op) {
        return sOpToString[op];
    }

    public static int strDebugOpToOp(String op) {
        for (int i = 0; i < sOpNames.length; i++) {
            if (sOpNames[i].equals(op)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown operation string: " + op);
    }

    /**
     * Retrieve the permission associated with an operation, or null if there is not one.
     */
    public static String opToPermission(int op) {
        return sOpPerms[op];
    }

    /**
     * Retrieve the permission associated with an operation, or null if there is not one.
     *
     * @param op The operation name.
     */
    @Nullable
    public static String opToPermission(@NonNull String op) {
        return opToPermission(strOpToOp(op));
    }

    /**
     * Retrieve the user restriction associated with an operation, or null if there is not one.
     */
    public static String opToRestriction(int op) {
        return sOpRestrictions[op];
    }

    /**
     * Retrieve the app op code for a permission, or null if there is not one.
     * This API is intended to be used for mapping runtime or appop permissions
     * to the corresponding app op.
     */
    public static int permissionToOpCode(String permission) {
        Integer boxedOpCode = sPermToOp.get(permission);
        return boxedOpCode != null ? boxedOpCode : OP_NONE;
    }

    /**
     * Retrieve whether the op allows to bypass the user restriction.
     */
    public static RestrictionBypass opAllowSystemBypassRestriction(int op) {
        return sOpAllowSystemRestrictionBypass[op];
    }

    /**
     * Retrieve the default mode for the operation.
     */
    @Mode
    public static int opToDefaultMode(int op) {
        if (op > OP_NONE && op < _NUM_OP) {
            return sOpDefaultMode[op];
        }
        return op;
    }

    /**
     * Retrieve the default mode for the app op.
     *
     * @param appOp The app op name
     * @return the default mode for the app op
     */
    public static int opToDefaultMode(@NonNull String appOp) {
        return opToDefaultMode(strOpToOp(appOp));
    }

    /**
     * Retrieve the human readable mode.
     */
    public static String modeToName(@Mode int mode) {
        if (mode >= 0 && mode < MODE_NAMES.length) {
            return MODE_NAMES[mode];
        }
        return "mode=" + mode;
    }

    /**
     * Retrieve whether the op allows itself to be reset.
     */
    public static boolean opAllowsReset(int op) {
        return !sOpDisableReset[op];
    }

    public static int strOpToOp(@NonNull String op) {
        Integer val = sOpStrToOp.get(op);
        if (val == null) {
            // TODO: Try old names
            throw new IllegalArgumentException("Unknown operation string: " + op);
        }
        return val;
    }

    /**
     * Gets the app op name associated with a given permission.
     * The app op name is one of the public constants defined
     * in this class such as {@link #OPSTR_COARSE_LOCATION}.
     * This API is intended to be used for mapping runtime
     * permissions to the corresponding app op.
     *
     * @param permission The permission.
     * @return The app op associated with the permission or null.
     */
    @Nullable
    public static String permissionToOp(String permission) {
        final Integer opCode = sPermToOp.get(permission);
        if (opCode == null) {
            return null;
        }
        return sOpToString[opCode];
    }

    /**
     * When to not enforce user restrictions.
     */
    public static class RestrictionBypass {
        /**
         * Does the app need to be privileged to bypass the restriction
         */
        public boolean isPrivileged;

        /**
         * Does the app need to have the EXEMPT_FROM_AUDIO_RESTRICTIONS permission to bypass the
         * restriction
         */
        public boolean isRecordAudioRestrictionExcept;

        public RestrictionBypass(boolean isPrivileged, boolean isRecordAudioRestrictionExcept) {
            this.isPrivileged = isPrivileged;
            this.isRecordAudioRestrictionExcept = isRecordAudioRestrictionExcept;
        }

        public static RestrictionBypass UNRESTRICTED = new RestrictionBypass(true, true);
    }
}