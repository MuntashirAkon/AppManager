/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.HiddenUtil;

/**
 * Overall information about the contents of a package.  This corresponds
 * to all of the information collected from AndroidManifest.xml.
 */
@SuppressWarnings("unused")
@RefineAs(PackageInfo.class)
public class PackageInfoHidden implements Parcelable {
    /**
     * The name of this package.  From the &lt;manifest&gt; tag's "name"
     * attribute.
     */
    @NonNull
    public String packageName = HiddenUtil.throwUOE();

    /**
     * The names of any installed split APKs for this package.
     */
    @NonNull
    public String[] splitNames = HiddenUtil.throwUOE();

    /**
     * @deprecated Use {@link #getLongVersionCode()} instead, which includes both
     * this and the additional
     * {@link android.R.styleable#AndroidManifest_versionCodeMajor versionCodeMajor} attribute.
     * The version number of this package, as specified by the &lt;manifest&gt;
     * tag's {@link android.R.styleable#AndroidManifest_versionCode versionCode}
     * attribute.
     * @see #getLongVersionCode()
     */
    @Deprecated
    public int versionCode = HiddenUtil.throwUOE();

    /**
     * The major version number of this package, as specified by the &lt;manifest&gt;
     * tag's {@link android.R.styleable#AndroidManifest_versionCode versionCodeMajor}
     * attribute.
     * @see #getLongVersionCode()
     */
    public int versionCodeMajor = HiddenUtil.throwUOE();

    /**
     * Return {@link android.R.styleable#AndroidManifest_versionCode versionCode} and
     * {@link android.R.styleable#AndroidManifest_versionCodeMajor versionCodeMajor} combined
     * together as a single long value.  The
     * {@link android.R.styleable#AndroidManifest_versionCodeMajor versionCodeMajor} is placed in
     * the upper 32 bits.
     */
    public long getLongVersionCode() {
        return HiddenUtil.throwUOE();
    }

    /**
     * Set the full version code in this PackageInfo, updating {@link #versionCode}
     * with the lower bits.
     * @see #getLongVersionCode()
     */
    public void setLongVersionCode(long longVersionCode) {HiddenUtil.throwUOE(longVersionCode);}

    /**
     * Internal implementation for composing a minor and major version code in to
     * a single long version code.
     */
    public static long composeLongVersionCode(int major, int minor) {
        return HiddenUtil.throwUOE(major, minor);
    }

    /**
     * The version name of this package, as specified by the &lt;manifest&gt;
     * tag's {@link android.R.styleable#AndroidManifest_versionName versionName}
     * attribute, or null if there was none.
     */
    @Nullable
    public String versionName = HiddenUtil.throwUOE();

    /**
     * The revision number of the base APK for this package, as specified by the
     * &lt;manifest&gt; tag's
     * {@link android.R.styleable#AndroidManifest_revisionCode revisionCode}
     * attribute.
     */
    public int baseRevisionCode = HiddenUtil.throwUOE();

    /**
     * The revision number of any split APKs for this package, as specified by
     * the &lt;manifest&gt; tag's
     * {@link android.R.styleable#AndroidManifest_revisionCode revisionCode}
     * attribute. Indexes are a 1:1 mapping against {@link #splitNames}.
     */
    @NonNull
    public int[] splitRevisionCodes = HiddenUtil.throwUOE();

    /**
     * The shared user ID name of this package, as specified by the &lt;manifest&gt;
     * tag's {@link android.R.styleable#AndroidManifest_sharedUserId sharedUserId}
     * attribute.
     */
    @Nullable
    public String sharedUserId = HiddenUtil.throwUOE();

    /**
     * The shared user ID label of this package, as specified by the &lt;manifest&gt;
     * tag's {@link AndroidManifest_sharedUserLabel sharedUserLabel}
     * attribute.
     */
    public int sharedUserLabel = HiddenUtil.throwUOE();

    /**
     * Information collected from the &lt;application&gt; tag, or null if
     * there was none.
     */
    @Nullable
    public ApplicationInfo applicationInfo = HiddenUtil.throwUOE();

    /**
     * The time at which the app was first installed.  Units are as
     * per {@link System#currentTimeMillis()}.
     */
    public long firstInstallTime = HiddenUtil.throwUOE();

    /**
     * The time at which the app was last updated.  Units are as
     * per {@link System#currentTimeMillis()}.
     */
    public long lastUpdateTime = HiddenUtil.throwUOE();

    /**
     * All kernel group-IDs that have been assigned to this package.
     * This is only filled in if the flag {@link PackageManager#GET_GIDS} was set.
     */
    @Nullable
    public int[] gids = HiddenUtil.throwUOE();

