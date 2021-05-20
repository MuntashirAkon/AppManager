// SPDX-License-Identifier: Apache-2.0

package org.openintents.openpgp;

import java.util.Arrays;

import android.os.Parcel;
import android.os.Parcelable;

// Copyright 2015 Dominik Schürmann
public class OpenPgpDecryptionResult implements Parcelable {
    /**
     * Since there might be a case where new versions of the client using the library getting
     * old versions of the protocol (and thus old versions of this class), we need a versioning
     * system for the parcels sent between the clients and the providers.
     */
    public static final int PARCELABLE_VERSION = 2;

    // content not encrypted
    public static final int RESULT_NOT_ENCRYPTED = -1;
    // insecure!
    public static final int RESULT_INSECURE = 0;
    // encrypted
    public static final int RESULT_ENCRYPTED = 1;

    private final int result;
    private final byte[] sessionKey;
    private final byte[] decryptedSessionKey;

    public OpenPgpDecryptionResult(int result) {
        this.result = result;
        this.sessionKey = null;
        this.decryptedSessionKey = null;
    }

    public OpenPgpDecryptionResult(int result, byte[] sessionKey, byte[] decryptedSessionKey) {
        this.result = result;
        if ((sessionKey == null) != (decryptedSessionKey == null)) {
            throw new AssertionError("sessionkey must be null iff decryptedSessionKey is null");
        }
        this.sessionKey = sessionKey;
        this.decryptedSessionKey = decryptedSessionKey;
    }

    public int getResult() {
        return result;
    }

    public boolean hasDecryptedSessionKey() {
        return sessionKey != null;
    }

    public byte[] getSessionKey() {
        if (sessionKey == null) {
            return null;
        }
        return Arrays.copyOf(sessionKey, sessionKey.length);
    }

    public byte[] getDecryptedSessionKey() {
        if (sessionKey == null || decryptedSessionKey == null) {
            return null;
        }
        return Arrays.copyOf(decryptedSessionKey, decryptedSessionKey.length);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        /*
          NOTE: When adding fields in the process of updating this API, make sure to bump
          {@link #PARCELABLE_VERSION}.
         */
        dest.writeInt(PARCELABLE_VERSION);
        // Inject a placeholder that will store the parcel size from this point on
        // (not including the size itself).
        int sizePosition = dest.dataPosition();
        dest.writeInt(0);
        int startPosition = dest.dataPosition();
        // version 1
        dest.writeInt(result);
        // version 2
        dest.writeByteArray(sessionKey);
        dest.writeByteArray(decryptedSessionKey);
        // Go back and write the size
        int parcelableSize = dest.dataPosition() - startPosition;
        dest.setDataPosition(sizePosition);
        dest.writeInt(parcelableSize);
        dest.setDataPosition(startPosition + parcelableSize);
    }

    public static final Creator<OpenPgpDecryptionResult> CREATOR = new Creator<OpenPgpDecryptionResult>() {
        public OpenPgpDecryptionResult createFromParcel(final Parcel source) {
            int version = source.readInt(); // parcelableVersion
            int parcelableSize = source.readInt();
            int startPosition = source.dataPosition();

            int result = source.readInt();
            byte[] sessionKey = version > 1 ? source.createByteArray() : null;
            byte[] decryptedSessionKey = version > 1 ? source.createByteArray() : null;

            OpenPgpDecryptionResult vr = new OpenPgpDecryptionResult(result, sessionKey, decryptedSessionKey);

            // skip over all fields added in future versions of this parcel
            source.setDataPosition(startPosition + parcelableSize);

            return vr;
        }

        public OpenPgpDecryptionResult[] newArray(final int size) {
            return new OpenPgpDecryptionResult[size];
        }
    };

    @Override
    public String toString() {
        return "\nresult: " + result;
    }

}
