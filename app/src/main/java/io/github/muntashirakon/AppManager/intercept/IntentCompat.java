// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.intercept;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;

import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.*;

public class IntentCompat {
    public static void removeFlags(@NonNull Intent intent, int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.removeFlags(flags);
        } else {
            int _flags = intent.getFlags();
            _flags &= ~flags;
            intent.setFlags(_flags);
        }
    }

    @Nullable
    public static Object parseExtraValue(@Type int type, String rawValue) {
        switch (type) {
            case TYPE_STRING:
                return rawValue;
            case TYPE_NULL:
                return null;
            case TYPE_INTEGER:
                return Integer.decode(rawValue);
            case TYPE_URI:
                return Uri.parse(rawValue);
            case TYPE_COMPONENT_NAME:
                ComponentName cn = ComponentName.unflattenFromString(rawValue);
                if (cn == null) {
                    throw new IllegalArgumentException("Bad component name: " + rawValue);
                }
                return cn;
            case TYPE_INT_ARR: {
                String[] strings = rawValue.split(",");
                int[] list = new int[strings.length];
                for (int i = 0; i < strings.length; i++) {
                    list[i] = Integer.decode(strings[i].trim());
                }
                return list;
            }
            case TYPE_INT_AL: {
                String[] strings = rawValue.split(",");
                ArrayList<Integer> list = new ArrayList<>(strings.length);
                for (String string : strings) {
                    list.add(Integer.decode(string.trim()));
                }
                return list;
            }
            case TYPE_LONG:
                return Long.parseLong(rawValue);
            case TYPE_LONG_ARR: {
                String[] strings = rawValue.split(",");
                long[] list = new long[strings.length];
                for (int i = 0; i < strings.length; i++) {
                    list[i] = Long.decode(strings[i].trim());
                }
                return list;
            }
            case TYPE_LONG_AL: {
                String[] strings = rawValue.split(",");
                ArrayList<Long> list = new ArrayList<>(strings.length);
                for (String string : strings) {
                    list.add(Long.decode(string.trim()));
                }
                return list;
            }
            case TYPE_FLOAT:
                return Float.parseFloat(rawValue);
            case TYPE_FLOAT_ARR: {
                String[] strings = rawValue.split(",");
                float[] list = new float[strings.length];
                for (int i = 0; i < strings.length; i++) {
                    list[i] = Float.parseFloat(strings[i]);
                }
                return list;
            }
            case TYPE_FLOAT_AL: {
                String[] strings = rawValue.split(",");
                ArrayList<Float> list = new ArrayList<>(strings.length);
                for (String string : strings) {
                    list.add(Float.parseFloat(string));
                }
                return list;
            }
            case TYPE_STRING_ARR:
                // Split on commas unless they are preceded by an escape.
                // The escape character must be escaped for the string and
                // again for the regex, thus four escape characters become one.
                return rawValue.split("(?<!\\\\),");
            case TYPE_STRING_AL: {
                // Split on commas unless they are preceded by an escape.
                // The escape character must be escaped for the string and
                // again for the regex, thus four escape characters become one.
                String[] strings = rawValue.split("(?<!\\\\),");
                ArrayList<String> list = new ArrayList<>(strings.length);
                Collections.addAll(list, strings);
                return list;
            }
            case TYPE_BOOLEAN: {
                // Boolean.valueOf() results in false for anything that is not "true", which is
                // error-prone in shell commands
                boolean boolValue;
                if ("true".equals(rawValue) || "t".equals(rawValue)) {
                    boolValue = true;
                } else if ("false".equals(rawValue) || "f".equals(rawValue)) {
                    boolValue = false;
                } else {
                    try {
                        boolValue = Integer.decode(rawValue) != 0;
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException("Invalid boolean value: " + rawValue);
                    }
                }
                return boolValue;
            }
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    public static void addToIntent(@NonNull Intent intent, @NonNull ExtraItem extraItem) {
        if (extraItem.keyValue == null && extraItem.type != TYPE_NULL) {
            return;
        }
        switch (extraItem.type) {
            case AddIntentExtraFragment.TYPE_BOOLEAN:
                intent.putExtra(extraItem.keyName, (boolean) extraItem.keyValue);
                break;
            case AddIntentExtraFragment.TYPE_COMPONENT_NAME:
                intent.putExtra(extraItem.keyName, (ComponentName) extraItem.keyValue);
                break;
            case AddIntentExtraFragment.TYPE_FLOAT:
                intent.putExtra(extraItem.keyName, (float) extraItem.keyValue);
                break;
            case AddIntentExtraFragment.TYPE_FLOAT_AL:
            case AddIntentExtraFragment.TYPE_STRING_AL:
            case AddIntentExtraFragment.TYPE_LONG_AL:
            case AddIntentExtraFragment.TYPE_INT_AL:
                intent.putExtra(extraItem.keyName, (ArrayList<?>) extraItem.keyValue);
                break;
            case AddIntentExtraFragment.TYPE_FLOAT_ARR:
                intent.putExtra(extraItem.keyName, (float[]) extraItem.keyValue);
                break;
            case AddIntentExtraFragment.TYPE_INTEGER:
                intent.putExtra(extraItem.keyName, (int) extraItem.keyValue);
                break;
            case AddIntentExtraFragment.TYPE_INT_ARR:
                intent.putExtra(extraItem.keyName, (int[]) extraItem.keyValue);
                break;
            case AddIntentExtraFragment.TYPE_LONG:
                intent.putExtra(extraItem.keyName, (long) extraItem.keyValue);
                break;
            case AddIntentExtraFragment.TYPE_LONG_ARR:
                intent.putExtra(extraItem.keyName, (long[]) extraItem.keyValue);
                break;
            case AddIntentExtraFragment.TYPE_NULL:
                intent.putExtra(extraItem.keyName, (String) null);
                break;
            case AddIntentExtraFragment.TYPE_STRING:
                intent.putExtra(extraItem.keyName, (String) extraItem.keyValue);
                break;
            case AddIntentExtraFragment.TYPE_STRING_ARR:
                intent.putExtra(extraItem.keyName, (String[]) extraItem.keyValue);
                break;
            case AddIntentExtraFragment.TYPE_URI:
                intent.putExtra(extraItem.keyName, (Uri) extraItem.keyValue);
                break;
        }
    }
}
