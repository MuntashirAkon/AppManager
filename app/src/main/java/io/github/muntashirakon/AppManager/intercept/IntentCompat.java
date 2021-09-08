// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.intercept;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.ExtraItem;
import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.TYPE_BOOLEAN;
import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.TYPE_COMPONENT_NAME;
import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.TYPE_FLOAT;
import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.TYPE_FLOAT_AL;
import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.TYPE_FLOAT_ARR;
import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.TYPE_INTEGER;
import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.TYPE_INT_AL;
import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.TYPE_INT_ARR;
import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.TYPE_LONG;
import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.TYPE_LONG_AL;
import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.TYPE_LONG_ARR;
import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.TYPE_NULL;
import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.TYPE_STRING;
import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.TYPE_STRING_AL;
import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.TYPE_STRING_ARR;
import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.TYPE_URI;
import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.Type;

public class IntentCompat {
    @Nullable
    public static Uri getDataUri(@NonNull Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            return intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        return intent.getData();
    }

    @Nullable
    public static List<Uri> getDataUris(@NonNull Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            return Collections.singletonList(intent.getParcelableExtra(Intent.EXTRA_STREAM));
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            return intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        }
        Uri data = intent.getData();
        if (data == null) return null;
        return Collections.singletonList(data);
    }

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

    @Nullable
    public static Pair<Integer, String> valueToParsableStringAndType(Object object) {
        if (object == null) {
            return new Pair<>(TYPE_NULL, null);
        } else if (object instanceof String) {
            return new Pair<>(TYPE_STRING, (String) object);
        } else if (object instanceof Integer) {
            return new Pair<>(TYPE_INTEGER, String.valueOf((int) object));
        } else if (object instanceof Long) {
            return new Pair<>(TYPE_LONG, String.valueOf((long) object));
        } else if (object instanceof Float) {
            return new Pair<>(TYPE_FLOAT, String.valueOf((float) object));
        } else if (object instanceof Boolean) {
            return new Pair<>(TYPE_BOOLEAN, String.valueOf((boolean) object));
        } else if (object instanceof Uri) {
            return new Pair<>(TYPE_URI, object.toString());
        } else if (object instanceof ComponentName) {
            return new Pair<>(TYPE_COMPONENT_NAME, ((ComponentName) object).flattenToString());
        } else if (object instanceof int[]) {
            StringBuilder sb = new StringBuilder();
            int[] list = (int[]) object;
            if (list.length >= 1) sb.append(list[0]);
            for (int i = 1; i < list.length; ++i) {
                sb.append(",").append(list[i]);
            }
            return new Pair<>(TYPE_INT_ARR, sb.toString());
        } else if (object instanceof long[]) {
            StringBuilder sb = new StringBuilder();
            long[] list = (long[]) object;
            if (list.length >= 1) sb.append(list[0]);
            for (int i = 1; i < list.length; ++i) {
                sb.append(",").append(list[i]);
            }
            return new Pair<>(TYPE_LONG_ARR, sb.toString());
        } else if (object instanceof float[]) {
            StringBuilder sb = new StringBuilder();
            float[] list = (float[]) object;
            if (list.length >= 1) sb.append(list[0]);
            for (int i = 1; i < list.length; ++i) {
                sb.append(",").append(list[i]);
            }
            return new Pair<>(TYPE_FLOAT_ARR, sb.toString());
        } else if (object instanceof String[]) {
            StringBuilder sb = new StringBuilder();
            String[] list = (String[]) object;
            if (list.length >= 1) sb.append(list[0].replace(",", "\\,"));
            for (int i = 1; i < list.length; ++i) {
                sb.append(",").append(list[i].replace(",", "\\,"));
            }
            return new Pair<>(TYPE_STRING_ARR, sb.toString());
        } else if (object instanceof List) {
            @SuppressWarnings("rawtypes")
            List list = (List) object;
            if (list.size() == 0) {
                // Type is lost forever, return null
                // FIXME: Try to infer type using reflection
                return new Pair<>(TYPE_NULL, null);
            }
            Object item = list.get(0);
            if (item instanceof Integer) {
                StringBuilder sb = new StringBuilder();
                sb.append(item);
                for (int i = 1; i < list.size(); ++i) {
                    sb.append(",").append(list.get(i));
                }
                return new Pair<>(TYPE_INT_AL, sb.toString());
            } else if (item instanceof Long) {
                StringBuilder sb = new StringBuilder();
                sb.append(item);
                for (int i = 1; i < list.size(); ++i) {
                    sb.append(",").append(list.get(i));
                }
                return new Pair<>(TYPE_LONG_AL, sb.toString());
            } else if (item instanceof Float) {
                StringBuilder sb = new StringBuilder();
                sb.append(item);
                for (int i = 1; i < list.size(); ++i) {
                    sb.append(",").append(list.get(i));
                }
                return new Pair<>(TYPE_FLOAT_AL, sb.toString());
            } else if (item instanceof String) {
                StringBuilder sb = new StringBuilder();
                sb.append(((String) item).replace(",", "\\,"));
                for (int i = 1; i < list.size(); ++i) {
                    sb.append(",").append(((String) list.get(i)).replace(",", "\\,"));
                }
                return new Pair<>(TYPE_STRING_AL, sb.toString());
            }
        }
        return null;
    }

    public static void addToIntent(@NonNull Intent intent, @NonNull ExtraItem extraItem) {
        if (extraItem.keyValue == null && extraItem.type != TYPE_NULL) {
            return;
        }
        switch (extraItem.type) {
            case TYPE_BOOLEAN:
                intent.putExtra(extraItem.keyName, (boolean) extraItem.keyValue);
                break;
            case TYPE_COMPONENT_NAME:
                intent.putExtra(extraItem.keyName, (ComponentName) extraItem.keyValue);
                break;
            case TYPE_FLOAT:
                intent.putExtra(extraItem.keyName, (float) extraItem.keyValue);
                break;
            case TYPE_FLOAT_AL:
            case TYPE_STRING_AL:
            case TYPE_LONG_AL:
            case TYPE_INT_AL:
                intent.putExtra(extraItem.keyName, (ArrayList<?>) extraItem.keyValue);
                break;
            case TYPE_FLOAT_ARR:
                intent.putExtra(extraItem.keyName, (float[]) extraItem.keyValue);
                break;
            case TYPE_INTEGER:
                intent.putExtra(extraItem.keyName, (int) extraItem.keyValue);
                break;
            case TYPE_INT_ARR:
                intent.putExtra(extraItem.keyName, (int[]) extraItem.keyValue);
                break;
            case TYPE_LONG:
                intent.putExtra(extraItem.keyName, (long) extraItem.keyValue);
                break;
            case TYPE_LONG_ARR:
                intent.putExtra(extraItem.keyName, (long[]) extraItem.keyValue);
                break;
            case TYPE_NULL:
                intent.putExtra(extraItem.keyName, (String) null);
                break;
            case TYPE_STRING:
                intent.putExtra(extraItem.keyName, (String) extraItem.keyValue);
                break;
            case TYPE_STRING_ARR:
                intent.putExtra(extraItem.keyName, (String[]) extraItem.keyValue);
                break;
            case TYPE_URI:
                intent.putExtra(extraItem.keyName, (Uri) extraItem.keyValue);
                break;
        }
    }

    @NonNull
    public static String flattenToString(@NonNull Intent intent) {
        String action = intent.getAction();
        String data = intent.getDataString();
        String type = intent.getType();
        Set<String> categories = intent.getCategories();
        ComponentName cn = intent.getComponent();
        String packageName = intent.getPackage();
        int flags = intent.getFlags();
        Bundle extras = intent.getExtras();

        StringBuilder sb = new StringBuilder("VERSION\t").append(1).append("\n");
        if (action != null) sb.append("ACTION\t").append(action).append("\n");
        if (data != null) sb.append("DATA\t").append(data).append("\n");
        if (type != null) sb.append("TYPE\t").append(type).append("\n");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String id = intent.getIdentifier();
            if (id != null) sb.append("IDENTIFIER\t").append(id).append("\n");
        }
        if (categories != null) {
            for (String category : categories) {
                sb.append("CATEGORY\t").append(category).append("\n");
            }
        }
        if (cn != null) sb.append("COMPONENT\t").append(cn.flattenToString()).append("\n");
        if (packageName != null) sb.append("PACKAGE\t").append(packageName).append("\n");
        if (flags != 0) sb.append("FLAGS\t0x").append(Integer.toHexString(flags)).append("\n");
        if (extras != null) {
            for (String key : extras.keySet()) {
                Pair<Integer, String> typeAndString = valueToParsableStringAndType(extras.get(key));
                if (typeAndString != null) {
                    sb.append("EXTRA\t").append(key).append("\t").append(typeAndString.first);
                    if (typeAndString.first != TYPE_NULL) {
                        sb.append("\t").append(typeAndString.second);
                    }
                    sb.append("\n");
                } // else unsupported bundle item, ignore
                // TODO: Add support for more items
            }
        }
        return sb.toString();
    }

    @NonNull
    public static String describeIntent(@NonNull Intent intent, String prefix) {
        String action = intent.getAction();
        String data = intent.getDataString();
        String type = intent.getType();
        Set<String> categories = intent.getCategories();
        ComponentName cn = intent.getComponent();
        String packageName = intent.getPackage();
        int flags = intent.getFlags();
        Bundle extras = intent.getExtras();

        StringBuilder sb = new StringBuilder();
        if (action != null) sb.append(prefix).append(" ACTION\t").append(action).append("\n");
        if (data != null) sb.append(prefix).append(" DATA\t").append(data).append("\n");
        if (type != null) sb.append(prefix).append(" TYPE\t").append(type).append("\n");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String id = intent.getIdentifier();
            if (id != null) sb.append(prefix).append(" IDENTIFIER\t").append(id).append("\n");
        }
        if (categories != null) {
            for (String category : categories) {
                sb.append(prefix).append(" CATEGORY\t").append(category).append("\n");
            }
        }
        if (cn != null) sb.append(prefix).append(" COMPONENT\t").append(cn.flattenToString()).append("\n");
        if (packageName != null) sb.append(prefix).append(" PACKAGE\t").append(packageName).append("\n");
        if (flags != 0) sb.append(prefix).append(" FLAGS\t0x").append(Integer.toHexString(flags)).append("\n");
        if (extras != null) {
            for (String key : extras.keySet()) {
                Pair<Integer, String> typeAndString = valueToParsableStringAndType(extras.get(key));
                if (typeAndString != null) {
                    sb.append(prefix).append(" EXTRA\t").append(key).append("\t").append(typeAndString.first);
                    if (typeAndString.first != TYPE_NULL) {
                        sb.append("\t").append(typeAndString.second);
                    }
                    sb.append("\n");
                } // else unsupported bundle item, ignore
                // TODO: Add support for more items
            }
        }
        return sb.toString();
    }

    @Nullable
    public static Intent unflattenFromString(@NonNull String intentString) {
        Intent intent = new Intent();
        String[] lines = intentString.split("\n");
        Uri data = null;
        String type = null;
        for (String line : lines) {
            if (TextUtils.isEmpty(line)) continue;
            StringTokenizer tokenizer = new StringTokenizer(line, "\t");
            if (tokenizer.countTokens() < 2) {
                // Invalid line
                return null;
            }
            switch (tokenizer.nextToken()) {
                case "VERSION": {
                    int version = Integer.decode(tokenizer.nextToken());
                    if (version != 1) {
                        // Unsupported version
                        return null;
                    }
                    break;
                }
                case "ACTION":
                    intent.setAction(tokenizer.nextToken());
                    break;
                case "DATA":
                    data = Uri.parse(tokenizer.nextToken());
                    break;
                case "TYPE":
                    type = tokenizer.nextToken();
                    break;
                case "IDENTIFIER":
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        intent.setIdentifier(tokenizer.nextToken());
                    }
                    break;
                case "CATEGORY":
                    intent.addCategory(tokenizer.nextToken());
                    break;
                case "COMPONENT":
                    intent.setComponent(ComponentName.unflattenFromString(tokenizer.nextToken()));
                    break;
                case "PACKAGE":
                    intent.setPackage(tokenizer.nextToken());
                    break;
                case "FLAGS":
                    intent.setFlags(Integer.decode(tokenizer.nextToken()));
                    break;
                case "EXTRA": {
                    ExtraItem item = new ExtraItem();
                    item.keyName = tokenizer.nextToken();
                    item.type = Integer.decode(tokenizer.nextToken());
                    item.keyValue = parseExtraValue(item.type, tokenizer.nextToken());
                    addToIntent(intent, item);
                }
            }
        }
        if (data != null) {
            intent.setDataAndType(data, type);
        } else if (type != null) {
            intent.setType(type);
        }
        return intent;
    }
}