    /**
     * Array of all {@link android.R.styleable#AndroidManifestActivity
     * &lt;activity&gt;} tags included under &lt;application&gt;,
     * or null if there were none.  This is only filled in if the flag
     * {@link PackageManager#GET_ACTIVITIES} was set.
     */
    @Nullable
    public ActivityInfo[] activities = HiddenUtil.throwUOE();

    /**
     * Array of all {@link android.R.styleable#AndroidManifestReceiver
     * &lt;receiver&gt;} tags included under &lt;application&gt;,
     * or null if there were none.  This is only filled in if the flag
     * {@link PackageManager#GET_RECEIVERS} was set.
     */
    @Nullable
    public ActivityInfo[] receivers = HiddenUtil.throwUOE();

    /**
     * Array of all {@link android.R.styleable#AndroidManifestService
     * &lt;service&gt;} tags included under &lt;application&gt;,
     * or null if there were none.  This is only filled in if the flag
     * {@link PackageManager#GET_SERVICES} was set.
     */
    @Nullable
    public ServiceInfo[] services = HiddenUtil.throwUOE();

    /**
     * Array of all {@link android.R.styleable#AndroidManifestProvider
     * &lt;provider&gt;} tags included under &lt;application&gt;,
     * or null if there were none.  This is only filled in if the flag
     * {@link PackageManager#GET_PROVIDERS} was set.
     */
    @Nullable
    public ProviderInfo[] providers = HiddenUtil.throwUOE();

    /**
     * Array of all {@link android.R.styleable#AndroidManifestInstrumentation
     * &lt;instrumentation&gt;} tags included under &lt;manifest&gt;,
     * or null if there were none.  This is only filled in if the flag
     * {@link PackageManager#GET_INSTRUMENTATION} was set.
     */
    @Nullable
    public InstrumentationInfo[] instrumentation = HiddenUtil.throwUOE();

    /**
     * Array of all {@link android.R.styleable#AndroidManifestPermission
     * &lt;permission&gt;} tags included under &lt;manifest&gt;,
     * or null if there were none.  This is only filled in if the flag
     * {@link PackageManager#GET_PERMISSIONS} was set.
     */
    @Nullable
    public PermissionInfo[] permissions = HiddenUtil.throwUOE();

    /**
     * Array of all {@link android.R.styleable#AndroidManifestUsesPermission
     * &lt;uses-permission&gt;} tags included under &lt;manifest&gt;,
     * or null if there were none.  This is only filled in if the flag
     * {@link PackageManager#GET_PERMISSIONS} was set.  This list includes
     * all permissions requested, even those that were not granted or known
     * by the system at install time.
     */
    @Nullable
    public String[] requestedPermissions = HiddenUtil.throwUOE();

    /**
     * Array of flags of all {@link android.R.styleable#AndroidManifestUsesPermission
     * &lt;uses-permission&gt;} tags included under &lt;manifest&gt;,
     * or null if there were none.  This is only filled in if the flag
     * {@link PackageManager#GET_PERMISSIONS} was set.  Each value matches
     * the corresponding entry in {@link #requestedPermissions}, and will have
     * the flags {@link #REQUESTED_PERMISSION_GRANTED}, {@link #REQUESTED_PERMISSION_IMPLICIT}, and
     * {@link #REQUESTED_PERMISSION_NEVER_FOR_LOCATION} set as appropriate.
     */
    @Nullable
    public int[] requestedPermissionsFlags = HiddenUtil.throwUOE();

    /**
     * Array of all {@link android.R.styleable#AndroidManifestAttribution
     * &lt;attribution&gt;} tags included under &lt;manifest&gt;, or null if there were none. This
     * is only filled if the flag {@link PackageManager#GET_ATTRIBUTIONS_LONG} was set.
     */
    @SuppressWarnings({"ArrayReturn", "NullableCollection"})
    @Nullable
    public Attribution[] attributions = HiddenUtil.throwUOE();

    /**
     * The time at which the app was archived for the user.  Units are as
     * per {@link System#currentTimeMillis()}.
     *
     */
    private long mArchiveTimeMillis = HiddenUtil.throwUOE();

    /**
     * Flag for {@link #requestedPermissionsFlags}: the requested permission
     * is required for the application to run; the user can not optionally
     * disable it.  Currently all permissions are required.
     *
     * @removed We do not support required permissions.
     */
    public static final int REQUESTED_PERMISSION_REQUIRED = 0x00000001;

    /**
     * Flag for {@link #requestedPermissionsFlags}: the requested permission
     * is currently granted to the application.
     */
    public static final int REQUESTED_PERMISSION_GRANTED = 0x00000002;

