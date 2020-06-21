package io.github.muntashirakon.AppManager.appops;

import android.Manifest;
import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserManager;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * API for interacting with "application operation" tracking.
 *
 * <p>This API is not generally intended for third party application developers; most
 * features are only available to system applications.
 */
@SuppressWarnings("unused")
public class AppOpsManager {
    @IntDef(flag = true, value = {
            MODE_ALLOWED,
            MODE_IGNORED,
            MODE_ERRORED,
            MODE_DEFAULT,
            MODE_FOREGROUND
    })
    public @interface Mode {}
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
    public static final int MODE_FOREGROUND = 1 << 2;

    /**
     * Flag for {@link android.app.AppOpsManager#startWatchingMode}:
     * Also get reports if the foreground state of an op's uid changes.  This only works
     * when watching a particular op, not when watching a package.
     */
    public static final int WATCH_FOREGROUND_CHANGES = 1;

    public static final String[] MODE_NAMES = new String[] {
            "allow",        // MODE_ALLOWED
            "ignore",       // MODE_IGNORED
            "deny",         // MODE_ERRORED
            "default",      // MODE_DEFAULT
            "foreground",   // MODE_FOREGROUND
    };

    @IntDef(value = {
            UID_STATE_PERSISTENT,
            UID_STATE_TOP,
            UID_STATE_FOREGROUND_SERVICE_LOCATION,
            UID_STATE_FOREGROUND_SERVICE,
            UID_STATE_FOREGROUND,
            UID_STATE_BACKGROUND,
            UID_STATE_CACHED
    })
    public @interface UidState {}

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
     */
    public static final int UID_STATE_FOREGROUND_SERVICE_LOCATION = 300;
    /**
     * Uid state: The UID is running a foreground service. The lower the UID
     * state the more important the UID is for the user.
     */
    public static final int UID_STATE_FOREGROUND_SERVICE = 400;
    /**
     * The max, which is min priority, UID state for which any app op
     * would be considered as performed in the foreground.
     */
    public static final int UID_STATE_MAX_LAST_NON_RESTRICTED = UID_STATE_FOREGROUND_SERVICE;
    /**
     * Uid state: The UID is a foreground app. The lower the UID
     * state the more important the UID is for the user.
     */
    public static final int UID_STATE_FOREGROUND = 500;
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

    /** Note: Keep these sorted */
    public static final int[] UID_STATES = {
            UID_STATE_PERSISTENT,
            UID_STATE_TOP,
            UID_STATE_FOREGROUND_SERVICE_LOCATION,
            UID_STATE_FOREGROUND_SERVICE,
            UID_STATE_FOREGROUND,
            UID_STATE_BACKGROUND,
            UID_STATE_CACHED
    };

