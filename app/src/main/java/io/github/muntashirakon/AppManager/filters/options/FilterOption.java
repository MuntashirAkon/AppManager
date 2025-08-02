// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.pm.ComponentInfo;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.util.LocalizedString;

public abstract class FilterOption implements LocalizedString, Parcelable {
    public static final int TYPE_NONE = 0;
    public static final int TYPE_STR_SINGLE = 1;
    public static final int TYPE_STR_MULTIPLE = 2;
    public static final int TYPE_INT = 3;
    public static final int TYPE_LONG = 4;
    public static final int TYPE_REGEX = 5;
    public static final int TYPE_INT_FLAGS = 6;
    public static final int TYPE_TIME_MILLIS = 7;
    public static final int TYPE_DURATION_MILLIS = 8;
    public static final int TYPE_SIZE_BYTES = 9;

    @IntDef(value = {
            TYPE_NONE,
            TYPE_STR_SINGLE,
            TYPE_STR_MULTIPLE,
            TYPE_INT,
            TYPE_LONG,
            TYPE_REGEX,
            TYPE_INT_FLAGS,
            TYPE_TIME_MILLIS,
            TYPE_DURATION_MILLIS,
            TYPE_SIZE_BYTES,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface KeyType {
    }

    public static final String KEY_ALL = "all";

    /**
     * Option type (e.g., target_sdk, last_update)
     */
    public final String type;

    public int id;
    /**
     * A key under the option (e.g., for target_sdk: eq, le, ge, all; for last_update: before, after, all)
     */
    @NonNull
    protected String key;
    /**
     * Type of the key (e.g., for target_sdk: eq, le, ge => int, all => none, for last_update: before, after => date, all => none)
     */
    @KeyType
    protected int keyType;
    /**
     * Value for the key if keyType is anything but TYPE_NONE
     */
    @Nullable
    protected String value;

    protected int intValue;
    protected long longValue;
    protected Pattern regexValue;
    protected String[] stringValues;

    public FilterOption(String type) {
        this.type = type;
        this.key = KEY_ALL;
        this.keyType = TYPE_NONE;
    }

    public String getFullId() {
        return type + "_" + id;
    }

    @NonNull
    public String getKey() {
        return key;
    }

    @KeyType
    public int getKeyType() {
        return keyType;
    }

    @Nullable
    public String getValue() {
        return value;
    }

    @CallSuper
    public void setKeyValue(@NonNull String key, @Nullable String value) {
        Integer keyType = getKeysWithType().get(key);
        if (keyType == null) {
            throw new IllegalArgumentException("Invalid key: " + key + " for type: " + type);
        }
        this.key = key;
        this.keyType = keyType;
        if (keyType != TYPE_NONE) {
            this.value = Objects.requireNonNull(value);
            switch (keyType) {
                case TYPE_INT:
                case TYPE_INT_FLAGS:
                    this.intValue = Integer.parseInt(value);
                    break;
                case TYPE_LONG:
                case TYPE_TIME_MILLIS:
                case TYPE_DURATION_MILLIS:
                case TYPE_SIZE_BYTES:
                    this.longValue = Long.parseLong(value);
                    break;
                case TYPE_REGEX:
                    this.regexValue = Pattern.compile(Pattern.quote(value));
                case TYPE_STR_MULTIPLE:
                    this.stringValues = value.split("\\n");
            }
        }
    }

    @NonNull
    public abstract Map<String, Integer> getKeysWithType();

    public Map<Integer, CharSequence> getFlags(String key) {
        throw new UnsupportedOperationException("Flags must be returned by the corresponding subclasses. key: " + key);
    }

    @NonNull
    public abstract TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result);

    @NonNull
    @Override
    public String toString() {
        return "FilterOption{" +
                "type='" + type + '\'' +
                ", id=" + id +
                ", key='" + key + '\'' +
                ", keyType=" + keyType +
                ", value='" + value + '\'' +
                '}';
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(type);
        dest.writeInt(id);
        dest.writeString(key);
        dest.writeString(value);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<FilterOption> CREATOR = new Creator<FilterOption>() {
        @Override
        @NonNull
        public FilterOption createFromParcel(@NonNull Parcel in) {
            String type = Objects.requireNonNull(in.readString());
            FilterOption filterOption = FilterOptions.create(type);
            filterOption.id = in.readInt();
            String key = Objects.requireNonNull(in.readString());
            String value = in.readString();
            filterOption.setKeyValue(key, value);
            return filterOption;
        }

        @Override
        @NonNull
        public FilterOption[] newArray(int size) {
            return new FilterOption[size];
        }
    };

    @Nullable
    public JSONObject toJson() throws JSONException {
        if (value == null) {
            return null;
        }
        JSONObject object = new JSONObject();
        object.put("type", type);
        object.put("id", id);
        object.put("key", key);
        object.put("key_type", keyType);
        if (value != null) {
            object.put("value", value);
        }
        return object;
    }

    @NonNull
    public static FilterOption fromJson(@NonNull JSONObject object) throws JSONException {
        FilterOption option = FilterOptions.create(object.getString("type"));
        option.id = object.getInt("id");
        // int keyType = object.getInt("key_type");
        String key = object.getString("key");
        String value = JSONUtils.optString(object, "value");
        option.setKeyValue(key, value);
        return option;
    }

    protected String flagsToString(String key, int flags) {
        List<CharSequence> result = new ArrayList<>();
        for (Map.Entry<Integer, CharSequence> entry : getFlags(key).entrySet()) {
            int flag = entry.getKey();
            if ((flags & flag) != 0) {
                result.add(entry.getValue());
            }
        }
        return String.join(", ", result);
    }


    public static class TestResult {
        private boolean mMatched = true;
        private List<Backup> mMatchedBackups;
        private Map<ComponentInfo, Integer> mMatchedComponents;
        private Map<ComponentInfo, Integer> mMatchedTrackers;
        private List<String> mMatchedPermissions;
        private List<String> mMatchedSubjectLines;

        public TestResult setMatched(boolean matched) {
            mMatched = matched;
            return this;
        }

        public boolean isMatched() {
            return mMatched;
        }

        public TestResult setMatchedBackups(List<Backup> matchedBackups) {
            mMatchedBackups = matchedBackups;
            return this;
        }

        @Nullable
        public List<Backup> getMatchedBackups() {
            return mMatchedBackups;
        }

        public TestResult setMatchedComponents(Map<ComponentInfo, Integer> matchedComponents) {
            mMatchedComponents = matchedComponents;
            return this;
        }

        @Nullable
        public Map<ComponentInfo, Integer> getMatchedComponents() {
            return mMatchedComponents;
        }

        public TestResult setMatchedTrackers(Map<ComponentInfo, Integer> matchedTrackers) {
            mMatchedTrackers = matchedTrackers;
            return this;
        }

        @Nullable
        public Map<ComponentInfo, Integer> getMatchedTrackers() {
            return mMatchedTrackers;
        }

        public TestResult setMatchedPermissions(List<String> matchedPermissions) {
            mMatchedPermissions = matchedPermissions;
            return this;
        }

        @Nullable
        public List<String> getMatchedPermissions() {
            return mMatchedPermissions;
        }

        public TestResult setMatchedSubjectLines(List<String> matchedSubjectLines) {
            mMatchedSubjectLines = matchedSubjectLines;
            return this;
        }

        @Nullable
        public List<String> getMatchedSubjectLines() {
            return mMatchedSubjectLines;
        }
    }
}