    /**
     * Flag for {@link #requestedPermissionsFlags}: the requested permission has
     * declared {@code neverForLocation} in their manifest as a strong assertion
     * by a developer that they will never use this permission to derive the
     * physical location of the device, regardless of
     * {@link android.Manifest.permission#ACCESS_FINE_LOCATION} and/or
     * {@link android.Manifest.permission#ACCESS_COARSE_LOCATION} being granted.
     */
    public static final int REQUESTED_PERMISSION_NEVER_FOR_LOCATION = 0x00010000;

    /**
     * Flag for {@link #requestedPermissionsFlags}: the requested permission was
     * not explicitly requested via uses-permission, but was instead implicitly
     * requested (e.g., for version compatibility reasons).
     */
    public static final int REQUESTED_PERMISSION_IMPLICIT = 0x00000004;

    /**
     * Array of all signatures read from the package file. This is only filled
     * in if the flag {@link PackageManager#GET_SIGNATURES} was set. A package
     * must be signed with at least one certificate which is at position zero.
     * The package can be signed with additional certificates which appear as
     * subsequent entries.
     * <p>
     * <strong>Note:</strong> Signature ordering is not guaranteed to be
     * stable which means that a package signed with certificates A and B is
     * equivalent to being signed with certificates B and A. This means that
     * in case multiple signatures are reported you cannot assume the one at
     * the first position to be the same across updates.
     * <p>
     * <strong>Deprecated</strong> This has been replaced by the
     * {@link PackageInfo#signingInfo} field, which takes into
     * account signing certificate rotation.  For backwards compatibility in
     * the event of signing certificate rotation, this will return the oldest
     * reported signing certificate, so that an application will appear to
     * callers as though no rotation occurred.
     *
     * @deprecated use {@code signingInfo} instead
     */
    @Deprecated
    @Nullable
    public Signature[] signatures = HiddenUtil.throwUOE();

    /**
     * Signing information read from the package file, potentially
     * including past signing certificates no longer used after signing
     * certificate rotation.  This is only filled in if
     * the flag {@link PackageManager#GET_SIGNING_CERTIFICATES} was set.
     * <p>
     * Use this field instead of the deprecated {@code signatures} field.
     * See {@link SigningInfo} for more information on its contents.
     */
    @Nullable
    public SigningInfo signingInfo = HiddenUtil.throwUOE();

    /**
     * Application specified preferred configuration
     * {@link android.R.styleable#AndroidManifestUsesConfiguration
     * &lt;uses-configuration&gt;} tags included under &lt;manifest&gt;,
     * or null if there were none. This is only filled in if the flag
     * {@link PackageManager#GET_CONFIGURATIONS} was set.
     */
    @Nullable
    public ConfigurationInfo[] configPreferences = HiddenUtil.throwUOE();

    /**
     * Features that this application has requested.
     *
     * @see FeatureInfo#FLAG_REQUIRED
     */
    @Nullable
    public FeatureInfo[] reqFeatures = HiddenUtil.throwUOE();

    /**
     * Groups of features that this application has requested.
     * Each group contains a set of features that are required.
     * A device must match the features listed in {@link #reqFeatures} and one
     * or more FeatureGroups in order to have satisfied the feature requirement.
     *
     * @see FeatureInfo#FLAG_REQUIRED
     */
    @Nullable
    public FeatureGroupInfo[] featureGroups = HiddenUtil.throwUOE();

    /**
     * Constant corresponding to <code>auto</code> in
     * the {@link android.R.attr#installLocation} attribute.
     */
    public static final int INSTALL_LOCATION_UNSPECIFIED = -1;

    /**
     * Constant corresponding to <code>auto</code> in the
     * {@link android.R.attr#installLocation} attribute.
     */
    public static final int INSTALL_LOCATION_AUTO = 0;

    /**
     * Constant corresponding to <code>internalOnly</code> in the
     * {@link android.R.attr#installLocation} attribute.
     */
    public static final int INSTALL_LOCATION_INTERNAL_ONLY = 1;

    /**
     * Constant corresponding to <code>preferExternal</code> in the
     * {@link android.R.attr#installLocation} attribute.
     */
    public static final int INSTALL_LOCATION_PREFER_EXTERNAL = 2;

    /**
     * The install location requested by the package. From the
     * {@link android.R.attr#installLocation} attribute, one of
     * {@link #INSTALL_LOCATION_AUTO}, {@link #INSTALL_LOCATION_INTERNAL_ONLY},
     * {@link #INSTALL_LOCATION_PREFER_EXTERNAL}
     */
    public int installLocation = INSTALL_LOCATION_INTERNAL_ONLY;

    /**
     * Whether or not the package is a stub and should be replaced by a full version of the app.
     *
     */
    public boolean isStub = HiddenUtil.throwUOE();

    /**
     * Whether the app is included when the device is booted into a minimal state. Set through the
     * non-namespaced "coreApp" attribute of the manifest tag.
     *
     */
    public boolean coreApp = HiddenUtil.throwUOE();

