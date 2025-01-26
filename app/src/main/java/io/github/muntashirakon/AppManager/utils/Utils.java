// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.UserHandleHidden;
import android.text.GetChars;
import android.text.TextUtils;
import android.util.Pair;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.pm.PermissionInfoCompat;
import androidx.fragment.app.FragmentActivity;

import org.jetbrains.annotations.Contract;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import aosp.libcore.util.EmptyArray;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.signing.SignerInfo;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;

public class Utils {
    public static final String TERMUX_LOGIN_PATH = OsEnvironment.getDataDirectoryRaw() + "/data/com.termux/files/usr/bin/login";

    @NonNull
    public static String camelCaseToSpaceSeparatedString(@NonNull String str) {
        return TextUtils.join(" ", splitByCharacterType(str, true)).replace(" _", "");
    }


    public static boolean containsOrHasInitials(@NonNull String query, @NonNull String str) {
        query = query.toLowerCase(Locale.ROOT);
        if (str.toLowerCase(Locale.ROOT).contains(query)) return true;
        return getFirstLettersInLowerCase(camelCaseToSpaceSeparatedString(str)).contains(query);
    }

    @NonNull
    public static String getFirstLettersInLowerCase(@NonNull String str) {
        String[] strings = str.split("\\s");
        StringBuilder builder = new StringBuilder();
        for (String s : strings) {
            if (!s.isEmpty()) builder.append(s.charAt(0));
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    // https://github.com/apache/commons-lang/blob/master/src/main/java/org/apache/commons/lang3/StringUtils.java#L7514
    @NonNull
    public static String[] splitByCharacterType(@NonNull String str, boolean camelCase) {
        if (str.isEmpty()) return EmptyArray.STRING;
        char[] c = str.toCharArray();
        List<String> list = new ArrayList<>();
        int tokenStart = 0;
        int currentType = Character.getType(c[tokenStart]);
        for (int pos = tokenStart + 1; pos < c.length; pos++) {
            int type = Character.getType(c[pos]);
            if (type == currentType) {
                continue;
            }
            if (camelCase && type == Character.LOWERCASE_LETTER && currentType == Character.UPPERCASE_LETTER) {
                int newTokenStart = pos - 1;
                if (newTokenStart != tokenStart) {
                    list.add(new String(c, tokenStart, newTokenStart - tokenStart));
                    tokenStart = newTokenStart;
                }
            } else {
                list.add(new String(c, tokenStart, pos - tokenStart));
                tokenStart = pos;
            }
            currentType = type;
        }
        list.add(new String(c, tokenStart, c.length - tokenStart));
        return list.toArray(new String[0]);
    }

    @NonNull
    public static String getLastComponent(@NonNull String str) {
        try {
            return str.substring(str.lastIndexOf('.') + 1);
        } catch (Exception e) {
            return str;
        }
    }

    @StringRes
    public static int getProcessStateName(@NonNull String shortName) {
        switch (shortName) {
            case "R":
                return R.string.running;
            case "S":
                return R.string.state_sleeping;
            case "D":
                return R.string.state_device_io;
            case "T":
                return R.string.stopped;
            case "t":
                return R.string.state_trace_stop;
            case "x":
            case "X":
                return R.string.state_dead;
            case "Z":
                return R.string.state_zombie;
            case "P":
                return R.string.state_parked;
            case "I":
                return R.string.state_idle;
            case "K":
                return R.string.state_wake_kill;
            case "W":
                return R.string.state_waking;
            default:
                return R.string.state_unknown;
        }
    }

    @StringRes
    public static int getProcessStateExtraName(String shortName) {
        if (shortName == null) return R.string.empty;
        switch (shortName) {
            case "<":
                return R.string.state_high_priority;
            case "N":
                return R.string.state_low_priority;
            case "L":
                return R.string.state_locked_memory;
            case "s":
                return R.string.state_session_leader;
            case "+":
                return R.string.state_foreground;
            case "l":
                return R.string.state_multithreaded;
            default:
                return R.string.state_unknown;
        }
    }

    @StringRes
    public static int getLaunchMode(int mode) {
        switch (mode) {
            case ActivityInfo.LAUNCH_MULTIPLE:
                return R.string.launch_mode_multiple;
            case ActivityInfo.LAUNCH_SINGLE_INSTANCE:
                return R.string.launch_mode_single_instance;
            case ActivityInfo.LAUNCH_SINGLE_TASK:
                return R.string.launch_mode_single_task;
            case ActivityInfo.LAUNCH_SINGLE_TOP:
                return R.string.launch_mode_single_top;
            default:
                return R.string._null;
        }
    }

    @StringRes
    public static int getOrientationString(int orientation) {
        switch (orientation) {
            case ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED:
                return R.string.orientation_unspecified;
            case ActivityInfo.SCREEN_ORIENTATION_BEHIND:
                return R.string.orientation_behind;
            case ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR:
                return R.string.orientation_full_sensor;
            case ActivityInfo.SCREEN_ORIENTATION_FULL_USER:
                return R.string.orientation_full_user;
            case ActivityInfo.SCREEN_ORIENTATION_LOCKED:
                return R.string.orientation_locked;
            case ActivityInfo.SCREEN_ORIENTATION_NOSENSOR:
                return R.string.orientation_no_sensor;
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                return R.string.orientation_landscape;
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                return R.string.orientation_portrait;
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                return R.string.orientation_reverse_portrait;
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                return R.string.orientation_reverse_landscape;
            case ActivityInfo.SCREEN_ORIENTATION_USER:
                return R.string.orientation_user;
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                return R.string.orientation_sensor_landscape;
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:
                return R.string.orientation_sensor_portrait;
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR:
                return R.string.orientation_sensor;
            case ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE:
                return R.string.orientation_user_landscape;
            case ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT:
                return R.string.orientation_user_portrait;
            default:
                return R.string._null;
        }
    }

    // FIXME: Translation support
    @NonNull
    public static String getSoftInputString(int flag) {
        StringBuilder builder = new StringBuilder();
        if ((flag & WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING) != 0)
            builder.append("Adjust nothing, ");
        if ((flag & WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN) != 0)
            builder.append("Adjust pan, ");
        if ((flag & WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) != 0)
            builder.append("Adjust resize, ");
        if ((flag & WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED) != 0)
            builder.append("Adjust unspecified, ");
        if ((flag & WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN) != 0)
            builder.append("Always hidden, ");
        if ((flag & WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE) != 0)
            builder.append("Always visible, ");
        if ((flag & WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN) != 0)
            builder.append("Hidden, ");
        if ((flag & WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE) != 0)
            builder.append("Visible, ");
        if ((flag & WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED) != 0)
            builder.append("Unchanged, ");
        if ((flag & WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED) != 0)
            builder.append("Unspecified, ");
        if ((flag & WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION) != 0)
            builder.append("ForwardNav, ");
        if ((flag & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST) != 0)
            builder.append("Mask adjust, ");
        if ((flag & WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE) != 0)
            builder.append("Mask state, ");
        if ((flag & WindowManager.LayoutParams.SOFT_INPUT_MODE_CHANGED) != 0)
            builder.append("Mode changed, ");
        checkStringBuilderEnd(builder);
        String result = builder.toString();
        return result.isEmpty() ? "null" : result;
    }

    // FIXME Add translation support
    @NonNull
    public static CharSequence getServiceFlagsString(int flag) {
        StringBuilder builder = new StringBuilder();
        if ((flag & ServiceInfo.FLAG_STOP_WITH_TASK) != 0)
            builder.append("Stop with task, ");
        if ((flag & ServiceInfo.FLAG_ISOLATED_PROCESS) != 0)
            builder.append("Isolated process, ");

        if ((flag & ServiceInfo.FLAG_SINGLE_USER) != 0)
            builder.append("Single user, ");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if ((flag & ServiceInfo.FLAG_EXTERNAL_SERVICE) != 0)
                builder.append("External service, ");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if ((flag & ServiceInfo.FLAG_USE_APP_ZYGOTE) != 0)
                    builder.append("Use app zygote, ");
            }
        }
        checkStringBuilderEnd(builder);
        String result = builder.toString();
        return TextUtils.isEmpty(result) ? "" : ("⚑ " + result);
    }

