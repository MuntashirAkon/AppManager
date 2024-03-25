// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ssaid;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Base64;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.compat.xml.TypedXmlPullParser;
import io.github.muntashirakon.compat.xml.TypedXmlSerializer;
import io.github.muntashirakon.compat.xml.Xml;
import io.github.muntashirakon.io.AtomicExtendedFile;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

/**
 * This class contains the state for one type of settings. It is responsible
 * for saving the state asynchronously to an XML file after a mutation and
 * loading the from an XML file on construction.
 * <p>
 * This class uses the same lock as the settings provider to ensure that
 * multiple changes made by the settings provider, e,g, upgrade, bulk insert,
 * etc, are atomically persisted since the asynchronous persistence is using
 * the same lock to grab the current state to write to disk.
 * </p>
 */
@RequiresApi(Build.VERSION_CODES.O)
// Copyright 2015 The Android Open Source Project
public final class SettingsStateV26 implements SettingsState {
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_PERSISTENCE = false;

    private static final String LOG_TAG = SettingsStateV26.class.getSimpleName();

    static final int SETTINGS_VERSION_NEW_ENCODING = 121;

    private static final long WRITE_SETTINGS_DELAY_MILLIS = 200;
    private static final long MAX_WRITE_SETTINGS_DELAY_MILLIS = 2000;

    public static final int VERSION_UNDEFINED = -1;

    public static final String FALLBACK_FILE_SUFFIX = ".fallback";

    private static final String TAG_SETTINGS = "settings";
    private static final String TAG_SETTING = "setting";
    private static final String ATTR_PACKAGE = "package";
    private static final String ATTR_DEFAULT_SYS_SET = "defaultSysSet";
    private static final String ATTR_TAG = "tag";
    private static final String ATTR_TAG_BASE64 = "tagBase64";

    private static final String ATTR_VERSION = "version";
    private static final String ATTR_ID = "id";
    private static final String ATTR_NAME = "name";

    private static final String TAG_NAMESPACE_HASHES = "namespaceHashes";
    private static final String TAG_NAMESPACE_HASH = "namespaceHash";
    private static final String ATTR_NAMESPACE = "namespace";
    private static final String ATTR_BANNED_HASH = "bannedHash";

    private static final String ATTR_PRESERVE_IN_RESTORE = "preserve_in_restore";

    /**
     * Non-binary value will be written in this attributes.
     */
    private static final String ATTR_VALUE = "value";
    private static final String ATTR_DEFAULT_VALUE = "defaultValue";

    /**
     * KXmlSerializer won't like some characters. We encode such characters
     * in base64 and store in this attribute.
     * NOTE: A null value will have *neither* ATTR_VALUE nor ATTR_VALUE_BASE64.
     */
    private static final String ATTR_VALUE_BASE64 = "valueBase64";
    private static final String ATTR_DEFAULT_VALUE_BASE64 = "defaultValueBase64";

    // This was used in version 120 and before.
    private static final String NULL_VALUE_OLD_STYLE = "null";

    private static final int HISTORICAL_OPERATION_COUNT = 20;
    private static final String HISTORICAL_OPERATION_UPDATE = "update";
    private static final String HISTORICAL_OPERATION_DELETE = "delete";
    private static final String HISTORICAL_OPERATION_PERSIST = "persist";
    private static final String HISTORICAL_OPERATION_INITIALIZE = "initialize";
    private static final String HISTORICAL_OPERATION_RESET = "reset";

    private static final String NULL_VALUE = "null";

    private final Object mWriteLock = new Object();

    private final Object mLock;

    private final Handler mHandler;

    @GuardedBy("mLock")
    private final ArrayMap<String, Setting> mSettings = new ArrayMap<>();

    @GuardedBy("mLock")
    private final ArrayMap<String, String> mNamespaceBannedHashes = new ArrayMap<>();

    @GuardedBy("mLock")
    private final ArrayMap<String, Integer> mPackageToMemoryUsage;

    @GuardedBy("mLock")
    private final int mMaxBytesPerAppPackage;

    @GuardedBy("mLock")
    private final Path mStatePersistFile;

    private final Setting mNullSetting = new Setting(null, null, false, null, null) {
        @Override
        public boolean isNull() {
            return true;
        }
    };

    @GuardedBy("mLock")
    private final List<HistoricalOperation> mHistoricalOperations;

    @GuardedBy("mLock")
    public final int mKey;

    @GuardedBy("mLock")
    private int mVersion = VERSION_UNDEFINED;

    @GuardedBy("mLock")
    private long mLastNotWrittenMutationTimeMillis;

    @GuardedBy("mLock")
    private boolean mDirty;

    @GuardedBy("mLock")
    private boolean mWriteScheduled;

    @GuardedBy("mLock")
    private long mNextId;

    @GuardedBy("mLock")
    private int mNextHistoricalOpIdx;

    public static final int SETTINGS_TYPE_MASK = 0xF0000000;
    public static final int SETTINGS_TYPE_SHIFT = 28;

    public static int makeKey(int type, int userId) {
        return (type << SETTINGS_TYPE_SHIFT) | userId;
    }

    public static int getTypeFromKey(int key) {
        return key >>> SETTINGS_TYPE_SHIFT;
    }

    public static int getUserIdFromKey(int key) {
        return key & ~SETTINGS_TYPE_MASK;
    }

