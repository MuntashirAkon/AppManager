package io.github.muntashirakon.AppManager.overlays;

import android.annotation.SuppressLint;
import android.content.om.FabricatedOverlay;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayManagerTransaction;
import android.content.om.OverlayManagerTransactionHidden;
import android.content.om.OverlayableInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManagerHidden;
import android.content.res.Resources;
import android.os.Build;
import android.os.FabricatedOverlayInternal;
import android.os.FabricatedOverlayInternalEntry;
import android.os.ParcelFileDescriptor;
import android.util.TypedValue;

import androidx.annotation.AnyRes;
import androidx.annotation.BoolRes;
import androidx.annotation.IntRange;
import androidx.annotation.IntegerRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.ReturnThis;
import androidx.annotation.StringRes;

import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.rikka.tools.refine.Refine;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.compat.OverlayManagerCompact;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ResourceUtil;

/**
 * Ease of use builder for {@link FabricatedOverlay}
 * You can't enable a newly created overlay in the same {@link OverlayManagerTransactionHidden transaction}
 * it will require a second Transaction to enable for ease of use {@link FabricatedOverlayBuilder#commit()}
 * will build and register this overlay for you.
 * <p>
 * FabricatedOverlays use the signature of the {@link FabricatedOverlayBuilder#setOwningPackage(String) owner package}
 * to check weather or not the overlay can be applied to the package for convenience Non Persistent Overlays
 * are owned by android as it is exempt from the signature check. This check is done will the rules defined by {@link OverlayConfig} see AOSP.
 * It should be noted that Due to OverlayConfig it's possible to make overlays that are owned by us as long as we meet the requirements for it.
 * This can allow system configuration overlays via a platform signed AM. but not overlays over any non platform signed packages.
 * <p>
 * It is recommend to create a {@link FabricatedOverlayBuilder#getNonPersistentOverlayBuilder(String, String) Non Persistent Overlay} first
 * then see if the phone doesn't crash before creating a PersistentOverlay TODO Create a system for automatically Persisting Overlays that don't crash the phone.
 * <p>
 * It is possible for us to spoof applications that use self applied overlays as we can set the {@link FabricatedOverlayBuilder#setOwningPackage(String) owning package} to it's self.
 * as there is no check on who actually register the Fabricated Overlay
 * @see FabricatedOverlayBuilder#getPersistentOverlayBuilder(String, String) Persistent Fabricated Overlays
 * @see FabricatedOverlayBuilder#getNonPersistentOverlayBuilder(String, String) Non Persistent Fabricated Overlays
 *
 */