    // FIXME Add translation support
    @NonNull
    public static String getActivitiesFlagsString(int flag) {
        StringBuilder builder = new StringBuilder();
        if ((flag & ActivityInfo.FLAG_ALLOW_TASK_REPARENTING) != 0)
            builder.append("AllowReparenting, ");
        if ((flag & ActivityInfo.FLAG_ALWAYS_RETAIN_TASK_STATE) != 0)
            builder.append("AlwaysRetain, ");
        if ((flag & ActivityInfo.FLAG_AUTO_REMOVE_FROM_RECENTS) != 0)
            builder.append("AutoRemove, ");
        if ((flag & ActivityInfo.FLAG_CLEAR_TASK_ON_LAUNCH) != 0)
            builder.append("ClearOnLaunch, ");
        if ((flag & ActivityInfo.FLAG_ENABLE_VR_MODE) != 0)
            builder.append("EnableVR, ");
        if ((flag & ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS) != 0)
            builder.append("ExcludeRecent, ");
        if ((flag & ActivityInfo.FLAG_FINISH_ON_CLOSE_SYSTEM_DIALOGS) != 0)
            builder.append("FinishCloseDialogs, ");
        if ((flag & ActivityInfo.FLAG_FINISH_ON_TASK_LAUNCH) != 0)
            builder.append("FinishLaunch, ");
        if ((flag & ActivityInfo.FLAG_HARDWARE_ACCELERATED) != 0)
            builder.append("HardwareAccel, ");
        if ((flag & ActivityInfo.FLAG_IMMERSIVE) != 0)
            builder.append("Immersive, ");
        if ((flag & ActivityInfo.FLAG_MULTIPROCESS) != 0)
            builder.append("Multiprocess, ");
        if ((flag & ActivityInfo.FLAG_NO_HISTORY) != 0)
            builder.append("NoHistory, ");
        if ((flag & ActivityInfo.FLAG_RELINQUISH_TASK_IDENTITY) != 0)
            builder.append("RelinquishIdentity, ");
        if ((flag & ActivityInfo.FLAG_RESUME_WHILE_PAUSING) != 0)
            builder.append("Resume, ");
        if ((flag & ActivityInfo.FLAG_SINGLE_USER) != 0)
            builder.append("Single, ");
        if ((flag & ActivityInfo.FLAG_STATE_NOT_NEEDED) != 0)
            builder.append("NotNeeded, ");
        checkStringBuilderEnd(builder);
        String result = builder.toString();
        return result.isEmpty() ? "⚐" : "⚑ " + result;
    }