    public static String getUidStateName(@UidState int uidState) {
        switch (uidState) {
            case UID_STATE_PERSISTENT:
                return "pers";
            case UID_STATE_TOP:
                return "top";
            case UID_STATE_FOREGROUND_SERVICE_LOCATION:
                return "fgsvcl";
            case UID_STATE_FOREGROUND_SERVICE:
                return "fgsvc";
            case UID_STATE_FOREGROUND:
                return "fg";
            case UID_STATE_BACKGROUND:
                return "bg";
            case UID_STATE_CACHED:
                return "cch";
            default:
                return "unknown";
        }
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
    /**
     * Flags: all operations. These include operations matched
     * by {@link #OP_FLAG_SELF}, {@link #OP_FLAG_TRUSTED_PROXIED},
     * {@link #OP_FLAG_UNTRUSTED_PROXIED}, {@link #OP_FLAG_TRUSTED_PROXIED},
     * {@link #OP_FLAG_UNTRUSTED_PROXIED}.
     */
    public static final int OP_FLAGS_ALL =
            OP_FLAG_SELF
                    | OP_FLAG_TRUSTED_PROXY
                    | OP_FLAG_UNTRUSTED_PROXY
                    | OP_FLAG_TRUSTED_PROXIED
                    | OP_FLAG_UNTRUSTED_PROXIED;
    /**
     * Flags: all trusted operations which is ones either the app did {@link #OP_FLAG_SELF},
     * or it was blamed for by a trusted app {@link #OP_FLAG_TRUSTED_PROXIED}, or ones the
     * app if untrusted blamed on other apps {@link #OP_FLAG_UNTRUSTED_PROXY}.
     */
    public static final int OP_FLAGS_ALL_TRUSTED = AppOpsManager.OP_FLAG_SELF
            | AppOpsManager.OP_FLAG_UNTRUSTED_PROXY
            | AppOpsManager.OP_FLAG_TRUSTED_PROXIED;

    @IntDef(flag = true, value = {
            OP_FLAG_SELF,
            OP_FLAG_TRUSTED_PROXY,
            OP_FLAG_UNTRUSTED_PROXY,
            OP_FLAG_TRUSTED_PROXIED,
            OP_FLAG_UNTRUSTED_PROXIED
    })
    public @interface OpFlags {}

    @NonNull
    public static String getFlagName(@OpFlags int flag) {
        switch (flag) {
            case OP_FLAG_SELF:
                return "s";
            case OP_FLAG_TRUSTED_PROXY:
                return "tp";
            case OP_FLAG_UNTRUSTED_PROXY:
                return "up";
            case OP_FLAG_TRUSTED_PROXIED:
                return "tpd";
            case OP_FLAG_UNTRUSTED_PROXIED:
                return "upd";
            default:
                return "unknown";
        }
    }
    private static final int UID_STATE_OFFSET = 31;
    private static final int FLAGS_MASK = 0xFFFFFFFF;

    @Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
    public @interface DataBucketKey {}

    @NonNull
    public static String keyToString(@DataBucketKey long key) {
        final int uidState = extractUidStateFromKey(key);
        final int flags = extractFlagsFromKey(key);
        return "[" + getUidStateName(uidState) + "-" + flagsToString(flags) + "]";
    }

    public static @DataBucketKey long makeKey(@UidState int uidState, @OpFlags int flags) {
        return ((long) uidState << UID_STATE_OFFSET) | flags;
    }

    public static int extractUidStateFromKey(@DataBucketKey long key) {
        return (int) (key >> UID_STATE_OFFSET);
    }

    public static int extractFlagsFromKey(@DataBucketKey long key) {
        return (int) (key & FLAGS_MASK);
    }

    @SuppressLint("WrongConstant")
    @NonNull
    public static String flagsToString(@OpFlags int flags) {
        final StringBuilder flagsBuilder = new StringBuilder();
        while (flags != 0) {
            final int flag = 1 << Integer.numberOfTrailingZeros(flags);
            flags &= ~flag;
            if (flagsBuilder.length() > 0) {
                flagsBuilder.append('|');
            }
            flagsBuilder.append(getFlagName(flag));
        }
        return flagsBuilder.toString();
    }

    // when adding one of these:
    //  - increment _NUM_OP
    //  - define an OPSTR_* constant (marked as @SystemApi)
    //  - add rows to sOpToSwitch, sOpToString, sOpNames, sOpToPerms, sOpDefault
    //  - add descriptive strings to Settings/res/values/arrays.xml
    //  - add the op to the appropriate template in AppOpsState.OpsTemplate (settings app)

    /** No operation specified. */
    public static final int OP_NONE = -1;
    /** Access to coarse location information. */
    public static final int OP_COARSE_LOCATION = 0;
    /** Access to fine location information. */
    public static final int OP_FINE_LOCATION = 1;
    /** Causing GPS to run. */
    public static final int OP_GPS = 2;
    /** Access to vibration */
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
    public static final int OP_RECEIVE_EMERGECY_SMS = 17;
    public static final int OP_RECEIVE_MMS = 18;
    public static final int OP_RECEIVE_WAP_PUSH = 19;
    public static final int OP_SEND_SMS = 20;
    public static final int OP_READ_ICC_SMS = 21;
    public static final int OP_WRITE_ICC_SMS = 22;
    public static final int OP_WRITE_SETTINGS = 23;
    /** Required to draw on top of other apps. */
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
    /** Continually monitoring location data. */
    public static final int OP_MONITOR_LOCATION = 41;
    /** Continually monitoring location data with a relatively high power request. */
    public static final int OP_MONITOR_HIGH_POWER_LOCATION = 42;
    /** Retrieve current usage stats via {@link android.app.usage.UsageStatsManager}. */
    public static final int OP_GET_USAGE_STATS = 43;
    public static final int OP_MUTE_MICROPHONE = 44;
    public static final int OP_TOAST_WINDOW = 45;
    /** Capture the device's display contents and/or audio */
    public static final int OP_PROJECT_MEDIA = 46;
    /**
     * Start (without additional user intervention) a VPN connection, as used by {@link
     * android.net.VpnService} along with as Platform VPN connections, as used by
     * android.net.VpnManager
     *
     * <p>This appop is granted to apps that have already been given user consent to start
     * VpnService based VPN connections. As this is a superset of OP_ACTIVATE_PLATFORM_VPN, this
     * appop also allows the starting of Platform VPNs.
     */
    public static final int OP_ACTIVATE_VPN = 47;
    /** Access the WallpaperManagerAPI to write wallpapers. */
    public static final int OP_WRITE_WALLPAPER = 48;
    /** Received the assist structure from an app. */
    public static final int OP_ASSIST_STRUCTURE = 49;
    /** Received a screenshot from assist. */
    public static final int OP_ASSIST_SCREENSHOT = 50;
    /** Read the phone state. */
    public static final int OP_READ_PHONE_STATE = 51;
    /** Add voicemail messages to the voicemail content provider. */
    public static final int OP_ADD_VOICEMAIL = 52;
    /** Access APIs for SIP calling over VOIP or WiFi. */
    public static final int OP_USE_SIP = 53;
    /** Intercept outgoing calls. */
    public static final int OP_PROCESS_OUTGOING_CALLS = 54;
    /** User the fingerprint API. */
    public static final int OP_USE_FINGERPRINT = 55;
    /** Access to body sensors such as heart rate, etc. */
    public static final int OP_BODY_SENSORS = 56;
    /** Read previously received cell broadcast messages. */
    public static final int OP_READ_CELL_BROADCASTS = 57;
    /** Inject mock location into the system. */
    public static final int OP_MOCK_LOCATION = 58;
    /** Read external storage. */
    public static final int OP_READ_EXTERNAL_STORAGE = 59;
    /** Write external storage. */
    public static final int OP_WRITE_EXTERNAL_STORAGE = 60;
    /** Turned on the screen. */
    public static final int OP_TURN_SCREEN_ON = 61;
    /** Get device accounts. */
    public static final int OP_GET_ACCOUNTS = 62;
    /** Control whether an application is allowed to run in the background. */
    public static final int OP_RUN_IN_BACKGROUND = 63;
    public static final int OP_AUDIO_ACCESSIBILITY_VOLUME = 64;
    /** Read the phone number. */
    public static final int OP_READ_PHONE_NUMBERS = 65;
    /** Request package installs through package installer */
    public static final int OP_REQUEST_INSTALL_PACKAGES = 66;
    /** Enter picture-in-picture. */
    public static final int OP_PICTURE_IN_PICTURE = 67;
    /** Instant app start foreground service. */
    public static final int OP_INSTANT_APP_START_FOREGROUND = 68;
    /** Answer incoming phone calls */
    public static final int OP_ANSWER_PHONE_CALLS = 69;
    /** Run jobs when in background */
    public static final int OP_RUN_ANY_IN_BACKGROUND = 70;
    /** Change Wi-Fi connectivity state */
    public static final int OP_CHANGE_WIFI_STATE = 71;
    /** Request package deletion through package installer */
    public static final int OP_REQUEST_DELETE_PACKAGES = 72;
    /** Bind an accessibility service. */
    public static final int OP_BIND_ACCESSIBILITY_SERVICE = 73;
    /** Continue handover of a call from another app */
    public static final int OP_ACCEPT_HANDOVER = 74;
    /** Create and Manage IPsec Tunnels */
    public static final int OP_MANAGE_IPSEC_TUNNELS = 75;
    /** Any app start foreground service. */
    public static final int OP_START_FOREGROUND = 76;
    public static final int OP_BLUETOOTH_SCAN = 77;
    /** Use the BiometricPrompt/BiometricManager APIs. */
    public static final int OP_USE_BIOMETRIC = 78;
    /** Physical activity recognition. */
    public static final int OP_ACTIVITY_RECOGNITION = 79;
    /** Financial app sms read. */
    public static final int OP_SMS_FINANCIAL_TRANSACTIONS = 80;
    /** Read media of audio type. */
    public static final int OP_READ_MEDIA_AUDIO = 81;
    /** Write media of audio type. */
    public static final int OP_WRITE_MEDIA_AUDIO = 82;
    /** Read media of video type. */
    public static final int OP_READ_MEDIA_VIDEO = 83;
    /** Write media of video type. */
    public static final int OP_WRITE_MEDIA_VIDEO = 84;
    /** Read media of image type. */
    public static final int OP_READ_MEDIA_IMAGES = 85;
    /** Write media of image type. */
    public static final int OP_WRITE_MEDIA_IMAGES = 86;
    /** Has a legacy (non-isolated) view of storage. */
    public static final int OP_LEGACY_STORAGE = 87;
    /** Accessing accessibility features */
    public static final int OP_ACCESS_ACCESSIBILITY = 88;
    /** Read the device identifiers (IMEI / MEID, IMSI, SIM / Build serial) */
    public static final int OP_READ_DEVICE_IDENTIFIERS = 89;
    /** Read location metadata from media */
    public static final int OP_ACCESS_MEDIA_LOCATION = 90;
    /**
     * Start (without additional user intervention) a Platform VPN connection, as used by
     * android.net.VpnManager
     *
     * <p>This appop is granted to apps that have already been given user consent to start Platform
     * VPN connections. This appop is insufficient to start VpnService based VPNs (but the opposite
     * is true).
     */
    public static final int OP_ACTIVATE_PLATFORM_VPN = 91;
    public static final int _NUM_OP = 92;

    /** Access to coarse location information. */
    public static final String OPSTR_COARSE_LOCATION = "android:coarse_location";
    /** Access to fine location information. */
    public static final String OPSTR_FINE_LOCATION =
            "android:fine_location";
    /** Continually monitoring location data. */
    public static final String OPSTR_MONITOR_LOCATION
            = "android:monitor_location";
    /** Continually monitoring location data with a relatively high power request. */
    public static final String OPSTR_MONITOR_HIGH_POWER_LOCATION
            = "android:monitor_location_high_power";
    /** Access to {@link android.app.usage.UsageStatsManager}. */
    public static final String OPSTR_GET_USAGE_STATS
            = "android:get_usage_stats";
    /** Activate a VPN connection without user intervention. @hide */
    public static final String OPSTR_ACTIVATE_VPN
            = "android:activate_vpn";
    /** Allows an application to read the user's contacts data. */
    public static final String OPSTR_READ_CONTACTS
            = "android:read_contacts";
    /** Allows an application to write to the user's contacts data. */
    public static final String OPSTR_WRITE_CONTACTS
            = "android:write_contacts";
    /** Allows an application to read the user's call log. */
    public static final String OPSTR_READ_CALL_LOG
            = "android:read_call_log";
    /** Allows an application to write to the user's call log. */
    public static final String OPSTR_WRITE_CALL_LOG
            = "android:write_call_log";
    /** Allows an application to read the user's calendar data. */
    public static final String OPSTR_READ_CALENDAR
            = "android:read_calendar";
    /** Allows an application to write to the user's calendar data. */
    public static final String OPSTR_WRITE_CALENDAR
            = "android:write_calendar";
    /** Allows an application to initiate a phone call. */
    public static final String OPSTR_CALL_PHONE
            = "android:call_phone";
    /** Allows an application to read SMS messages. */
    public static final String OPSTR_READ_SMS
            = "android:read_sms";
    /** Allows an application to receive SMS messages. */
    public static final String OPSTR_RECEIVE_SMS
            = "android:receive_sms";
    /** Allows an application to receive MMS messages. */
    public static final String OPSTR_RECEIVE_MMS
            = "android:receive_mms";
    /** Allows an application to receive WAP push messages. */
    public static final String OPSTR_RECEIVE_WAP_PUSH
            = "android:receive_wap_push";
    /** Allows an application to send SMS messages. */
    public static final String OPSTR_SEND_SMS
            = "android:send_sms";
    /** Required to be able to access the camera device. */
    public static final String OPSTR_CAMERA
            = "android:camera";
    /** Required to be able to access the microphone device. */
    public static final String OPSTR_RECORD_AUDIO
            = "android:record_audio";
    /** Required to access phone state related information. */
    public static final String OPSTR_READ_PHONE_STATE
            = "android:read_phone_state";
    /** Required to access phone state related information. */
    public static final String OPSTR_ADD_VOICEMAIL
            = "android:add_voicemail";
    /** Access APIs for SIP calling over VOIP or WiFi */
    public static final String OPSTR_USE_SIP
            = "android:use_sip";
    /** Access APIs for diverting outgoing calls */
    public static final String OPSTR_PROCESS_OUTGOING_CALLS
            = "android:process_outgoing_calls";
    /** Use the fingerprint API. */
    public static final String OPSTR_USE_FINGERPRINT
            = "android:use_fingerprint";
    /** Access to body sensors such as heart rate, etc. */
    public static final String OPSTR_BODY_SENSORS
            = "android:body_sensors";
    /** Read previously received cell broadcast messages. */
    public static final String OPSTR_READ_CELL_BROADCASTS
            = "android:read_cell_broadcasts";
    /** Inject mock location into the system. */
    public static final String OPSTR_MOCK_LOCATION
            = "android:mock_location";
    /** Read external storage. */
    public static final String OPSTR_READ_EXTERNAL_STORAGE
            = "android:read_external_storage";
    /** Write external storage. */
    public static final String OPSTR_WRITE_EXTERNAL_STORAGE
            = "android:write_external_storage";
    /** Required to draw on top of other apps. */
    public static final String OPSTR_SYSTEM_ALERT_WINDOW
            = "android:system_alert_window";
    /** Required to write/modify/update system settingss. */
    public static final String OPSTR_WRITE_SETTINGS
            = "android:write_settings";
    /** Get device accounts. */
    public static final String OPSTR_GET_ACCOUNTS
            = "android:get_accounts";
    public static final String OPSTR_READ_PHONE_NUMBERS
            = "android:read_phone_numbers";
    /** Access to picture-in-picture. */
    public static final String OPSTR_PICTURE_IN_PICTURE
            = "android:picture_in_picture";
    public static final String OPSTR_INSTANT_APP_START_FOREGROUND
            = "android:instant_app_start_foreground";
    /** Answer incoming phone calls */
    public static final String OPSTR_ANSWER_PHONE_CALLS
            = "android:answer_phone_calls";
    /** Accept call handover */
    public static final String OPSTR_ACCEPT_HANDOVER
            = "android:accept_handover";
    public static final String OPSTR_GPS = "android:gps";
    public static final String OPSTR_VIBRATE = "android:vibrate";
    public static final String OPSTR_WIFI_SCAN = "android:wifi_scan";
    public static final String OPSTR_POST_NOTIFICATION = "android:post_notification";
    public static final String OPSTR_NEIGHBORING_CELLS = "android:neighboring_cells";
    public static final String OPSTR_WRITE_SMS = "android:write_sms";
    public static final String OPSTR_RECEIVE_EMERGENCY_BROADCAST =
            "android:receive_emergency_broadcast";
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
    public static final String OPSTR_AUDIO_NOTIFICATION_VOLUME =
            "android:audio_notification_volume";
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
    public static final String OPSTR_AUDIO_ACCESSIBILITY_VOLUME =
            "android:audio_accessibility_volume";
    public static final String OPSTR_REQUEST_INSTALL_PACKAGES = "android:request_install_packages";
    public static final String OPSTR_RUN_ANY_IN_BACKGROUND = "android:run_any_in_background";
    public static final String OPSTR_CHANGE_WIFI_STATE = "android:change_wifi_state";
    public static final String OPSTR_REQUEST_DELETE_PACKAGES = "android:request_delete_packages";
    public static final String OPSTR_BIND_ACCESSIBILITY_SERVICE =
            "android:bind_accessibility_service";
    public static final String OPSTR_MANAGE_IPSEC_TUNNELS = "android:manage_ipsec_tunnels";
    public static final String OPSTR_START_FOREGROUND = "android:start_foreground";
    public static final String OPSTR_BLUETOOTH_SCAN = "android:bluetooth_scan";
    /** Use the BiometricPrompt/BiometricManager APIs. */
    public static final String OPSTR_USE_BIOMETRIC = "android:use_biometric";
    /** Recognize physical activity. */
    public static final String OPSTR_ACTIVITY_RECOGNITION = "android:activity_recognition";
    /** Financial app read sms. */
    public static final String OPSTR_SMS_FINANCIAL_TRANSACTIONS =
            "android:sms_financial_transactions";
    /** Read media of audio type. */
    public static final String OPSTR_READ_MEDIA_AUDIO = "android:read_media_audio";
    /** Write media of audio type. */
    public static final String OPSTR_WRITE_MEDIA_AUDIO = "android:write_media_audio";
    /** Read media of video type. */
    public static final String OPSTR_READ_MEDIA_VIDEO = "android:read_media_video";
    /** Write media of video type. */
    public static final String OPSTR_WRITE_MEDIA_VIDEO = "android:write_media_video";
    /** Read media of image type. */
    public static final String OPSTR_READ_MEDIA_IMAGES = "android:read_media_images";
    /** Write media of image type. */
    public static final String OPSTR_WRITE_MEDIA_IMAGES = "android:write_media_images";
    /** Has a legacy (non-isolated) view of storage. */
    public static final String OPSTR_LEGACY_STORAGE = "android:legacy_storage";
    /** Read location metadata from media */
    public static final String OPSTR_ACCESS_MEDIA_LOCATION = "android:access_media_location";
    /** Interact with accessibility. */
    public static final String OPSTR_ACCESS_ACCESSIBILITY = "android:access_accessibility";
    /** Read device identifiers */
    public static final String OPSTR_READ_DEVICE_IDENTIFIERS = "android:read_device_identifiers";
    /** Start Platform VPN without user intervention */
    public static final String OPSTR_ACTIVATE_PLATFORM_VPN = "android:activate_platform_vpn";

    // Warning: If an permission is added here it also has to be added to
    // com.android.packageinstaller.permission.utils.EventLogger
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
    };