// Copyright TherayTharow 2025
@RequiresApi(api = Build.VERSION_CODES.O)
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class FabricatedOverlayBuilder {
    private static final String TAG = FabricatedOverlayBuilder.class.getSimpleName();
    private final String targetPackage;
    private String owningPackage;
    private String targetOverlayable;
    private final String overlayName;
    private final Resources pkgres;
    private final String configuration;

    private final Object mTmpValueLock = new Object();
    private TypedValue mTmpValue = new TypedValue();

    private final ArrayList<FabricatedOverlayInternalEntry> entries = new ArrayList<>();


    /**
     * Create Builder for Fabricated Overlay, which is automatically removed on start-up of android during
     * early init. internally owner is set to the currently set SystemShell package(owning package of the shell uid 2000)
     * @see FabricatedOverlayBuilder#getPersistentOverlayBuilder(String, String)
     * @param overlayName Name of this Fabricated Overlay must by unique
     * @param packageName Target package to which this overlay will be applied
     * @return new Instance of {@link FabricatedOverlayBuilder}
     */
    @NonNull
    @Contract("_, _ -> new")
    public static FabricatedOverlayBuilder getNonPersistentOverlayBuilder(@NonNull String overlayName, @NonNull String packageName) {
        ResourceUtil u = new ResourceUtil();
        if (u.loadAndroidResources()) {
            throw new IllegalStateException("Android Resources failed to load");
        }
        return new FabricatedOverlayBuilder(overlayName, packageName, u.getString("android:string/config_systemShell"), null);
    }

    /**
     * Create builder for Fabricated Overlay, which is a System Persistent Overlay and can not be removed by expect by root.
     * Internally owner of this overlay is set to android.
     * @param overlayName Name of this Fabricated Overlay must be unique
     * @param packageName Target package to which this overlay will be applied
     * @return new Instance of {@link FabricatedOverlayBuilder}
     */
    @NonNull
    @Contract("_, _ -> new")
    public static FabricatedOverlayBuilder getPersistentOverlayBuilder(@NonNull String overlayName, @NonNull String packageName) {
        return new FabricatedOverlayBuilder(overlayName, packageName, "android", null);
    }
    /**
     * Create a fabricated overlay to overlay on the specified package.
     *
     * @param overlayName a name used to uniquely identify the fabricated overlay owned by the
     *                   caller itself.
     * @param targetPackage the name of the package to be overlaid
     * @param owningPackage the package that should own the overlay
     * @param configuration string version of the res {@link android.content.res.Configuration configuration}
     */
    @SuppressWarnings("NewApi")
    public FabricatedOverlayBuilder(
            @NonNull String overlayName,
            @NonNull String targetPackage,
            @NonNull String owningPackage,
            @Nullable String configuration) {
        ResourceUtil resourceUtil = new ResourceUtil();
        PackageManager pm = ContextUtils.getContext().getPackageManager();
        resourceUtil.loadResources(pm, targetPackage);
        if (resourceUtil.resources == null) {
            throw new IllegalStateException("Failed to load resources");
        }
        this.targetPackage = targetPackage;
        this.owningPackage = owningPackage;
        this.overlayName = overlayName;
        this.pkgres = resourceUtil.resources;
        this.configuration = configuration;
    }

    /**
     * Used in other resource classes and was implemented here as well
     * @return TypedValue
     */
    @NonNull
    private TypedValue obtainTempTypedValue() {
        TypedValue tmpValue = null;
        synchronized (mTmpValueLock) {
            if (mTmpValue != null) {
                tmpValue = mTmpValue;
                mTmpValue = null;
            }
        }
        if (tmpValue ==null)
            return new TypedValue();
        return tmpValue;
    }

    /**
     * Return value to the pool;
     * @param value to return
     */
    private void releaseTempTypedValue(TypedValue value) {
        synchronized (mTmpValueLock) {
            if (mTmpValue == null) {
                mTmpValue = value;
            }
        }
    }

    /**
     * Retrieves the identifier for this fabricated overlay.
     * @return the {@link OverlayIdentifier overlay identifier}
     */
    @SuppressLint("NewApi")
    @NonNull
    public OverlayIdentifier getIdentifier() {
        return new FabricatedOverlay(overlayName, targetPackage).getIdentifier();
    }

    /**
     * Set the package that owns the overlay
     *
     * @param owningPackage the package that should own the overlay.
     */
    @ReturnThis
    public FabricatedOverlayBuilder setOwningPackage(@NonNull String owningPackage) {
        this.owningPackage = owningPackage;
        return this;
    }

    /**
     * Get the available overlay Targets
     */
    public List<OverlayableInfo> getTargetOverlayable() {
        AssetManagerHidden am = Refine.unsafeCast(pkgres.getAssets());
        final List<OverlayableInfo> result = new ArrayList<>();
        //noinspection DataFlowIssue Can't Actually be null otherwise it wouldn't be a valid application
        for (Map.Entry<String, String> entry : am.getOverlayableMap(targetPackage).entrySet()) {
            result.add(new OverlayableInfo(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /**
     * Same as {@link FabricatedOverlayBuilder#getTargetOverlayable()}
     * @param output Map to fill with overlayables
     * @return {@link FabricatedOverlayBuilder self}
     */
    @ReturnThis
    public FabricatedOverlayBuilder getTargetOverlayable(@NonNull List<OverlayableInfo> overlay) {
        overlay.addAll(getTargetOverlayable());
        return this;
    }

    /**
     * Set the target overlayable name of the overlay
     *
     * <p>The target package defines may define several overlayables. The {@link FabricatedOverlay}
     * should specify which overlayable to be overlaid.
     *
     * @param targetOverlayable the overlayable name defined in target package. see {@link FabricatedOverlayBuilder#getTargetOverlayable()} for a list of overlayables in this package
     */
    @ReturnThis
    public FabricatedOverlayBuilder setTargetOverlayable(@Nullable String targetOverlayable) {
        this.targetOverlayable = targetOverlayable;
        return this;
    }
    /**
     * Sets the resource value in the fabricated overlay for the integer-like types with the
     * configuration.
     *
     * @param resourceName name of the target resource to overlay (in the form
     *     [package]:type/entry)
     * @param dataType the data type of the new value
     * @param value the integer representing the new value
     * @param configuration The string representation of the config this overlay is enabled for
     * @see android.util.TypedValue#TYPE_INT_COLOR_ARGB8 android.util.TypedValue#type
     */
    @ReturnThis
    public FabricatedOverlayBuilder setResourceValue(
            @NonNull String resourceName,
            @IntRange(from = TypedValue.TYPE_FIRST_INT, to = TypedValue.TYPE_LAST_INT )int dataType,
            int value,
            @Nullable String configuration) {
        setResourceValue(resourceName, null, null, null, value, dataType, configuration, false);
        return this;
    }

    /**
     * Sets the resource value in the fabricated overlay for the file descriptor type with the
     * configuration.
     *
     * @param resourceName name of the target resource to overlay (in the form
     *     [package]:type/entry)
     * @param value the file descriptor whose contents are the value of the frro
     * @param configuration The string representation of the config this overlay is enabled for
     */
    @ReturnThis
    public FabricatedOverlayBuilder setResourceValue(@NonNull String resourceName, @NonNull ParcelFileDescriptor value, @Nullable String configuration) {
        setResourceValue(resourceName, null, value, null, 0, 0, configuration, false);
        return this;
    }
    /**
     * Sets the resource value in the fabricated overlay for the string-like type with the
     * configuration.
     *
     * @param resourceName name of the target resource to overlay (in the form
     *     [package]:type/entry)
     * @param dataType the data type of the new value
     * @param value the string representing the new value
     * @param configuration The string representation of the config this overlay is enabled for
     * @see android.util.TypedValue#TYPE_STRING android.util.TypedValue#type
     */
    @ReturnThis
    public FabricatedOverlayBuilder setResourceValue(@NonNull String resourceName, int dataType, @NonNull String value, @Nullable String configuration) {
        setResourceValue(resourceName, null, null, value, 0, dataType, configuration, false);
        return this;
    }
    /**
     * Sets the resource value in the fabricated overlay from a nine patch.
     *
     * @param resourceName name of the target resource to overlay (in the form
     *     [package]:type/entry)
     * @param value the file descriptor whose contents are the value of the frro
     * @param configuration The string representation of the config this overlay is enabled for
     */
    @ReturnThis
    public FabricatedOverlayBuilder setNinePatchResourceValue(@NonNull String resourceName, @NonNull ParcelFileDescriptor value, @Nullable String configuration) throws Resources.NotFoundException {
        setResourceValue(resourceName, null, value, null, 0, 0, configuration, true);
        return this;
    }
    /**
     * Sets the resource value in the fabricated overlay for the file descriptor type with the
     * configuration.
     *
     * @param resourceName name of the target resource to overlay (in the form
     *     [package]:type/entry)
     * @param value the file descriptor whose contents are the value of the frro
     * @param configuration The string representation of the config this overlay is enabled for
     */

    @ReturnThis
    public FabricatedOverlayBuilder setResourceValue(@NonNull String resourceName, @NonNull AssetFileDescriptor value, @Nullable String configuration) throws Resources.NotFoundException {
        setResourceValue(resourceName, value, null, null, 0, 0, configuration, false);
        return this;
    }

    /**
     * Handles any resource calls except for the direct {@link FabricatedOverlayBuilder#setValue(String, TypedValue)}
     */
    @SuppressLint("DiscouragedApi")
    private void setResourceValue(@NonNull String resourceName, AssetFileDescriptor assetFileDescriptor, ParcelFileDescriptor parcelFileDescriptor, CharSequence string, int data, int dataType, @Nullable String configuration, boolean isNinePatch) {
        final String name = ensureValidResourceName(resourceName);
        TypedValue tmp = obtainTempTypedValue();
        pkgres.getValue(name, tmp, true);
        FabricatedOverlayInternalEntry entry = createEntry(name, dataType, data, string, parcelFileDescriptor, configuration, parcelFileDescriptor!=null?parcelFileDescriptor.getStatSize():0L, 0L, isNinePatch);
        if (dataType != tmp.type) {
            entry.dataType = tmp.type;
        }
        if (assetFileDescriptor!=null) {
            entry.binaryData = assetFileDescriptor.getParcelFileDescriptor();
            entry.binaryDataSize = assetFileDescriptor.getLength();
            entry.binaryDataOffset = assetFileDescriptor.getStartOffset();
        }
        if (string!=null) {
            entry.data = tmp.data;
        }
        entries.add(entry);
        releaseTempTypedValue(tmp);
    }

    /**
     * Builds a {@link android.os.Parcelable} version of the FabricatedOverlay
     * Used for internal binder communication between the {@link android.content.om.IOverlayManager}
     * and {@link android.os.IIdmap2 }
     * @return {@link FabricatedOverlayInternal}
     */
    public FabricatedOverlayInternal build() {
        FabricatedOverlayInternal result = new FabricatedOverlayInternal();
        result.overlayName = overlayName;
        result.packageName = owningPackage==null? BuildConfig.APPLICATION_ID:owningPackage;
        result.targetOverlayable = targetOverlayable;
        result.targetPackageName = targetPackage;
        result.entries = new ArrayList<>(entries.size());
        result.entries.addAll(entries);
        return result;
    }

    /**
     * Enables this overlay after it has been built
     * @throws SecurityException if the {@link android.content.om.IOverlayManager#commit(OverlayManagerTransaction) transaction} failed
     */

    @ReturnThis
    public FabricatedOverlayBuilder setEnabled() throws SecurityException {
        OverlayManagerCompact.getOverlayManager().commit(new OverlayManagerTransactionHidden.Builder().setEnabled(getIdentifier(), true).build());
        return this;
    }

    /**
     * Automatically Registers this Fabricated Overlay with the Overlay Manager
     * This method does some trickery as we use the internal Fabricated Overlay data class instead of the user space version
     * so we have to replace it.
     * @throws SecurityException if the {@link android.content.om.IOverlayManager#commit(OverlayManagerTransaction) transaction} failed
     */
    @SuppressLint("NewApi")
    @ReturnThis
    public FabricatedOverlayBuilder commit() throws SecurityException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            OverlayManagerTransactionHidden.Builder builder = new OverlayManagerTransactionHidden.Builder();
            builder.registerFabricatedOverlay(new FabricatedOverlay(overlayName, targetPackage));
            OverlayManagerTransactionHidden hidden = Refine.unsafeCast(builder.build());
            Iterator<OverlayManagerTransactionHidden.Request> requestIterator = hidden.getRequests();
            while (requestIterator.hasNext()) {
                final OverlayManagerTransactionHidden.Request request = requestIterator.next();
                switch (request.type) {
                    case OverlayManagerTransactionHidden.Request.TYPE_REGISTER_FABRICATED: {
                        if (request.extras!=null) {
                            request.extras.putParcelable(OverlayManagerTransactionHidden.Request.BUNDLE_FABRICATED_OVERLAY, build());
                        }
                        break;
                    }
                    case OverlayManagerTransactionHidden.Request.TYPE_SET_DISABLED:
                    case OverlayManagerTransactionHidden.Request.TYPE_SET_ENABLED:
                    case OverlayManagerTransactionHidden.Request.TYPE_UNREGISTER_FABRICATED:
                        Log.d(TAG, "Unused Transaction Type");
                        break;
                    default: {
                        Log.e(TAG, "Unknown Transaction Type");
                    }
                }
            }
            OverlayManagerCompact.getOverlayManager().commit(Refine.unsafeCast(hidden));
        }
        return this;
    }

    // START OF RESOURCES.

    /**
     * Res Id Version of {@link FabricatedOverlayBuilder#setText(String, CharSequence)}
     */
    @ReturnThis
    public FabricatedOverlayBuilder setText(@StringRes int id, CharSequence value) throws Resources.NotFoundException {
        setText(pkgres.getResourceName(id), value);
        return this;
    }
    /**
     * Set Resource text to given value
     * @param name A {@link FabricatedOverlayBuilder#ensureValidResourceName(String) Valid Resource Name}
     * @param value to set this resource to;
     * @return {@link FabricatedOverlayBuilder self}
     * @throws Resources.NotFoundException if resource doesn't exist with in the target package
     */
    @ReturnThis
    public FabricatedOverlayBuilder setText(String name, @NonNull CharSequence value) throws Resources.NotFoundException {
        setResourceValue(name, TypedValue.TYPE_STRING, value.toString(), configuration);
        return this;
    }

    /**
     * Res Id Version of {@link FabricatedOverlayBuilder#setString(String, String)}
     */
    @ReturnThis
    public FabricatedOverlayBuilder setString(@StringRes int id, @NonNull String value) throws Resources.NotFoundException {
        setString(pkgres.getResourceName(id), value);
        return this;
    }

    /**
     * Set Resource string to given value
     * @param name A {@link FabricatedOverlayBuilder#ensureValidResourceName(String) Valid Resource Name}
     * @param value to set this resource to;
     * @return {@link FabricatedOverlayBuilder self}
     * @throws Resources.NotFoundException if resource doesn't exist with in the target package
     */
    @ReturnThis
    public FabricatedOverlayBuilder setString(String name, @NonNull String value) throws Resources.NotFoundException {
        setResourceValue(name, TypedValue.TYPE_STRING, value, configuration);
        return this;
    }

    /**
     * Res Id Version of {@link FabricatedOverlayBuilder#setBoolean(String, boolean)}
     */
    @ReturnThis
    public FabricatedOverlayBuilder setBoolean(@BoolRes int id, boolean value) throws Resources.NotFoundException {
        setBoolean(pkgres.getResourceName(id), value);
        return this;
    }

    /**
     * Set Resource boolean to given value
     * @param name A {@link FabricatedOverlayBuilder#ensureValidResourceName(String) Valid Resource Name}
     * @param value to set this resource to;
     * @return {@link FabricatedOverlayBuilder self}
     * @throws Resources.NotFoundException if resource doesn't exist with in the target package
     */
    @ReturnThis
    public FabricatedOverlayBuilder setBoolean(@NonNull String name, boolean value) throws Resources.NotFoundException {
        setResourceValue(name, TypedValue.TYPE_INT_BOOLEAN, value?1:0, configuration);
        return this;
    }
    /**
     * Res Id Version of {@link FabricatedOverlayBuilder#setInteger(String, int, int)}
     */
    @ReturnThis
    public FabricatedOverlayBuilder setInteger(@IntegerRes int id, int type, int value) throws Resources.NotFoundException {
        setInteger(pkgres.getResourceName(id), type, value);
        return this;
    }

    /**
     * Set Resource integer to given value
     * @param name A {@link FabricatedOverlayBuilder#ensureValidResourceName(String) Valid Resource Name}
     * @param value to set this resource to;
     * @param type the {@link TypedValue#type dataType} of the integer
     * @return {@link FabricatedOverlayBuilder self}
     * @throws Resources.NotFoundException if resource doesn't exist with in the target package
     */
    @ReturnThis
    public FabricatedOverlayBuilder setInteger(@NonNull String name, int type, int value) throws Resources.NotFoundException {
        if (!(type >= TypedValue.TYPE_FIRST_INT && type <= TypedValue.TYPE_LAST_INT)) {
            throw new Resources.NotFoundException("Resource ID %s" + name
                    + " type #0x" + Integer.toHexString(value) + " is not valid");
        }
        setResourceValue(name, type, value, configuration);
        return this;
    }

    /**
     * Res Id Version of {@link FabricatedOverlayBuilder#setFloat(String, float)}
     */
    @ReturnThis
    public FabricatedOverlayBuilder setFloat(int id, float value) throws Resources.NotFoundException {
        setFloat(pkgres.getResourceName(id), value);
        return this;
    }

    /**
     * Set Resource float to given value
     * @param name A {@link FabricatedOverlayBuilder#ensureValidResourceName(String) Valid Resource Name}
     * @param value to set this resource to;
     * @return {@link FabricatedOverlayBuilder self}
     * @throws Resources.NotFoundException if resource doesn't exist with in the target package
     */
    @ReturnThis
    @SuppressLint("Range")
    public FabricatedOverlayBuilder setFloat(@NonNull String name, float value) throws Resources.NotFoundException {
        setResourceValue(name, TypedValue.TYPE_FLOAT, Float.floatToIntBits(value), configuration);
        return this;
    }


    /**
     * Allows Creating a resource directly form a TypedValue
     * Can only be used in Idmap Direct Mode {@link FabricatedOverlayBuilder#getPersistentOverlayBuilder(String, String)} )}
     * @param name resource name {@link FabricatedOverlayBuilder#ensureValidResourceName(String)}
     * @param input The new {@link TypedValue} to set this resource to;
     * @return the builder
     * @throws Resources.NotFoundException when resource name is invalid or not found within the target package
     */
    @ReturnThis
    @ForIdmapOnly
    public FabricatedOverlayBuilder setValue(@NonNull String name, @NonNull TypedValue input) throws Resources.NotFoundException {
        entries.add(createEntry(name, input.type, input.data, input.string.toString(), null, configuration, 0L, 0L, false));
        return this;
    }

    /**
     * Res Id version of {@link FabricatedOverlayBuilder#setValue(String, TypedValue)}
     */
    @ReturnThis
    @ForIdmapOnly
    public FabricatedOverlayBuilder setValue(@AnyRes int id, @NonNull TypedValue input) throws Resources.NotFoundException {
        setValue(pkgres.getResourceName(id), input);
        return this;
    }


    /**
     * Ensure the resource name is in the form [package]:type/entry.
     *
     * @param name name of the target resource to overlay (in the form [package]:type/entry) package is optional
     * @return the valid name
     * @throws IllegalArgumentException if
     */
    @NonNull
    @Contract("_-> param1")
    private String ensureValidResourceName(@NonNull String name) {
        Objects.requireNonNull(name);
        String nameInternal = name;
        int slashIndex = name.indexOf('/'); /* must contain '/' */
        int colonIndex = name.indexOf(':'); /* ':' should before '/' if ':' exist */
        if (colonIndex == -1) {
            nameInternal = targetPackage + ':' + nameInternal;
            colonIndex = nameInternal.indexOf(':');
            slashIndex = name.indexOf('/');
        }
        // The minimum length of resource type is "id".
        if (
                slashIndex >= 0 /* It must contain the type name */
                        && colonIndex != 0 /* 0 means the package name is empty */
                        && (slashIndex - colonIndex) > 2 /* The shortest length of type is "id" */) {
            throw new IllegalArgumentException('\"'+name+"\" is invalid resource name");
        }
        return nameInternal;
    }

    /**
     * Private Constructor for {@link FabricatedOverlayInternalEntry}
     */
    @NonNull
    private static FabricatedOverlayInternalEntry createEntry(
            String resourceName,
            int dataType,
            int data,
            CharSequence stringData,
            ParcelFileDescriptor binaryData,
            String configuration,
            long binaryDataOffset,
            long binaryDataSize,
            boolean isNinePatch
    ) {
        FabricatedOverlayInternalEntry entry = new FabricatedOverlayInternalEntry();
        entry.resourceName = resourceName;
        entry.dataType = dataType;
        entry.data = data;
        entry.stringData = (String) stringData;
        entry.binaryData = binaryData;
        entry.configuration = configuration;
        entry.binaryDataOffset = binaryDataOffset;
        entry.binaryDataSize = binaryDataSize;
        entry.isNinePatch = isNinePatch;
        return entry;
    }

    /**
     * For use only in direct idmap creation mode can't be used in the normal mode (Non Root)
     */
    private @interface ForIdmapOnly {}
}