    @NonNull
    public static String settingTypeToString(int type) {
        switch (type) {
            case SETTINGS_TYPE_CONFIG: {
                return "SETTINGS_CONFIG";
            }
            case SETTINGS_TYPE_GLOBAL: {
                return "SETTINGS_GLOBAL";
            }
            case SETTINGS_TYPE_SECURE: {
                return "SETTINGS_SECURE";
            }
            case SETTINGS_TYPE_SYSTEM: {
                return "SETTINGS_SYSTEM";
            }
            case SETTINGS_TYPE_SSAID: {
                return "SETTINGS_SSAID";
            }
            default: {
                return "UNKNOWN";
            }
        }
    }

    @NonNull
    public static String keyToString(int key) {
        return "Key[user=" + getUserIdFromKey(key) + ";type="
                + settingTypeToString(getTypeFromKey(key)) + "]";
    }

    public SettingsStateV26(Object lock, Path file, int key, int maxBytesPerAppPackage, Looper looper)
            throws IllegalStateException {
        // It is important that we use the same lock as the settings provider
        // to ensure multiple mutations on this state are atomically persisted
        // as the async persistence should be blocked while we make changes.
        mLock = lock;
        mStatePersistFile = file;
        mKey = key;
        mHandler = new MyHandler(looper);
        if (maxBytesPerAppPackage == MAX_BYTES_PER_APP_PACKAGE_LIMITED) {
            mMaxBytesPerAppPackage = maxBytesPerAppPackage;
            mPackageToMemoryUsage = new ArrayMap<>();
        } else {
            mMaxBytesPerAppPackage = maxBytesPerAppPackage;
            mPackageToMemoryUsage = null;
        }

        mHistoricalOperations = BuildConfig.DEBUG ? new ArrayList<>(HISTORICAL_OPERATION_COUNT) : null;

        synchronized (mLock) {
            readStateSyncLocked();
        }
    }

    // The settings provider must hold its lock when calling here.
    @GuardedBy("mLock")
    public int getVersionLocked() {
        return mVersion;
    }

    public Setting getNullSetting() {
        return mNullSetting;
    }

    // The settings provider must hold its lock when calling here.
    @GuardedBy("mLock")
    public void setVersionLocked(int version) {
        if (version == mVersion) {
            return;
        }
        mVersion = version;

        scheduleWriteIfNeededLocked();
    }

    // The settings provider must hold its lock when calling here.
    @GuardedBy("mLock")
    public void removeSettingsForPackageLocked(String packageName) {
        boolean removedSomething = false;

        final int settingCount = mSettings.size();
        for (int i = settingCount - 1; i >= 0; i--) {
            Setting setting = mSettings.valueAt(i);
            if (packageName.equals(setting.packageName)) {
                mSettings.removeAt(i);
                removedSomething = true;
            }
        }

        if (removedSomething) {
            scheduleWriteIfNeededLocked();
        }
    }

    // The settings provider must hold its lock when calling here.
    @NonNull
    @GuardedBy("mLock")
    public List<String> getSettingNamesLocked() {
        ArrayList<String> names = new ArrayList<>();
        final int settingsCount = mSettings.size();
        for (int i = 0; i < settingsCount; i++) {
            String name = mSettings.keyAt(i);
            names.add(name);
        }
        return names;
    }

    // The settings provider must hold its lock when calling here.
    @GuardedBy("mLock")
    @Override
    public Setting getSettingLocked(String name) {
        if (TextUtils.isEmpty(name)) {
            return mNullSetting;
        }
        Setting setting = mSettings.get(name);
        if (setting != null) {
            return new Setting(setting);
        }
        return mNullSetting;
    }

    // The settings provider must hold its lock when calling here.
    public boolean updateSettingLocked(String name, String value, String tag,
                                       boolean makeValue, String packageName) {
        if (!hasSettingLocked(name)) {
            return false;
        }

        return insertSettingLocked(name, value, tag, makeValue, packageName);
    }

    // The settings provider must hold its lock when calling here.
    @GuardedBy("mLock")
    public void resetSettingDefaultValueLocked(String name) {
        Setting oldSetting = getSettingLocked(name);
        if (oldSetting != null && !oldSetting.isNull() && oldSetting.getDefaultValue() != null) {
            String oldValue = oldSetting.getValue();
            String oldDefaultValue = oldSetting.getDefaultValue();
            Setting newSetting = new Setting(name, oldSetting.getValue(), null,
                    oldSetting.getPackageName(), oldSetting.getTag(), false,
                    oldSetting.getId());
            mSettings.put(name, newSetting);
            updateMemoryUsagePerPackageLocked(newSetting.getPackageName(), oldValue,
                    newSetting.getValue(), oldDefaultValue, newSetting.getDefaultValue());
            scheduleWriteIfNeededLocked();
        }
    }

    // The settings provider must hold its lock when calling here.
    @GuardedBy("mLock")
    public boolean insertSettingOverrideableByRestoreLocked(String name, String value, String tag,
                                                            boolean makeDefault, String packageName) {
        return insertSettingLocked(name, value, tag, makeDefault, false, packageName,
                /* overrideableByRestore */ true);
    }

    // The settings provider must hold its lock when calling here.
    @GuardedBy("mLock")
    @Override
    public boolean insertSettingLocked(String name, String value, String tag,
                                       boolean makeDefault, String packageName) {
        return insertSettingLocked(name, value, tag, makeDefault, false, packageName,
                /* overrideableByRestore */ false);
    }

