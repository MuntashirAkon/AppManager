// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sysconfig;

import android.content.ComponentName;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.SparseArray;
import android.util.Xml;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.misc.SystemProperties;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat;
import io.github.muntashirakon.compat.xml.XmlUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

/**
 * Loads global system configuration info.
 * Note: Initializing this class hits the disk and is slow.  This class should generally only be
 * accessed by the system_server process.
 */
// Source: https://cs.android.com/android/_/android/platform/frameworks/base/+/9c0d523bc0b1ac3ebba92acb7e5d9675aff08aef:core/java/com/android/server/SystemConfig.java;l=36;bpv=1;bpt=0;drc=1172ffa7c261a499132fb59e18c2f972b1f57f45
public class SystemConfig {
    static final String TAG = "SystemConfig";

    static SystemConfig sInstance;

    // permission flag, determines which types of configuration are allowed to be read
    private static final int ALLOW_FEATURES = 0x01;
    private static final int ALLOW_LIBS = 0x02;
    private static final int ALLOW_PERMISSIONS = 0x04;
    private static final int ALLOW_APP_CONFIGS = 0x08;
    private static final int ALLOW_PRIVAPP_PERMISSIONS = 0x10;
    private static final int ALLOW_OEM_PERMISSIONS = 0x20;
    private static final int ALLOW_HIDDENAPI_WHITELISTING = 0x40;
    private static final int ALLOW_ASSOCIATIONS = 0x80;
    private static final int ALLOW_ALL = ~0;

    // property for runtime configuration differentiation
    private static final String SKU_PROPERTY = "ro.boot.product.hardware.sku";

    // property for runtime configuration differentiation in vendor
    private static final String VENDOR_SKU_PROPERTY = "ro.boot.product.vendor.sku";

    // Group-ids that are given to all packages as read from etc/permissions/*.xml.
    int[] mGlobalGids;

    // These are the built-in uid -> permission mappings that were read from the
    // system configuration files.
    final SparseArray<Set<String>> mSystemPermissions = new SparseArray<>();

    /**
     * A permission that was added in a previous API level might have split into several
     * permissions. This object describes one such split.
     */
    // Source: https://cs.android.com/android/_/android/platform/frameworks/base/+/9c0d523bc0b1ac3ebba92acb7e5d9675aff08aef:core/java/android/content/pm/permission/SplitPermissionInfoParcelable.java
    public static final class SplitPermissionInfo {

        /**
         * The permission that is split.
         */
        @NonNull
        private final String mSplitPermission;

        /**
         * The permissions that are added.
         */
        @NonNull
        private final List<String> mNewPermissions;

        /**
         * The target API level when the permission was split.
         */
        @IntRange(from = 0)
        private final int mTargetSdk;

        /**
         * Get the permission that is split.
         */
        public @NonNull String getSplitPermission() {
            return mSplitPermission;
        }

        /**
         * Get the permissions that are added.
         */
        public @NonNull List<String> getNewPermissions() {
            return mNewPermissions;
        }

        /**
         * Get the target API level when the permission was split.
         */
        public int getTargetSdk() {
            return mTargetSdk;
        }

        /**
         * Constructs a split permission.
         *
         * @param splitPerm old permission that will be split
         * @param newPerms list of new permissions that {@code rootPerm} will be split into
         * @param targetSdk apps targetting SDK versions below this will have {@code rootPerm}
         * split into {@code newPerms}
         */
        public SplitPermissionInfo(@NonNull String splitPerm, @NonNull List<String> newPerms,
                                   int targetSdk) {
            mSplitPermission = splitPerm;
            mNewPermissions = newPerms;
            mTargetSdk = targetSdk;
        }
    }

    final ArrayList<SplitPermissionInfo> mSplitPermissions = new ArrayList<>();

    public static final class SharedLibraryEntry {
        public final String name;
        public final String filename;
        public final String[] dependencies;

        SharedLibraryEntry(String name, String filename, String[] dependencies) {
            this.name = name;
            this.filename = filename;
            this.dependencies = dependencies;
        }
    }

    // These are the built-in shared libraries that were read from the
    // system configuration files. Keys are the library names; values are
    // the individual entries that contain information such as filename
    // and dependencies.
    final ArrayMap<String, SharedLibraryEntry> mSharedLibraries = new ArrayMap<>();

    // These are the features this devices supports that were read from the
    // system configuration files.
    final ArrayMap<String, FeatureInfo> mAvailableFeatures = new ArrayMap<>();

    // These are the features which this device doesn't support; the OEM
    // partition uses these to opt-out of features from the system image.
    final Set<String> mUnavailableFeatures = new HashSet<>();

    public static final class PermissionEntry {
        public final String name;
        public int[] gids;
        public boolean perUser;

        PermissionEntry(String name, boolean perUser) {
            this.name = name;
            this.perUser = perUser;
        }
    }

    // These are the permission -> gid mappings that were read from the
    // system configuration files.
    final ArrayMap<String, PermissionEntry> mPermissions = new ArrayMap<>();

    // These are the packages that are white-listed to be able to run in the
    // background while in power save mode (but not whitelisted from device idle modes),
    // as read from the configuration files.
    final Set<String> mAllowInPowerSaveExceptIdle = new HashSet<>();

    // These are the packages that are white-listed to be able to run in the
    // background while in power save mode, as read from the configuration files.
    final Set<String> mAllowInPowerSave = new HashSet<>();

    // These are the packages that are white-listed to be able to run in the
    // background while in data-usage save mode, as read from the configuration files.
    final Set<String> mAllowInDataUsageSave = new HashSet<>();

    // These are the packages that are white-listed to be able to run background location
    // without throttling, as read from the configuration files.
    final Set<String> mAllowUnthrottledLocation = new HashSet<>();

    // These are the packages that are white-listed to be able to retrieve location even when user
    // location settings are off, for emergency purposes, as read from the configuration files.
    final Set<String> mAllowIgnoreLocationSettings = new HashSet<>();

    // These are the action strings of broadcasts which are whitelisted to
    // be delivered anonymously even to apps which target O+.
    final Set<String> mAllowImplicitBroadcasts = new HashSet<>();

    // These are the package names of apps which should be in the 'always'
    // URL-handling state upon factory reset.
    final Set<String> mLinkedApps = new HashSet<>();

    // These are the packages that are whitelisted to be able to run as system user
    final Set<String> mSystemUserWhitelistedApps = new HashSet<>();

    // These are the packages that should not run under system user
    final Set<String> mSystemUserBlacklistedApps = new HashSet<>();

    // These are the components that are enabled by default as VR mode listener services.
    final Set<ComponentName> mDefaultVrComponents = new HashSet<>();

    // These are the permitted backup transport service components
    final Set<ComponentName> mBackupTransportWhitelist = new HashSet<>();

