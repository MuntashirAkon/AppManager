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

package io.github.muntashirakon.AppManager.utils;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Parcel;
import android.text.TextUtils;
import android.util.Pair;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.ipc.AuthenticationActivity;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.pm.PermissionInfoCompat;
import aosp.libcore.util.HexEncoding;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;

public class Utils {
    public static final String TERMUX_LOGIN_PATH = OsEnvironment.getDataDataDirectory() + "/com.termux/files/usr/bin/login";

    @SuppressWarnings({"BooleanMethodIsAlwaysInverted", "deprecation"})
    public static boolean checkUsageStatsPermission(@NonNull Context context) {
        AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        assert appOpsManager != null;
        final int mode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mode = appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.getPackageName());
        } else {
            mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.getPackageName());
        }
        if (mode == AppOpsManager.MODE_DEFAULT
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

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
            if (s.length() > 0) builder.append(s.charAt(0));
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    // https://commons.apache.org/proper/commons-lang/javadocs/api-3.1/src-html/org/apache/commons/lang3/StringUtils.html#line.3164
    @NonNull
    public static String[] splitByCharacterType(@NonNull String str, boolean camelCase) {
        if (str.length() == 0) return new String[]{};
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
        return result.equals("") ? "null" : result;
    }

    // FIXME Add translation support
    @NonNull
    public static String getServiceFlagsString(int flag) {
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
        return result.equals("") ? "\u2690" : "\u2691 " + result;
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
        return result.equals("") ? "\u2690" : "\u2691 " + result;
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

    @StringRes
    public static int getFeatureFlags(int flags) {
        return (flags == FeatureInfo.FLAG_REQUIRED) ? R.string.required : R.string._null;
    }

    // FIXME Add translation support
    @NonNull
    public static String getInputFeaturesString(int flag) {
        String string = "";
        if ((flag & ConfigurationInfo.INPUT_FEATURE_FIVE_WAY_NAV) != 0)
            string += "Five way nav";
        if ((flag & ConfigurationInfo.INPUT_FEATURE_HARD_KEYBOARD) != 0)
            string += (string.length() == 0 ? "" : "|") + "Hard keyboard";
        return string.length() == 0 ? "null" : string;
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
        if (reqGlEsVersion == 0) return "1";
        int major = ((reqGlEsVersion & 0xffff0000) >> 16);
        int minor = reqGlEsVersion & 0x0000ffff;
        return major + "." + minor;
    }

    @NonNull
    public static String bytesToHex(@NonNull byte[] bytes) {
        return HexEncoding.encodeToString(bytes, false /* lowercase */);
    }

    /**
     * Decodes a hex string.
     * <p>
     * Note that this function does <em>NOT</em> convert a hexadecimal number to a
     * binary number.
     *
     * @param hex Hexadecimal representation of data.
     * @return The byte[] representation of the given data.
     * @throws NumberFormatException If the hexadecimal input string is of odd
     *                               length or invalid hexadecimal string.
     */
    @NonNull
    public static byte[] hexToBytes(@NonNull String hex) throws NumberFormatException {
        return HexEncoding.decode(hex);
    }

    @NonNull
    public static byte[] longToBytes(long l) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (l & 0xFF);
            l >>= 8;
        }
        return result;
    }

    @CheckResult
    @NonNull
    public static byte[] charsToBytes(@NonNull char[] chars) {
        final ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] bytes = Arrays.copyOf(byteBuffer.array(), byteBuffer.limit());
        clearBytes(byteBuffer.array());
        return bytes;
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
        Signature[] signatures = PackageUtils.getSigningInfo(p, false);
        X509Certificate c;
        if (signatures == null) return new Pair<>("", "");
        String name = "";
        String algoName = "";
        try {
            for (Signature sg : signatures) {
                c = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(sg.toByteArray()));
                name = c.getIssuerX500Principal().getName();
                algoName = c.getSigAlgName();
                break;
            }
        } catch (CertificateException ignored) {
        }
        return new Pair<>(name, algoName);
    }

    /**
     * Format xml file to correct indentation ...
     */
    @NonNull
    public static String getProperXml(@NonNull String dirtyXml) {
        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new ByteArrayInputStream(dirtyXml.getBytes(StandardCharsets.UTF_8))));

            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodeList = (NodeList) xPath.evaluate("//text()[normalize-space()='']",
                    document,
                    XPathConstants.NODESET);

            for (int i = 0; i < nodeList.getLength(); ++i) {
                Node node = nodeList.item(i);
                node.getParentNode().removeChild(node);
            }

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            StringWriter stringWriter = new StringWriter();
            StreamResult streamResult = new StreamResult(stringWriter);

            transformer.transform(new DOMSource(document), streamResult);

            return stringWriter.toString();
        } catch (Exception e) {
            Log.e("Utils", "Could not get proper XML.", e);
            return dirtyXml;
        }
    }

    public static String getFormattedDuration(Context context, long time) {
        return getFormattedDuration(context, time, false);
    }

    public static String getFormattedDuration(Context context, long time, boolean addSign) {
        String fTime = "";
        if (time < 0) {
            time = -time;
            if (addSign) fTime = "- ";
        }
        time /= 60000; // minutes
        long month, day, hour, min;
        month = time / 43200;
        time %= 43200;
        day = time / 1440;
        time %= 1440;
        hour = time / 60;
        min = time % 60;
        int count = 0;
        if (month != 0) {
            fTime += context.getResources().getQuantityString(R.plurals.usage_months, (int) month, month);
            ++count;
        }
        if (day != 0) {
            fTime += (count > 0 ? " " : "") + context.getResources().getQuantityString(R.plurals.usage_days, (int) day, day);
            ++count;
        }
        if (hour != 0) {
            fTime += (count > 0 ? " " : "") + context.getResources().getQuantityString(R.plurals.usage_hours, (int) hour, hour);
            ++count;
        }
        if (min != 0) {
            fTime += (count > 0 ? " " : "") + context.getString(R.string.usage_min, min);
        } else {
            if (count == 0) fTime = context.getString(R.string.usage_less_than_a_minute);
        }
        return fTime;
    }

    public static boolean isAppUpdated() {
        long newVersionCode = BuildConfig.VERSION_CODE;
        long oldVersionCode = (long) AppPref.get(AppPref.PrefKey.PREF_LAST_VERSION_CODE_LONG);
        return oldVersionCode != 0 && oldVersionCode < newVersionCode;
    }

    public static boolean isAppInstalled() { // or data cleared
        return (long) AppPref.get(AppPref.PrefKey.PREF_LAST_VERSION_CODE_LONG) == 0;
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
    public static String replaceOnce(@NonNull final String text, @NonNull String searchString, @NonNull final String replacement) {
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

    public static int getIntegerFromString(CharSequence needle,
                                           List<CharSequence> stringsToMatch,
                                           List<Integer> associatedIntegers)
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

    public static void writeBoolean(boolean b, @NonNull Parcel dest) {
        dest.writeInt(b ? 1 : 0);
    }

    public static boolean readBoolean(@NonNull Parcel in) {
        return in.readInt() != 0;
    }

    @NonNull
    public static Intent getAuthIntent(@Nullable Intent intent, @NonNull ComponentName target) {
        intent = new Intent();
        intent.setClass(AppManager.getContext(), AuthenticationActivity.class);
        return intent;
    }
}
