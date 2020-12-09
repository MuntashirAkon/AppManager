/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.server.common;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.usage.UsageStatsManager;
import android.os.Build;

public final class FixCompat {
    /**
     * No operation specified.
     */
    public static final int OP_NONE = -1;
    /**
     * Access to coarse location information.
     */
    public static final int OP_COARSE_LOCATION = 0;
    /**
     * Access to fine location information.
     */
    public static final int OP_FINE_LOCATION = 1;
    /**
     * Causing GPS to run.
     */
    public static final int OP_GPS = 2;
    /**
     *
     */
    public static final int OP_VIBRATE = 3;
    /**
     *
     */
    public static final int OP_READ_CONTACTS = 4;
    /**
     *
     */
    public static final int OP_WRITE_CONTACTS = 5;
    /**
     *
     */
    public static final int OP_READ_CALL_LOG = 6;
    /**
     *
     */
    public static final int OP_WRITE_CALL_LOG = 7;
    /**
     *
     */
    public static final int OP_READ_CALENDAR = 8;
    /**
     *
     */
    public static final int OP_WRITE_CALENDAR = 9;
    /**
     *
     */
    public static final int OP_WIFI_SCAN = 10;
    /**
     *
     */
    public static final int OP_POST_NOTIFICATION = 11;
    /**
     *
     */
    public static final int OP_NEIGHBORING_CELLS = 12;
    /**
     *
     */
    public static final int OP_CALL_PHONE = 13;
    /**
     *
     */
    public static final int OP_READ_SMS = 14;
    /**
     *
     */
    public static final int OP_WRITE_SMS = 15;
    /**
     *
     */
    public static final int OP_RECEIVE_SMS = 16;
    /**
     *
     */
    public static final int OP_RECEIVE_EMERGECY_SMS = 17;
    /**
     *
     */
    public static final int OP_RECEIVE_MMS = 18;
    /**
     *
     */
    public static final int OP_RECEIVE_WAP_PUSH = 19;
    /**
     *
     */
    public static final int OP_SEND_SMS = 20;
    /**
     *
     */
    public static final int OP_READ_ICC_SMS = 21;
    /**
     *
     */
    public static final int OP_WRITE_ICC_SMS = 22;
    /**
     *
     */
    public static final int OP_WRITE_SETTINGS = 23;
    /**
     * Required to draw on top of other apps.
     */
    public static final int OP_SYSTEM_ALERT_WINDOW = 24;
    /**
     *
     */
    public static final int OP_ACCESS_NOTIFICATIONS = 25;
    /**
     *
     */
    public static final int OP_CAMERA = 26;
    /**
     *
     */
    public static final int OP_RECORD_AUDIO = 27;
    /**
     *
     */
    public static final int OP_PLAY_AUDIO = 28;
    /**
     *
     */
    public static final int OP_READ_CLIPBOARD = 29;
    /**
     *
     */
    public static final int OP_WRITE_CLIPBOARD = 30;
    /**
     *
     */
    public static final int OP_TAKE_MEDIA_BUTTONS = 31;
    /**
     *
     */
    public static final int OP_TAKE_AUDIO_FOCUS = 32;
    /**
     *
     */
    public static final int OP_AUDIO_MASTER_VOLUME = 33;
    /**
     *
     */
    public static final int OP_AUDIO_VOICE_VOLUME = 34;
    /**
     *
     */
    public static final int OP_AUDIO_RING_VOLUME = 35;
    /**
     *
     */
    public static final int OP_AUDIO_MEDIA_VOLUME = 36;
    /**
     *
     */
    public static final int OP_AUDIO_ALARM_VOLUME = 37;
    /**
     *
     */
    public static final int OP_AUDIO_NOTIFICATION_VOLUME = 38;
    /**
     *
     */
    public static final int OP_AUDIO_BLUETOOTH_VOLUME = 39;
    /**
     *
     */
    public static final int OP_WAKE_LOCK = 40;
    /**
     * Continually monitoring location data.
     */
    public static final int OP_MONITOR_LOCATION = 41;
    /**
     * Continually monitoring location data with a relatively high power request.
     */
    public static final int OP_MONITOR_HIGH_POWER_LOCATION = 42;
    /**
     * Retrieve current usage stats via {@link UsageStatsManager}.
     */
    public static final int OP_GET_USAGE_STATS = 43;
    /**
     *
     */
    public static final int OP_MUTE_MICROPHONE = 44;
    /**
     *
     */
    public static final int OP_TOAST_WINDOW = 45;
    /**
     * Capture the device's display contents and/or audio
     */
    public static final int OP_PROJECT_MEDIA = 46;
    /**
     * Activate a VPN connection without user intervention.
     */
    public static final int OP_ACTIVATE_VPN = 47;
    /**
     * Access the WallpaperManagerAPI to write wallpapers.
     */
    public static final int OP_WRITE_WALLPAPER = 48;
    /**
     * Received the assist structure from an app.
     */
    public static final int OP_ASSIST_STRUCTURE = 49;
    /**
     * Received a screenshot from assist.
     */
    public static final int OP_ASSIST_SCREENSHOT = 50;
    /**
     * Read the phone state.
     */
    public static final int OP_READ_PHONE_STATE = 51;
    /**
     * Add voicemail messages to the voicemail content provider.
     */
    public static final int OP_ADD_VOICEMAIL = 52;
    /**
     * Access APIs for SIP calling over VOIP or WiFi.
     */
    public static final int OP_USE_SIP = 53;
    /**
     * Intercept outgoing calls.
     */
    public static final int OP_PROCESS_OUTGOING_CALLS = 54;
    /**
     * User the fingerprint API.
     */
    public static final int OP_USE_FINGERPRINT = 55;
    /**
     * Access to body sensors such as heart rate, etc.
     */
    public static final int OP_BODY_SENSORS = 56;
    /**
     * Read previously received cell broadcast messages.
     */
    public static final int OP_READ_CELL_BROADCASTS = 57;
    /**
     * Inject mock location into the system.
     */
    public static final int OP_MOCK_LOCATION = 58;
    /**
     * Read external storage.
     */
    public static final int OP_READ_EXTERNAL_STORAGE = 59;
    /**
     * Write external storage.
     */
    public static final int OP_WRITE_EXTERNAL_STORAGE = 60;
    /**
     * Turned on the screen.
     */
    public static final int OP_TURN_SCREEN_ON = 61;
    /**
     * Get device accounts.
     */
    public static final int OP_GET_ACCOUNTS = 62;
    /**
     * Control whether an application is allowed to run in the background.
     */
    public static final int OP_RUN_IN_BACKGROUND = 63;
    /**
     *
     */
    public static final int OP_AUDIO_ACCESSIBILITY_VOLUME = 64;
    /**
     * Read the phone number.
     */
    public static final int OP_READ_PHONE_NUMBERS = 65;
    /**
     * Request package installs through package installer
     */
    public static final int OP_REQUEST_INSTALL_PACKAGES = 66;
    /**
     * Enter picture-in-picture.
     */
    public static final int OP_PICTURE_IN_PICTURE = 67;
    /**
     * Instant app start foreground service.
     */
    public static final int OP_INSTANT_APP_START_FOREGROUND = 68;
    /**
     * Answer incoming phone calls
     */
    public static final int OP_ANSWER_PHONE_CALLS = 69;
    /**
     * Run jobs when in background
     */
    public static final int OP_RUN_ANY_IN_BACKGROUND = 70;
    /**
     * Change Wi-Fi connectivity state
     */
    public static final int OP_CHANGE_WIFI_STATE = 71;
    /**
     * Request package deletion through package installer
     */
    public static final int OP_REQUEST_DELETE_PACKAGES = 72;
    /**
     * Bind an accessibility service.
     */
    public static final int OP_BIND_ACCESSIBILITY_SERVICE = 73;
    /**
     * Continue handover of a call from another app
     */
    public static final int OP_ACCEPT_HANDOVER = 74;
    /**
     * Create and Manage IPsec Tunnels
     */
    public static final int OP_MANAGE_IPSEC_TUNNELS = 75;
    /**
     * Any app start foreground service.
     */
    public static final int OP_START_FOREGROUND = 76;
    /**
     *
     */
    public static final int OP_BLUETOOTH_SCAN = 77;
    /**
     *
     */
    public static final int _NUM_OP = 78;

