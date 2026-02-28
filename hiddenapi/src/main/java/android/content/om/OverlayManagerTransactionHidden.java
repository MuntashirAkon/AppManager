/*
 * Copyright (C) 2019 The Android Open Source Project
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
import androidx.annotation.NonNull;

import androidx.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.res.AssetManagerHidden;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Iterator;
import java.util.List;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.HiddenUtil;

/**
 * A container for a batch of requests to the OverlayManager.
 *
 * <p>An app can get an {@link OverlayManagerTransactionHidden} with the specified {@link OverlayManager}
 * to handle the transaction. The app can register multiple overlays and unregister multiple
 * registered overlays in one transaction commitment.
 *
 * <p>The below example is registering a {@code updatingOverlay} and unregistering a {@code
 * deprecatedOverlay} in one transaction commitment.
 *
 * <pre>{@code
 * final OverlayManager overlayManager = ctx.getSystemService(OverlayManager.class);
 * final OverlayManagerTransaction transaction = new OverlayManagerTransaction(overlayManager);
 * transaction.registerFabricatedOverlay(updatingOverlay);
 * transaction.unregisterFabricatedOverlay(deprecatedOverlay)
 * transaction.commit();
 * }</pre>
 *
 * @see OverlayManager
 * @see FabricatedOverlay
 */
@SuppressWarnings("unused")
@RefineAs(OverlayManagerTransaction.class)
public final class OverlayManagerTransactionHidden implements Parcelable {

    /**
     * Get an overlay manager transaction.
     *
     * @return a new {@link OverlayManagerTransactionHidden} instance.
     */
    @NonNull
    public static OverlayManagerTransactionHidden newInstance() {
        return HiddenUtil.throwUOE();
    }
    /**
     * Get the iterator of requests
     *
     * @return the iterator of request
     * @SystemApi (client = SystemApi.Client.SYSTEM_SERVER)
     */
    @NonNull
    //
    public Iterator<Request> getRequests() {
        return HiddenUtil.throwUOE();
    }

    /**
     * {@inheritDoc}
     *
     */
    @NonNull
    @Override
    public String toString() {
        return HiddenUtil.throwUOE();
    }

    /**
     * A single unit of the transaction, such as a request to enable an
     * overlay, or to disable an overlay.
     *
     * @hide
     */
   // @SystemApi(client = SYSTEM_SERVER)
    public static final class Request {
        @IntDef(value = {
                TYPE_SET_ENABLED,
                TYPE_SET_DISABLED,
                TYPE_UNREGISTER_FABRICATED,
                TYPE_REGISTER_FABRICATED
        })
        @Retention(RetentionPolicy.SOURCE)
        @interface RequestType {}

        public static final int TYPE_SET_ENABLED = 0;
        public static final int TYPE_SET_DISABLED = 1;
        public static final int TYPE_REGISTER_FABRICATED = 2;
        public static final int TYPE_UNREGISTER_FABRICATED = 3;

        public static final String BUNDLE_FABRICATED_OVERLAY = "fabricated_overlay";

        @RequestType
        public final int type;
        public final OverlayIdentifier overlay;
        public final int userId;

        @SuppressLint("NullableCollection")
        @androidx.annotation.Nullable
        public final Bundle extras;

        public Request(@RequestType final int type, @NonNull final OverlayIdentifier overlay,
                       final int userId) {
            this(type, overlay, userId, null /* extras */);
        }

        public Request(@RequestType final int type, @NonNull final OverlayIdentifier overlay,
                       final int userId, @Nullable Bundle extras) {
            this.type = type;
            this.overlay = overlay;
            this.userId = userId;
            this.extras = extras;
        }

        /**
         * Translate the request type into a human readable string. Only
         * intended for debugging.
         */
        public String typeToString() {
            return HiddenUtil.throwUOE();
        }
    }

    /**
     * Builder class for OverlayManagerTransaction objects.
     */
    public static final class Builder {
        private final List<Request> mRequests = HiddenUtil.throwUOE();
        private boolean mSelfTargeting = false;

