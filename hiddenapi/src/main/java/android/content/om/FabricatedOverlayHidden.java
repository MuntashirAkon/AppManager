/*
 * Copyright (C) 2021 The Android Open Source Project
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

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.content.res.AssetFileDescriptor;
import android.os.FabricatedOverlayInternal;
import android.os.FabricatedOverlayInternalEntry;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.TypedValue;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Objects;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.HiddenUtil;

/**
 * FabricatedOverlay describes the content of Fabricated Runtime Resource Overlay (FRRO) that is
 * used to overlay the app's resources. The app should register the {@link FabricatedOverlay}
 * instance in an {@link OverlayManagerTransaction} by calling {@link
 * OverlayManagerTransaction#registerFabricatedOverlay(FabricatedOverlay)}. The FRRO is
 * created once the transaction is committed successfully.
 *
 * <p>The app creates a FabricatedOverlay to describe the how to overlay string, integer, and file
 * type resources. Before creating any frro, please define a target overlayable in {@code
 * res/values/overlayable.xml} that describes what kind of resources can be overlaid, what kind of
 * roles or applications can overlay the resources. Here is an example.
 *
 * <pre>{@code
 * <overlayable name="SignatureOverlayable" actor="overlay://theme">
 *     <!-- The app with the same signature can overlay the below resources -->
 *     <policy type="signature">
 *         <item type="color" name="mycolor" />
 *         <item type="string" name="mystring" />
 *     </policy>
 * </overlayable>
 * }</pre>
 *
 * <p>The overlay must assign the target overlayable name just like the above example by calling
 * {@link #setTargetOverlayable(String)}. Here is an example:
 *
 * <pre>{@code
 * FabricatedOverlay fabricatedOverlay = new FabricatedOverlay("overlay_name",
 *                                                             context.getPackageName());
 * fabricatedOverlay.setTargetOverlayable("SignatureOverlayable")
 * fabricatedOverlay.setResourceValue("mycolor", TypedValue.TYPE_INT_COLOR_ARGB8, Color.White)
 * fabricatedOverlay.setResourceValue("mystring", TypedValue.TYPE_STRING, "Hello")
 * }</pre>
 *
 * <p>The app can create any {@link FabricatedOverlay} instance by calling the following APIs.
 *
 * <ul>
 *   <li>{@link #setTargetOverlayable(String)}
 *   <li>{@link #setResourceValue(String, int, int, String)}
 *   <li>{@link #setResourceValue(String, int, String, String)}
 *   <li>{@link #setResourceValue(String, ParcelFileDescriptor, String)}
 * </ul>
 *
 * @see OverlayManager
 * @see OverlayManagerTransaction
 */
@RefineAs(FabricatedOverlay.class)
public class FabricatedOverlayHidden {

    /**
     * Retrieves the identifier for this fabricated overlay.
     * @return the overlay identifier
     */
    @NonNull
    public OverlayIdentifier getIdentifier() {
        return HiddenUtil.throwUOE();
    }



    /**
     * Create a fabricated overlay to overlay on the specified package.
     *
     * @param overlayName a name used to uniquely identify the fabricated overlay owned by the
     *                   caller itself.
     * @param targetPackage the name of the package to be overlaid
     */
    public FabricatedOverlayHidden(@NonNull String overlayName, @NonNull String targetPackage) {
        HiddenUtil.throwUOE(overlayName, targetPackage);
    }

    /**
     * Set the package that owns the overlay
     *
     * @param owningPackage the package that should own the overlay.
     */
    public void setOwningPackage(@NonNull String owningPackage) {
        HiddenUtil.throwUOE(owningPackage);
    }

    /**
     * Set the target overlayable name of the overlay
     *
     * The target package defines may define several overlayables. The {@link FabricatedOverlay}
     * should specify which overlayable to be overlaid.
     *
     * @param targetOverlayable the overlayable name defined in target package.
     */
    public void setTargetOverlayable(@Nullable String targetOverlayable) {
        HiddenUtil.throwUOE(targetOverlayable);
    }

    /**
     * Return the target overlayable name of the overlay
     *
     * The target package defines may define several overlayables. The {@link FabricatedOverlay}
     * should specify which overlayable to be overlaid.
     *
     * @return the target overlayable name.
     * @hide
     */
    @Nullable
    public String getTargetOverlayable() {
        return HiddenUtil.throwUOE();
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
    @NonNull
    public void setResourceValue(
            @NonNull String resourceName,
            @IntRange(from = TypedValue.TYPE_FIRST_INT, to = TypedValue.TYPE_LAST_INT) int dataType,
            int value,
            @Nullable String configuration) {
        HiddenUtil.throwUOE(resourceName, dataType, value, configuration);
    }
    @IntDef(
            value = {
                    TypedValue.TYPE_STRING,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface StringTypeOverlayResource {}

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
    @NonNull
    public void setResourceValue(
            @NonNull String resourceName,
            @StringTypeOverlayResource int dataType,
            @NonNull String value,
            @Nullable String configuration) {
     HiddenUtil.throwUOE(resourceName, dataType, value, configuration);
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
    @NonNull
    public void setResourceValue(
            @NonNull String resourceName,
            @NonNull ParcelFileDescriptor value,
            @Nullable String configuration) {
        HiddenUtil.throwUOE(resourceName, value, configuration);
    }

    /**
     * Sets the resource value in the fabricated overlay from a nine patch.
     *
     * @param resourceName name of the target resource to overlay (in the form
     *     [package]:type/entry)
     * @param value the file descriptor whose contents are the value of the frro
     * @param configuration The string representation of the config this overlay is enabled for
     */
    //@FlaggedApi(android.content.res.Flags.FLAG_NINE_PATCH_FRRO)
    public void setNinePatchResourceValue(
            @NonNull String resourceName,
            @NonNull ParcelFileDescriptor value,
            @Nullable String configuration) {
        HiddenUtil.throwUOE(resourceName, value, configuration);
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
    //@FlaggedApi(android.content.res.Flags.FLAG_ASSET_FILE_DESCRIPTOR_FRRO)
    public void setResourceValue(
            @NonNull String resourceName,
            @NonNull AssetFileDescriptor value,
            @Nullable String configuration) {
        HiddenUtil.throwUOE(resourceName, value, configuration);
    }
}