    /**
     * This maps each operation to the operation that serves as the
     * switch to determine whether it is allowed.  Generally this is
     * a 1:1 mapping, but for some things (like location) that have
     * multiple low-level operations being tracked that should be
     * presented to the user as one switch then this can be used to
     * make them all controlled by the same single operation.
     */
    private static int[] sOpToSwitch = new int[] {
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
            OP_ACTIVATE_PLATFORM_VPN,           // ACTIVATE_PLATFORM_VPN
    };

    /**
     * This maps each operation to the public string constant for it.
     */
    private static String[] sOpToString = new String[]{
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
            OPSTR_ACTIVATE_PLATFORM_VPN,
    };

    /**
     * This provides a simple name for each operation to be used
     * in debug output.
     */
    public static String[] sOpNames = new String[] {
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
            "ACTIVATE_PLATFORM_VPN"
    };

    /**
     * This optionally maps a permission to an operation.  If there
     * is no permission associated with an operation, it is null.
     */
    @SuppressLint("InlinedApi")
    public static String[] sOpPerms = new String[] {
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
            "android.Manifest.permission.RECEIVE_EMERGENCY_BROADCAST",
            android.Manifest.permission.RECEIVE_MMS,
            android.Manifest.permission.RECEIVE_WAP_PUSH,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_SMS,
            null, // no permission required for writing icc sms
            android.Manifest.permission.WRITE_SETTINGS,
            android.Manifest.permission.SYSTEM_ALERT_WINDOW,
            "android.Manifest.permission.ACCESS_NOTIFICATIONS",
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
            "Manifest.permission.PROCESS_OUTGOING_CALLS",
            "Manifest.permission.USE_FINGERPRINT",
            Manifest.permission.BODY_SENSORS,
            "Manifest.permission.READ_CELL_BROADCASTS",
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
            Manifest.permission.USE_BIOMETRIC,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.SMS_FINANCIAL_TRANSACTIONS,
            null,
            null, // no permission for OP_WRITE_MEDIA_AUDIO
            null,
            null, // no permission for OP_WRITE_MEDIA_VIDEO
            null,
            null, // no permission for OP_WRITE_MEDIA_IMAGES
            null, // no permission for OP_LEGACY_STORAGE
            null, // no permission for OP_ACCESS_ACCESSIBILITY
            null, // no direct permission for OP_READ_DEVICE_IDENTIFIERS
            Manifest.permission.ACCESS_MEDIA_LOCATION,
            null, // no permission for OP_ACTIVATE_PLATFORM_VPN
    };

