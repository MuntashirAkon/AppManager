// SPDX-License-Identifier: Apache-2.0

package org.openintents.openpgp;

import android.os.Parcel;
import android.os.Parcelable;

// Copyright 2014-2015 Dominik Schürmann
public class OpenPgpMetadata implements Parcelable {
    /**
     * Since there might be a case where new versions of the client using the library getting
     * old versions of the protocol (and thus old versions of this class), we need a versioning
     * system for the parcels sent between the clients and the providers.
     */
    public static final int PARCELABLE_VERSION = 2;

    String filename;
    String mimeType;
    String charset;
    long modificationTime;
    long originalSize;

    public String getFilename() {
        return filename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getModificationTime() {
        return modificationTime;
    }

    public long getOriginalSize() {
        return originalSize;
    }

    public String getCharset() {
        return charset;
    }

    public OpenPgpMetadata() {
    }

    public OpenPgpMetadata(String filename, String mimeType, long modificationTime,
                           long originalSize, String charset) {
        this.filename = filename;
        this.mimeType = mimeType;
        this.modificationTime = modificationTime;
        this.originalSize = originalSize;
        this.charset = charset;
    }

    public OpenPgpMetadata(String filename, String mimeType, long modificationTime,
                           long originalSize) {
        this.filename = filename;
        this.mimeType = mimeType;
        this.modificationTime = modificationTime;
        this.originalSize = originalSize;
    }

    public OpenPgpMetadata(OpenPgpMetadata b) {
        this.filename = b.filename;
        this.mimeType = b.mimeType;
        this.modificationTime = b.modificationTime;
        this.originalSize = b.originalSize;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        /*
         * NOTE: When adding fields in the process of updating this API, make sure to bump
         * {@link #PARCELABLE_VERSION}.
         */
        dest.writeInt(PARCELABLE_VERSION);
        // Inject a placeholder that will store the parcel size from this point on
        // (not including the size itself).
        int sizePosition = dest.dataPosition();
        dest.writeInt(0);
        int startPosition = dest.dataPosition();
        // version 1
        dest.writeString(filename);
        dest.writeString(mimeType);
        dest.writeLong(modificationTime);
        dest.writeLong(originalSize);
        // version 2
        dest.writeString(charset);
        // Go back and write the size
        int parcelableSize = dest.dataPosition() - startPosition;
        dest.setDataPosition(sizePosition);
        dest.writeInt(parcelableSize);
        dest.setDataPosition(startPosition + parcelableSize);
    }

    public static final Creator<OpenPgpMetadata> CREATOR = new Creator<OpenPgpMetadata>() {
        public OpenPgpMetadata createFromParcel(final Parcel source) {
            int version = source.readInt(); // parcelableVersion
            int parcelableSize = source.readInt();
            int startPosition = source.dataPosition();

            OpenPgpMetadata vr = new OpenPgpMetadata();
            vr.filename = source.readString();
            vr.mimeType = source.readString();
            vr.modificationTime = source.readLong();
            vr.originalSize = source.readLong();
            if (version >= 2) {
                vr.charset = source.readString();
            }

            // skip over all fields added in future versions of this parcel
            source.setDataPosition(startPosition + parcelableSize);

            return vr;
        }

        public OpenPgpMetadata[] newArray(final int size) {
            return new OpenPgpMetadata[size];
        }
    };

    @Override
    public String toString() {
        String out = "\nfilename: " + filename;
        out += "\nmimeType: " + mimeType;
        out += "\nmodificationTime: " + modificationTime;
        out += "\noriginalSize: " + originalSize;
        out += "\ncharset: " + charset;
        return out;
    }

}
