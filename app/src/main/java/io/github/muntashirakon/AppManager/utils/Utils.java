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
import android.annotation.TargetApi;
import android.app.AppOpsManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.WindowManager;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.misc.RequestCodes;
import io.github.muntashirakon.AppManager.runner.RootShellRunner;

public class Utils {
    static final Spannable.Factory sSpannableFactory = Spannable.Factory.getInstance();

    @NonNull
    public static Spannable getHighlightedText(@NonNull String text, @NonNull String constraint,
                                               int color) {
        Spannable spannable = sSpannableFactory.newSpannable(text);
        int start = text.toLowerCase(Locale.ROOT).indexOf(constraint);
        int end = start + constraint.length();
        spannable.setSpan(new BackgroundColorSpan(color), start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean checkUsageStatsPermission(@NonNull Context context) {
        AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        assert appOpsManager != null;
        final int mode;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            mode = appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.getPackageName());
        } else {
            mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.getPackageName());
        }
        if (mode == AppOpsManager.MODE_DEFAULT
                && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            return context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static int getArrayLengthSafely(Object[] array) {
        return array == null ? 0 : array.length;
    }

    public static int dpToPx(@NonNull Context context, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }

    public static int compareBooleans(boolean b1, boolean b2) {
        if (b1 && !b2) return +1;
        if (!b1 && b2) return -1;
        return 0;
    }

    @NonNull
    public static String camelCaseToSpaceSeparatedString(@NonNull String str) {
        return TextUtils.join(" ", splitByCharacterType(str, true)).replace(" _", "");
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

    @Nullable
    public static String getName(@NonNull ContentResolver resolver, Uri uri) {
        Cursor returnCursor =
                resolver.query(uri, null, null, null, null);
        if (returnCursor == null) return null;
        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        returnCursor.moveToFirst();
        String name = returnCursor.getString(nameIndex);
        returnCursor.close();
        return name;
    }

    @NonNull
    public static String getFileNameFromZipEntry(@NonNull ZipEntry zipEntry) {
        String path = zipEntry.getName();
        int lastIndexOfSeparator = path.lastIndexOf("/");
        if (lastIndexOfSeparator == -1)
            return path;
        return path.substring(lastIndexOfSeparator + 1);
    }

    @NonNull
    public static String trimExtension(@NonNull String filename) {
        try {
            return filename.substring(0, filename.lastIndexOf('.'));
        } catch (Exception e) {
            return filename;
        }
    }

    @NonNull
    public static String getLastComponent(@NonNull String str) {
        try {
            return str.substring(str.lastIndexOf('.')+1);
        } catch (Exception e) {
            return str;
        }
    }

    @NonNull
    public static String getFileContent(@NonNull File file) {
        return getFileContent(file, "");
    }

    /**
     * Read the full content of a file.
     * @param file The file to be read
     * @param emptyValue Empty value if no content has been found
     * @return File content as string
     */
    @NonNull
    public static String getFileContent(@NonNull File file, @NonNull String emptyValue) {
        if (!file.exists() || file.isDirectory()) return emptyValue;
        try {
            return getInputStreamContent(new FileInputStream(file));
        } catch (IOException e) {
            e.printStackTrace();
            return emptyValue;
        }
    }

    @NonNull
    public static String getInputStreamContent(@NonNull InputStream inputStream) throws IOException {
        return new String(IOUtils.readFully(inputStream, -1, true), Charset.defaultCharset());
    }

    @NonNull
    public static String getContentFromAssets(@NonNull Context context, String fileName) {
        try {
            InputStream inputStream = context.getResources().getAssets().open(fileName);
            return getInputStreamContent(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    @NonNull
    public static String getFileContent(@NonNull ContentResolver contentResolver, @NonNull Uri file)
            throws IOException {
        InputStream inputStream = contentResolver.openInputStream(file);
        if (inputStream == null) throw new IOException("Failed to open " + file.toString());
        return getInputStreamContent(inputStream);
    }

    @NonNull
    public static String getProcessStateName(@NonNull String shortName) {
        switch (shortName) {
            case "R": return "Running";
            case "S": return "Sleeping";
            case "D": return "Device I/O";
            case "T": return "Stopped";
            case "t": return "Trace stop";
            case "x":
            case "X": return "Dead";
            case "Z": return "Zombie";
            case "P": return "Parked";
            case "I": return "Idle";
            case "K": return "Wake kill";
            case "W": return "Waking";
            default: return "";
        }
    }

    @NonNull
    public static String getProcessStateExtraName(String shortName) {
        if (shortName == null) return "";
        switch (shortName) {
            case "<": return "High priority";
            case "N": return "Low priority";
            case "L": return "Locked memory";
            case "s": return "Session leader";
            case "+": return "foreground";
            case "l": return "Multithreaded";
            default: return "";
        }
    }

    // FIXME: Add translation support
    @NonNull
    public static String getLaunchMode(int mode) {
        switch (mode) {
            case ActivityInfo.LAUNCH_MULTIPLE: return "Multiple";
            case ActivityInfo.LAUNCH_SINGLE_INSTANCE: return "Single instance";
            case ActivityInfo.LAUNCH_SINGLE_TASK: return "Single task";
            case ActivityInfo.LAUNCH_SINGLE_TOP: return "Single top";
            default: return "null";
        }
    }

    // FIXME: Add translation support
    @NonNull
    public static String getOrientationString(int orientation) {
        switch (orientation) {
            case ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED: return "Unspecified";
            case ActivityInfo.SCREEN_ORIENTATION_BEHIND: return "Behind";
            case ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR: return "Full sensor";
            case ActivityInfo.SCREEN_ORIENTATION_FULL_USER: return "Full user";
            case ActivityInfo.SCREEN_ORIENTATION_LOCKED: return "Locked";
            case ActivityInfo.SCREEN_ORIENTATION_NOSENSOR: return "No sensor";
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE: return "Landscape";
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT: return "Portrait";
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT: return "Reverse portrait";
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE: return "Reverse landscape";
            case ActivityInfo.SCREEN_ORIENTATION_USER: return "User";
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE: return "Sensor landscape";
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT: return "Sensor portrait";
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR: return "Sensor";
            case ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE: return "User landscape";
            case ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT: return "User portrait";
            default: return "null";
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

        if (Build.VERSION.SDK_INT >= 24){
            if ((flag & ServiceInfo.FLAG_EXTERNAL_SERVICE)!= 0)
            builder.append("External service, ");

            if (Build.VERSION.SDK_INT >= 29){
                if ((flag & ServiceInfo.FLAG_USE_APP_ZYGOTE) != 0)
                    builder.append("Use app zygote, ");
            }
        }
        checkStringBuilderEnd(builder);
        String result = builder.toString();
        return result.equals("") ? "\u2690" : "\u2691 "+result;
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
        return result.equals("") ? "\u2690" : "\u2691 "+result;
    }

    // FIXME Add translation support
    @NonNull
    public static String getProtectionLevelString(PermissionInfo permissionInfo) {
        int basePermissionType;
        int permissionFlags;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissionFlags = permissionInfo.getProtectionFlags();
            basePermissionType = permissionInfo.getProtection();
        } else {
            permissionFlags = permissionInfo.protectionLevel;
            basePermissionType = permissionFlags & PermissionInfo.PROTECTION_MASK_BASE;
        }
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
        }
        if (Build.VERSION.SDK_INT >= 23) {
            if (basePermissionType == (PermissionInfo.PROTECTION_SIGNATURE
                    | PermissionInfo.PROTECTION_FLAG_PRIVILEGED)) {
                protectionLevel = "signatureOrPrivileged";
            }
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
            if (Build.VERSION.SDK_INT >= 24) {
                if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_SETUP) != 0)
                    protectionLevel += "|setup";
            }
            if (Build.VERSION.SDK_INT >= 26) {
                if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_RUNTIME_ONLY) != 0)
                    protectionLevel += "|runtime";
            }
            if (Build.VERSION.SDK_INT >= 27) {
                if ((permissionFlags & PermissionInfo.PROTECTION_FLAG_INSTANT) != 0)
                    protectionLevel += "|instant";
            }
        } else {
            if (basePermissionType == PermissionInfo.PROTECTION_SIGNATURE_OR_SYSTEM) {
                protectionLevel = "signatureOrSystem";
            }
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
    public static String getFeatureFlagsString(int flags) {
        return (flags == FeatureInfo.FLAG_REQUIRED) ? "Required": "null";
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

    // FIXME Add translation support
    @NonNull
    public static String getKeyboardTypeString(int KbType) {
        switch (KbType) {
            case Configuration.KEYBOARD_UNDEFINED: return "Undefined";
            case Configuration.KEYBOARD_NOKEYS: return "No keys";
            case Configuration.KEYBOARD_QWERTY: return "Qwerty";
            case Configuration.KEYBOARD_12KEY: return "12 key";
        }
        return String.valueOf(KbType);
    }

    // FIXME Add translation support
    @NonNull
    public static String getNavigationString(int navId) {
        switch (navId) {
            case Configuration.NAVIGATION_UNDEFINED: return "Undefined";
            case Configuration.NAVIGATION_NONAV: return "No nav";
            case Configuration.NAVIGATION_DPAD: return "Dial pad";
            case Configuration.NAVIGATION_TRACKBALL: return "Trackball";
            case Configuration.NAVIGATION_WHEEL: return "Wheel";
        }
        return String.valueOf(navId);
    }

    // FIXME Add translation support
    @NonNull
    public static String getTouchScreenString(int touchId) {
        switch (touchId) {
            case Configuration.TOUCHSCREEN_UNDEFINED: return "Undefined";
            case Configuration.TOUCHSCREEN_NOTOUCH: return "No touch";
            case 2: return "Stylus";  // Configuration.TOUCHSCREEN_STYLUS
            case Configuration.TOUCHSCREEN_FINGER: return "Finger";
        }
        return String.valueOf(touchId);
    }

    public static void checkStringBuilderEnd(@NonNull StringBuilder builder) {
        int length = builder.length();
        if (length > 2) builder.delete(length - 2, length);
    }

    @NonNull
    public static String getOpenGL(int reqGL){
            if (reqGL != 0) {
                return (short) (reqGL >> 16) + "." + (short) reqGL; // Integer.toString((reqGL & 0xffff0000) >> 16);
            } else return "1"; // Lack of property means OpenGL ES version 1
    }

    @NonNull
    public static String convertToHex(@NonNull byte[] data) { // https://stackoverflow.com/questions/5980658/how-to-sha1-hash-a-string-in-android
        StringBuilder buf = new StringBuilder();
        for (byte b : data) {
            int halfbyte = (b >>> 4) & 0x0F;
            int two_halfs = 0;
            do {
                buf.append(halfbyte <= 9 ? (char) ('0' + halfbyte) : (char) ('a' + halfbyte - 10));
                halfbyte = b & 0x0F;
            } while (two_halfs++ < 1);
        }
        return buf.toString();
    }

    // FIXME Add translation support
    @NonNull
    public static String signCert(@NonNull Signature sign){
        String s = "";
        try {
            X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(sign.toByteArray()));

            s = "\n" + cert.getIssuerX500Principal().getName()
                    + "\nCertificate fingerprints:"
                    + "\nmd5: " + Utils.convertToHex(MessageDigest.getInstance("md5").digest(sign.toByteArray()))
                    + "\nsha1: " + Utils.convertToHex(MessageDigest.getInstance("sha1").digest(sign.toByteArray()))
                    + "\nsha256: " + Utils.convertToHex(MessageDigest.getInstance("sha256").digest(sign.toByteArray()))
                    + "\n" + cert.toString()
                    + "\n" + cert.getPublicKey().getAlgorithm()
                    + "---" + cert.getSigAlgName() + "---" + cert.getSigAlgOID()
                    + "\n" + cert.getPublicKey()
                    + "\n";
        }catch (NoSuchAlgorithmException | CertificateException e) {
            return e.toString() + s;
        }
        return s;
    }

    @NonNull
    public static Tuple<String, String> getIssuerAndAlg(@NonNull PackageInfo p){
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            SigningInfo signingInfo = p.signingInfo;
            signatures = signingInfo.hasMultipleSigners() ? signingInfo.getApkContentsSigners()
                    : signingInfo.getSigningCertificateHistory();
        } else {
            signatures = p.signatures;
        }
        X509Certificate c;
        Tuple<String, String> t= new Tuple<>("", "");
        try {
            for (Signature sg: signatures) {
                c = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(sg.toByteArray()));
                t.setFirst(c.getIssuerX500Principal().getName());
                t.setSecond(c.getSigAlgName());
            }
        } catch (CertificateException ignored) {}
        return t;
    }

