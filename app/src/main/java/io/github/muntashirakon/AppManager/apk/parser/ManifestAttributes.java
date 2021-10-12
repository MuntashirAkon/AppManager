// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PermissionInfo;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

// Sorted based on frameworks/base/core/res/res/values/attrs_manifest.xml
// and then frameworks/base/core/res/res/values/attrs.xml
public class ManifestAttributes {
    // TODO: 12/10/21 Add all from upstream
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
                levels.add(Integer.toHexString(value));
        }
        return TextUtils.join("|", levels);
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
            case 4:
                return "singleInstancePerTask";
            default:
                return Integer.toHexString(value);
        }
    }

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
                return Integer.toHexString(value);
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
            list.add("layoutDirection");
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
        } else if ((value & 0x10000000) != 0) {
            list.add("fontWeightAdjustment");
        }
        return TextUtils.join("|", list);
    }

    public static String getReqTouchScreen(int value) {
        switch (value) {
            case Configuration.TOUCHSCREEN_NOTOUCH:
                return "notouch";
            case 2:  // Configuration.TOUCHSCREEN_STYLUS
                return "stylus";
            case Configuration.TOUCHSCREEN_FINGER:
                return "finger";
            case Configuration.TOUCHSCREEN_UNDEFINED:
            default:
                return "undefined";
        }
    }

    public static String getReqKeyboardType(int value) {
        switch (value) {
            case Configuration.KEYBOARD_NOKEYS:
                return "nokeys";
            case Configuration.KEYBOARD_QWERTY:
                return "qwerty";
            case Configuration.KEYBOARD_12KEY:
                return "twelvekey";
            case Configuration.KEYBOARD_UNDEFINED:
            default:
                return "undefined";
        }
    }

    public static String getReqNavigation(int value) {
        switch (value) {
            case Configuration.NAVIGATION_NONAV:
                return "nonav";
            case Configuration.NAVIGATION_DPAD:
                return "dpad";
            case Configuration.NAVIGATION_TRACKBALL:
                return "trackball";
            case Configuration.NAVIGATION_WHEEL:
                return "wheel";
            case Configuration.NAVIGATION_UNDEFINED:
            default:
                return "undefined";
        }
    }

    @NonNull
    public static String getInstallLocation(int value) {
        switch (value) {
            default:
            case PackageInfo.INSTALL_LOCATION_AUTO:
                return "auto";
            case PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY:
                return "internalOnly";
            case PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL:
                return "preferExternal";
        }
    }

    public static String getUiOptions(int value) {
        if ((value & ActivityInfo.UIOPTION_SPLIT_ACTION_BAR_WHEN_NARROW) != 0) {
            return "splitActionBarWhenNarrow";
        }
        return "none";
    }

    public static String getPersistableMode(int value) {
        switch (value) {
            default:
            case ActivityInfo.PERSIST_ROOT_ONLY:
                return "persistRootOnly";
            case ActivityInfo.PERSIST_NEVER:
                return "persistNever";
            case ActivityInfo.PERSIST_ACROSS_REBOOTS:
                return "persistAcrossReboots";
        }
    }

    public static String getDocumentLaunchMode(int value) {
        switch (value) {
            default:
            case ActivityInfo.DOCUMENT_LAUNCH_NONE:
                return "none";
            case ActivityInfo.DOCUMENT_LAUNCH_INTO_EXISTING:
                return "intoExisting";
            case ActivityInfo.DOCUMENT_LAUNCH_ALWAYS:
                return "always";
            case ActivityInfo.DOCUMENT_LAUNCH_NEVER:
                return "never";
        }
    }

    public static String getLockTaskMode(int value) {
        switch (value) {
            default:
            case 0:
                return "normal";
            case 1:
                return "never";
            case 2:
                return "always";
            case 3:
                return "if_whitelisted";
        }
    }

    public static String getForegroundServiceType(int value) {
        List<String> list = new ArrayList<>(1);
        if ((value & 0x01) != 0) list.add("dataSync");
        if ((value & 0x02) != 0) list.add("mediaPlayback");
        if ((value & 0x04) != 0) list.add("phoneCall");
        if ((value & 0x08) != 0) list.add("location");
        if ((value & 0x10) != 0) list.add("connectedDevice");
        if ((value & 0x20) != 0) list.add("mediaProjection");
        if ((value & 0x40) != 0) list.add("camera");
        if ((value & 0x80) != 0) list.add("microphone");
        return TextUtils.join("|", list);
    }

    public static String getGwpAsanMode(int value) {
        switch (value) {
            default:
            case ApplicationInfo.GWP_ASAN_DEFAULT:
                return "default";
            case ApplicationInfo.GWP_ASAN_NEVER:
                return "never";
            case ApplicationInfo.GWP_ASAN_ALWAYS:
                return "always";
        }
    }

    public static String getMemtagMode(int value) {
        switch (value) {
            default:
            case -1:
                return "default";
            case 0:
                return "off";
            case 1:
                return "async";
            case 2:
                return "sync";
        }
    }

    public static String getAppCategory(int value) {
        switch (value) {
            case ApplicationInfo.CATEGORY_GAME:
                return "game";
            case ApplicationInfo.CATEGORY_AUDIO:
                return "audio";
            case ApplicationInfo.CATEGORY_VIDEO:
                return "video";
            case ApplicationInfo.CATEGORY_IMAGE:
                return "image";
            case ApplicationInfo.CATEGORY_SOCIAL:
                return "social";
            case ApplicationInfo.CATEGORY_NEWS:
                return "news";
            case ApplicationInfo.CATEGORY_MAPS:
                return "maps";
            case ApplicationInfo.CATEGORY_PRODUCTIVITY:
                return "productivity";
            case 8:
                return "accessibility";
            default:
                return Integer.toHexString(value);
        }
    }

    public static String getAutoRevokePermissions(int value) {
        switch (value) {
            case 0:
                return "allowed";
            case 1:
                return "discouraged";
            case 2:
                return "disallowed";
            default:
                return Integer.toHexString(value);
        }
    }

    public static String getRollbackDataPolicy(int value) {
        switch (value) {
            case 0:
                return "restore";
            case 1:
                return "wipe";
            case 2:
                return "retain";
            default:
                return Integer.toHexString(value);
        }
    }

    public static String getUsesPermissionFlags(int value) {
        if ((value & 0x00010000) != 0) {
            return "neverForLocation";
        }
        return Integer.toHexString(value);
    }

    public static String getColorMode(int value) {
        switch (value) {
            default:
            case ActivityInfo.COLOR_MODE_DEFAULT:
                return "default";
            case ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT:
                return "wideColorGamut";
            case ActivityInfo.COLOR_MODE_HDR:
                return "hdr";
        }
    }

    public static String getScreenSize(int value) {
        switch (value) {
            case 200:
                return "small";
            case 300:
                return "normal";
            case 400:
                return "large";
            case 500:
                return "xlarge";
            default:
                return Integer.toString(value);
        }
    }

    public static String getScreenDensity(int value) {
        switch (value) {
            case 120:
                return "ldpi";
            case 160:
                return "mdpi";
            case 240:
                return "hdpi";
            case 320:
                return "xhdpi";
            case 480:
                return "xxhdpi";
            case 640:
                return "xxxhdpi";
            default:
                return Integer.toString(value);
        }
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
                list.add("adjust:" + Integer.toHexString(adjust));
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
                list.add("state:" + Integer.toHexString(state));
        }
        return TextUtils.join("|", list);
    }
}
