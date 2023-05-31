// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.j256.simplemagic.entries.IanaEntries;
import com.j256.simplemagic.entries.IanaEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public enum ContentType2 {
    APKM("application/vnd.apkm", "apkm", "apkm"),
    APKS("application/x-apks", "apks", "apks"),
    CONFIGURATION("text/plain", "configuration", "cnf", "conf", "cfg", "cf", "ini", "rc", "sys"),
    DEX("application/x-dex", "dex", "dex"),
    KOTLIN("text/x-kotlin", "kotlin", "kt"),
    LOG("text/plain", "log", "log"),
    LUA("text/x-lua", "lua", "lua"),
    M4A("audio/mp4a-latm", "mp4a-latm", "m4a"),
    MARKDOWN("text/markdown", "markdown", "md", "markdown"),
    PEM("application/pem-certificate-chain", "pem", "pem"),
    PK8("application/pkcs8", "pkcs8", "pk8"),
    PLIST("application/x-plist", "property-list", "plist"),
    PROPERTIES("text/plain", "properties", "prop", "properties"),
    SMALI("text/x-smali", "smali", "smali"),
    SQLITE3("application/vnd.sqlite3", "sqlite", "db", "db3", "s3db", "sl3", "sqlite", "sqlite3"),
    TOML("application/toml", "toml", "toml"),
    XAPK("application/xapk-package-archive", "xapk", "xapk"),
    YAML("text/plain", "yaml", "yml", "yaml"),
    /** default if no specific match to the mime-type */
    OTHER("application/octet-stream", "other"),
    ;

    private final static Map<String, ContentType2> mimeTypeMap = new HashMap<>();
    private final static Map<String, ContentType2> fileExtensionMap = new HashMap<>();
    private static IanaEntries ianaEntries;

    static {
        for (ContentType2 type : values()) {
            // NOTE: this may overwrite this mapping
            mimeTypeMap.put(type.mimeType.toLowerCase(Locale.ROOT), type);
            if (type.fileExtensions != null) {
                for (String fileExtension : type.fileExtensions) {
                    // NOTE: this may overwrite this mapping
                    fileExtensionMap.put(fileExtension, type);
                }
            }
        }
    }

    @NonNull
    private final String mimeType;
    @NonNull
    private final String simpleName;
    @Nullable
    private final String[] fileExtensions;
    @Nullable
    private final IanaEntry ianaEntry;

    ContentType2(@NonNull String mimeType, @NonNull String simpleName, @Nullable String... fileExtensions) {
        this.mimeType = mimeType;
        this.simpleName = simpleName;
        this.fileExtensions = fileExtensions;
        this.ianaEntry = findIanaEntryByMimeType(mimeType);
    }

    /**
     * Get simple name of the type.
     */
    @NonNull
    public String getSimpleName() {
        return simpleName;
    }

    @NonNull
    public String getMimeType() {
        return mimeType;
    }

    @Nullable
    public String[] getFileExtensions() {
        return fileExtensions;
    }

    /**
     * Return the type associated with the mime-type string or {@link #OTHER} if not found.
     */
    @NonNull
    public static ContentType2 fromMimeType(String mimeType) {
        // NOTE: mimeType can be null
        if (mimeType != null) {
            mimeType = mimeType.toLowerCase(Locale.ROOT);
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
        return fileExtensionMap.get(fileExtension.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns the references of the mime type or null if none.
     */
    @Nullable
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
    @Nullable
    public List<String> getReferenceUrls() {
        if (ianaEntry == null) {
            return null;
        } else {
            return ianaEntry.getReferenceUrls();
        }
    }

    @Nullable
    private static IanaEntry findIanaEntryByMimeType(String mimeType) {
        if (ianaEntries == null) {
            ianaEntries = new IanaEntries();
        }
        return ianaEntries.lookupByMimeType(mimeType);
    }
}
