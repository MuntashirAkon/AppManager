// SPDX-License-Identifier: Apache-2.0

package android.content.pm;

import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // 14 r50
public class SigningDetails implements Parcelable {
    @IntDef(flag = true,
            value = {CertCapabilities.INSTALLED_DATA,
                    CertCapabilities.SHARED_USER_ID,
                    CertCapabilities.PERMISSION,
                    CertCapabilities.ROLLBACK})
    public @interface CertCapabilities {

        /** accept data from already installed pkg with this cert */
        int INSTALLED_DATA = 1;

        /** accept sharedUserId with pkg with this cert */
        int SHARED_USER_ID = 2;

        /** grant SIGNATURE permissions to pkgs with this cert */
        int PERMISSION = 4;

        /** allow pkg to update to one signed by this certificate */
        int ROLLBACK = 8;

        /** allow pkg to continue to have auth access gated by this cert */
        int AUTH = 16;
    }

    /**
     * Returns true if the signing details have one or more signatures.
     */
    public boolean hasSignatures() {
        return HiddenUtil.throwUOE();
    }

    /**
     * Returns true if the signing details have past signing certificates.
     */
    public boolean hasPastSigningCertificates() {
        return HiddenUtil.throwUOE();
    }

    /**
     * Determine if {@code signature} is in this SigningDetails' signing certificate history,
     * including the current signer.  Automatically returns false if this object has multiple
     * signing certificates, since rotation is only supported for single-signers; this is
     * enforced by {@code hasCertificateInternal}.
     */
    public boolean hasCertificate(@NonNull Signature signature) {
        return HiddenUtil.throwUOE();
    }

    /**
     * Determine if {@code signature} is in this SigningDetails' signing certificate history,
     * including the current signer, and whether or not it has the given permission.
     * Certificates which match our current signer automatically get all capabilities.
     * Automatically returns false if this object has multiple signing certificates, since
     * rotation is only supported for single-signers.
     */
    public boolean hasCertificate(@NonNull Signature signature, @CertCapabilities int flags) {
        return HiddenUtil.throwUOE(signature, flags);
    }

    /** Convenient wrapper for calling {@code hasCertificate} with certificate's raw bytes. */
    public boolean hasCertificate(byte[] certificate) {
        Signature signature = new Signature(certificate);
        return hasCertificate(signature);
    }

    public static final Creator<SigningDetails> CREATOR = HiddenUtil.creator();

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        HiddenUtil.throwUOE(dest, flags);
    }
}