    private static final int _NUM_UID_STATE = 6;

    private static int[] _sOpToSwitch = new int[]{
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
    };


    private static String[] _sOpNames = new String[]{
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
            "OP_READ_PHONE_STATE",
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
    };

    /**
     * This optionally maps a permission to an operation.  If there
     * is no permission associated with an operation, it is null.
     */
    private static String[] _sOpPerms = new String[]{
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            null,
            android.Manifest.permission.VIBRATE,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.WRITE_CONTACTS,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.WRITE_CALL_LOG,
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.WRITE_CALENDAR,
            android.Manifest.permission.ACCESS_WIFI_STATE,
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
            null, // no permission for changing master volume
            null, // no permission for changing voice volume
            null, // no permission for changing ring volume
            null, // no permission for changing media volume
            null, // no permission for changing alarm volume
            null, // no permission for changing notification volume
            null, // no permission for changing bluetooth volume
            android.Manifest.permission.WAKE_LOCK,
            null, // no permission for generic location monitoring
            null, // no permission for high power location monitoring
            android.Manifest.permission.PACKAGE_USAGE_STATS,
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
            Manifest.permission.PROCESS_OUTGOING_CALLS,
            Manifest.permission.USE_FINGERPRINT,
            Manifest.permission.BODY_SENSORS,
            "android.permission.READ_CELL_BROADCASTS",
            null,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            null, // no permission for turning the screen on
            Manifest.permission.GET_ACCOUNTS,
            null, // no permission for running in background
            null, // no permission for changing accessibility volume
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.REQUEST_INSTALL_PACKAGES,
            null, // no permission for entering picture-in-picture on hide
            Manifest.permission.INSTANT_APP_FOREGROUND_SERVICE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            null, // no permission for OP_RUN_ANY_IN_BACKGROUND
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.REQUEST_DELETE_PACKAGES,
            Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
            Manifest.permission.ACCEPT_HANDOVER,
            null, // no permission for OP_MANAGE_IPSEC_TUNNELS
            Manifest.permission.FOREGROUND_SERVICE,
            null, // no permission for OP_BLUETOOTH_SCAN
    };

    private static int[] ref_sOpToSwitch = null;
    private static String[] ref_sOpNames = null;
    private static String[] ref_sOpPerms = null;


    public static int[] sOpToSwitch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return _sOpToSwitch;
        } else {
            if (ref_sOpToSwitch == null) {
                ref_sOpToSwitch = (int[]) ReflectUtils.getFieldValue(AppOpsManager.class, "sOpToSwitch");
            }
            return ref_sOpToSwitch;
        }
    }

    public static String[] sOpNames() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return _sOpNames;
        } else {
            if (ref_sOpNames == null) {
                ref_sOpNames = (String[]) ReflectUtils.getFieldValue(AppOpsManager.class, "sOpNames");
            }
            return ref_sOpNames;
        }
    }

    public static String[] sOpPerms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return _sOpPerms;
        } else {
            if (ref_sOpPerms == null) {
                ref_sOpPerms = (String[]) ReflectUtils.getFieldValue(AppOpsManager.class, "sOpPerms");
            }
            return ref_sOpPerms;
        }
    }


}