    // These are packages mapped to maps of component class name to default enabled state.
    final ArrayMap<String, ArrayMap<String, Boolean>> mPackageComponentEnabledState = new ArrayMap<>();

    // Package names that are exempted from private API blacklisting
    final Set<String> mHiddenApiPackageWhitelist = new HashSet<>();

    // The list of carrier applications which should be disabled until used.
    // This function suppresses update notifications for these pre-installed apps.
    // In SubscriptionInfoUpdater, the listed applications are disabled until used when all of the
    // following conditions are met.
    // 1. Not currently carrier-privileged according to the inserted SIM
    // 2. Pre-installed
    // 3. In the default state (enabled but not explicitly)
    // And SubscriptionInfoUpdater undoes this and marks the app enabled when a SIM is inserted
    // that marks the app as carrier privileged. It also grants the app default permissions
    // for Phone and Location. As such, apps MUST only ever be added to this list if they
    // obtain user consent to access their location through other means.
    final Set<String> mDisabledUntilUsedPreinstalledCarrierApps = new HashSet<>();

    /**
     * Represents a carrier app entry for use with {@link SystemConfig}.
     */
    // Taken from https://cs.android.com/android/_/android/platform/frameworks/base/+/9c0d523bc0b1ac3ebba92acb7e5d9675aff08aef:core/java/android/os/CarrierAssociatedAppEntry.java
    public static final class CarrierAssociatedAppEntry {
        /**
         * For carrier-associated app entries that don't specify the addedInSdk XML
         * attribute.
         */
        public static final int SDK_UNSPECIFIED = -1;

        public final String packageName;
        /**
         * May be {@link #SDK_UNSPECIFIED}.
         */
        public final int addedInSdk;

        public CarrierAssociatedAppEntry(String packageName, int addedInSdk) {
            this.packageName = packageName;
            this.addedInSdk = addedInSdk;
        }

        @NonNull
        @Override
        public String toString() {
            return "CarrierAssociatedAppEntry{" +
                    "packageName='" + packageName + '\'' +
                    ", addedInSdk=" + addedInSdk +
                    '}';
        }
    }

    // These are the packages of carrier-associated apps which should be disabled until used until
    // a SIM is inserted which grants carrier privileges to that carrier app.
    final ArrayMap<String, List<CarrierAssociatedAppEntry>>
            mDisabledUntilUsedPreinstalledCarrierAssociatedApps = new ArrayMap<>();

    final ArrayMap<String, Set<String>> mPrivAppPermissions = new ArrayMap<>();
    final ArrayMap<String, Set<String>> mPrivAppDenyPermissions = new ArrayMap<>();

    final ArrayMap<String, Set<String>> mVendorPrivAppPermissions = new ArrayMap<>();
    final ArrayMap<String, Set<String>> mVendorPrivAppDenyPermissions = new ArrayMap<>();

    final ArrayMap<String, Set<String>> mProductPrivAppPermissions = new ArrayMap<>();
    final ArrayMap<String, Set<String>> mProductPrivAppDenyPermissions = new ArrayMap<>();

    final ArrayMap<String, Set<String>> mSystemExtPrivAppPermissions = new ArrayMap<>();
    final ArrayMap<String, Set<String>> mSystemExtPrivAppDenyPermissions = new ArrayMap<>();

    final ArrayMap<String, ArrayMap<String, Boolean>> mOemPermissions = new ArrayMap<>();

    // Allowed associations between applications.  If there are any entries
    // for an app, those are the only associations allowed; otherwise, all associations
    // are allowed.  Allowing an association from app A to app B means app A can not
    // associate with any other apps, but does not limit what apps B can associate with.
    final ArrayMap<String, Set<String>> mAllowedAssociations = new ArrayMap<>();

    private final Set<String> mBugreportWhitelistedPackages = new HashSet<>();
    private final Set<String> mAppDataIsolationWhitelistedApps = new HashSet<>();

    // Map of packagesNames to userTypes. Stored temporarily until cleared by UserManagerService().
    ArrayMap<String, Set<String>> mPackageToUserTypeWhitelist = new ArrayMap<>();
    ArrayMap<String, Set<String>> mPackageToUserTypeBlacklist = new ArrayMap<>();

    private final Set<String> mRollbackWhitelistedPackages = new HashSet<>();
    private final Set<String> mWhitelistedStagedInstallers = new HashSet<>();

    /**
     * Map of system pre-defined, uniquely named actors; keys are namespace,
     * value maps actor name to package name.
     */
    private ArrayMap<String, ArrayMap<String, String>> mNamedActors = new ArrayMap<>();

    public static SystemConfig getInstance() {
        synchronized (SystemConfig.class) {
            if (sInstance == null) {
                sInstance = new SystemConfig();
            }
            return sInstance;
        }
    }

    public int[] getGlobalGids() {
        return mGlobalGids;
    }

    public SparseArray<Set<String>> getSystemPermissions() {
        return mSystemPermissions;
    }

    public ArrayList<SplitPermissionInfo> getSplitPermissions() {
        return mSplitPermissions;
    }

    public ArrayMap<String, SharedLibraryEntry> getSharedLibraries() {
        return mSharedLibraries;
    }

    public ArrayMap<String, FeatureInfo> getAvailableFeatures() {
        return mAvailableFeatures;
    }

    public ArrayMap<String, PermissionEntry> getPermissions() {
        return mPermissions;
    }

    public Set<String> getAllowImplicitBroadcasts() {
        return mAllowImplicitBroadcasts;
    }

    public Set<String> getAllowInPowerSaveExceptIdle() {
        return mAllowInPowerSaveExceptIdle;
    }

    public Set<String> getAllowInPowerSave() {
        return mAllowInPowerSave;
    }

    public Set<String> getAllowInDataUsageSave() {
        return mAllowInDataUsageSave;
    }

    public Set<String> getAllowUnthrottledLocation() {
        return mAllowUnthrottledLocation;
    }

    public Set<String> getAllowIgnoreLocationSettings() {
        return mAllowIgnoreLocationSettings;
    }

    public Set<String> getLinkedApps() {
        return mLinkedApps;
    }

    public Set<String> getSystemUserWhitelistedApps() {
        return mSystemUserWhitelistedApps;
    }

    public Set<String> getSystemUserBlacklistedApps() {
        return mSystemUserBlacklistedApps;
    }

    public Set<String> getHiddenApiWhitelistedApps() {
        return mHiddenApiPackageWhitelist;
    }

    public Set<ComponentName> getDefaultVrComponents() {
        return mDefaultVrComponents;
    }

    public Set<ComponentName> getBackupTransportWhitelist() {
        return mBackupTransportWhitelist;
    }

    public ArrayMap<String, Boolean> getComponentsEnabledStates(String packageName) {
        return mPackageComponentEnabledState.get(packageName);
    }

