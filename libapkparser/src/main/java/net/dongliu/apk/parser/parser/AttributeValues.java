// SPDX-License-Identifier: BSD-2-Clause

package net.dongliu.apk.parser.parser;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PermissionInfo;
import android.view.WindowManager;

import net.dongliu.apk.parser.utils.Strings;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

/**
 * attribute value constant
 */
// Copyright 2018 Liu Dong
public class AttributeValues {
    @NonNull
    public static String getScreenOrientation(int value) {
        switch (value) {
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
                return "landscape";
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
                return "portrait";
            case ActivityInfo.SCREEN_ORIENTATION_USER:
                return "user";
            case ActivityInfo.SCREEN_ORIENTATION_BEHIND:
                return "behind";
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR:
                return "sensor";
            case ActivityInfo.SCREEN_ORIENTATION_NOSENSOR:
                return "nosensor";
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                return "sensorLandscape";
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:
                return "sensorPortrait";
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                return "reverseLandscape";
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
                return "reversePortrait";
            case ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR:
                return "fullSensor";
            case ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE:
                return "userLandscape";
            case ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT:
                return "userPortrait";
            case ActivityInfo.SCREEN_ORIENTATION_FULL_USER:
                return "fullUser";
            case ActivityInfo.SCREEN_ORIENTATION_LOCKED:
                return "locked";
            case ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED:
                return "unspecified";
            default:
                return "ScreenOrientation:" + Integer.toHexString(value);
        }
    }

    @NonNull
    public static String getLaunchMode(int value) {
        switch (value) {
            case ActivityInfo.LAUNCH_MULTIPLE:
                return "standard";
            case ActivityInfo.LAUNCH_SINGLE_TOP:
                return "singleTop";
            case ActivityInfo.LAUNCH_SINGLE_TASK:
                return "singleTask";
            case ActivityInfo.LAUNCH_SINGLE_INSTANCE:
                return "singleInstance";
            default:
                return "LaunchMode:" + Integer.toHexString(value);
        }

    }

    public static String getConfigChanges(int value) {
        List<String> list = new ArrayList<>();
        if ((value & ActivityInfo.CONFIG_DENSITY) != 0) {
            list.add("density");
        } else if ((value & ActivityInfo.CONFIG_FONT_SCALE) != 0) {
            list.add("fontScale");
        } else if ((value & ActivityInfo.CONFIG_KEYBOARD) != 0) {
            list.add("keyboard");
        } else if ((value & ActivityInfo.CONFIG_KEYBOARD_HIDDEN) != 0) {
            list.add("keyboardHidden");
        } else if ((value & ActivityInfo.CONFIG_LAYOUT_DIRECTION) != 0) {
            list.add("direction");
        } else if ((value & ActivityInfo.CONFIG_LOCALE) != 0) {
            list.add("locale");
        } else if ((value & ActivityInfo.CONFIG_MCC) != 0) {
            list.add("mcc");
        } else if ((value & ActivityInfo.CONFIG_MNC) != 0) {
            list.add("mnc");
        } else if ((value & ActivityInfo.CONFIG_NAVIGATION) != 0) {
            list.add("navigation");
        } else if ((value & ActivityInfo.CONFIG_ORIENTATION) != 0) {
            list.add("orientation");
        } else if ((value & ActivityInfo.CONFIG_SCREEN_LAYOUT) != 0) {
            list.add("screenLayout");
        } else if ((value & ActivityInfo.CONFIG_SCREEN_SIZE) != 0) {
            list.add("screenSize");
        } else if ((value & ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE) != 0) {
            list.add("smallestScreenSize");
        } else if ((value & ActivityInfo.CONFIG_TOUCHSCREEN) != 0) {
            list.add("touchscreen");
        } else if ((value & ActivityInfo.CONFIG_UI_MODE) != 0) {
            list.add("uiMode");
        } else if ((value & ActivityInfo.CONFIG_COLOR_MODE) != 0) {
            list.add("colorMode");
        }
        return Strings.join(list, "|");
    }

    public static String getWindowSoftInputMode(int value) {
        int adjust = value & 0x000000f0;
        int state = value & 0x0000000f;
        List<String> list = new ArrayList<>(2);
        switch (adjust) {
            case WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING:
                list.add("adjustNothing");
                break;
            case WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN:
                list.add("adjustPan");
                break;
            case WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE:
                list.add("adjustResize");
                break;
            case WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED:
                list.add("adjustUnspecified");
                break;
            default:
                list.add("WindowInputModeAdjust:" + Integer.toHexString(adjust));
        }
        switch (state) {
            case WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED:
                list.add("stateUnspecified");
                break;
            case WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED:
                list.add("stateUnchanged");
                break;
            case WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN:
                list.add("stateHidden");
                break;
            case WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN:
                list.add("stateAlwaysHidden");
                break;
            case WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE:
                list.add("stateVisible");
                break;
            case WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE:
                list.add("stateAlwaysVisible");
                break;
            default:
                list.add("WindowInputModeState:" + Integer.toHexString(state));
        }
        return Strings.join(list, "|");
        //isForwardNavigation(0x00000100),
        //mode_changed(0x00000200),
    }

    public static String getProtectionLevel(int value) {
        List<String> levels = new ArrayList<>(3);
        if ((value & PermissionInfo.PROTECTION_FLAG_SYSTEM) != 0) {
            value = value ^ PermissionInfo.PROTECTION_FLAG_SYSTEM;
            levels.add("system");
        }
        if ((value & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0) {
            value = value ^ PermissionInfo.PROTECTION_FLAG_DEVELOPMENT;
            levels.add("development");
        }
        switch (value) {
            case PermissionInfo.PROTECTION_NORMAL:
                levels.add("normal");
                break;
            case PermissionInfo.PROTECTION_DANGEROUS:
                levels.add("dangerous");
                break;
            case PermissionInfo.PROTECTION_SIGNATURE:
                levels.add("signature");
                break;
            case PermissionInfo.PROTECTION_SIGNATURE_OR_SYSTEM:
                levels.add("signatureOrSystem");
                break;
            default:
                levels.add("ProtectionLevel:" + Integer.toHexString(value));
        }
        return Strings.join(levels, "|");
    }

    /**
     * get Installation string values from int
     */
    @NonNull
    public static String getInstallLocation(int value) {
        switch (value) {
            case PackageInfo.INSTALL_LOCATION_AUTO:
                return "auto";
            case PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY:
                return "internalOnly";
            case PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL:
                return "preferExternal";
            default:
                return "installLocation:" + Integer.toHexString(value);
        }
    }

}