    /**
     * Specifies whether an Op should be restricted by a user restriction.
     * Each Op should be filled with a restriction string from UserManager or
     * null to specify it is not affected by any user restriction.
     */
    private static String[] sOpRestrictions = new String[] {
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
            null, // UserManager.DISALLOW_CAMERA, //CAMERA
            null, // UserManager.DISALLOW_RECORD_AUDIO, //RECORD_AUDIO
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
            null, // UserManager.DISALLOW_WALLPAPER, // WRITE_WALLPAPER
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
            null, // ACTIVATE_PLATFORM_VPN
    };

    /**
     * This specifies whether each option should allow the system
     * (and system ui) to bypass the user restriction when active.
     */
    private static boolean[] sOpAllowSystemRestrictionBypass = new boolean[] {
            true, //COARSE_LOCATION
            true, //FINE_LOCATION
            false, //GPS
            false, //VIBRATE
            false, //READ_CONTACTS
            false, //WRITE_CONTACTS
            false, //READ_CALL_LOG
            false, //WRITE_CALL_LOG
            false, //READ_CALENDAR
            false, //WRITE_CALENDAR
            true, //WIFI_SCAN
            false, //POST_NOTIFICATION
            false, //NEIGHBORING_CELLS
            false, //CALL_PHONE
            false, //READ_SMS
            false, //WRITE_SMS
            false, //RECEIVE_SMS
            false, //RECEIVE_EMERGECY_SMS
            false, //RECEIVE_MMS
            false, //RECEIVE_WAP_PUSH
            false, //SEND_SMS
            false, //READ_ICC_SMS
            false, //WRITE_ICC_SMS
            false, //WRITE_SETTINGS
            true, //SYSTEM_ALERT_WINDOW
            false, //ACCESS_NOTIFICATIONS
            false, //CAMERA
            false, //RECORD_AUDIO
            false, //PLAY_AUDIO
            false, //READ_CLIPBOARD
            false, //WRITE_CLIPBOARD
            false, //TAKE_MEDIA_BUTTONS
            false, //TAKE_AUDIO_FOCUS
            false, //AUDIO_MASTER_VOLUME
            false, //AUDIO_VOICE_VOLUME
            false, //AUDIO_RING_VOLUME
            false, //AUDIO_MEDIA_VOLUME
            false, //AUDIO_ALARM_VOLUME
            false, //AUDIO_NOTIFICATION_VOLUME
            false, //AUDIO_BLUETOOTH_VOLUME
            false, //WAKE_LOCK
            false, //MONITOR_LOCATION
            false, //MONITOR_HIGH_POWER_LOCATION
            false, //GET_USAGE_STATS
            false, //MUTE_MICROPHONE
            true, //TOAST_WINDOW
            false, //PROJECT_MEDIA
            false, //ACTIVATE_VPN
            false, //WALLPAPER
            false, //ASSIST_STRUCTURE
            false, //ASSIST_SCREENSHOT
            false, //READ_PHONE_STATE
            false, //ADD_VOICEMAIL
            false, // USE_SIP
            false, // PROCESS_OUTGOING_CALLS
            false, // USE_FINGERPRINT
            false, // BODY_SENSORS
            false, // READ_CELL_BROADCASTS
            false, // MOCK_LOCATION
            false, // READ_EXTERNAL_STORAGE
            false, // WRITE_EXTERNAL_STORAGE
            false, // TURN_ON_SCREEN
            false, // GET_ACCOUNTS
            false, // RUN_IN_BACKGROUND
            false, // AUDIO_ACCESSIBILITY_VOLUME
            false, // READ_PHONE_NUMBERS
            false, // REQUEST_INSTALL_PACKAGES
            false, // ENTER_PICTURE_IN_PICTURE_ON_HIDE
            false, // INSTANT_APP_START_FOREGROUND
            false, // ANSWER_PHONE_CALLS
            false, // OP_RUN_ANY_IN_BACKGROUND
            false, // OP_CHANGE_WIFI_STATE
            false, // OP_REQUEST_DELETE_PACKAGES
            false, // OP_BIND_ACCESSIBILITY_SERVICE
            false, // ACCEPT_HANDOVER
            false, // MANAGE_IPSEC_HANDOVERS
            false, // START_FOREGROUND
            true, // BLUETOOTH_SCAN
            false, // USE_BIOMETRIC
            false, // ACTIVITY_RECOGNITION
            false, // SMS_FINANCIAL_TRANSACTIONS
            false, // READ_MEDIA_AUDIO
            false, // WRITE_MEDIA_AUDIO
            false, // READ_MEDIA_VIDEO
            false, // WRITE_MEDIA_VIDEO
            false, // READ_MEDIA_IMAGES
            false, // WRITE_MEDIA_IMAGES
            false, // LEGACY_STORAGE
            false, // ACCESS_ACCESSIBILITY
            false, // READ_DEVICE_IDENTIFIERS
            false, // ACCESS_MEDIA_LOCATION
            false, // ACTIVATE_PLATFORM_VPN
    };