    // The settings provider must hold its lock when calling here.
    @GuardedBy("mLock")
    public boolean insertSettingLocked(String name, String value, String tag,
                                       boolean makeDefault, boolean forceNonSystemPackage, String packageName,
                                       boolean overrideableByRestore) {
        if (TextUtils.isEmpty(name)) {
            return false;
        }

        Setting oldState = mSettings.get(name);
        String oldValue = (oldState != null) ? oldState.value : null;
        String oldDefaultValue = (oldState != null) ? oldState.defaultValue : null;
        Setting newState;

        if (oldState != null) {
            if (!oldState.update(value, makeDefault, packageName, tag, forceNonSystemPackage,
                    overrideableByRestore)) {
                return false;
            }
            newState = oldState;
        } else {
            newState = new Setting(name, value, makeDefault, packageName, tag,
                    forceNonSystemPackage);
            mSettings.put(name, newState);
        }

        addHistoricalOperationLocked(HISTORICAL_OPERATION_UPDATE, newState);

        updateMemoryUsagePerPackageLocked(packageName, oldValue, value,
                oldDefaultValue, newState.getDefaultValue());

        scheduleWriteIfNeededLocked();

        return true;
    }

    @GuardedBy("mLock")
    public boolean isNewConfigBannedLocked(String prefix, Map<String, String> keyValues) {
        // Replaces old style "null" String values with actual null's. This is done to simulate
        // what will happen to String "null" values when they are written to Settings. This needs to
        // be done here make sure that config hash computed during is banned check matches the
        // one computed during banning when values are already stored.
        keyValues = removeNullValueOldStyle(keyValues);
        String bannedHash = mNamespaceBannedHashes.get(prefix);
        if (bannedHash == null) {
            return false;
        }
        return bannedHash.equals(hashCode(keyValues));
    }

    @GuardedBy("mLock")
    public void unbanAllConfigIfBannedConfigUpdatedLocked(String prefix) {
        // If the prefix updated is a banned namespace, clear mNamespaceBannedHashes
        // to unban all unbanned namespaces.
        if (mNamespaceBannedHashes.get(prefix) != null) {
            mNamespaceBannedHashes.clear();
            scheduleWriteIfNeededLocked();
        }
    }

    @GuardedBy("mLock")
    public void banConfigurationLocked(String prefix, Map<String, String> keyValues) {
        if (prefix == null || keyValues.isEmpty()) {
            return;
        }
        // The write is intentionally not scheduled here, banned hashes should and will be written
        // when the related setting changes are written
        mNamespaceBannedHashes.put(prefix, hashCode(keyValues));
    }

    @GuardedBy("mLock")
    public Set<String> getAllConfigPrefixesLocked() {
        Set<String> prefixSet = new HashSet<>();
        final int settingsCount = mSettings.size();
        for (int i = 0; i < settingsCount; i++) {
            String name = mSettings.keyAt(i);
            prefixSet.add(name.split("/")[0] + "/");
        }
        return prefixSet;
    }

    // The settings provider must hold its lock when calling here.
    // Returns the list of keys which changed (added, updated, or deleted).
    @GuardedBy("mLock")
    public List<String> setSettingsLocked(String prefix, Map<String, String> keyValues,
                                          String packageName) {
        List<String> changedKeys = new ArrayList<>();
        final Iterator<Map.Entry<String, Setting>> iterator = mSettings.entrySet().iterator();
        // Delete old keys with the prefix that are not part of the new set.
        while (iterator.hasNext()) {
            Map.Entry<String, Setting> entry = iterator.next();
            final String key = entry.getKey();
            final Setting oldState = entry.getValue();
            if (key != null && key.startsWith(prefix) && !keyValues.containsKey(key)) {
                iterator.remove();

                addHistoricalOperationLocked(HISTORICAL_OPERATION_DELETE, oldState);
                changedKeys.add(key); // key was removed
            }
        }

        // Update/add new keys
        for (String key : keyValues.keySet()) {
            String value = keyValues.get(key);
            String oldValue = null;
            Setting state = mSettings.get(key);
            if (state == null) {
                state = new Setting(key, value, false, packageName, null);
                mSettings.put(key, state);
                changedKeys.add(key); // key was added
            } else if (!state.value.equals(value)) {
                oldValue = state.value;
                state.update(value, false, packageName, null, true,
                        /* overrideableByRestore */ false);
                changedKeys.add(key); // key was updated
            } else {
                // this key/value already exists, no change and no logging necessary
                continue;
            }

            addHistoricalOperationLocked(HISTORICAL_OPERATION_UPDATE, state);
        }

        if (!changedKeys.isEmpty()) {
            scheduleWriteIfNeededLocked();
        }

        return changedKeys;
    }

    // The settings provider must hold its lock when calling here.
    public void persistSyncLocked() {
        mHandler.removeMessages(MyHandler.MSG_PERSIST_SETTINGS);
        doWriteState();
    }

    // The settings provider must hold its lock when calling here.
    @GuardedBy("mLock")
    public boolean deleteSettingLocked(String name) {
        if (TextUtils.isEmpty(name) || !hasSettingLocked(name)) {
            return false;
        }

        Setting oldState = mSettings.remove(name);

        updateMemoryUsagePerPackageLocked(oldState.packageName, oldState.value,
                null, oldState.defaultValue, null);

        addHistoricalOperationLocked(HISTORICAL_OPERATION_DELETE, oldState);

        scheduleWriteIfNeededLocked();

        return true;
    }

