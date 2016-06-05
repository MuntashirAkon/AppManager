package com.majeur.applicationsinfo.utils;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.util.TypedValue;
import android.view.WindowManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

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
            return null;

        try {
            Scanner scanner = new Scanner(file);
            String result = "";
            while (scanner.hasNext())
                result += scanner.next();
            return result;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public static boolean isApi20() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH;
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

        checkStringBuilderEnd(builder);
        String result = builder.toString();
        return result.equals("") ? "null" : result;
    }

    public static String getServiceFlagsString(int flag) {
        StringBuilder builder = new StringBuilder();
        if ((flag & ServiceInfo.FLAG_STOP_WITH_TASK) != 0)
            builder.append("Stop with task, ");
        if ((flag & ServiceInfo.FLAG_SINGLE_USER) != 0)
            builder.append("Single user, ");
        if ((flag & ServiceInfo.FLAG_ISOLATED_PROCESS) != 0)
            builder.append("Isolated process, ");

        checkStringBuilderEnd(builder);
        String result = builder.toString();
        return result.equals("") ? "null" : result;
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
        if ((level & PermissionInfo.PROTECTION_FLAG_SYSTEM) != 0) {
            protLevel += "|system";
        }
        if ((level & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0) {
            protLevel += "|development";
        }
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
}