    /**
     * This specifies the default mode for each operation.
     */
    private static int[] sOpDefaultMode = new int[] {
            AppOpsManager.MODE_ALLOWED, // COARSE_LOCATION
            AppOpsManager.MODE_ALLOWED, // FINE_LOCATION
            AppOpsManager.MODE_ALLOWED, // GPS
            AppOpsManager.MODE_ALLOWED, // VIBRATE
            AppOpsManager.MODE_ALLOWED, // READ_CONTACTS
            AppOpsManager.MODE_ALLOWED, // WRITE_CONTACTS
            AppOpsManager.MODE_ALLOWED, // READ_CALL_LOG
            AppOpsManager.MODE_ALLOWED, // WRITE_CALL_LOG
            AppOpsManager.MODE_ALLOWED, // READ_CALENDAR
            AppOpsManager.MODE_ALLOWED, // WRITE_CALENDAR
            AppOpsManager.MODE_ALLOWED, // WIFI_SCAN
            AppOpsManager.MODE_ALLOWED, // POST_NOTIFICATION
            AppOpsManager.MODE_ALLOWED, // NEIGHBORING_CELLS
            AppOpsManager.MODE_ALLOWED, // CALL_PHONE
            AppOpsManager.MODE_ALLOWED, // READ_SMS
            AppOpsManager.MODE_IGNORED, // WRITE_SMS
            AppOpsManager.MODE_ALLOWED, // RECEIVE_SMS
            AppOpsManager.MODE_ALLOWED, // RECEIVE_EMERGENCY_BROADCAST
            AppOpsManager.MODE_ALLOWED, // RECEIVE_MMS
            AppOpsManager.MODE_ALLOWED, // RECEIVE_WAP_PUSH
            AppOpsManager.MODE_ALLOWED, // SEND_SMS
            AppOpsManager.MODE_ALLOWED, // READ_ICC_SMS
            AppOpsManager.MODE_ALLOWED, // WRITE_ICC_SMS
            AppOpsManager.MODE_DEFAULT, // WRITE_SETTINGS
            AppOpsManager.MODE_DEFAULT, // FIXME: getSystemAlertWindowDefault(), // SYSTEM_ALERT_WINDOW
            AppOpsManager.MODE_ALLOWED, // ACCESS_NOTIFICATIONS
            AppOpsManager.MODE_ALLOWED, // CAMERA
            AppOpsManager.MODE_ALLOWED, // RECORD_AUDIO
            AppOpsManager.MODE_ALLOWED, // PLAY_AUDIO
            AppOpsManager.MODE_ALLOWED, // READ_CLIPBOARD
            AppOpsManager.MODE_ALLOWED, // WRITE_CLIPBOARD
            AppOpsManager.MODE_ALLOWED, // TAKE_MEDIA_BUTTONS
            AppOpsManager.MODE_ALLOWED, // TAKE_AUDIO_FOCUS
            AppOpsManager.MODE_ALLOWED, // AUDIO_MASTER_VOLUME
            AppOpsManager.MODE_ALLOWED, // AUDIO_VOICE_VOLUME
            AppOpsManager.MODE_ALLOWED, // AUDIO_RING_VOLUME
            AppOpsManager.MODE_ALLOWED, // AUDIO_MEDIA_VOLUME
            AppOpsManager.MODE_ALLOWED, // AUDIO_ALARM_VOLUME
            AppOpsManager.MODE_ALLOWED, // AUDIO_NOTIFICATION_VOLUME
            AppOpsManager.MODE_ALLOWED, // AUDIO_BLUETOOTH_VOLUME
            AppOpsManager.MODE_ALLOWED, // WAKE_LOCK
            AppOpsManager.MODE_ALLOWED, // MONITOR_LOCATION
            AppOpsManager.MODE_ALLOWED, // MONITOR_HIGH_POWER_LOCATION
            AppOpsManager.MODE_DEFAULT, // GET_USAGE_STATS
            AppOpsManager.MODE_ALLOWED, // MUTE_MICROPHONE
            AppOpsManager.MODE_ALLOWED, // TOAST_WINDOW
            AppOpsManager.MODE_IGNORED, // PROJECT_MEDIA
            AppOpsManager.MODE_IGNORED, // ACTIVATE_VPN
            AppOpsManager.MODE_ALLOWED, // WRITE_WALLPAPER
            AppOpsManager.MODE_ALLOWED, // ASSIST_STRUCTURE
            AppOpsManager.MODE_ALLOWED, // ASSIST_SCREENSHOT
            AppOpsManager.MODE_ALLOWED, // READ_PHONE_STATE
            AppOpsManager.MODE_ALLOWED, // ADD_VOICEMAIL
            AppOpsManager.MODE_ALLOWED, // USE_SIP
            AppOpsManager.MODE_ALLOWED, // PROCESS_OUTGOING_CALLS
            AppOpsManager.MODE_ALLOWED, // USE_FINGERPRINT
            AppOpsManager.MODE_ALLOWED, // BODY_SENSORS
            AppOpsManager.MODE_ALLOWED, // READ_CELL_BROADCASTS
            AppOpsManager.MODE_ERRORED, // MOCK_LOCATION
            AppOpsManager.MODE_ALLOWED, // READ_EXTERNAL_STORAGE
            AppOpsManager.MODE_ALLOWED, // WRITE_EXTERNAL_STORAGE
            AppOpsManager.MODE_ALLOWED, // TURN_SCREEN_ON
            AppOpsManager.MODE_ALLOWED, // GET_ACCOUNTS
            AppOpsManager.MODE_ALLOWED, // RUN_IN_BACKGROUND
            AppOpsManager.MODE_ALLOWED, // AUDIO_ACCESSIBILITY_VOLUME
            AppOpsManager.MODE_ALLOWED, // READ_PHONE_NUMBERS
            AppOpsManager.MODE_DEFAULT, // REQUEST_INSTALL_PACKAGES
            AppOpsManager.MODE_ALLOWED, // PICTURE_IN_PICTURE
            AppOpsManager.MODE_DEFAULT, // INSTANT_APP_START_FOREGROUND
            AppOpsManager.MODE_ALLOWED, // ANSWER_PHONE_CALLS
            AppOpsManager.MODE_ALLOWED, // RUN_ANY_IN_BACKGROUND
            AppOpsManager.MODE_ALLOWED, // CHANGE_WIFI_STATE
            AppOpsManager.MODE_ALLOWED, // REQUEST_DELETE_PACKAGES
            AppOpsManager.MODE_ALLOWED, // BIND_ACCESSIBILITY_SERVICE
            AppOpsManager.MODE_ALLOWED, // ACCEPT_HANDOVER
            AppOpsManager.MODE_ERRORED, // MANAGE_IPSEC_TUNNELS
            AppOpsManager.MODE_ALLOWED, // START_FOREGROUND
            AppOpsManager.MODE_ALLOWED, // BLUETOOTH_SCAN
            AppOpsManager.MODE_ALLOWED, // USE_BIOMETRIC
            AppOpsManager.MODE_ALLOWED, // ACTIVITY_RECOGNITION
            AppOpsManager.MODE_DEFAULT, // SMS_FINANCIAL_TRANSACTIONS
            AppOpsManager.MODE_ALLOWED, // READ_MEDIA_AUDIO
            AppOpsManager.MODE_ERRORED, // WRITE_MEDIA_AUDIO
            AppOpsManager.MODE_ALLOWED, // READ_MEDIA_VIDEO
            AppOpsManager.MODE_ERRORED, // WRITE_MEDIA_VIDEO
            AppOpsManager.MODE_ALLOWED, // READ_MEDIA_IMAGES
            AppOpsManager.MODE_ERRORED, // WRITE_MEDIA_IMAGES
            AppOpsManager.MODE_DEFAULT, // LEGACY_STORAGE
            AppOpsManager.MODE_ALLOWED, // ACCESS_ACCESSIBILITY
            AppOpsManager.MODE_ERRORED, // READ_DEVICE_IDENTIFIERS
            AppOpsManager.MODE_ALLOWED, // ALLOW_MEDIA_LOCATION
            AppOpsManager.MODE_IGNORED, // ACTIVATE_PLATFORM_VPN
    };