    public Set<String> getDisabledUntilUsedPreinstalledCarrierApps() {
        return mDisabledUntilUsedPreinstalledCarrierApps;
    }

    public ArrayMap<String, List<CarrierAssociatedAppEntry>>
    getDisabledUntilUsedPreinstalledCarrierAssociatedApps() {
        return mDisabledUntilUsedPreinstalledCarrierAssociatedApps;
    }

    public Set<String> getPrivAppPermissions(String packageName) {
        return mPrivAppPermissions.get(packageName);
    }

    public Set<String> getPrivAppDenyPermissions(String packageName) {
        return mPrivAppDenyPermissions.get(packageName);
    }

    public Set<String> getVendorPrivAppPermissions(String packageName) {
        return mVendorPrivAppPermissions.get(packageName);
    }

    public Set<String> getVendorPrivAppDenyPermissions(String packageName) {
        return mVendorPrivAppDenyPermissions.get(packageName);
    }

    public Set<String> getProductPrivAppPermissions(String packageName) {
        return mProductPrivAppPermissions.get(packageName);
    }

    public Set<String> getProductPrivAppDenyPermissions(String packageName) {
        return mProductPrivAppDenyPermissions.get(packageName);
    }

    /**
     * Read from "permission" tags in /system_ext/etc/permissions/*.xml
     *
     * @return Set of privileged permissions that are explicitly granted.
     */
    public Set<String> getSystemExtPrivAppPermissions(String packageName) {
        return mSystemExtPrivAppPermissions.get(packageName);
    }

    /**
     * Read from "deny-permission" tags in /system_ext/etc/permissions/*.xml
     *
     * @return Set of privileged permissions that are explicitly denied.
     */
    public Set<String> getSystemExtPrivAppDenyPermissions(String packageName) {
        return mSystemExtPrivAppDenyPermissions.get(packageName);
    }

    public Map<String, Boolean> getOemPermissions(String packageName) {
        final Map<String, Boolean> oemPermissions = mOemPermissions.get(packageName);
        if (oemPermissions != null) {
            return oemPermissions;
        }
        return Collections.emptyMap();
    }

    public ArrayMap<String, Set<String>> getAllowedAssociations() {
        return mAllowedAssociations;
    }

    public Set<String> getBugreportWhitelistedPackages() {
        return mBugreportWhitelistedPackages;
    }

    public Set<String> getRollbackWhitelistedPackages() {
        return mRollbackWhitelistedPackages;
    }

    public Set<String> getWhitelistedStagedInstallers() {
        return mWhitelistedStagedInstallers;
    }

    public Set<String> getAppDataIsolationWhitelistedApps() {
        return mAppDataIsolationWhitelistedApps;
    }

    /**
     * Gets map of packagesNames to userTypes, dictating on which user types each package should be
     * initially installed, and then removes this map from SystemConfig.
     * Called by UserManagerService when it is constructed.
     */
    public ArrayMap<String, Set<String>> getAndClearPackageToUserTypeWhitelist() {
        ArrayMap<String, Set<String>> r = mPackageToUserTypeWhitelist;
        mPackageToUserTypeWhitelist = new ArrayMap<>(0);
        return r;
    }

    /**
     * Gets map of packagesNames to userTypes, dictating on which user types each package should NOT
     * be initially installed, even if they are whitelisted, and then removes this map from
     * SystemConfig.
     * Called by UserManagerService when it is constructed.
     */
    public ArrayMap<String, Set<String>> getAndClearPackageToUserTypeBlacklist() {
        ArrayMap<String, Set<String>> r = mPackageToUserTypeBlacklist;
        mPackageToUserTypeBlacklist = new ArrayMap<>(0);
        return r;
    }

    @NonNull
    public ArrayMap<String, ArrayMap<String, String>> getNamedActors() {
        return mNamedActors;
    }

    /**
     * Only use for testing. Do NOT use in production code.
     *
     * @param readPermissions false to create an empty SystemConfig; true to read the permissions.
     */
    @WorkerThread
    public SystemConfig(boolean readPermissions) {
        if (readPermissions) {
            Log.w(TAG, "Constructing a test SystemConfig");
            readAllPermissions();
        } else {
            Log.w(TAG, "Constructing an empty test SystemConfig");
        }
    }

    @WorkerThread
    SystemConfig() {
        readAllPermissions();
    }

    private void readAllPermissions() {
        // Read configuration from system
        readPermissions(Paths.build(Environment.getRootDirectory(), "etc", "sysconfig"), ALLOW_ALL);

        // Read configuration from the old permissions dir
        readPermissions(Paths.build(Environment.getRootDirectory(), "etc", "permissions"), ALLOW_ALL);

        // Vendors are only allowed to customize these
        int vendorPermissionFlag = ALLOW_LIBS | ALLOW_FEATURES | ALLOW_PRIVAPP_PERMISSIONS
                | ALLOW_ASSOCIATIONS;
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
            // For backward compatibility
            vendorPermissionFlag |= (ALLOW_PERMISSIONS | ALLOW_APP_CONFIGS);
        }
        readPermissions(Paths.build(OsEnvironment.getVendorDirectory(), "etc", "sysconfig"), vendorPermissionFlag);
        readPermissions(Paths.build(OsEnvironment.getVendorDirectory(), "etc", "permissions"), vendorPermissionFlag);

        String vendorSkuProperty = SystemProperties.get(VENDOR_SKU_PROPERTY, "");
        if (!vendorSkuProperty.isEmpty()) {
            String vendorSkuDir = "sku_" + vendorSkuProperty;
            readPermissions(Paths.build(OsEnvironment.getVendorDirectory(), "etc", "sysconfig", vendorSkuDir),
                    vendorPermissionFlag);
            readPermissions(Paths.build(OsEnvironment.getVendorDirectory(), "etc", "permissions", vendorSkuDir),
                    vendorPermissionFlag);
        }

        // Allow ODM to customize system configs as much as Vendor, because /odm is another
        // vendor partition other than /vendor.
        int odmPermissionFlag = vendorPermissionFlag;
        readPermissions(Paths.build(OsEnvironment.getOdmDirectory(), "etc", "sysconfig"), odmPermissionFlag);
        readPermissions(Paths.build(OsEnvironment.getOdmDirectory(), "etc", "permissions"), odmPermissionFlag);

        String skuProperty = SystemProperties.get(SKU_PROPERTY, "");
        if (!skuProperty.isEmpty()) {
            String skuDir = "sku_" + skuProperty;

            readPermissions(Paths.build(OsEnvironment.getOdmDirectory(), "etc", "sysconfig", skuDir),
                    odmPermissionFlag);
            readPermissions(Paths.build(OsEnvironment.getOdmDirectory(), "etc", "permissions", skuDir),
                    odmPermissionFlag);
        }

