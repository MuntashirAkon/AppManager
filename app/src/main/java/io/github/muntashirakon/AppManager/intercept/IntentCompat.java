// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.intercept;

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
import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.TYPE_URI_AL;
import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.TYPE_URI_ARR;
import static io.github.muntashirakon.AppManager.intercept.AddIntentExtraFragment.Type;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.compat.IntegerCompat;
import io.github.muntashirakon.AppManager.fm.FmUtils;
import io.github.muntashirakon.AppManager.utils.MotorolaUtils;

public final class IntentCompat {
    /**
     * Retrieve extended data from the intent.
     *
     * @param name  The name of the desired item.
     * @param clazz The type of the object expected.
     * @return the value of an item previously added with putExtra(),
     * or null if no Parcelable value was found.
     * @see Intent#putExtra(String, Parcelable)
     */
    @Nullable
    @SuppressWarnings("deprecation")
    public static <T extends Parcelable> T getParcelableExtra(@NonNull Intent intent, @Nullable String name,
                                                              @NonNull Class<T> clazz) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // See androidx.core.content.IntentCompat.getParcelableExtra(intent, name, clazz)
            return intent.getParcelableExtra(name, clazz);
        }
        // May need to set class loader to prevent crash
        intent.setExtrasClassLoader(clazz.getClassLoader());
        T extra = intent.getParcelableExtra(name);
        return clazz.isInstance(extra) ? extra : null;
    }

    /**
     * Retrieve extended data from the intent.
     *
     * @param name  The name of the desired item.
     * @param clazz The type of the items inside the array list. This is only verified when
     *              parcelling.
     * @return the value of an item previously added with
     * putParcelableArrayListExtra(), or null if no
     * ArrayList<Parcelable> value was found.
     * @see Intent#putParcelableArrayListExtra(String, ArrayList)
     */
    @Nullable
    public static <T extends Parcelable> ArrayList<T> getParcelableArrayListExtra(@NonNull Intent intent,
                                                                                  @Nullable String name,
                                                                                  @NonNull Class<? extends T> clazz) {
        return androidx.core.content.IntentCompat.getParcelableArrayListExtra(intent, name, clazz);
    }

    @Nullable
    public static Uri getDataUri(@Nullable Intent intent) {
        if (intent == null) {
            return null;
        }
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            return FmUtils.sanitizeContentInput(getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri.class));
        }
        return FmUtils.sanitizeContentInput(intent.getData());
    }

    @Nullable
    public static List<Uri> getDataUris(@NonNull Intent intent) {
        if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            List<Uri> inputUris = getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri.class);
            if (inputUris == null) {
                return null;
            }
            List<Uri> filteredUris = new ArrayList<>(inputUris.size());
            for (Uri uri : inputUris) {
                Uri fixedUri = FmUtils.sanitizeContentInput(uri);
                if (fixedUri != null) {
                    filteredUris.add(fixedUri);
                }
            }
            return filteredUris.isEmpty() ? null : filteredUris;
        }
        Uri uri = getDataUri(intent);
        return uri != null ? Collections.singletonList(uri) : null;
    }

    public static void removeFlags(@NonNull Intent intent, int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.removeFlags(flags);
        } else {
            intent.setFlags(intent.getFlags() & ~flags);
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
                return IntegerCompat.decode(rawValue);
            case TYPE_URI:
                return Uri.parse(rawValue);
            case TYPE_URI_ARR: {
                // Split on commas unless they are preceded by an escape.
                // The escape character must be escaped for the string and
                // again for the regex, thus four escape characters become one.
                String[] strings = rawValue.split("(?<!\\\\),");
                Uri[] list = new Uri[strings.length];
                for (int i = 0; i < list.length; ++i) {
                    list[i] = Uri.parse(strings[i]);
                }
                return list;
            }
            case TYPE_URI_AL: {
                // Split on commas unless they are preceded by an escape.
                // The escape character must be escaped for the string and
                // again for the regex, thus four escape characters become one.
                String[] strings = rawValue.split("(?<!\\\\),");
                List<Uri> list = new ArrayList<>(strings.length);
                for (String s : strings) {
                    list.add(Uri.parse(s));
                }
                return list;
            }
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
                    list[i] = IntegerCompat.decode(strings[i].trim());
                }
                return list;
            }
            case TYPE_INT_AL: {
                String[] strings = rawValue.split(",");
                ArrayList<Integer> list = new ArrayList<>(strings.length);
                for (String string : strings) {
                    list.add(IntegerCompat.decode(string.trim()));
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
                        boolValue = IntegerCompat.decode(rawValue) != 0;
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
    private static Pair<Integer, String> valueToParsableStringAndType(@Nullable Object object) {
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
        } else if (object instanceof Uri[]) {
            StringBuilder sb = new StringBuilder();
            Uri[] list = (Uri[]) object;
            if (list.length >= 1) sb.append(list[0].toString().replace(",", "\\,"));
            for (int i = 1; i < list.length; ++i) {
                sb.append(",").append(list[i].toString().replace(",", "\\,"));
            }
            return new Pair<>(TYPE_URI_ARR, sb.toString());
        } else if (object instanceof List) {
            @SuppressWarnings("rawtypes")
            List list = (List) object;
            if (list.isEmpty()) {
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
            } else if (item instanceof Uri) {
                StringBuilder sb = new StringBuilder();
                sb.append(item.toString().replace(",", "\\,"));
                for (int i = 1; i < list.size(); ++i) {
                    sb.append(",").append(list.get(i).toString().replace(",", "\\,"));
                }
                return new Pair<>(TYPE_URI_AL, sb.toString());
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
            case TYPE_FLOAT:
                intent.putExtra(extraItem.keyName, (float) extraItem.keyValue);
                break;
            case TYPE_FLOAT_AL:
            case TYPE_STRING_AL:
            case TYPE_LONG_AL:
            case TYPE_INT_AL:
            case TYPE_URI_AL:
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
            case TYPE_COMPONENT_NAME:
            case TYPE_URI:
                intent.putExtra(extraItem.keyName, (Parcelable) extraItem.keyValue);
                break;
            case TYPE_URI_ARR:
                intent.putExtra(extraItem.keyName, (Parcelable[]) extraItem.keyValue);
                break;
        }
    }

    @NonNull
    public static List<String> flattenToCommand(@NonNull Intent intent) {
        List<String> args = new ArrayList<>();
        String action = intent.getAction();
        String data = intent.getDataString();
        String type = intent.getType();
        Set<String> categories = intent.getCategories();
        ComponentName cn = intent.getComponent();
        String packageName = intent.getPackage();
        int flags = intent.getFlags();
        Bundle extras = intent.getExtras();

        if (action != null) {
            args.add("-a");
            args.add(action);
        }
        if (data != null) {
            args.add("-d");
            args.add(data);
        }
        if (type != null) {
            args.add("-t");
            args.add(type);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String id = intent.getIdentifier();
            if (id != null) {
                args.add("-i");
                args.add(id);
            }
        }
        if (categories != null) {
            for (String category : categories) {
                args.add("-c");
                args.add(category);
            }
        }
        if (cn != null) {
            args.add("-n");
            args.add(cn.flattenToString());
        }
        if (extras != null) {
            for (String key : extras.keySet()) {
                Pair<Integer, String> typeAndString = valueToParsableStringAndType(extras.get(key));
                if (typeAndString == null) {
                    // else unsupported bundle item, ignore
                    continue;
                }
                switch (typeAndString.first) {
                    case TYPE_STRING:
                        args.add("--es");
                        break;
                    case TYPE_NULL:
                        args.add("--esn");
                        break;
                    case TYPE_BOOLEAN:
                        args.add("--ez");
                        break;
                    case TYPE_INTEGER:
                        args.add("--ei");
                        break;
                    case TYPE_LONG:
                        args.add("--el");
                        break;
                    case TYPE_FLOAT:
                        args.add("--ef");
                        break;
                    case TYPE_URI:
                        args.add("--eu");
                        break;
                    case TYPE_COMPONENT_NAME:
                        args.add("--ecn");
                        break;
                    case TYPE_INT_ARR:
                        args.add("--eia");
                        break;
                    case TYPE_INT_AL:
                        args.add("--eial");
                        break;
                    case TYPE_LONG_ARR:
                        args.add("--ela");
                        break;
                    case TYPE_LONG_AL:
                        args.add("--elal");
                        break;
                    case TYPE_FLOAT_ARR:
                        args.add("--efa");
                        break;
                    case TYPE_FLOAT_AL:
                        args.add("--efal");
                        break;
                    case TYPE_STRING_ARR:
                        args.add("--esa");
                        break;
                    case TYPE_STRING_AL:
                        args.add("--esal");
                        break;
                    default:
                        // Unsupported
                        continue;
                }
                // Add key
                args.add(key);
                if (typeAndString.first != TYPE_NULL) {
                    // All except NULL has a value
                    args.add(typeAndString.second);
                }
            }
        }
        args.add("-f");
        args.add(String.valueOf(flags));
        if (packageName != null) {
            args.add(packageName);
        }
        return args;
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
                    int version = IntegerCompat.decode(tokenizer.nextToken());
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
                    intent.setFlags(IntegerCompat.decode(tokenizer.nextToken()));
                    break;
                case "EXTRA": {
                    ExtraItem item = new ExtraItem();
                    item.keyName = tokenizer.nextToken();
                    item.type = IntegerCompat.decode(tokenizer.nextToken());
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

    /**
     * Convert this Intent into a String holding a URI representation of it.
     * The returned URI string has been properly URI encoded, so it can be
     * used with {@link Uri#parse Uri.parse(String)}.  The URI contains the
     * Intent's data as the base URI, with an additional fragment describing
     * the action, categories, type, flags, package, component, and extras.
     *
     * <p>You can convert the returned string back to an Intent with
     * {@link Intent#getIntent(String)}.
     *
     * @param flags Additional operating flags.
     * @return Returns a URI encoding URI string describing the entire contents
     * of the Intent.
     * @see Intent#toUri(int)
     */
    @NonNull
    public static String toUri(@NonNull Intent intent, int flags) {
        if (!MotorolaUtils.isMotorola() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return intent.toUri(flags);
        }
        // Special case for Motorola (A11+)
        StringBuilder uri = new StringBuilder(128);
        if ((flags & Intent.URI_ANDROID_APP_SCHEME) != 0) {
            if (intent.getPackage() == null) {
                throw new IllegalArgumentException(
                        "Intent must include an explicit package name to build an android-app: " + intent);
            }
            uri.append("android-app://");
            uri.append(intent.getPackage());
            String scheme = null;
            Uri data = intent.getData();
            if (data != null) {
                scheme = data.getScheme();
                if (scheme != null) {
                    uri.append('/');
                    uri.append(scheme);
                    String authority = data.getEncodedAuthority();
                    if (authority != null) {
                        uri.append('/');
                        uri.append(authority);
                        String path = data.getEncodedPath();
                        if (path != null) {
                            uri.append(path);
                        }
                        String queryParams = data.getEncodedQuery();
                        if (queryParams != null) {
                            uri.append('?');
                            uri.append(queryParams);
                        }
                        String fragment = data.getEncodedFragment();
                        if (fragment != null) {
                            uri.append('#');
                            uri.append(fragment);
                        }
                    }
                }
            }
            toUriFragment(intent, uri, null, scheme == null ? Intent.ACTION_MAIN : Intent.ACTION_VIEW,
                    intent.getPackage(), flags);
            return uri.toString();
        }
        String scheme = null;
        Uri dataUri = intent.getData();
        if (dataUri != null) {
            String data = dataUri.toString();
            if ((flags & Intent.URI_INTENT_SCHEME) != 0) {
                final int N = data.length();
                for (int i = 0; i < N; i++) {
                    char c = data.charAt(i);
                    if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                            || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '+') {
                        continue;
                    }
                    if (c == ':' && i > 0) {
                        // Valid scheme.
                        scheme = data.substring(0, i);
                        uri.append("intent:");
                        data = data.substring(i + 1);
                        break;
                    }

                    // No scheme.
                    break;
                }
            }
            uri.append(data);

        } else if ((flags & Intent.URI_INTENT_SCHEME) != 0) {
            uri.append("intent:");
        }

        toUriFragment(intent, uri, scheme, Intent.ACTION_VIEW, null, flags);

        return uri.toString();
    }

    private static void toUriFragment(Intent intent, StringBuilder uri, @Nullable String scheme, String defAction,
                                      @Nullable String defPackage, int flags) {
        StringBuilder frag = new StringBuilder(128);

        toUriInner(intent, frag, scheme, defAction, defPackage, flags);
        if (intent.getSelector() != null) {
            frag.append("SEL;");
            // Note that for now we are not going to try to handle the
            // data part; not clear how to represent this as a URI, and
            // not much utility in it.
            Uri data = intent.getSelector().getData();
            toUriInner(intent.getSelector(), frag, data != null ? data.getScheme() : null, null, null, flags);
        }

        if (frag.length() > 0) {
            uri.append("#Intent;");
            uri.append(frag);
            uri.append("end");
        }
    }

    private static void toUriInner(Intent intent, StringBuilder uri, @Nullable String scheme,
                                   @Nullable String defAction, @Nullable String defPackage,
                                   int flags) {
        if (scheme != null) {
            uri.append("scheme=").append(scheme).append(';');
        }
        if (intent.getAction() != null && !intent.getAction().equals(defAction)) {
            uri.append("action=").append(Uri.encode(intent.getAction())).append(';');
        }
        if (intent.getCategories() != null) {
            for (String category : intent.getCategories()) {
                uri.append("category=").append(Uri.encode(category)).append(';');
            }
        }
        if (intent.getType() != null) {
            uri.append("type=").append(Uri.encode(intent.getType(), "/")).append(';');
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && intent.getIdentifier() != null) {
            uri.append("identifier=").append(Uri.encode(intent.getIdentifier(), "/")).append(';');
        }
        if (intent.getFlags() != 0) {
            uri.append("launchFlags=").append(IntegerCompat.toSignedHex(intent.getFlags())).append(';');
        }
        if (intent.getPackage() != null && !intent.getPackage().equals(defPackage)) {
            uri.append("package=").append(Uri.encode(intent.getPackage())).append(';');
        }
        if (intent.getComponent() != null) {
            uri.append("component=")
                    .append(Uri.encode(intent.getComponent().flattenToShortString(), "/"))
                    .append(';');
        }
        if (intent.getSourceBounds() != null) {
            uri.append("sourceBounds=")
                    .append(Uri.encode(intent.getSourceBounds().flattenToString()))
                    .append(';');
        }
        if (intent.getExtras() != null) {
            for (String key : intent.getExtras().keySet()) {
                final Object value = intent.getExtras().get(key);
                char entryType =
                        value instanceof String ? 'S' :
                                value instanceof Boolean ? 'B' :
                                        value instanceof Byte ? 'b' :
                                                value instanceof Character ? 'c' :
                                                        value instanceof Double ? 'd' :
                                                                value instanceof Float ? 'f' :
                                                                        value instanceof Integer ? 'i' :
                                                                                value instanceof Long ? 'l' :
                                                                                        value instanceof Short ? 's' :
                                                                                                '\0';

                if (entryType != '\0') {
                    uri.append(entryType);
                    uri.append('.');
                    uri.append(Uri.encode(key));
                    uri.append('=');
                    uri.append(Uri.encode(value.toString()));
                    uri.append(';');
                }
            }
        }
    }
}