    /**
     * Signals that this app is required for all users on the device.
     * <p>
     * When a restricted user profile is created, the user is prompted with a list of apps to
     * install on that user. Settings uses this field to determine obligatory apps which cannot be
     * deselected.
     * <p>
     * This restriction is not handled by the framework itself.
     */
    public boolean requiredForAllUsers = HiddenUtil.throwUOE();

    /**
     * The restricted account authenticator type that is used by this application.
     */
    @Nullable
    public String restrictedAccountType = HiddenUtil.throwUOE();

    /**
     * The required account type without which this application will not function.
     */
    @Nullable
    public String requiredAccountType = HiddenUtil.throwUOE();

    /**
     * What package, if any, this package will overlay.
     * <p>
     * Package name of target package, or null.
     */
    @Nullable
    public String overlayTarget = HiddenUtil.throwUOE();

    /**
     * The name of the overlayable set of elements package, if any, this package will overlay.
     * <p>
     * Overlayable name defined within the target package, or null.
     */
    @Nullable
    public String targetOverlayableName = HiddenUtil.throwUOE();

    /**
     * The overlay category, if any, of this package
     *
     */
    @Nullable
    public String overlayCategory = HiddenUtil.throwUOE();

    public int overlayPriority = HiddenUtil.throwUOE();

    /**
     * Whether the overlay is static, meaning it cannot be enabled/disabled at runtime.
     */
    public boolean mOverlayIsStatic = HiddenUtil.throwUOE();

    /**
     * The user-visible SDK version (ex. 26) of the framework against which the application claims
     * to have been compiled, or {@code 0} if not specified.
     * <p>
     * This property is the compile-time equivalent of
     * {@link android.os.Build.VERSION#SDK_INT Build.VERSION.SDK_INT}.
     * <p>
     * For platform use only; we don't expect developers to need to read this value.
     */
    public int compileSdkVersion = HiddenUtil.throwUOE();

    /**
     * The development codename (ex. "O", "REL") of the framework against which the application
     * claims to have been compiled, or {@code null} if not specified.
     * <p>
     * This property is the compile-time equivalent of
     * {@link android.os.Build.VERSION#CODENAME Build.VERSION.CODENAME}.
     * <p>
     * For platform use only; we don't expect developers to need to read this value.
     */
    @Nullable
    public String compileSdkVersionCodename = HiddenUtil.throwUOE();

    /**
     * Whether the package is an APEX package.
     */
    public boolean isApex = HiddenUtil.throwUOE();

    /**
     * Whether this is an active APEX package.
     */
    public boolean isActiveApex = HiddenUtil.throwUOE();

    /**
     * If the package is an APEX package (i.e. the value of {@link #isApex}
     * is true), this field is the package name of the APEX. If the package
     * is one APK-in-APEX app, this field is the package name of the parent
     * APEX that contains the app. If the package is not one of the above
     * two cases, this field is {@code null}.
     */
    @Nullable
    private String mApexPackageName = HiddenUtil.throwUOE();;


    /**
     * Returns true if the package is a valid Runtime Overlay package.
     */
    public boolean isOverlayPackage() {
        return HiddenUtil.throwUOE();
    }

    /**
     * Returns true if the package is a valid static Runtime Overlay package. Static overlays
     * are not updatable outside of a system update and are safe to load in the system process.
     */
    public boolean isStaticOverlayPackage() {
        return HiddenUtil.throwUOE();
    }

    /**
     * Returns the time at which the app was archived for the user.  Units are as
     * per {@link System#currentTimeMillis()}.
     */
    public long getArchiveTimeMillis() {
        return HiddenUtil.throwUOE();
    }

    public void setArchiveTimeMillis(long value) {
        HiddenUtil.throwUOE(value);
    }

    /**
     * If the package is an APEX package (i.e. the value of {@link #isApex}
     * is true), returns the package name of the APEX. If the package
     * is one APK-in-APEX app, returns the package name of the parent
     * APEX that contains the app. If the package is not one of the above
     * two cases, returns {@code null}.
     */
    @Nullable
    public String getApexPackageName() {
        return HiddenUtil.throwUOE();
    }

    public void setApexPackageName(@Nullable String apexPackageName) {
        HiddenUtil.throwUOE(apexPackageName);
    }

    @NonNull
    @Override
    public String toString() {
        return HiddenUtil.throwUOE();
    }

    @Override
    public int describeContents() {
        return HiddenUtil.throwUOE();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int parcelableFlags) {
        HiddenUtil.throwUOE(dest, parcelableFlags);
    }

    public static final @NonNull Parcelable.Creator<PackageInfo> CREATOR = HiddenUtil.creator();
}