    /**
     * This specifies whether each option is allowed to be reset
     * when resetting all app preferences.  Disable reset for
     * app ops that are under strong control of some part of the
     * system (such as OP_WRITE_SMS, which should be allowed only
     * for whichever app is selected as the current SMS app).
     */
    private static boolean[] sOpDisableReset = new boolean[] {
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
            false, // LEGACY_STORAGE
            false, // ACCESS_ACCESSIBILITY
            false, // READ_DEVICE_IDENTIFIERS
            false, // ACCESS_MEDIA_LOCATION
            false, // ACTIVATE_PLATFORM_VPN
    };

    // Reference: https://developer.android.com/reference/android/Manifest.permission
    // Reference: rikka.appops
    // TODO: Move from hardcoding to settings
    public static int[] sAlwaysShownOp = {
            // [Location]
            OP_COARSE_LOCATION,
            // OP_MOCK_LOCATION,
            // OP_ACCESS_MEDIA_LOCATION,
            // [Storage]
            OP_READ_EXTERNAL_STORAGE,
            OP_WRITE_EXTERNAL_STORAGE,
            // OP_LEGACY_STORAGE,
            // [Calendar]
            OP_READ_CALENDAR,
            OP_WRITE_CALENDAR,
            // [Calling]
            OP_CALL_PHONE,
            OP_PROCESS_OUTGOING_CALLS,
            OP_READ_CALL_LOG,
            OP_ANSWER_PHONE_CALLS,
            OP_WRITE_CALL_LOG,
            OP_ADD_VOICEMAIL,
            OP_USE_SIP,
            OP_ACCEPT_HANDOVER,
            // [Phone]
            OP_READ_PHONE_STATE,
            OP_READ_PHONE_NUMBERS,
            OP_READ_DEVICE_IDENTIFIERS,
            OP_GET_USAGE_STATS,
            OP_GET_ACCOUNTS,
            // [Camera]
            OP_CAMERA,
            // [SMS]
            OP_READ_SMS,
            OP_WRITE_SMS,
            OP_RECEIVE_SMS,
            OP_RECEIVE_MMS,
            OP_RECEIVE_WAP_PUSH,
            OP_SEND_SMS,
            OP_SMS_FINANCIAL_TRANSACTIONS,
            OP_READ_CELL_BROADCASTS,
            // [Module]
            OP_BODY_SENSORS,
            OP_ACTIVITY_RECOGNITION,
            OP_USE_FINGERPRINT,
            OP_USE_BIOMETRIC,
            // [Contact]
            OP_READ_CONTACTS,
            OP_WRITE_CONTACTS,
            // [Microphone]
            OP_RECORD_AUDIO,
            // OP_MUTE_MICROPHONE,
            // [Audio]
            // OP_PLAY_AUDIO,
            // OP_PROJECT_MEDIA,
            // OP_TAKE_MEDIA_BUTTONS,
            // OP_TAKE_AUDIO_FOCUS,
            // OP_AUDIO_MASTER_VOLUME,
            // OP_AUDIO_VOICE_VOLUME,
            // OP_AUDIO_RING_VOLUME,
            // OP_AUDIO_MEDIA_VOLUME,
            // OP_AUDIO_ALARM_VOLUME,
            // OP_AUDIO_NOTIFICATION_VOLUME,
            // OP_AUDIO_BLUETOOTH_VOLUME,
            // OP_AUDIO_ACCESSIBILITY_VOLUME,
            // OP_READ_MEDIA_AUDIO,
            // OP_WRITE_MEDIA_AUDIO,
            // OP_READ_MEDIA_VIDEO,
            // OP_WRITE_MEDIA_VIDEO,
            // OP_READ_MEDIA_IMAGES,
            // OP_WRITE_MEDIA_IMAGES,
            // [Settings]
            OP_WRITE_SETTINGS,
            OP_WAKE_LOCK,
            OP_TURN_SCREEN_ON,
            OP_REQUEST_INSTALL_PACKAGES,
            OP_REQUEST_DELETE_PACKAGES,
            OP_CHANGE_WIFI_STATE,
            // OP_WRITE_WALLPAPER,
            // OP_ACTIVATE_VPN,
            // OP_ACTIVATE_PLATFORM_VPN,
            // [Accessibility]
            OP_BIND_ACCESSIBILITY_SERVICE,
            OP_ACCESS_ACCESSIBILITY,
            // OP_ASSIST_STRUCTURE,
            // OP_ASSIST_SCREENSHOT,
            // [Service]
            OP_RUN_IN_BACKGROUND,
            OP_RUN_ANY_IN_BACKGROUND,
            OP_INSTANT_APP_START_FOREGROUND,
            OP_START_FOREGROUND,
            // [Other]
            // OP_POST_NOTIFICATION,
            OP_SYSTEM_ALERT_WINDOW,
            OP_ACCESS_NOTIFICATIONS,
            OP_READ_CLIPBOARD,
            // OP_VIBRATE,
            OP_WRITE_CLIPBOARD,
            OP_TOAST_WINDOW,
            OP_PICTURE_IN_PICTURE,
            OP_MANAGE_IPSEC_TUNNELS,
    };

