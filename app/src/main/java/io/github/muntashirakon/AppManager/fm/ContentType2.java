// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.j256.simplemagic.entries.IanaEntries;
import com.j256.simplemagic.entries.IanaEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum ContentType2 {
    DEX("application/x-dex", "dex", "dex"),
    PEM("application/pem-certificate-chain", "pem", "pem"),
    PK8("application/pkcs8", "pkcs8", "pk8"),
    M4A("audio/mp4a-latm", "mp4a-latm", "m4a"),
    SMALI("text/x-smali", "smali", "smali"),
    /** default if no specific match to the mime-type */
    OTHER("application/octet-stream", "other"),
    ;

    private final static Map<String, ContentType2> mimeTypeMap = new HashMap<>();
    private final static Map<String, ContentType2> fileExtensionMap = new HashMap<>();
    private static IanaEntries ianaEntries;

    static {
        for (ContentType2 type : values()) {
            if (type.mimeType != null) {
                // NOTE: this may overwrite this mapping
                mimeTypeMap.put(type.mimeType.toLowerCase(), type);
            }
            if (type.fileExtensions != null) {
                for (String fileExtension : type.fileExtensions) {
                    // NOTE: this may overwrite this mapping
                    fileExtensionMap.put(fileExtension, type);
                }
            }
        }
    }

    private final String mimeType;
    private final String simpleName;
    private final String[] fileExtensions;
    private final IanaEntry ianaEntry;

    private ContentType2(String mimeType, String simpleName, String... fileExtensions) {
        this.mimeType = mimeType;
        this.simpleName = simpleName;
        this.fileExtensions = fileExtensions;
        this.ianaEntry = findIanaEntryByMimeType(mimeType);
    }

    /**
     * Get simple name of the type.
     */
    public String getSimpleName() {
        return simpleName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String[] getFileExtensions() {
        return fileExtensions;
    }

    /**
     * Return the type associated with the mime-type string or {@link #OTHER} if not found.
     */
    public static ContentType2 fromMimeType(String mimeType) {
        // NOTE: mimeType can be null
        if (mimeType != null) {
            mimeType = mimeType.toLowerCase();
        }
        ContentType2 type = mimeTypeMap.get(mimeType);
        if (type == null) {
            return OTHER;
        } else {
            return type;
        }

    }

    /**
     * Return the type associated with the file-extension string or {@code null} if not found.
     */
    @Nullable
    public static ContentType2 fromFileExtension(@NonNull String fileExtension) {
        return fileExtensionMap.get(fileExtension.toLowerCase());
    }

    /**
     * Returns the references of the mime type or null if none.
     */
    public List<String> getReferences() {
        if (ianaEntry == null) {
            return null;
        } else {
            return ianaEntry.getReferences();
        }
    }

    /**
     * Returns the URL of the references or null if none.
     */
    public List<String> getReferenceUrls() {
        if (ianaEntry == null) {
            return null;
        } else {
            return ianaEntry.getReferenceUrls();
        }
    }

    private static IanaEntry findIanaEntryByMimeType(String mimeType) {
        if (ianaEntries == null) {
            ianaEntries = new IanaEntries();
        }
        return ianaEntries.lookupByMimeType(mimeType);
    }
}