    /**
     * Format xml file to correct indentation ...
     */
    @Nullable
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
            e.printStackTrace();
            return null;
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
        month = time / 43200; time %= 43200;
        day = time / 1440; time %= 1440;
        hour = time / 60;
        min = time % 60;
        int count = 0;
        if (month != 0){
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

    @TargetApi(29)
    public static int getSystemColor(@NonNull Context context, int resAttrColor) { // Ex. android.R.attr.colorPrimary
        // Get accent color
        TypedValue typedValue = new TypedValue();
        ContextThemeWrapper contextThemeWrapper = new ContextThemeWrapper(context,
                android.R.style.Theme_DeviceDefault_DayNight);
        contextThemeWrapper.getTheme().resolveAttribute(resAttrColor,
                typedValue, true);
        return typedValue.data;
    }

    public static int getThemeColor(@NonNull Context context, int resAttrColor) { // Ex. android.R.attr.colorPrimary
        // Get accent color
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(resAttrColor,
                typedValue, true);
        return typedValue.data;
    }

    public static boolean isRootGiven() {
        if (isRootAvailable()) {
            String output = RootShellRunner.runCommand("id").getOutput();
            return output != null && output.toLowerCase().contains("uid=0");
        }
        return false;
    }

    private static boolean isRootAvailable() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String pathDir : pathEnv.split(":")) {
                try {
                    if (new File(pathDir, "su").exists()) {
                        return true;
                    }
                } catch (NullPointerException ignore) {}
            }
        }
        return false;
    }

    public static boolean isAppUpdated() {
        long newVersionCode = BuildConfig.VERSION_CODE;
        long oldVersionCode = (long) AppPref.get(AppPref.PrefKey.PREF_LAST_VERSION_CODE_LONG);
        return oldVersionCode != 0 && oldVersionCode < newVersionCode;
    }

    public static boolean isAppInstalled() { // or data cleared
        return (long) AppPref.get(AppPref.PrefKey.PREF_LAST_VERSION_CODE_LONG) == 0;
    }

    public static boolean requestExternalStoragePermissions(FragmentActivity activity) {
        List<String> permissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (permissions.size() > 0) {
            ActivityCompat.requestPermissions(activity, permissions.toArray(new String[0]), RequestCodes.REQUEST_CODE_EXTERNAL_STORAGE_PERMISSIONS);
            return false;  // Need to receive the results
        }
        return true;  // Permissions given
    }

    public static final String TERMUX_LOGIN_PATH = OsEnvironment.getDataDataDirectory() + "/com.termux/files/usr/bin/login";
    public static final String TERMUX_PERM_RUN_COMMAND = "com.termux.permission.RUN_COMMAND";
    /**
     * Request permissions for running arbitrary command in Termux. In addition to this, user needs
     * to set {@code allow-external-apps=true} in ~/.termux/termux.properties.
     * @param activity The fragment or activity that requested the permission
     * @param requestCode {@link RequestCodes#REQUEST_CODE_TERMUX_PERM_OPEN_IN} or
     *      {@link RequestCodes#REQUEST_CODE_TERMUX_PERM_RUN_AS}
     * @return {@code true} if the permission is given, {@code false} otherwise. If the result is
     *      {@code false}, user need to check for {@link RequestCodes#REQUEST_CODE_TERMUX_PERM_OPEN_IN}
     *      or {@link RequestCodes#REQUEST_CODE_TERMUX_PERM_RUN_AS} to see if the permission is granted.
     */
    public static boolean requestTermuxPermission(FragmentActivity activity, int requestCode) {
        if (ContextCompat.checkSelfPermission(activity, TERMUX_PERM_RUN_COMMAND) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{TERMUX_PERM_RUN_COMMAND}, requestCode);
            return false;
        }
        return true;
    }
}