    /**
     * Mapping from an app op name to the app op code.
     */
    private static HashMap<String, Integer> sOpStrToOp = new HashMap<>();

    /**
     * Mapping from a permission to the corresponding app op.
     */
    private static HashMap<String, Integer> sPermToOp = new HashMap<>();

    static {
        if (sOpToSwitch.length != _NUM_OP) {
            throw new IllegalStateException("sOpToSwitch length " + sOpToSwitch.length
                    + " should be " + _NUM_OP);
        }
        if (sOpToString.length != _NUM_OP) {
            throw new IllegalStateException("sOpToString length " + sOpToString.length
                    + " should be " + _NUM_OP);
        }
        if (sOpNames.length != _NUM_OP) {
            throw new IllegalStateException("sOpNames length " + sOpNames.length
                    + " should be " + _NUM_OP);
        }
        if (sOpPerms.length != _NUM_OP) {
            throw new IllegalStateException("sOpPerms length " + sOpPerms.length
                    + " should be " + _NUM_OP);
        }
        if (sOpDefaultMode.length != _NUM_OP) {
            throw new IllegalStateException("sOpDefaultMode length " + sOpDefaultMode.length
                    + " should be " + _NUM_OP);
        }
        if (sOpDisableReset.length != _NUM_OP) {
            throw new IllegalStateException("sOpDisableReset length " + sOpDisableReset.length
                    + " should be " + _NUM_OP);
        }
        if (sOpRestrictions.length != _NUM_OP) {
            throw new IllegalStateException("sOpRestrictions length " + sOpRestrictions.length
                    + " should be " + _NUM_OP);
        }
        if (sOpAllowSystemRestrictionBypass.length != _NUM_OP) {
            throw new IllegalStateException("sOpAllowSYstemRestrictionsBypass length "
                    + sOpRestrictions.length + " should be " + _NUM_OP);
        }
        for (int i=0; i<_NUM_OP; i++) {
            if (sOpToString[i] != null) {
                sOpStrToOp.put(sOpToString[i], i);
            }
        }
        for (int op : RUNTIME_AND_APPOP_PERMISSIONS_OPS) {
            if (sOpPerms[op] != null) {
                sPermToOp.put(sOpPerms[op], op);
            }
        }
    }

    public static final String KEY_HISTORICAL_OPS = "historical_ops";
    /** System properties for debug logging of noteOp call sites */
    private static final String DEBUG_LOGGING_ENABLE_PROP = "appops.logging_enabled";
    private static final String DEBUG_LOGGING_PACKAGES_PROP = "appops.logging_packages";
    private static final String DEBUG_LOGGING_OPS_PROP = "appops.logging_ops";
    private static final String DEBUG_LOGGING_TAG = "AppOpsManager";

    /**
     * Retrieve the op switch that controls the given operation.
     */
    public static int opToSwitch(int op) {
        return sOpToSwitch[op];
    }

