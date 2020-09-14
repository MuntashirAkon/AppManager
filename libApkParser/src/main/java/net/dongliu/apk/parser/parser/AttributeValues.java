package net.dongliu.apk.parser.parser;

import net.dongliu.apk.parser.utils.Strings;

import java.util.ArrayList;
import java.util.List;

/**
 * attribute value constant
 *
 * @author Liu Dong
 */
public class AttributeValues {


    // Activity constants begin. see:
    // http://developer.android.com/reference/android/content/pm/ActivityInfo.html
    // http://developer.android.com/guide/topics/manifest/activity-element.html
    public static String getScreenOrientation(int value) {
        switch (value) {
            case 0x00000003:
                return "behind";
            case 0x0000000a:
                return "fullSensor";
            case 0x0000000d:
                return "fullUser";
            case 0x00000000:
                return "landscape";
            case 0x0000000e:
                return "locked";
            case 0x00000005:
                return "nosensor";
            case 0x00000001:
                return "portrait";
            case 0x00000008:
                return "reverseLandscape";
            case 0x00000009:
                return "reversePortrait";
            case 0x00000004:
                return "sensor";
            case 0x00000006:
                return "sensorLandscape";
            case 0x00000007:
                return "sensorPortrait";
            case 0xffffffff:
                return "unspecified";
            case 0x00000002:
                return "user";
            case 0x0000000b:
                return "userLandscape";
            case 0x0000000c:
                return "userPortrait";
            default:
                return "ScreenOrientation:" + Integer.toHexString(value);
        }
    }

    public static String getLaunchMode(int value) {
        switch (value) {
            case 0x00000000:
                return "standard";
            case 0x00000001:
                return "singleTop";
            case 0x00000002:
                return "singleTask";
            case 0x00000003:
                return "singleInstance";
            default:
                return "LaunchMode:" + Integer.toHexString(value);
        }

    }

    public static String getConfigChanges(int value) {
        List<String> list = new ArrayList<>();
        if ((value & 0x00001000) != 0) {
            list.add("density");
        } else if ((value & 0x40000000) != 0) {
            list.add("fontScale");
        } else if ((value & 0x00000010) != 0) {
            list.add("keyboard");
        } else if ((value & 0x00000020) != 0) {
            list.add("keyboardHidden");
        } else if ((value & 0x00002000) != 0) {
            list.add("direction");
        } else if ((value & 0x00000004) != 0) {
            list.add("locale");
        } else if ((value & 0x00000001) != 0) {
            list.add("mcc");
        } else if ((value & 0x00000002) != 0) {
            list.add("mnc");
        } else if ((value & 0x00000040) != 0) {
            list.add("navigation");
        } else if ((value & 0x00000080) != 0) {
            list.add("orientation");
        } else if ((value & 0x00000100) != 0) {
            list.add("screenLayout");
        } else if ((value & 0x00000400) != 0) {
            list.add("screenSize");
        } else if ((value & 0x00000800) != 0) {
            list.add("smallestScreenSize");
        } else if ((value & 0x00000008) != 0) {
            list.add("touchscreen");
        } else if ((value & 0x00000200) != 0) {
            list.add("uiMode");
        }
        return Strings.join(list, "|");
    }

    public static String getWindowSoftInputMode(int value) {
        int adjust = value & 0x000000f0;
        int state = value & 0x0000000f;
        List<String> list = new ArrayList<>(2);
        switch (adjust) {
            case 0x00000030:
                list.add("adjustNothing");
                break;
            case 0x00000020:
                list.add("adjustPan");
                break;
            case 0x00000010:
                list.add("adjustResize");
                break;
            case 0x00000000:
                //levels.add("adjustUnspecified");
                break;
            default:
                list.add("WindowInputModeAdjust:" + Integer.toHexString(adjust));
        }
        switch (state) {
            case 0x00000003:
                list.add("stateAlwaysHidden");
                break;
            case 0x00000005:
                list.add("stateAlwaysVisible");
                break;
            case 0x00000002:
                list.add("stateHidden");
                break;
            case 0x00000001:
                list.add("stateUnchanged");
                break;
            case 0x00000004:
                list.add("stateVisible");
                break;
            case 0x00000000:
                //levels.add("stateUnspecified");
                break;
            default:
                list.add("WindowInputModeState:" + Integer.toHexString(state));
        }
        return Strings.join(list, "|");
        //isForwardNavigation(0x00000100),
        //mode_changed(0x00000200),
    }

    //http://developer.android.com/reference/android/content/pm/PermissionInfo.html
    public static String getProtectionLevel(int value) {
        List<String> levels = new ArrayList<>(3);
        if ((value & 0x10) != 0) {
            value = value ^ 0x10;
            levels.add("system");
        }
        if ((value & 0x20) != 0) {
            value = value ^ 0x20;
            levels.add("development");
        }
        switch (value) {
            case 0:
                levels.add("normal");
                break;
            case 1:
                levels.add("dangerous");
                break;
            case 2:
                levels.add("signature");
                break;
            case 3:
                levels.add("signatureOrSystem");
                break;
            default:
                levels.add("ProtectionLevel:" + Integer.toHexString(value));
        }
        return Strings.join(levels, "|");
    }


    // Activity constants end

    /**
     * get Installation string values from int
     */
    public static String getInstallLocation(int value) {
        switch (value) {
            case 0:
                return "auto";
            case 1:
                return "internalOnly";
            case 2:
                return "preferExternal";
            default:
                return "installLocation:" + Integer.toHexString(value);
        }
    }

}
