package net.dongliu.apk.parser.struct.resource;

import net.dongliu.apk.parser.struct.ResourceValue;

import java.util.Locale;

import androidx.annotation.Nullable;

/**
 * A Resource entry specifies the key (name) of the Resource.
 * It is immediately followed by the value of that Resource.
 *
 * @author dongliu
 */
public class ResourceEntry {
    // Number of bytes in this structure. uint16_t
    private int size;

    // If set, this is a complex entry, holding a set of name/value
    // mappings.  It is followed by an array of ResTable_map structures.
    public static final int FLAG_COMPLEX = 0x0001;
    // If set, this resource has been declared public, so libraries
    // are allowed to reference it.
    public static final int FLAG_PUBLIC = 0x0002;
    // uint16_t
    private int flags;

    // Reference into ResTable_package::keyStrings identifying this entry.
    //public long keyRef;

    private String key;

    // the resvalue following this resource entry.
    private ResourceValue value;

    /**
     * get value as string
     *
     * @return
     */
    public String toStringValue(ResourceTable resourceTable, Locale locale) {
        if (value != null) {
            return value.toStringValue(resourceTable, locale);
        } else {
            return "null";
        }
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @Nullable
    public ResourceValue getValue() {
        return value;
    }

    @Nullable
    public void setValue(@Nullable ResourceValue value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "ResourceEntry{" +
                "size=" + size +
                ", flags=" + flags +
                ", key='" + key + '\'' +
                ", value=" + value +
                '}';
    }
}