    // The settings provider must hold its lock when calling here.
    @GuardedBy("mLock")
    public boolean resetSettingLocked(String name) {
        if (TextUtils.isEmpty(name) || !hasSettingLocked(name)) {
            return false;
        }

        Setting setting = mSettings.get(name);

        Setting oldSetting = new Setting(setting);
        String oldValue = setting.getValue();
        String oldDefaultValue = setting.getDefaultValue();

        if (!setting.reset()) {
            return false;
        }

        String newValue = setting.getValue();
        String newDefaultValue = setting.getDefaultValue();

        updateMemoryUsagePerPackageLocked(setting.packageName, oldValue,
                newValue, oldDefaultValue, newDefaultValue);

        addHistoricalOperationLocked(HISTORICAL_OPERATION_RESET, oldSetting);

        scheduleWriteIfNeededLocked();

        return true;
    }

    // The settings provider must hold its lock when calling here.
    @GuardedBy("mLock")
    public void destroyLocked(Runnable callback) {
        mHandler.removeMessages(MyHandler.MSG_PERSIST_SETTINGS);
        if (callback != null) {
            if (mDirty) {
                // Do it without a delay.
                mHandler.obtainMessage(MyHandler.MSG_PERSIST_SETTINGS,
                        callback).sendToTarget();
                return;
            }
            callback.run();
        }
    }

    @GuardedBy("mLock")
    private void addHistoricalOperationLocked(String type, Setting setting) {
        if (mHistoricalOperations == null) {
            return;
        }
        HistoricalOperation operation = new HistoricalOperation(
                SystemClock.elapsedRealtime(), type,
                setting != null ? new Setting(setting) : null);
        if (mNextHistoricalOpIdx >= mHistoricalOperations.size()) {
            mHistoricalOperations.add(operation);
        } else {
            mHistoricalOperations.set(mNextHistoricalOpIdx, operation);
        }
        mNextHistoricalOpIdx++;
        if (mNextHistoricalOpIdx >= HISTORICAL_OPERATION_COUNT) {
            mNextHistoricalOpIdx = 0;
        }
    }

    @GuardedBy("mLock")
    private void updateMemoryUsagePerPackageLocked(String packageName, String oldValue,
                                                   String newValue, String oldDefaultValue, String newDefaultValue) {
        if (mMaxBytesPerAppPackage == MAX_BYTES_PER_APP_PACKAGE_UNLIMITED) {
            return;
        }

        if (SYSTEM_PACKAGE_NAME.equals(packageName)) {
            return;
        }

        final int oldValueSize = (oldValue != null) ? oldValue.length() : 0;
        final int newValueSize = (newValue != null) ? newValue.length() : 0;
        final int oldDefaultValueSize = (oldDefaultValue != null) ? oldDefaultValue.length() : 0;
        final int newDefaultValueSize = (newDefaultValue != null) ? newDefaultValue.length() : 0;
        final int deltaSize = newValueSize + newDefaultValueSize
                - oldValueSize - oldDefaultValueSize;

        Integer currentSize = mPackageToMemoryUsage.get(packageName);
        final int newSize = Math.max((currentSize != null)
                ? currentSize + deltaSize : deltaSize, 0);

        if (newSize > mMaxBytesPerAppPackage) {
            throw new IllegalStateException("You are adding too many system settings. "
                    + "You should stop using system settings for app specific data"
                    + " package: " + packageName);
        }

        if (DEBUG) {
            Log.i(LOG_TAG, "Settings for package: %s size: %s bytes.", packageName, newSize);
        }

        mPackageToMemoryUsage.put(packageName, newSize);
    }

    @GuardedBy("mLock")
    private boolean hasSettingLocked(String name) {
        return mSettings.indexOfKey(name) >= 0;
    }

    @GuardedBy("mLock")
    private void scheduleWriteIfNeededLocked() {
        // If dirty then we have a write already scheduled.
        if (!mDirty) {
            mDirty = true;
            writeStateAsyncLocked();
        }
    }

    @GuardedBy("mLock")
    private void writeStateAsyncLocked() {
        final long currentTimeMillis = SystemClock.uptimeMillis();

        if (mWriteScheduled) {
            mHandler.removeMessages(MyHandler.MSG_PERSIST_SETTINGS);

            // If enough time passed, write without holding off anymore.
            final long timeSinceLastNotWrittenMutationMillis = currentTimeMillis
                    - mLastNotWrittenMutationTimeMillis;
            if (timeSinceLastNotWrittenMutationMillis >= MAX_WRITE_SETTINGS_DELAY_MILLIS) {
                mHandler.obtainMessage(MyHandler.MSG_PERSIST_SETTINGS).sendToTarget();
                return;
            }

            // Hold off a bit more as settings are frequently changing.
            final long maxDelayMillis = Math.max(mLastNotWrittenMutationTimeMillis
                    + MAX_WRITE_SETTINGS_DELAY_MILLIS - currentTimeMillis, 0);
            final long writeDelayMillis = Math.min(WRITE_SETTINGS_DELAY_MILLIS, maxDelayMillis);

            Message message = mHandler.obtainMessage(MyHandler.MSG_PERSIST_SETTINGS);
            mHandler.sendMessageDelayed(message, writeDelayMillis);
        } else {
            mLastNotWrittenMutationTimeMillis = currentTimeMillis;
            Message message = mHandler.obtainMessage(MyHandler.MSG_PERSIST_SETTINGS);
            mHandler.sendMessageDelayed(message, WRITE_SETTINGS_DELAY_MILLIS);
            mWriteScheduled = true;
        }
    }