        // Allow OEM to customize these
        int oemPermissionFlag = ALLOW_FEATURES | ALLOW_OEM_PERMISSIONS | ALLOW_ASSOCIATIONS;
        readPermissions(Paths.build(OsEnvironment.getOemDirectory(), "etc", "sysconfig"), oemPermissionFlag);
        readPermissions(Paths.build(OsEnvironment.getOemDirectory(), "etc", "permissions"), oemPermissionFlag);

        // Allow Product to customize all system configs
        readPermissions(Paths.build(OsEnvironment.getProductDirectory(), "etc", "sysconfig"), ALLOW_ALL);
        readPermissions(Paths.build(OsEnvironment.getProductDirectory(), "etc", "permissions"), ALLOW_ALL);

        // Allow /system_ext to customize all system configs
        readPermissions(Paths.build(OsEnvironment.getSystemExtDirectory(), "etc", "sysconfig"), ALLOW_ALL);
        readPermissions(Paths.build(OsEnvironment.getSystemExtDirectory(), "etc", "permissions"), ALLOW_ALL);
    }

    public void readPermissions(@Nullable Path libraryDir, int permissionFlag) {
        // Read permissions from given directory.
        if (libraryDir == null || !libraryDir.exists() || !libraryDir.isDirectory()) {
            if (permissionFlag == ALLOW_ALL) {
                Log.w(TAG, "No directory " + libraryDir + ", skipping");
            }
            return;
        }

        // Iterate over the files in the directory and scan .xml files
        Path platformFile = null;
        for (Path f : libraryDir.listFiles()) {
            if (!f.isFile()) {
                continue;
            }

            // We'll read platform.xml last
            if (f.getUri().getPath().endsWith("etc/permissions/platform.xml")) {
                platformFile = f;
                continue;
            }

            if (!f.getUri().getPath().endsWith(".xml")) {
                Log.i(TAG, "Non-xml file " + f + " in " + libraryDir + " directory, ignoring");
                continue;
            }
            if (!f.canRead()) {
                Log.w(TAG, "Permissions library file " + f + " cannot be read");
                continue;
            }

            readPermissionsFromXml(f, permissionFlag);
        }

        // Read platform permissions last so it will take precedence
        if (platformFile != null) {
            readPermissionsFromXml(platformFile, permissionFlag);
        }
    }

    private void logNotAllowedInPartition(String name, Path permFile, @NonNull XmlPullParser parser) {
        Log.w(TAG, "<" + name + "> not allowed in partition of "
                + permFile + " at " + parser.getPositionDescription());
    }

    private void readPermissionsFromXml(Path permFile, int permissionFlag) {
        StringReader permReader = new StringReader(permFile.getContentAsString());
        Log.i(TAG, "Reading permissions from " + permFile);

//        final boolean lowRam = ActivityManager.isLowRamDeviceStatic();

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(permReader);

            int type;
            //noinspection StatementWithEmptyBody
            while ((type = parser.next()) != parser.START_TAG && type != parser.END_DOCUMENT) {
            }

            if (type != parser.START_TAG) {
                throw new XmlPullParserException("No start tag found");
            }

            if (!parser.getName().equals("permissions") && !parser.getName().equals("config")) {
                throw new XmlPullParserException("Unexpected start tag in " + permFile
                        + ": found " + parser.getName() + ", expected 'permissions' or 'config'");
            }

            final boolean allowAll = permissionFlag == ALLOW_ALL;
            final boolean allowLibs = (permissionFlag & ALLOW_LIBS) != 0;
            final boolean allowFeatures = (permissionFlag & ALLOW_FEATURES) != 0;
            final boolean allowPermissions = (permissionFlag & ALLOW_PERMISSIONS) != 0;
            final boolean allowAppConfigs = (permissionFlag & ALLOW_APP_CONFIGS) != 0;
            final boolean allowPrivappPermissions = (permissionFlag & ALLOW_PRIVAPP_PERMISSIONS)
                    != 0;
            final boolean allowOemPermissions = (permissionFlag & ALLOW_OEM_PERMISSIONS) != 0;
            final boolean allowApiWhitelisting = (permissionFlag & ALLOW_HIDDENAPI_WHITELISTING)
                    != 0;
            final boolean allowAssociations = (permissionFlag & ALLOW_ASSOCIATIONS) != 0;
            while (true) {
                XmlUtils.nextElement(parser);
                if (parser.getEventType() == XmlPullParser.END_DOCUMENT) {
                    break;
                }

                @SysConfigType String name = parser.getName();
                if (name == null) {
                    XmlUtils.skipCurrentTag(parser);
                    continue;
                }
                switch (name) {
                    case SysConfigType.TYPE_GROUP: {
                        if (allowAll) {
                            String gidStr = parser.getAttributeValue(null, "gid");
                            if (gidStr != null) {
                                int gid = android.os.Process.getGidForName(gidStr);
                                mGlobalGids = ArrayUtils.appendInt(mGlobalGids, gid);
                            } else {
                                Log.w(TAG, "<" + name + "> without gid in " + permFile + " at "
                                        + parser.getPositionDescription());
                            }
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_PERMISSION: {
                        if (allowPermissions) {
                            String perm = parser.getAttributeValue(null, "name");
                            if (perm == null) {
                                Log.w(TAG, "<" + name + "> without name in " + permFile + " at "
                                        + parser.getPositionDescription());
                                XmlUtils.skipCurrentTag(parser);
                                break;
                            }
                            perm = perm.intern();
                            readPermission(parser, perm);
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                            XmlUtils.skipCurrentTag(parser);
                        }
                    }
                    break;
                    case SysConfigType.TYPE_ASSIGN_PERMISSION: {
                        if (allowPermissions) {
                            String perm = parser.getAttributeValue(null, "name");
                            if (perm == null) {
                                Log.w(TAG, "<" + name + "> without name in " + permFile
                                        + " at " + parser.getPositionDescription());
                                XmlUtils.skipCurrentTag(parser);
                                break;
                            }
                            String uidStr = parser.getAttributeValue(null, "uid");
                            if (uidStr == null) {
                                Log.w(TAG, "<" + name + "> without uid in " + permFile
                                        + " at " + parser.getPositionDescription());
                                XmlUtils.skipCurrentTag(parser);
                                break;
                            }
                            int uid = Process.getUidForName(uidStr);
                            if (uid < 0) {
                                Log.w(TAG, "<" + name + "> with unknown uid \""
                                        + uidStr + "  in " + permFile + " at "
                                        + parser.getPositionDescription());
                                XmlUtils.skipCurrentTag(parser);
                                break;
                            }
                            perm = perm.intern();
                            Set<String> perms = mSystemPermissions.get(uid);
                            if (perms == null) {
                                perms = new HashSet<>();
                                mSystemPermissions.put(uid, perms);
                            }
                            perms.add(perm);
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_SPLIT_PERMISSION: {
                        if (allowPermissions) {
                            readSplitPermission(parser, permFile);
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                            XmlUtils.skipCurrentTag(parser);
                        }
                    }
                    break;
                    case SysConfigType.TYPE_LIBRARY: {
                        if (allowLibs) {
                            String lname = parser.getAttributeValue(null, "name");
                            String lfile = parser.getAttributeValue(null, "file");
                            String ldependency = parser.getAttributeValue(null, "dependency");
                            if (lname == null) {
                                Log.w(TAG, "<" + name + "> without name in " + permFile + " at "
                                        + parser.getPositionDescription());
                            } else if (lfile == null) {
                                Log.w(TAG, "<" + name + "> without file in " + permFile + " at "
                                        + parser.getPositionDescription());
                            } else {
                                //Log.i(TAG, "Got library " + lname + " in " + lfile);
                                SharedLibraryEntry entry = new SharedLibraryEntry(lname, lfile,
                                        ldependency == null ? new String[0] : ldependency.split(":"));
                                mSharedLibraries.put(lname, entry);
                            }
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_FEATURE: {
                        if (allowFeatures) {
                            String fname = parser.getAttributeValue(null, "name");
                            int fversion = XmlUtils.readIntAttribute(parser, "version", 0);
                            boolean allowed = true;  // FIXME
//                            if (!lowRam) {
//                                allowed = true;
//                            } else {
//                            String notLowRam = parser.getAttributeValue(null, "notLowRam");
//                            allowed = !"true".equals(notLowRam);
//                            }
                            if (fname == null) {
                                Log.w(TAG, "<" + name + "> without name in " + permFile + " at "
                                        + parser.getPositionDescription());
                            } else if (allowed) {
                                addFeature(fname, fversion);
                            }
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_UNAVAILABLE_FEATURE: {
                        if (allowFeatures) {
                            String fname = parser.getAttributeValue(null, "name");
                            if (fname == null) {
                                Log.w(TAG, "<" + name + "> without name in " + permFile
                                        + " at " + parser.getPositionDescription());
                            } else {
                                mUnavailableFeatures.add(fname);
                            }
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_ALLOW_IN_POWER_SAVE_EXCEPT_IDLE: {
                        if (allowAll) {
                            String pkgname = parser.getAttributeValue(null, "package");
                            if (pkgname == null) {
                                Log.w(TAG, "<" + name + "> without package in "
                                        + permFile + " at " + parser.getPositionDescription());
                            } else {
                                mAllowInPowerSaveExceptIdle.add(pkgname);
                            }
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_ALLOW_IN_POWER_SAVE: {
                        if (allowAll) {
                            String pkgname = parser.getAttributeValue(null, "package");
                            if (pkgname == null) {
                                Log.w(TAG, "<" + name + "> without package in "
                                        + permFile + " at " + parser.getPositionDescription());
                            } else {
                                mAllowInPowerSave.add(pkgname);
                            }
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_ALLOW_IN_DATA_USAGE_SAVE: {
                        if (allowAll) {
                            String pkgname = parser.getAttributeValue(null, "package");
                            if (pkgname == null) {
                                Log.w(TAG, "<" + name + "> without package in "
                                        + permFile + " at " + parser.getPositionDescription());
                            } else {
                                mAllowInDataUsageSave.add(pkgname);
                            }
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_ALLOW_UNTHROTTLED_LOCATION: {
                        if (allowAll) {
                            String pkgname = parser.getAttributeValue(null, "package");
                            if (pkgname == null) {
                                Log.w(TAG, "<" + name + "> without package in "
                                        + permFile + " at " + parser.getPositionDescription());
                            } else {
                                mAllowUnthrottledLocation.add(pkgname);
                            }
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_ALLOW_IGNORE_LOCATION_SETTINGS: {
                        if (allowAll) {
                            String pkgname = parser.getAttributeValue(null, "package");
                            if (pkgname == null) {
                                Log.w(TAG, "<" + name + "> without package in "
                                        + permFile + " at " + parser.getPositionDescription());
                            } else {
                                mAllowIgnoreLocationSettings.add(pkgname);
                            }
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_ALLOW_IMPLICIT_BROADCAST: {
                        if (allowAll) {
                            String action = parser.getAttributeValue(null, "action");
                            if (action == null) {
                                Log.w(TAG, "<" + name + "> without action in "
                                        + permFile + " at " + parser.getPositionDescription());
                            } else {
                                mAllowImplicitBroadcasts.add(action);
                            }
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_APP_LINK: {
                        if (allowAppConfigs) {
                            String pkgname = parser.getAttributeValue(null, "package");
                            if (pkgname == null) {
                                Log.w(TAG, "<" + name + "> without package in " + permFile
                                        + " at " + parser.getPositionDescription());
                            } else {
                                mLinkedApps.add(pkgname);
                            }
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_SYSTEM_USER_WHITELISTED_APP: {
                        if (allowAppConfigs) {
                            String pkgname = parser.getAttributeValue(null, "package");
                            if (pkgname == null) {
                                Log.w(TAG, "<" + name + "> without package in "
                                        + permFile + " at " + parser.getPositionDescription());
                            } else {
                                mSystemUserWhitelistedApps.add(pkgname);
                            }
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_SYSTEM_USER_BLACKLISTED_APP: {
                        if (allowAppConfigs) {
                            String pkgname = parser.getAttributeValue(null, "package");
                            if (pkgname == null) {
                                Log.w(TAG, "<" + name + "> without package in "
                                        + permFile + " at " + parser.getPositionDescription());
                            } else {
                                mSystemUserBlacklistedApps.add(pkgname);
                            }
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_DEFAULT_ENABLED_VR_APP: {
                        if (allowAppConfigs) {
                            String pkgname = parser.getAttributeValue(null, "package");
                            String clsname = parser.getAttributeValue(null, "class");
                            if (pkgname == null) {
                                Log.w(TAG, "<" + name + "> without package in "
                                        + permFile + " at " + parser.getPositionDescription());
                            } else if (clsname == null) {
                                Log.w(TAG, "<" + name + "> without class in "
                                        + permFile + " at " + parser.getPositionDescription());
                            } else {
                                mDefaultVrComponents.add(new ComponentName(pkgname, clsname));
                            }
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_COMPONENT_OVERRIDE: {
                        readComponentOverrides(parser, permFile);
                    }
                    break;
                    case SysConfigType.TYPE_BACKUP_TRANSPORT_WHITELISTED_SERVICE: {
                        if (allowFeatures) {
                            String serviceName = parser.getAttributeValue(null, "service");
                            if (serviceName == null) {
                                Log.w(TAG, "<" + name + "> without service in "
                                        + permFile + " at " + parser.getPositionDescription());
                            } else {
                                ComponentName cn = ComponentName.unflattenFromString(serviceName);
                                if (cn == null) {
                                    Log.w(TAG, "<" + name + "> with invalid service name "
                                            + serviceName + " in " + permFile
                                            + " at " + parser.getPositionDescription());
                                } else {
                                    mBackupTransportWhitelist.add(cn);
                                }
                            }
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_ASSOCIATED_APP: {
                        if (allowAppConfigs) {
                            String pkgname = parser.getAttributeValue(null, "package");
                            String carrierPkgname = parser.getAttributeValue(null,
                                    "carrierAppPackage");
                            if (pkgname == null || carrierPkgname == null) {
                                Log.w(TAG, "<" + name
                                        + "> without package or carrierAppPackage in " + permFile
                                        + " at " + parser.getPositionDescription());
                            } else {
                                // APKs added to system images via OTA should specify the addedInSdk
                                // attribute, otherwise they may be enabled-by-default in too many
                                // cases. See CarrierAppUtils for more info.
                                int addedInSdk = CarrierAssociatedAppEntry.SDK_UNSPECIFIED;
                                String addedInSdkStr = parser.getAttributeValue(null, "addedInSdk");
                                if (!TextUtils.isEmpty(addedInSdkStr)) {
                                    try {
                                        addedInSdk = Integer.parseInt(addedInSdkStr);
                                    } catch (NumberFormatException e) {
                                        Log.w(TAG, "<" + name + "> addedInSdk not an integer in "
                                                + permFile + " at "
                                                + parser.getPositionDescription());
                                        XmlUtils.skipCurrentTag(parser);
                                        break;
                                    }
                                }
                                List<CarrierAssociatedAppEntry> associatedPkgs =
                                        mDisabledUntilUsedPreinstalledCarrierAssociatedApps.get(
                                                carrierPkgname);
                                if (associatedPkgs == null) {
                                    associatedPkgs = new ArrayList<>();
                                    mDisabledUntilUsedPreinstalledCarrierAssociatedApps.put(
                                            carrierPkgname, associatedPkgs);
                                }
                                associatedPkgs.add(
                                        new CarrierAssociatedAppEntry(pkgname, addedInSdk));
                            }
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_APP: {
                        if (allowAppConfigs) {
                            String pkgname = parser.getAttributeValue(null, "package");
                            if (pkgname == null) {
                                Log.w(TAG,
                                        "<" + name + "> without "
                                                + "package in " + permFile + " at "
                                                + parser.getPositionDescription());
                            } else {
                                mDisabledUntilUsedPreinstalledCarrierApps.add(pkgname);
                            }
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_PRIVAPP_PERMISSIONS: {
                        if (allowPrivappPermissions) {
                            // privapp permissions from system, vendor, product and system_ext
                            // partitions are stored separately. This is to prevent xml files in
                            // the vendor partition from granting permissions to priv apps in the
                            // system partition and vice versa.
                            boolean vendor = permFile.getFilePath().startsWith(
                                    OsEnvironment.getVendorDirectory().getFilePath() + "/")
                                    || permFile.getFilePath().startsWith(
                                    OsEnvironment.getOdmDirectory().getFilePath() + "/");
                            boolean product = permFile.getFilePath().startsWith(
                                    OsEnvironment.getProductDirectory().getFilePath() + "/");
                            boolean systemExt = permFile.getFilePath().startsWith(
                                    OsEnvironment.getSystemExtDirectory().getFilePath() + "/");
                            if (vendor) {
                                readPrivAppPermissions(parser, mVendorPrivAppPermissions,
                                        mVendorPrivAppDenyPermissions);
                            } else if (product) {
                                readPrivAppPermissions(parser, mProductPrivAppPermissions,
                                        mProductPrivAppDenyPermissions);
                            } else if (systemExt) {
                                readPrivAppPermissions(parser, mSystemExtPrivAppPermissions,
                                        mSystemExtPrivAppDenyPermissions);
                            } else {
                                readPrivAppPermissions(parser, mPrivAppPermissions,
                                        mPrivAppDenyPermissions);
                            }
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                            XmlUtils.skipCurrentTag(parser);
                        }
                    }
                    break;
                    case SysConfigType.TYPE_OEM_PERMISSIONS: {
                        if (allowOemPermissions) {
                            readOemPermissions(parser);
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                            XmlUtils.skipCurrentTag(parser);
                        }
                    }
                    break;
                    case SysConfigType.TYPE_HIDDEN_API_WHITELISTED_APP: {
                        if (allowApiWhitelisting) {
                            String pkgname = parser.getAttributeValue(null, "package");
                            if (pkgname == null) {
                                Log.w(TAG, "<" + name + "> without package in "
                                        + permFile + " at " + parser.getPositionDescription());
                            } else {
                                mHiddenApiPackageWhitelist.add(pkgname);
                            }
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_ALLOW_ASSOCIATION: {
                        if (allowAssociations) {
                            String target = parser.getAttributeValue(null, "target");
                            if (target == null) {
                                Log.w(TAG, "<" + name + "> without target in " + permFile
                                        + " at " + parser.getPositionDescription());
                                XmlUtils.skipCurrentTag(parser);
                                break;
                            }
                            String allowed = parser.getAttributeValue(null, "allowed");
                            if (allowed == null) {
                                Log.w(TAG, "<" + name + "> without allowed in " + permFile
                                        + " at " + parser.getPositionDescription());
                                XmlUtils.skipCurrentTag(parser);
                                break;
                            }
                            target = target.intern();
                            allowed = allowed.intern();
                            Set<String> associations = mAllowedAssociations.get(target);
                            if (associations == null) {
                                associations = new HashSet<>();
                                mAllowedAssociations.put(target, associations);
                            }
                            Log.i(TAG, "Adding association: " + target + " <- " + allowed);
                            associations.add(allowed);
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_APP_DATA_ISOLATION_WHITELISTED_APP: {
                        String pkgname = parser.getAttributeValue(null, "package");
                        if (pkgname == null) {
                            Log.w(TAG, "<" + name + "> without package in " + permFile
                                    + " at " + parser.getPositionDescription());
                        } else {
                            mAppDataIsolationWhitelistedApps.add(pkgname);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_BUGREPORT_WHITELISTED: {
                        String pkgname = parser.getAttributeValue(null, "package");
                        if (pkgname == null) {
                            Log.w(TAG, "<" + name + "> without package in " + permFile
                                    + " at " + parser.getPositionDescription());
                        } else {
                            mBugreportWhitelistedPackages.add(pkgname);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_INSTALL_IN_USER_TYPE: {
                        // NB: We allow any directory permission to declare install-in-user-type.
                        readInstallInUserType(parser,
                                mPackageToUserTypeWhitelist, mPackageToUserTypeBlacklist);
                    }
                    break;
                    case SysConfigType.TYPE_NAMED_ACTOR: {
                        String namespace = TextUtilsCompat.safeIntern(
                                parser.getAttributeValue(null, "namespace"));
                        String actorName = parser.getAttributeValue(null, "name");
                        String pkgName = TextUtilsCompat.safeIntern(
                                parser.getAttributeValue(null, "package"));
                        if (TextUtils.isEmpty(namespace)) {
                            Log.e(TAG, "<" + name + "> without namespace in " + permFile
                                    + " at " + parser.getPositionDescription());
                        } else if (TextUtils.isEmpty(actorName)) {
                            Log.e(TAG, "<" + name + "> without actor name in " + permFile
                                    + " at " + parser.getPositionDescription());
                        } else if (TextUtils.isEmpty(pkgName)) {
                            Log.e(TAG, "<" + name + "> without package name in " + permFile
                                    + " at " + parser.getPositionDescription());
                        } else if ("android".equalsIgnoreCase(namespace)) {
                            throw new IllegalStateException("Defining " + actorName + " as "
                                    + pkgName + " for the android namespace is not allowed");
                        } else {
                            ArrayMap<String, String> nameToPkgMap = mNamedActors.get(namespace);
                            if (nameToPkgMap == null) {
                                nameToPkgMap = new ArrayMap<>();
                                mNamedActors.put(namespace, nameToPkgMap);
                            } else if (nameToPkgMap.containsKey(actorName)) {
                                String existing = nameToPkgMap.get(actorName);
                                throw new IllegalStateException("Duplicate actor definition for "
                                        + namespace + "/" + actorName
                                        + "; defined as both " + existing + " and " + pkgName);
                            }

                            nameToPkgMap.put(actorName, pkgName);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_ROLLBACK_WHITELISTED_APP: {
                        String pkgname = parser.getAttributeValue(null, "package");
                        if (pkgname == null) {
                            Log.w(TAG, "<" + name + "> without package in " + permFile
                                    + " at " + parser.getPositionDescription());
                        } else {
                            mRollbackWhitelistedPackages.add(pkgname);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    case SysConfigType.TYPE_WHITELISTED_STAGED_INSTALLER: {
                        if (allowAppConfigs) {
                            String pkgname = parser.getAttributeValue(null, "package");
                            if (pkgname == null) {
                                Log.w(TAG, "<" + name + "> without package in " + permFile
                                        + " at " + parser.getPositionDescription());
                            } else {
                                mWhitelistedStagedInstallers.add(pkgname);
                            }
                        } else {
                            logNotAllowedInPartition(name, permFile, parser);
                        }
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                    default: {
                        Log.w(TAG, "Tag " + name + " is unknown in "
                                + permFile + " at " + parser.getPositionDescription());
                        XmlUtils.skipCurrentTag(parser);
                    }
                    break;
                }
            }
        } catch (XmlPullParserException | IOException e) {
            Log.w(TAG, "Got exception parsing permissions.", e);
        } finally {
            IoUtils.closeQuietly(permReader);
        }

        // Some devices can be field-converted to FBE, so offer to splice in
        // those features if not already defined by the static config
        // FIXME
//        if (StorageManager.isFileEncryptedNativeOnly()) {
//            addFeature(PackageManager.FEATURE_FILE_BASED_ENCRYPTION, 0);
//            addFeature(PackageManager.FEATURE_SECURELY_REMOVES_USERS, 0);
//        }
//
//        // Help legacy devices that may not have updated their static config
//        if (StorageManager.hasAdoptable()) {
//            addFeature(PackageManager.FEATURE_ADOPTABLE_STORAGE, 0);
//        }
//
//        if (ActivityManager.isLowRamDeviceStatic()) {
//            addFeature(PackageManager.FEATURE_RAM_LOW, 0);
//        } else {
//            addFeature(PackageManager.FEATURE_RAM_NORMAL, 0);
//        }
//
//        if (IncrementalManager.isFeatureEnabled()) {
//            addFeature(PackageManager.FEATURE_INCREMENTAL_DELIVERY, 0);
//        }
//
//        if (PackageManager.APP_ENUMERATION_ENABLED_BY_DEFAULT) {
//            addFeature(PackageManager.FEATURE_APP_ENUMERATION, 0);
//        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addFeature(PackageManager.FEATURE_IPSEC_TUNNELS, 0);
        }

        for (String featureName : mUnavailableFeatures) {
            removeFeature(featureName);
        }
    }

    private void addFeature(String name, int version) {
        FeatureInfo fi = mAvailableFeatures.get(name);
        if (fi == null) {
            fi = new FeatureInfo();
            fi.name = name;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                fi.version = version;
            }
            mAvailableFeatures.put(name, fi);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                fi.version = Math.max(fi.version, version);
            }
        }
    }

    private void removeFeature(String name) {
        if (mAvailableFeatures.remove(name) != null) {
            Log.d(TAG, "Removed unavailable feature " + name);
        }
    }

    void readPermission(XmlPullParser parser, String name)
            throws IOException, XmlPullParserException {
        if (mPermissions.containsKey(name)) {
            throw new IllegalStateException("Duplicate permission definition for " + name);
        }

        final boolean perUser = XmlUtils.readBooleanAttribute(parser, "perUser", false);
        final PermissionEntry perm = new PermissionEntry(name, perUser);
        mPermissions.put(name, perm);

        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG
                || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG
                    || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if ("group".equals(tagName)) {
                String gidStr = parser.getAttributeValue(null, "gid");
                if (gidStr != null) {
                    int gid = Process.getGidForName(gidStr);
                    perm.gids = ArrayUtils.appendInt(perm.gids, gid);
                } else {
                    Log.w(TAG, "<group> without gid at "
                            + parser.getPositionDescription());
                }
            }
            XmlUtils.skipCurrentTag(parser);
        }
    }

    private void readPrivAppPermissions(@NonNull XmlPullParser parser,
                                        ArrayMap<String, Set<String>> grantMap,
                                        ArrayMap<String, Set<String>> denyMap)
            throws IOException, XmlPullParserException {
        String packageName = parser.getAttributeValue(null, "package");
        if (TextUtils.isEmpty(packageName)) {
            Log.w(TAG, "package is required for <privapp-permissions> in "
                    + parser.getPositionDescription());
            return;
        }

        Set<String> permissions = grantMap.get(packageName);
        if (permissions == null) {
            permissions = new HashSet<>();
        }
        Set<String> denyPermissions = denyMap.get(packageName);
        int depth = parser.getDepth();
        while (XmlUtils.nextElementWithin(parser, depth)) {
            String name = parser.getName();
            if ("permission".equals(name)) {
                String permName = parser.getAttributeValue(null, "name");
                if (TextUtils.isEmpty(permName)) {
                    Log.w(TAG, "name is required for <permission> in "
                            + parser.getPositionDescription());
                    continue;
                }
                permissions.add(permName);
            } else if ("deny-permission".equals(name)) {
                String permName = parser.getAttributeValue(null, "name");
                if (TextUtils.isEmpty(permName)) {
                    Log.w(TAG, "name is required for <deny-permission> in "
                            + parser.getPositionDescription());
                    continue;
                }
                if (denyPermissions == null) {
                    denyPermissions = new HashSet<>();
                }
                denyPermissions.add(permName);
            }
        }
        grantMap.put(packageName, permissions);
        if (denyPermissions != null) {
            denyMap.put(packageName, denyPermissions);
        }
    }

    private void readInstallInUserType(@NonNull XmlPullParser parser,
                                       Map<String, Set<String>> doInstallMap,
                                       Map<String, Set<String>> nonInstallMap)
            throws IOException, XmlPullParserException {
        final String packageName = parser.getAttributeValue(null, "package");
        if (TextUtils.isEmpty(packageName)) {
            Log.w(TAG, "package is required for <install-in-user-type> in "
                    + parser.getPositionDescription());
            return;
        }

        Set<String> userTypesYes = doInstallMap.get(packageName);
        Set<String> userTypesNo = nonInstallMap.get(packageName);
        final int depth = parser.getDepth();
        while (XmlUtils.nextElementWithin(parser, depth)) {
            final String name = parser.getName();
            if ("install-in".equals(name)) {
                final String userType = parser.getAttributeValue(null, "user-type");
                if (TextUtils.isEmpty(userType)) {
                    Log.w(TAG, "user-type is required for <install-in-user-type> in "
                            + parser.getPositionDescription());
                    continue;
                }
                if (userTypesYes == null) {
                    userTypesYes = new HashSet<>();
                    doInstallMap.put(packageName, userTypesYes);
                }
                userTypesYes.add(userType);
            } else if ("do-not-install-in".equals(name)) {
                final String userType = parser.getAttributeValue(null, "user-type");
                if (TextUtils.isEmpty(userType)) {
                    Log.w(TAG, "user-type is required for <install-in-user-type> in "
                            + parser.getPositionDescription());
                    continue;
                }
                if (userTypesNo == null) {
                    userTypesNo = new HashSet<>();
                    nonInstallMap.put(packageName, userTypesNo);
                }
                userTypesNo.add(userType);
            } else {
                Log.w(TAG, "unrecognized tag in <install-in-user-type> in "
                        + parser.getPositionDescription());
            }
        }
    }

    void readOemPermissions(@NonNull XmlPullParser parser) throws IOException, XmlPullParserException {
        final String packageName = parser.getAttributeValue(null, "package");
        if (TextUtils.isEmpty(packageName)) {
            Log.w(TAG, "package is required for <oem-permissions> in "
                    + parser.getPositionDescription());
            return;
        }

        ArrayMap<String, Boolean> permissions = mOemPermissions.get(packageName);
        if (permissions == null) {
            permissions = new ArrayMap<>();
        }
        final int depth = parser.getDepth();
        while (XmlUtils.nextElementWithin(parser, depth)) {
            final String name = parser.getName();
            if ("permission".equals(name)) {
                final String permName = parser.getAttributeValue(null, "name");
                if (TextUtils.isEmpty(permName)) {
                    Log.w(TAG, "name is required for <permission> in "
                            + parser.getPositionDescription());
                    continue;
                }
                permissions.put(permName, Boolean.TRUE);
            } else if ("deny-permission".equals(name)) {
                String permName = parser.getAttributeValue(null, "name");
                if (TextUtils.isEmpty(permName)) {
                    Log.w(TAG, "name is required for <deny-permission> in "
                            + parser.getPositionDescription());
                    continue;
                }
                permissions.put(permName, Boolean.FALSE);
            }
        }
        mOemPermissions.put(packageName, permissions);
    }

    private void readSplitPermission(@NonNull XmlPullParser parser, Path permFile)
            throws IOException, XmlPullParserException {
        String splitPerm = parser.getAttributeValue(null, "name");
        if (splitPerm == null) {
            Log.w(TAG, "<split-permission> without name in " + permFile + " at "
                    + parser.getPositionDescription());
            XmlUtils.skipCurrentTag(parser);
            return;
        }
        String targetSdkStr = parser.getAttributeValue(null, "targetSdk");
        int targetSdk = Build.VERSION_CODES.CUR_DEVELOPMENT + 1;
        if (!TextUtils.isEmpty(targetSdkStr)) {
            try {
                targetSdk = Integer.parseInt(targetSdkStr);
            } catch (NumberFormatException e) {
                Log.w(TAG, "<split-permission> targetSdk not an integer in " + permFile + " at "
                        + parser.getPositionDescription());
                XmlUtils.skipCurrentTag(parser);
                return;
            }
        }
        final int depth = parser.getDepth();
        List<String> newPermissions = new ArrayList<>();
        while (XmlUtils.nextElementWithin(parser, depth)) {
            String name = parser.getName();
            if ("new-permission".equals(name)) {
                final String newName = parser.getAttributeValue(null, "name");
                if (TextUtils.isEmpty(newName)) {
                    Log.w(TAG, "name is required for <new-permission> in "
                            + parser.getPositionDescription());
                    continue;
                }
                newPermissions.add(newName);
            } else {
                XmlUtils.skipCurrentTag(parser);
            }
        }
        if (!newPermissions.isEmpty()) {
            mSplitPermissions.add(new SplitPermissionInfo(splitPerm, newPermissions, targetSdk));
        }
    }

    private void readComponentOverrides(@NonNull XmlPullParser parser, Path permFile)
            throws IOException, XmlPullParserException {
        String pkgname = parser.getAttributeValue(null, "package");
        if (pkgname == null) {
            Log.w(TAG, "<component-override> without package in "
                    + permFile + " at " + parser.getPositionDescription());
            return;
        }

        pkgname = pkgname.intern();

        final int depth = parser.getDepth();
        while (XmlUtils.nextElementWithin(parser, depth)) {
            if ("component".equals(parser.getName())) {
                String clsname = parser.getAttributeValue(null, "class");
                String enabled = parser.getAttributeValue(null, "enabled");
                if (clsname == null) {
                    Log.w(TAG, "<component> without class in "
                            + permFile + " at " + parser.getPositionDescription());
                    return;
                } else if (enabled == null) {
                    Log.w(TAG, "<component> without enabled in "
                            + permFile + " at " + parser.getPositionDescription());
                    return;
                }

                if (clsname.startsWith(".")) {
                    clsname = pkgname + clsname;
                }

                clsname = clsname.intern();

                ArrayMap<String, Boolean> componentEnabledStates =
                        mPackageComponentEnabledState.get(pkgname);
                if (componentEnabledStates == null) {
                    componentEnabledStates = new ArrayMap<>();
                    mPackageComponentEnabledState.put(pkgname,
                            componentEnabledStates);
                }

                componentEnabledStates.put(clsname, !"false".equals(enabled));
            }
        }
    }
}