        /**
         * Request that an overlay package be enabled and change its loading
         * order to the last package to be loaded, or disabled
         *
         * If the caller has the correct permissions, it is always possible to
         * disable an overlay. Due to technical and security reasons it may not
         * always be possible to enable an overlay, for instance if the overlay
         * does not successfully overlay any target resources due to
         * overlayable policy restrictions.
         *
         * An enabled overlay is a part of target package's resources, i.e. it will
         * be part of any lookups performed via {@link android.content.res.Resources}
         * and {@link AssetManagerHidden}. A disabled overlay will no
         * longer affect the resources of the target package. If the target is
         * currently running, its outdated resources will be replaced by new ones.
         *
         * @param overlay The name of the overlay package.
         * @param enable true to enable the overlay, false to disable it.
         * @return this Builder object, so you can chain additional requests
         */
        public Builder setEnabled(@NonNull OverlayIdentifier overlay, boolean enable) {
            return HiddenUtil.throwUOE(overlay, enable);
        }

        public Builder setEnabled(@NonNull OverlayIdentifier overlay, boolean enable, int userId) {
            return HiddenUtil.throwUOE(overlay, enable, userId);
        }

        /**
         * Request that an overlay package be self-targeting. Self-targeting overlays enable
         * applications to overlay on itself resources. The overlay target is itself, or the Android
         * package, and the work range is only in caller application.
         * @param selfTargeting whether the overlay is self-targeting, the default is false.
         */
        public Builder setSelfTargeting(boolean selfTargeting) {
            return HiddenUtil.throwUOE(selfTargeting);
        }

        /**
         * Registers the fabricated overlay with the overlay manager so it can be enabled and
         * disabled for any user.
         * <p>
         * The fabricated overlay is initialized in a disabled state. If an overlay is re-registered
         * the existing overlay will be replaced by the newly registered overlay and the enabled
         * state of the overlay will be left unchanged if the target package and target overlayable
         * have not changed.
         *
         * @param overlay the overlay to register with the overlay manager
         *
         */
        @NonNull
        public Builder registerFabricatedOverlay(@NonNull FabricatedOverlay overlay) {
            return HiddenUtil.throwUOE(overlay);
        }

        /**
         * Disables and removes the overlay from the overlay manager for all users.
         *
         * @param overlay the overlay to disable and remove
         *
         */
        @NonNull
        public Builder unregisterFabricatedOverlay(@NonNull OverlayIdentifier overlay) {
            return HiddenUtil.throwUOE(overlay);
        }

        /**
         * Create a new transaction out of the requests added so far. Execute
         * the transaction by calling OverlayManager#commit.
         *
         * @see OverlayManager#commit
         * @return a new transaction
         */
        @NonNull
        public OverlayManagerTransaction build() {
            return HiddenUtil.throwUOE();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        HiddenUtil.throwUOE(dest, flags);
    }

    @NonNull
    public static final Parcelable.Creator<OverlayManagerTransactionHidden> CREATOR = HiddenUtil.creator();

    /**
     * Registers the fabricated overlays with the overlay manager so it can be used to overlay
     * the app resources in runtime.
     *
     * <p>If an overlay is re-registered the existing overlay will be replaced by the newly
     * registered overlay. The registered overlay will be left unchanged until the target
     * package or target overlayable is changed.
     *
     * @param overlay the overlay to register with the overlay manager
     */
    public void registerFabricatedOverlay(@NonNull FabricatedOverlay overlay) {
        HiddenUtil.throwUOE();
    }

    /**
     * Unregisters the registered overlays from the overlay manager.
     *
     * @param overlay the overlay to be unregistered
     *
     * @see OverlayManager#getOverlayInfosForTarget(String)
     * @see OverlayInfo#getOverlayIdentifier()
     */
    public void unregisterFabricatedOverlay(@NonNull OverlayIdentifier overlay) {
        HiddenUtil.throwUOE(overlay);
    }

    /**
     * Indicate whether the transaction is for self-targeting or not.
     */
    boolean isSelfTargeting() {
        return HiddenUtil.throwUOE();
    }
}
