package io.github.muntashirakon.AppManager.utils;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.util.TypedValue;
import android.view.WindowManager;


import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

public class Utils {

    public static int getArrayLengthSafely(Object[] array) {
        return array == null ? 0 : array.length;
    }

    public static int dpToPx(Context c, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, c.getResources().getDisplayMetrics());
    }

    public static int compareBooleans(boolean b1, boolean b2) {
        if (b1 && !b2) {
            return +1;
        }
        if (!b1 && b2) {
            return -1;
        }
        return 0;
    }

    public static String getFileContent(File file) {
        if (file.isDirectory())
            return "-1";

        try {
            Scanner scanner = new Scanner(file);
            String result = "";
            while (scanner.hasNext())
                result += scanner.next();
            return result;
        } catch (FileNotFoundException e) {
            return "-1";
        }
    }

    public static String getLaunchMode(int mode) {
        switch (mode) {
            case ActivityInfo.LAUNCH_MULTIPLE:
                return "Multiple";
            case ActivityInfo.LAUNCH_SINGLE_INSTANCE:
                return "Single instance";
            case ActivityInfo.LAUNCH_SINGLE_TASK:
                return "Single task";
            case ActivityInfo.LAUNCH_SINGLE_TOP:
                return "Single top";
            default:
                return "null";
        }
    }

    public static String getOrientationString(int orientation) {
        switch (orientation) {
            case ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED:
                return "Unspecified";
            case ActivityInfo.SCREEN_ORIENTATION_BEHIND:
                return "Behind";
            case ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR:
                return "Full sensor";
            case ActivityInfo.SCREEN_ORIENTATION_FULL_USER:
                return "Full user";
            case ActivityInfo.SCREEN_ORIENTATION_LOCKED:
                return "Locked";
            case ActivityInfo.SCREEN_ORIENTATION_NOSENSOR:
                return "No sensor";
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                return "Landscape";
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                return "Portrait";
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                return "Reverse portrait";
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                return "Reverse landscape";
            case ActivityInfo.SCREEN_ORIENTATION_USER:
                return "User";
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                return "Sensor landscape";
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:
                return "Sensor portrait";
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR:
                return "Sensor";
            case ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE:
                return "User landscape";
            case ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT:
                return "User portrait";
            default:
                return "null";
        }
    }

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

    public static String getProtectionLevelString(int level) {
        String protLevel = "????";
        switch (level & PermissionInfo.PROTECTION_MASK_BASE) {
            case PermissionInfo.PROTECTION_DANGEROUS:
                protLevel = "dangerous";
                break;
            case PermissionInfo.PROTECTION_NORMAL:
                protLevel = "normal";
                break;
            case PermissionInfo.PROTECTION_SIGNATURE:
                protLevel = "signature";
                break;
            case PermissionInfo.PROTECTION_SIGNATURE_OR_SYSTEM:
                protLevel = "signatureOrSystem";
                break;
        }
        if (Build.VERSION.SDK_INT >= 23){
            if ((level  & PermissionInfo.PROTECTION_FLAG_PRIVILEGED) != 0)
                protLevel += "|privileged";
            if ((level  & PermissionInfo.PROTECTION_FLAG_PRE23) != 0)
                protLevel += "|pre23";
            if ((level  & PermissionInfo.PROTECTION_FLAG_INSTALLER) != 0)
                protLevel += "|installer";
            if ((level  & PermissionInfo.PROTECTION_FLAG_VERIFIER) != 0)
                protLevel += "|verifier";
            if ((level  & PermissionInfo.PROTECTION_FLAG_PREINSTALLED) != 0)
                protLevel += "|preinstalled";
            //24
            if ((level  & PermissionInfo.PROTECTION_FLAG_SETUP) != 0)
                protLevel += "|setup";
            //26
            if ((level  & PermissionInfo.PROTECTION_FLAG_RUNTIME_ONLY) != 0)
                protLevel += "|runtime";
            //27
            if ((level  & PermissionInfo.PROTECTION_FLAG_INSTANT) != 0)
                protLevel += "|instant";
        }else if ((level & PermissionInfo.PROTECTION_FLAG_SYSTEM) != 0) {
            protLevel += "|system";
        }

        if ((level & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0) {
            protLevel += "|development";
        }
        //21
        if ((level & PermissionInfo.PROTECTION_FLAG_APPOP) != 0) {
            protLevel += "|appop";
        }
        return protLevel;
    }

    public static String getFeatureFlagsString(int flags) {
        if (flags == FeatureInfo.FLAG_REQUIRED)
            return "Required";
        return "null";
    }

    public static String getInputFeaturesString(int flag) {
        String string = "";
        if ((flag & ConfigurationInfo.INPUT_FEATURE_FIVE_WAY_NAV) != 0)
            string += "Five way nav";
        if ((flag & ConfigurationInfo.INPUT_FEATURE_HARD_KEYBOARD) != 0)
            string += (string.length() == 0 ? "" : "|") + "Hard keyboard";
        return string.length() == 0 ? "null" : string;
    }

    public static void checkStringBuilderEnd(StringBuilder builder) {
        int length = builder.length();
        if (length > 2)
            builder.delete(builder.length() - 2, builder.length());
    }

    public static String getOpenGL(int reqGL){
            if (reqGL != 0) {
                return (short)(reqGL>>16)+"."+(short)reqGL;//Integer.toString((reqGL & 0xffff0000) >> 16);
            } else {
                return "1"; // Lack of property means OpenGL ES version 1
            }
    }
    public static String convertToHex(byte[] data) {//https://stackoverflow.com/questions/5980658/how-to-sha1-hash-a-string-in-android
        StringBuilder buf = new StringBuilder();
        for (byte b : data) {
            int halfbyte = (b >>> 4) & 0x0F;
            int two_halfs = 0;
            do {
                buf.append((0 <= halfbyte) && (halfbyte <= 9) ? (char) ('0' + halfbyte) : (char) ('a' + (halfbyte - 10)));
                halfbyte = b & 0x0F;
            } while (two_halfs++ < 1);
        }
        return buf.toString();
    }

    public static String signCert(Signature sign){
        String s= "";
        try {
            X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(sign.toByteArray()));

            s="\n"+cert.getIssuerX500Principal().getName()
                    +"\nCertificate fingerprints:"
                    +"\n"+"md5: "+Utils.convertToHex(MessageDigest.getInstance("md5").digest(sign.toByteArray()))
                    +"\n"+"sha1: "+Utils.convertToHex(MessageDigest.getInstance("sha1").digest(sign.toByteArray()))
                    +"\n"+"sha256: "+Utils.convertToHex(MessageDigest.getInstance("sha256").digest(sign.toByteArray()))
                    +"\n"+cert.toString()
                    +"\n"+(cert.getPublicKey().getAlgorithm())
                    +"---"+cert.getSigAlgName()+"---"+cert.getSigAlgOID()
                    +"\n"+(cert.getPublicKey())
                    +"\n";
        }catch (NoSuchAlgorithmException |CertificateException e) {
            return e.toString()+s;
        }
        return s;
    }

    public static Tuple<String, String> apkPro(PackageInfo p){
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
    public static String getProperXml(String dirtyXml) {
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

}