    private void doWriteState() {
        boolean wroteState = false;
        final int version;
        final ArrayMap<String, Setting> settings;
        final ArrayMap<String, String> namespaceBannedHashes;

        synchronized (mLock) {
            version = mVersion;
            settings = new ArrayMap<>(mSettings);
            namespaceBannedHashes = new ArrayMap<>(mNamespaceBannedHashes);
            mDirty = false;
            mWriteScheduled = false;
        }

        synchronized (mWriteLock) {
            if (DEBUG_PERSISTENCE) {
                Log.i(LOG_TAG, "[PERSIST START]");
            }

            AtomicExtendedFile destination = new AtomicExtendedFile(mStatePersistFile.getFile());
            FileOutputStream out = null;
            try {
                out = destination.startWrite();

                TypedXmlSerializer serializer;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    serializer = Xml.newBinarySerializer();
                } else {
                    serializer = Xml.newFastSerializer();
                    serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output",
                            true);
                }
                serializer.startDocument(null, true);
                serializer.startTag(null, TAG_SETTINGS);
                serializer.attributeInt(null, ATTR_VERSION, version);

                final int settingCount = settings.size();
                for (int i = 0; i < settingCount; i++) {
                    Setting setting = settings.valueAt(i);

                    if (writeSingleSetting(mVersion, serializer, setting.getId(), setting.getName(),
                            setting.getValue(), setting.getDefaultValue(), setting.getPackageName(),
                            setting.getTag(), setting.isDefaultFromSystem(),
                            setting.isValuePreservedInRestore())) {
                        if (DEBUG_PERSISTENCE) {
                            Log.i(LOG_TAG, "[PERSISTED] %s=%s", setting.getName(), setting.getValue());
                        }
                    }
                }
                serializer.endTag(null, TAG_SETTINGS);

                serializer.startTag(null, TAG_NAMESPACE_HASHES);
                for (int i = 0; i < namespaceBannedHashes.size(); i++) {
                    String namespace = namespaceBannedHashes.keyAt(i);
                    String bannedHash = namespaceBannedHashes.get(namespace);
                    if (writeSingleNamespaceHash(serializer, namespace, bannedHash)) {
                        if (DEBUG_PERSISTENCE) {
                            Log.i(LOG_TAG, "[PERSISTED] namespace=%s, bannedHash=%s", namespace, bannedHash);
                        }
                    }
                }
                serializer.endTag(null, TAG_NAMESPACE_HASHES);
                serializer.endDocument();
                destination.finishWrite(out);

                wroteState = true;

                if (DEBUG_PERSISTENCE) {
                    Log.i(LOG_TAG, "[PERSIST END]");
                }
            } catch (Throwable t) {
                Log.e(LOG_TAG, "Failed to write settings, restoring backup", t);
                if (t instanceof IOException) {
                    // we failed to create a directory, so log the permissions and existence
                    // state for the settings file and directory
                    logSettingsDirectoryInformation(Paths.get(destination.getBaseFile()));
                    if (t.getMessage().contains("Couldn't create directory")) {
                        // attempt to create the directory with Files.createDirectories, which
                        // throws more informative errors than File.mkdirs.
                        Path parentPath = Paths.get(destination.getBaseFile().getParentFile());
                        if (parentPath.mkdirs()) {
                            Log.i(LOG_TAG, "Successfully created %s", parentPath);
                        } else {
                            Log.e(LOG_TAG, "Failed to write %s with Files.writeDirectories", parentPath);
                        }
                    }
                }
                destination.failWrite(out);
            } finally {
                IoUtils.closeQuietly(out);
            }
        }

        if (wroteState) {
            synchronized (mLock) {
                addHistoricalOperationLocked(HISTORICAL_OPERATION_PERSIST, null);
            }
        }
    }

    private static void logSettingsDirectoryInformation(Path settingsFile) {
        Path parent = settingsFile.getParent();
        Log.i(LOG_TAG, "directory info for directory/file %s with stacktrace ", new Exception(), settingsFile);
        Path ancestorDir = parent;
        while (ancestorDir != null) {
            if (!ancestorDir.exists()) {
                Log.i(LOG_TAG, "ancestor directory %s does not exist", ancestorDir);
                ancestorDir = ancestorDir.getParent();
            } else {
                Log.i(LOG_TAG, "ancestor directory %s exists", ancestorDir);
                Log.i(LOG_TAG, "ancestor directory %s permissions: r: %s w: %s x: %s", ancestorDir,
                        ancestorDir.canRead(), ancestorDir.canWrite(), ancestorDir.canExecute());
                Path ancestorParent = ancestorDir.getParent();
                if (ancestorParent != null) {
                    Log.i(LOG_TAG, "ancestor's parent directory %s permissions: r: %s w: %s" + " x: %s",
                            ancestorParent, ancestorParent.canRead(), ancestorParent.canWrite(),
                            ancestorParent.canExecute());
                }
                break;
            }
        }
    }

    static boolean writeSingleSetting(int version, TypedXmlSerializer serializer, String id,
                                   String name, String value, String defaultValue, String packageName,
                                   String tag, boolean defaultSysSet, boolean isValuePreservedInRestore)
            throws IOException {
        if (id == null || isBinary(id) || name == null || isBinary(name)
                || packageName == null || isBinary(packageName)) {
            if (DEBUG_PERSISTENCE) {
                Log.w(LOG_TAG, "Invalid arguments for writeSingleSetting: version=" + version
                        + ", id=" + id + ", name=" + name + ", value=" + value
                        + ", defaultValue=" + defaultValue + ", packageName=" + packageName
                        + ", tag=" + tag + ", defaultSysSet=" + defaultSysSet
                        + ", isValuePreservedInRestore=" + isValuePreservedInRestore);
            }
            return false;
        }
        serializer.startTag(null, TAG_SETTING);
        serializer.attribute(null, ATTR_ID, id);
        serializer.attribute(null, ATTR_NAME, name);
        setValueAttribute(ATTR_VALUE, ATTR_VALUE_BASE64,
                version, serializer, value);
        serializer.attribute(null, ATTR_PACKAGE, packageName);
        if (defaultValue != null) {
            setValueAttribute(ATTR_DEFAULT_VALUE, ATTR_DEFAULT_VALUE_BASE64,
                    version, serializer, defaultValue);
            serializer.attributeBoolean(null, ATTR_DEFAULT_SYS_SET, defaultSysSet);
            setValueAttribute(ATTR_TAG, ATTR_TAG_BASE64,
                    version, serializer, tag);
        }
        if (isValuePreservedInRestore) {
            serializer.attributeBoolean(null, ATTR_PRESERVE_IN_RESTORE, true);
        }
        serializer.endTag(null, TAG_SETTING);
        return true;
    }

    static void setValueAttribute(String attr, String attrBase64, int version,
                                  TypedXmlSerializer serializer, String value) throws IOException {
        if (version >= SETTINGS_VERSION_NEW_ENCODING) {
            if (value == null) {
                // Null value -> No ATTR_VALUE nor ATTR_VALUE_BASE64.
            } else if (isBinary(value)) {
                serializer.attribute(null, attrBase64, base64Encode(value));
            } else {
                serializer.attribute(null, attr, value);
            }
        } else {
            // Old encoding.
            if (value == null) {
                serializer.attribute(null, attr, NULL_VALUE_OLD_STYLE);
            } else {
                serializer.attribute(null, attr, value);
            }
        }
    }

    private static boolean writeSingleNamespaceHash(TypedXmlSerializer serializer, String namespace,
                                                 String bannedHashCode) throws IOException {
        if (namespace == null || bannedHashCode == null) {
            if (DEBUG_PERSISTENCE) {
                Log.w(LOG_TAG, "Invalid arguments for writeSingleNamespaceHash: namespace="
                        + namespace + ", bannedHashCode=" + bannedHashCode);
            }
            return false;
        }
        serializer.startTag(null, TAG_NAMESPACE_HASH);
        serializer.attribute(null, ATTR_NAMESPACE, namespace);
        serializer.attribute(null, ATTR_BANNED_HASH, bannedHashCode);
        serializer.endTag(null, TAG_NAMESPACE_HASH);
        return true;
    }

    private static String hashCode(Map<String, String> keyValues) {
        return Integer.toString(keyValues.hashCode());
    }

    private String getValueAttribute(TypedXmlPullParser parser, String attr, String base64Attr) {
        if (mVersion >= SETTINGS_VERSION_NEW_ENCODING) {
            final String value = parser.getAttributeValue(null, attr);
            if (value != null) {
                return value;
            }
            final String base64 = parser.getAttributeValue(null, base64Attr);
            if (base64 != null) {
                return base64Decode(base64);
            }
            // null has neither ATTR_VALUE nor ATTR_VALUE_BASE64.
            return null;
        } else {
            // Old encoding.
            final String stored = parser.getAttributeValue(null, attr);
            if (NULL_VALUE_OLD_STYLE.equals(stored)) {
                return null;
            } else {
                return stored;
            }
        }
    }

    @GuardedBy("mLock")
    private void readStateSyncLocked() throws IllegalStateException {
        FileInputStream in;
        AtomicExtendedFile file = new AtomicExtendedFile(mStatePersistFile.getFile());
        try {
            in = file.openRead();
        } catch (IOException | RemoteException fnfe) {
            Log.w(LOG_TAG, "No settings state %s", mStatePersistFile);
            logSettingsDirectoryInformation(mStatePersistFile);
            addHistoricalOperationLocked(HISTORICAL_OPERATION_INITIALIZE, null);
            return;
        }
        if (parseStateFromXmlStreamLocked(in)) {
            return;
        }

        // Settings file exists but is corrupted. Retry with the fallback file
        Path statePersistFallbackFile = Paths.get(mStatePersistFile.getFilePath() + FALLBACK_FILE_SUFFIX);
        Log.i(LOG_TAG, "Failed parsing settings file: %s, retrying with fallback file: %s", mStatePersistFile,
                statePersistFallbackFile);
        try {
            in = new AtomicExtendedFile(statePersistFallbackFile.getFile()).openRead();
        } catch (IOException | RemoteException fnfe) {
            final String message = "No fallback file found for: " + mStatePersistFile;
            throw new IllegalStateException(message, fnfe);
        }
        if (parseStateFromXmlStreamLocked(in)) {
            // Parsed state from fallback file. Restore original file with fallback file
            try {
                IoUtils.copy(statePersistFallbackFile, mStatePersistFile);
            } catch (IOException ignored) {
                // Failed to copy, but it's okay because we already parsed states from fallback file
            }
        } else {
            final String message = "Failed parsing settings file: " + mStatePersistFile;
            throw new IllegalStateException(message);
        }
    }

    @GuardedBy("mLock")
    private boolean parseStateFromXmlStreamLocked(InputStream in) {
        try {
            TypedXmlPullParser parser = Xml.resolvePullParser(in);
            parseStateLocked(parser);
            return true;
        } catch (XmlPullParserException | IOException e) {
            return false;
        } finally {
            IoUtils.closeQuietly(in);
        }
    }

    /**
     * Uses AtomicExtendedFile to check if the file or its backup exists.
     *
     * @param file The file to check for existence
     * @return whether the original or backup exist
     */
    public static boolean stateFileExists(Path file) {
        AtomicExtendedFile stateFile = new AtomicExtendedFile(Objects.requireNonNull(file.getFile()));
        return stateFile.exists();
    }

    private void parseStateLocked(@NonNull TypedXmlPullParser parser)
            throws IOException, XmlPullParserException {
        final int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (tagName.equals(TAG_SETTINGS)) {
                parseSettingsLocked(parser);
            } else if (tagName.equals(TAG_NAMESPACE_HASHES)) {
                parseNamespaceHash(parser);
            }
        }
    }

    @GuardedBy("mLock")
    private void parseSettingsLocked(@NonNull TypedXmlPullParser parser) throws IOException, XmlPullParserException {

        mVersion = parser.getAttributeInt(null, ATTR_VERSION);

        final int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (tagName.equals(TAG_SETTING)) {
                String id = parser.getAttributeValue(null, ATTR_ID);
                String name = parser.getAttributeValue(null, ATTR_NAME);
                String value = getValueAttribute(parser, ATTR_VALUE, ATTR_VALUE_BASE64);
                String packageName = parser.getAttributeValue(null, ATTR_PACKAGE);
                String defaultValue = getValueAttribute(parser, ATTR_DEFAULT_VALUE,
                        ATTR_DEFAULT_VALUE_BASE64);
                boolean isPreservedInRestore = parser.getAttributeBoolean(null,
                        ATTR_PRESERVE_IN_RESTORE, false);
                String tag = null;
                boolean fromSystem = false;
                if (defaultValue != null) {
                    fromSystem = parser.getAttributeBoolean(null, ATTR_DEFAULT_SYS_SET);
                    tag = getValueAttribute(parser, ATTR_TAG, ATTR_TAG_BASE64);
                }
                mSettings.put(name, new Setting(name, value, defaultValue, packageName, tag,
                        fromSystem, id, isPreservedInRestore));

                if (DEBUG_PERSISTENCE) {
                    Log.i(LOG_TAG, "[RESTORED] %s=%s", name, value);
                }
            }
        }
    }

    @GuardedBy("mLock")
    private void parseNamespaceHash(TypedXmlPullParser parser)
            throws IOException, XmlPullParserException {

        final int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }

            if (parser.getName().equals(TAG_NAMESPACE_HASH)) {
                String namespace = parser.getAttributeValue(null, ATTR_NAMESPACE);
                String bannedHashCode = parser.getAttributeValue(null, ATTR_BANNED_HASH);
                mNamespaceBannedHashes.put(namespace, bannedHashCode);
            }
        }
    }

    private static Map<String, String> removeNullValueOldStyle(@NonNull Map<String, String> keyValues) {
        for (Map.Entry<String, String> keyValueEntry : keyValues.entrySet()) {
            if (NULL_VALUE_OLD_STYLE.equals(keyValueEntry.getValue())) {
                keyValueEntry.setValue(null);
            }
        }
        return keyValues;
    }

    private final class MyHandler extends Handler {
        public static final int MSG_PERSIST_SETTINGS = 1;

        public MyHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message message) {
            if (message.what == MSG_PERSIST_SETTINGS) {
                Runnable callback = (Runnable) message.obj;
                doWriteState();
                if (callback != null) {
                    callback.run();
                }
            }
        }
    }

    private class HistoricalOperation {
        final long mTimestamp;
        final String mOperation;
        final Setting mSetting;

        public HistoricalOperation(long timestamp,
                                   String operation, Setting setting) {
            mTimestamp = timestamp;
            mOperation = operation;
            mSetting = setting;
        }
    }

    class Setting implements SettingsState.Setting {
        private String name;
        private String value;
        private String defaultValue;
        private String packageName;
        private String id;
        private String tag;
        // Whether the default is set by the system
        private boolean defaultFromSystem;
        // Whether the value of this setting will be preserved when restore happens.
        private boolean isValuePreservedInRestore;

        public Setting(@NonNull Setting other) {
            name = other.name;
            value = other.value;
            defaultValue = other.defaultValue;
            packageName = other.packageName;
            id = other.id;
            defaultFromSystem = other.defaultFromSystem;
            tag = other.tag;
            isValuePreservedInRestore = other.isValuePreservedInRestore;
        }

        public Setting(String name, String value, boolean makeDefault, String packageName,
                       String tag) {
            this(name, value, makeDefault, packageName, tag, false);
        }

        Setting(String name, String value, boolean makeDefault, String packageName,
                String tag, boolean forceNonSystemPackage) {
            this.name = name;
            // overrideableByRestore = true as the first initialization isn't considered a
            // modification.
            update(value, makeDefault, packageName, tag, forceNonSystemPackage, true);
        }

        public Setting(String name, String value, String defaultValue,
                       String packageName, String tag, boolean fromSystem, String id) {
            this(name, value, defaultValue, packageName, tag, fromSystem, id,
                    /* isOverrideableByRestore */ false);
        }

        Setting(String name, String value, String defaultValue,
                String packageName, String tag, boolean fromSystem, String id,
                boolean isValuePreservedInRestore) {
            mNextId = Math.max(mNextId, Long.parseLong(id) + 1);
            if (NULL_VALUE.equals(value)) {
                value = null;
            }
            init(name, value, tag, defaultValue, packageName, fromSystem, id,
                    isValuePreservedInRestore);
        }

        private void init(String name, String value, String tag, String defaultValue,
                          String packageName, boolean fromSystem, String id,
                          boolean isValuePreservedInRestore) {
            this.name = name;
            this.value = value;
            this.tag = tag;
            this.defaultValue = defaultValue;
            this.packageName = packageName;
            this.id = id;
            this.defaultFromSystem = fromSystem;
            this.isValuePreservedInRestore = isValuePreservedInRestore;
        }

        public String getName() {
            return name;
        }

        public int getKey() {
            return mKey;
        }

        public String getValue() {
            return value;
        }

        public String getTag() {
            return tag;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public String getPackageName() {
            return packageName;
        }

        public boolean isDefaultFromSystem() {
            return defaultFromSystem;
        }

        public boolean isValuePreservedInRestore() {
            return isValuePreservedInRestore;
        }

        public String getId() {
            return id;
        }

        public boolean isNull() {
            return false;
        }

        /**
         * @return whether the value changed
         */
        public boolean reset() {
            // overrideableByRestore = true as resetting to default value isn't considered a
            // modification.
            return update(this.defaultValue, false, packageName, null, true, true,
                    /* resetToDefault */ true);
        }

        public boolean update(String value, boolean setDefault, String packageName, String tag,
                              boolean forceNonSystemPackage, boolean overrideableByRestore) {
            return update(value, setDefault, packageName, tag, forceNonSystemPackage,
                    overrideableByRestore, /* resetToDefault */ false);
        }

        private boolean update(String value, boolean setDefault, String packageName, String tag,
                               boolean forceNonSystemPackage, boolean overrideableByRestore,
                               boolean resetToDefault) {
            if (NULL_VALUE.equals(value)) {
                value = null;
            }

            String defaultValue = this.defaultValue;
            boolean defaultFromSystem = this.defaultFromSystem;
            if (setDefault) {
                if (!Objects.equals(value, this.defaultValue)) {
                    defaultValue = value;
                    // Default null means no default, so the tag is irrelevant
                    // since it is used to reset a settings subset their defaults.
                    // Also it is irrelevant if the system set the canonical default.
                    if (defaultValue == null) {
                        tag = null;
                        defaultFromSystem = false;
                    }
                }
                if (!defaultFromSystem && value != null) {
                    defaultFromSystem = true;
                }
            }

            // isValuePreservedInRestore shouldn't change back to false if it has been set to true.
            boolean isPreserved = shouldPreserveSetting(overrideableByRestore, resetToDefault,
                    packageName, value);

            // Is something gonna change?
            if (Objects.equals(value, this.value)
                    && Objects.equals(defaultValue, this.defaultValue)
                    && Objects.equals(packageName, this.packageName)
                    && Objects.equals(tag, this.tag)
                    && defaultFromSystem == this.defaultFromSystem
                    && isPreserved == this.isValuePreservedInRestore) {
                return false;
            }

            init(name, value, tag, defaultValue, packageName, defaultFromSystem,
                    String.valueOf(mNextId++), isPreserved);

            return true;
        }

        @NonNull
        public String toString() {
            return "Setting{name=" + name + " value=" + value
                    + (defaultValue != null ? " default=" + defaultValue : "")
                    + " packageName=" + packageName + " tag=" + tag
                    + " defaultFromSystem=" + defaultFromSystem + "}";
        }

        private boolean shouldPreserveSetting(boolean overrideableByRestore,
                                              boolean resetToDefault, String packageName, String value) {
            if (resetToDefault) {
                // By default settings are not marked as preserved.
                return false;
            }
            if (value != null && value.equals(this.value)
                    && SYSTEM_PACKAGE_NAME.equals(packageName)) {
                // Do not mark preserved if it's the system reinitializing to the same value.
                return false;
            }

            // isValuePreservedInRestore shouldn't change back to false if it has been set to true.
            return this.isValuePreservedInRestore || !overrideableByRestore;
        }
    }

    /**
     * @return TRUE if a string is considered "binary" from KXML's point of view.  NOTE DO NOT
     * pass null.
     */
    public static boolean isBinary(String s) {
        if (s == null) {
            throw new NullPointerException();
        }
        // See KXmlSerializer.writeEscaped
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean allowedInXml = (c >= 0x20 && c <= 0xd7ff) || (c >= 0xe000 && c <= 0xfffd);
            if (!allowedInXml) {
                return true;
            }
        }
        return false;
    }

    private static String base64Encode(String s) {
        return Base64.encodeToString(toBytes(s), Base64.NO_WRAP);
    }

    @NonNull
    private static String base64Decode(String s) {
        return fromBytes(Base64.decode(s, Base64.DEFAULT));
    }

    // Note the followings are basically just UTF-16 encode/decode.  But we want to preserve
    // contents as-is, even if it contains broken surrogate pairs, we do it by ourselves,
    // since I don't know how Charset would treat them.

    @NonNull
    private static byte[] toBytes(@NonNull String s) {
        final byte[] result = new byte[s.length() * 2];
        int resultIndex = 0;
        for (int i = 0; i < s.length(); ++i) {
            char ch = s.charAt(i);
            result[resultIndex++] = (byte) (ch >> 8);
            result[resultIndex++] = (byte) ch;
        }
        return result;
    }

    @NonNull
    private static String fromBytes(@NonNull byte[] bytes) {
        final StringBuilder sb = new StringBuilder(bytes.length / 2);

        final int last = bytes.length - 1;

        for (int i = 0; i < last; i += 2) {
            final char ch = (char) ((bytes[i] & 0xff) << 8 | (bytes[i + 1] & 0xff));
            sb.append(ch);
        }
        return sb.toString();
    }
}