    // FIXME Add translation support
    @NonNull
    public static String getProtectionLevelString(PermissionInfo permissionInfo) {
        int basePermissionType = PermissionInfoCompat.getProtection(permissionInfo);
        int permissionFlags = PermissionInfoCompat.getProtectionFlags(permissionInfo);
        String protectionLevel = "????";
        switch (basePermissionType) {
            case PermissionInfo.PROTECTION_DANGEROUS:
                protectionLevel = "dangerous";
                break;
            case PermissionInfo.PROTECTION_NORMAL:
                protectionLevel = "normal";
                break;
            case PermissionInfo.PROTECTION_SIGNATURE:
                protectionLevel = "signature";
                break;
            case PermissionInfo.PROTECTION_SIGNATURE_OR_SYSTEM:
            case PermissionInfo.PROTECTION_SIGNATURE | PermissionInfo.PROTECTION_FLAG_PRIVILEGED:
                protectionLevel = "signatureOrPrivileged";
                break;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_PRIVILEGED) != 0)
                protectionLevel += "|privileged";
            if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_PRE23) != 0)
                protectionLevel += "|pre23";  // pre marshmallow
            if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_INSTALLER) != 0)
                protectionLevel += "|installer";
            if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_VERIFIER) != 0)
                protectionLevel += "|verifier";
            if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_PREINSTALLED) != 0)
                protectionLevel += "|preinstalled";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_SETUP) != 0)
                    protectionLevel += "|setup";
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_RUNTIME_ONLY) != 0)
                    protectionLevel += "|runtime";
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_INSTANT) != 0)
                    protectionLevel += "|instant";
            }
        } else {
            if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_SYSTEM) != 0) {
                protectionLevel += "|system";
            }
        }

        if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0) {
            protectionLevel += "|development";
        }
        if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_APPOP) != 0) {
            protectionLevel += "|appop";
        }
        return protectionLevel;
    }

    // FIXME Add translation support
    @NonNull
    public static String getInputFeaturesString(int flag) {
        String string = "";
        if ((flag & ConfigurationInfo.INPUT_FEATURE_FIVE_WAY_NAV) != 0)
            string += "Five way nav";
        if ((flag & ConfigurationInfo.INPUT_FEATURE_HARD_KEYBOARD) != 0)
            string += (string.isEmpty() ? "" : "|") + "Hard keyboard";
        return string.isEmpty() ? "null" : string;
    }

    @StringRes
    public static int getKeyboardType(int KbType) {
        switch (KbType) {
            case Configuration.KEYBOARD_NOKEYS:
                return R.string.keyboard_no_keys;
            case Configuration.KEYBOARD_QWERTY:
                return R.string.keyboard_qwerty;
            case Configuration.KEYBOARD_12KEY:
                return R.string.keyboard_12_keys;
            case Configuration.KEYBOARD_UNDEFINED:
            default:
                return R.string._undefined;
        }
    }

    @StringRes
    public static int getNavigation(int navId) {
        switch (navId) {
            case Configuration.NAVIGATION_NONAV:
                return R.string.navigation_no_nav;
            case Configuration.NAVIGATION_DPAD:
                return R.string.navigation_dial_pad;
            case Configuration.NAVIGATION_TRACKBALL:
                return R.string.navigation_trackball;
            case Configuration.NAVIGATION_WHEEL:
                return R.string.navigation_wheel;
            case Configuration.NAVIGATION_UNDEFINED:
            default:
                return R.string._undefined;
        }
    }

    @StringRes
    public static int getTouchScreen(int touchId) {
        switch (touchId) {
            case Configuration.TOUCHSCREEN_NOTOUCH:
                return R.string.touchscreen_no_touch;
            case 2:  // Configuration.TOUCHSCREEN_STYLUS
                return R.string.touchscreen_stylus;
            case Configuration.TOUCHSCREEN_FINGER:
                return R.string.touchscreen_finger;
            case Configuration.TOUCHSCREEN_UNDEFINED:
            default:
                return R.string._undefined;
        }
    }

    public static void checkStringBuilderEnd(@NonNull StringBuilder builder) {
        int length = builder.length();
        if (length > 2) builder.delete(length - 2, length);
    }

    @NonNull
    public static String getGlEsVersion(int reqGlEsVersion) {
        int major = ((reqGlEsVersion & 0xffff0000) >> 16);
        int minor = reqGlEsVersion & 0x0000ffff;
        return major + "." + minor;
    }

    @Nullable
    public static String getVulkanVersion(PackageManager pm) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return null;
        }
        // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/os/GraphicsEnvironment.java;l=193;drc=f80e786d308318894be30d54b93f38034496fc66
        if (pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x00403000)) {
            return "1.3";
        }
        if (pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x00402000)) {
            return "1.2";
        }
        if (pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x00401000)) {
            return "1.1";
        }
        if (pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x00400000)) {
            return "1.0";
        }
        return null;
    }

    @CheckResult
    @NonNull
    public static byte[] charsToBytes(@NonNull char[] chars) {
        final ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] bytes = Arrays.copyOf(byteBuffer.array(), byteBuffer.limit());
        clearBytes(byteBuffer.array());
        return bytes;
    }

    @Nullable
    public static char[] getChars(@Nullable GetChars getChars) {
        if (TextUtils.isEmpty(getChars)) return null;
        @SuppressWarnings("ConstantConditions")
        char[] chars = new char[getChars.length()];
        getChars.getChars(0, chars.length, chars, 0);
        return chars;
    }

    @CheckResult
    @NonNull
    public static char[] bytesToChars(@NonNull byte[] bytes) {
        final CharBuffer charBuffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes));
        char[] chars = Arrays.copyOf(charBuffer.array(), charBuffer.limit());
        clearChars(charBuffer.array());
        return chars;
    }

    public static void clearBytes(@NonNull byte[] bytes) {
        Arrays.fill(bytes, (byte) 0);
    }

    public static void clearChars(@NonNull char[] chars) {
        Arrays.fill(chars, '\u0000');
    }

    @NonNull
    public static Pair<String, String> getIssuerAndAlg(@NonNull PackageInfo p) {
        SignerInfo signerInfo = PackageUtils.getSignerInfo(p, false);
        if (signerInfo != null) {
            X509Certificate[] certs = signerInfo.getCurrentSignerCerts();
            if (certs != null && certs.length > 0) {
                X509Certificate c = certs[0];
                return new Pair<>(c.getIssuerX500Principal().getName(), c.getSigAlgName());
            }
        }
        return new Pair<>("", "");
    }

    /**
     * Replace the first occurrence of matched string
     *
     * @param text         The text where the operation will be carried out
     * @param searchString The string to replace
     * @param replacement  The string that takes in place of the search string
     * @return The modified string
     */
    // Similar impl. of https://commons.apache.org/proper/commons-lang/apidocs/src-html/org/apache/commons/lang3/StringUtils.html#line.6418
    @NonNull
    public static String replaceOnce(@NonNull String text, @NonNull CharSequence searchString, @NonNull CharSequence replacement) {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(searchString)) {
            return text;
        }
        int start = 0;
        final int end = TextUtils.indexOf(text, searchString, start);
        if (end == -1) {
            return text;
        }
        final int replLength = searchString.length();
        final int increase = Math.max(replacement.length() - replLength, 0);
        final StringBuilder buf = new StringBuilder(text.length() + increase);
        buf.append(text, start, end).append(replacement);
        start = end + replLength;
        buf.append(text, start, text.length());
        return buf.toString();
    }

    @Contract("null,_,_ -> fail")
    public static int getIntegerFromString(@Nullable CharSequence needle,
                                           @NonNull List<CharSequence> stringsToMatch,
                                           @NonNull List<Integer> associatedIntegers)
            throws IllegalArgumentException {
        if (needle == null) throw new IllegalArgumentException("Needle cannot be null");
        if (stringsToMatch.size() != associatedIntegers.size()) {
            throw new IllegalArgumentException("String and integer arrays have different sizes");
        }
        CharSequence trimmedNeedle = needle.toString().trim();
        int index = stringsToMatch.indexOf(trimmedNeedle);
        if (index == -1) {
            // Might be a numeric value
            return Integer.parseInt(trimmedNeedle.toString());
        } else {
            return associatedIntegers.get(index);
        }
    }

    public static boolean isTv(@NonNull Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    public static boolean canDisplayNotification(@NonNull Context context) {
        // Notifications can be displayed in all supported devices except Android TV (O+)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isTv(context);
    }

    public static boolean isAppInForeground() {
        ActivityManager.RunningAppProcessInfo appProcessInfo = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(appProcessInfo);
        return (appProcessInfo.importance == IMPORTANCE_FOREGROUND || appProcessInfo.importance == IMPORTANCE_VISIBLE);
    }

    public static int getTotalCores() {
        return Runtime.getRuntime().availableProcessors();
    }

    public static void copyToClipboard(@NonNull Context context, @Nullable CharSequence label, @NonNull CharSequence text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
        UIUtils.displayShortToast(R.string.copied_to_clipboard);
    }

    @Nullable
    public static View.OnClickListener openAsFolderInFM(@NonNull Context context, @Nullable String dir) {
        if (dir == null) return null;
        return view -> {
            Intent openFile = new Intent(Intent.ACTION_VIEW);
            openFile.setDataAndType(Uri.parse(dir), "resource/folder");
            openFile.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (openFile.resolveActivityInfo(context.getPackageManager(), 0) != null)
                context.startActivity(openFile);
        };
    }

    public static void relaunchApp(@NonNull FragmentActivity activity) {
        Intent intent = PackageManagerCompat.getLaunchIntentForPackage(activity.getPackageName(), UserHandleHidden.myUserId());
        if (intent == null) {
            // No launch intent
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }

    @Nullable
    public static String getRealReferrer(@NonNull Activity activity) {
        String callingPackage = activity.getCallingPackage();
        if (callingPackage != null && !BuildConfig.APPLICATION_ID.equals(callingPackage)) {
            return callingPackage;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            Intent intent = activity.getIntent();
            intent.removeExtra(Intent.EXTRA_REFERRER_NAME);
            intent.removeExtra(Intent.EXTRA_REFERRER);
            // Now that the custom referrers are removed, it should return the real referrer.
            // android-app:authority
            Uri referrer = activity.getReferrer();
            return referrer != null ? referrer.getAuthority() : null;
        }
        return null;
    }

    public static boolean isWifiActive(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.getType() == ConnectivityManager.TYPE_WIFI;
    }

    public static boolean isRoboUnitTest() {
        return "robolectric".equals(Build.FINGERPRINT);
    }
}