    /**
     * Retrieve a non-localized name for the operation, for debugging output.
     */
    public static String opToName(int op) {
        if (op == OP_NONE) return "NONE";
        return op < sOpNames.length ? sOpNames[op] : ("Unknown(" + op + ")");
    }

    /**
     * Retrieve a non-localized public name for the operation.
     */
    public static @NonNull String opToPublicName(int op) {
        return sOpToString[op];
    }

    public static int strDebugOpToOp(String op) {
        for (int i=0; i<sOpNames.length; i++) {
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
     * Retrieve whether the op allows the system (and system ui) to
     * bypass the user restriction.
     */
    public static boolean opAllowSystemBypassRestriction(int op) {
        return sOpAllowSystemRestrictionBypass[op];
    }

    /**
     * Retrieve the default mode for the operation.
     */
    public static @Mode int opToDefaultMode(int op) {
        return sOpDefaultMode[op];
    }

    /**
     * Retrieve the default mode for the app op.
     *
     * @param appOp The app op name
     *
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
        return String.valueOf(mode);
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
     * Class holding all of the operation information associated with an app.
     */
    public static final class PackageOps implements Parcelable {
        private final @NonNull String mPackageName;
        private final int mUid;
        private final List<OpEntry> mEntries;

        public PackageOps(@NonNull String packageName, int uid, List<OpEntry> entries) {
            mPackageName = packageName;
            mUid = uid;
            mEntries = entries;
        }

        /**
         * @return The name of the package.
         */
        public @NonNull String getPackageName() {
            return mPackageName;
        }

        /**
         * @return The uid of the package.
         */
        public int getUid() {
            return mUid;
        }

        /**
         * @return The ops of the package.
         */
        public @NonNull List<OpEntry> getOps() {
            return mEntries;
        }

        @Override
        public String toString() {
            return "PackageOps{" +
                    "mPackageName='" + mPackageName + '\'' +
                    ", mUid=" + mUid +
                    ", mEntries=" + mEntries +
                    '}';
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString(mPackageName);
            dest.writeInt(mUid);
            dest.writeInt(mEntries.size());
            for (int i=0; i<mEntries.size(); i++) {
                mEntries.get(i).writeToParcel(dest, flags);
            }
        }

        PackageOps(@NonNull Parcel source) {
            mPackageName = Objects.requireNonNull(source.readString());
            mUid = source.readInt();
            mEntries = new ArrayList<>();
            final int N = source.readInt();
            for (int i=0; i<N; i++) {
                mEntries.add(OpEntry.CREATOR.createFromParcel(source));
            }
        }

        public static final @NonNull Creator<PackageOps> CREATOR = new Creator<PackageOps>() {
            @Override
            public PackageOps createFromParcel(Parcel source) {
                return new PackageOps(source);
            }
            @Override
            public PackageOps[] newArray(int size) {
                return new PackageOps[size];
            }
        };
    }

    /**
     * Class holding the information about one unique operation of an application.
     *
     * NOTE: This class is different than the original
     */
    public static final class OpEntry implements Parcelable {
        private final int mOp;
        private final boolean mRunning;
        private final @Mode int mMode;
        private final long mAccessTime;
        private final long mRejectTime;
        private final long mDuration;
        private final @Nullable String mProxyUid;
        private final @Nullable String mProxyPackageName;

        public OpEntry(int op, boolean running, @Mode int mode,
                       long accessTime, long rejectTime,
                       long duration, @Nullable String proxyUid,
                       @Nullable String proxyPackageName) {
            mOp = op;
            mRunning = running;
            mMode = mode;
            mAccessTime = accessTime;
            mRejectTime = rejectTime;
            mDuration = duration;
            mProxyUid = proxyUid;
            mProxyPackageName = proxyPackageName;
        }

        public OpEntry(int op, @Mode int mode) {
            mOp = op;
            mMode = mode;
            mRunning = false;
            mAccessTime = 0;
            mRejectTime = 0;
            mDuration = 0;
            mProxyUid = null;
            mProxyPackageName = null;
        }

        public int getOp() {
            return mOp;
        }

        /**
         * @return This entry's op string name, such as {@link #OPSTR_COARSE_LOCATION}.
         */
        public @NonNull
        String getOpStr() {
            return sOpToString[mOp];
        }

        /**
         * @return this entry's current mode, such as {@link #MODE_ALLOWED}.
         */
        public @Mode
        int getMode() {
            return mMode;
        }

        public long getTime() {
            return mAccessTime;
        }

        public long getRejectTime() {
            return mRejectTime;
        }

        /**
         * @return Whether the operation is running.
         */
        public boolean isRunning() {
            return mRunning;
        }

        /**
         * @return The duration of the operation in milliseconds. The duration is in wall time.
         */
        public long getDuration() {
            return mDuration;
        }

        /**
         * Gets the UID of the app that performed the op on behalf of this app and
         * as a result blamed the op on this app or Process#INVALID_UID if
         * there is no proxy.
         *
         * @return The proxy UID.
         */
        public String getProxyUid() {
            return mProxyUid;
        }

        /**
         * Gets the package name of the app that performed the op on behalf of this
         * app and as a result blamed the op on this app or {@code null}
         * if there is no proxy.
         *
         * @return The proxy package name.
         */
        public @Nullable
        String getProxyPackageName() {
            return mProxyPackageName;
        }

        @Override
        public String toString() {
            return "OpEntry{" +
                    "mOp=" + opToName(mOp) +
                    ", mRunning=" + mRunning +
                    ", mMode=" + modeToName(mMode) +
                    ", mAccessTime=" + mAccessTime +
                    ", mRejectTime=" + mRejectTime +
                    ", mDuration=" + mDuration +
                    '}';
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeInt(mOp);
            dest.writeInt(mMode);
            dest.writeBoolean(mRunning);
            dest.writeLong(mAccessTime);
            dest.writeLong(mRejectTime);
            dest.writeLong(mDuration);
            dest.writeString(mProxyUid);
            dest.writeString(mProxyPackageName);
        }

        OpEntry(@NonNull Parcel source) {
            mOp = source.readInt();
            mMode = source.readInt();
            mRunning = source.readBoolean();
            mAccessTime = source.readLong();
            mRejectTime = source.readLong();
            mDuration = source.readLong();
            mProxyUid = source.readString();
            mProxyPackageName = source.readString();
        }

        public static final @NonNull Creator<OpEntry> CREATOR = new Creator<OpEntry>() {
            @Override
            public OpEntry createFromParcel(Parcel source) {
                return new OpEntry(source);
            }

            @Override
            public OpEntry[] newArray(int size) {
                return new OpEntry[size];
            }
        };
    }
}
