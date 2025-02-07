/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.content.om;

import android.annotation.UserIdInt;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.HiddenUtil;

/**
 * An immutable information about an overlay.
 *
 * <p>Applications calling {@link OverlayManager#getOverlayInfosForTarget(String)} get the
 * information list of the registered overlays. Each element in the list presents the information of
 * the particular overlay.
 * 
 * <p>Immutable overlay information about a package. All PackageInfos that
 * represent an overlay package will have a corresponding OverlayInfo.
 * 
 *
 * @see OverlayManager#getOverlayInfosForTarget(String)
 */
@RefineAs(OverlayInfo.class)
public final class OverlayInfoHidden implements Parcelable {
    @IntDef(value = {
            STATE_UNKNOWN,
            STATE_MISSING_TARGET,
            STATE_NO_IDMAP,
            STATE_DISABLED,
            STATE_ENABLED,
            STATE_ENABLED_IMMUTABLE,
            STATE_OVERLAY_IS_BEING_REPLACED,
            STATE_SYSTEM_UPDATE_UNINSTALL,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {}

    /**
     * An internal state used as the initial state of an overlay. OverlayInfo
     * objects exposed outside the {@link
     * com.android.server.om.OverlayManagerService} should never have this
     * state.
     *
     * 
     */
    public static final int STATE_UNKNOWN = -1;

    /**
     * The target package of the overlay is not installed. The overlay cannot be enabled.
     */
    public static final int STATE_MISSING_TARGET = 0;

    /**
     * Creation of idmap file failed (e.g. no matching resources). The overlay
     * cannot be enabled.
     *
     * 
     */
    public static final int STATE_NO_IDMAP = 1;

    /**
     * The overlay is currently disabled. It can be enabled.
     *
     * @see IOverlayManager#setEnabled
     * 
     */
    public static final int STATE_DISABLED = 2;

    /**
     * The overlay is currently enabled. It can be disabled.
     *
     * @see IOverlayManager#setEnabled
     * 
     */
    public static final int STATE_ENABLED = 3;

    /**
     * The target package is currently being upgraded or downgraded; the state
     * will change once the package installation has finished.
     * 
     *
     * @deprecated No longer used. Caused invalid transitions from enabled -> upgrading -> enabled,
     * where an update is propagated when nothing has changed. Can occur during --dont-kill
     * installs when code and resources are hot swapped and the Activity should not be relaunched.
     * In all other cases, the process and therefore Activity is killed, so the state loop is
     * irrelevant.
     */
    @Deprecated
    public static final int STATE_TARGET_IS_BEING_REPLACED = 4;

    /**
     * The overlay package is currently being upgraded or downgraded; the state
     * will change once the package installation has finished.
     * 
     */
    public static final int STATE_OVERLAY_IS_BEING_REPLACED = 5;

    /**
     * The overlay package is currently enabled because it is marked as
     * 'immutable'. It cannot be disabled but will change state if for instance
     * its target is uninstalled.
     * 
     */
    @Deprecated
    public static final int STATE_ENABLED_IMMUTABLE = 6;

    /**
     * The target package needs to be refreshed as a result of a system update uninstall, which
     * must recalculate the state of overlays against the newly enabled system package, which may
     * differ in resources/policy from the /data variant that was uninstalled.
     * 
     */
    public static final int STATE_SYSTEM_UPDATE_UNINSTALL = 7;

    /**
     * Overlay category: theme.
     * <p>
     * Change how Android (including the status bar, dialogs, ...) looks.
     *
     * 
     */
    public static final String CATEGORY_THEME = "android.theme";

    /**
     * Package name of the overlay package
     *
     * 
     */
    @NonNull
    public final String packageName;

    /**
     * The unique name within the package of the overlay.
     *
     * 
     */
    @Nullable
    public final String overlayName;

    /**
     * Package name of the target package
     *
     * 
     */
    @NonNull
    public final String targetPackageName;

    /**
     * Name of the target overlayable declaration.
     *
     * 
     */
    @Nullable
    public final String targetOverlayableName;

    /**
     * Category of the overlay package
     *
     * 
     */
    @Nullable
    public final String category;

    /**
     * Full path to the base APK for this overlay package
     * 
     */
    @NonNull
    public final String baseCodePath;

    /**
     * The state of this OverlayInfo as defined by the STATE_* constants in this class.
     * 
     */
    public final @State int state;

    /**
     * User handle for which this overlay applies
     * 
     */
    public final int userId;

    /**
     * Priority as configured by {@link com.android.internal.content.om.OverlayConfig}.
     * Not intended to be exposed to 3rd party.
     *
     * 
     */
    public final int priority;

    /**
     * isMutable as configured by {@link com.android.internal.content.om.OverlayConfig}.
     * If false, the overlay is unconditionally loaded and cannot be unloaded. Not intended to be
     * exposed to 3rd party.
     *
     * 
     */
    public final boolean isMutable;
    public final boolean isFabricated;

    public OverlayInfoHidden(@NonNull String packageName, @NonNull String targetPackageName,
                             @Nullable String targetOverlayableName, @Nullable String category,
                             @NonNull String baseCodePath, int state, int userId, int priority, boolean isMutable) {
        this(packageName, null /* overlayName */, targetPackageName, targetOverlayableName,
                category, baseCodePath, state, userId, priority, isMutable,
                false /* isFabricated */);
    }

    /**  */
    public OverlayInfoHidden(@NonNull String packageName, @Nullable String overlayName,
                             @NonNull String targetPackageName, @Nullable String targetOverlayableName,
                             @Nullable String category, @NonNull String baseCodePath, int state, int userId,
                             int priority, boolean isMutable, boolean isFabricated) {
        this.packageName = packageName;
        this.overlayName = overlayName;
        this.targetPackageName = targetPackageName;
        this.targetOverlayableName = targetOverlayableName;
        this.category = category;
        this.baseCodePath = baseCodePath;
        this.state = state;
        this.userId = userId;
        this.priority = priority;
        this.isMutable = isMutable;
        this.isFabricated = isFabricated;
    }

    /**
     * @return the package name of the overlay.
     */
    @NonNull
    public String getPackageName() {
        return HiddenUtil.throwUOE();
    }

    /**
     * Get the overlay name from the registered fabricated overlay.
     *
     * @return the overlay name
     */
    @Nullable
    public String getOverlayName() {
        return HiddenUtil.throwUOE();
    }

    /**
     * Returns the name of the target overlaid package.
     *
     * @return the target package name
     */
    @NonNull
    public String getTargetPackageName() {
        return HiddenUtil.throwUOE();
    }

    /**
     * Returns the category of the current overlay.
     *
     * 
     */
    
    @Nullable
    public String getCategory() {
        return HiddenUtil.throwUOE();
    }

    /**
     * Returns user handle for which this overlay applies to.
     *
     * 
     */
    
    @UserIdInt
    public int getUserId() {
        return HiddenUtil.throwUOE();
    }

    /**
     * Return the target overlayable name.
     *
     * @return the name of the target overlayable resources set
     */
    @Nullable
    public String getTargetOverlayableName() {
        return HiddenUtil.throwUOE();
    }

    public boolean isFabricated() {
        return HiddenUtil.throwUOE();
    }

    /**
     * Full path to the base APK or fabricated overlay for this overlay package.
     */
    @NonNull
    public String getBaseCodePath() {
        return HiddenUtil.throwUOE();
    }

    /**
     * Get the unique identifier from the overlay information.
     *
     * <p>The return value of this function can be used to unregister the related overlay.
     *
     * @return an identifier representing the current overlay.
     */
    @NonNull
    public OverlayIdentifier getOverlayIdentifier() {
        return HiddenUtil.throwUOE();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        HiddenUtil.throwUOE(dest, flags);
    }

    public static final @NonNull Parcelable.Creator<OverlayInfoHidden> CREATOR = HiddenUtil.throwUOE();

    /**
     * Return true if this overlay is enabled, i.e. should be used to overlay
     * the resources in the target package.
     *
     * Disabled overlay packages are installed but are currently not in use.
     *
     * @return true if the overlay is enabled, else false.
     * 
     */
    
    public boolean isEnabled() {
        return HiddenUtil.throwUOE();
    }

    /**
     * Translate a state to a human readable string. Only intended for
     * debugging purposes.
     *
     * @return a human readable String representing the state.
     * 
     */
    public static String stateToString(@State int state) {
        return HiddenUtil.throwUOE(state);
    }
}
