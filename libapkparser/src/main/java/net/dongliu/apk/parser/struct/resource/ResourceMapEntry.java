package net.dongliu.apk.parser.struct.resource;

import java.util.Arrays;
import java.util.Locale;

/**
 * @author dongliu.
 */
public class ResourceMapEntry extends ResourceEntry {
    // Resource identifier of the parent mapping, or 0 if there is none.
    //ResTable_ref specifies the parent Resource, if any, of this Resource.
    // struct ResTable_ref { uint32_t ident; };
    private long parent;

    // Number of name/value pairs that follow for FLAG_COMPLEX. uint32_t
    private long count;

    private ResourceTableMap[] resourceTableMaps;

    public ResourceMapEntry(ResourceEntry resourceEntry) {
        this.setSize(resourceEntry.getSize());
        this.setFlags(resourceEntry.getFlags());
        this.setKey(resourceEntry.getKey());
    }

    public long getParent() {
        return parent;
    }

    public void setParent(long parent) {
        this.parent = parent;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public ResourceTableMap[] getResourceTableMaps() {
        return resourceTableMaps;
    }

    public void setResourceTableMaps(ResourceTableMap[] resourceTableMaps) {
        this.resourceTableMaps = resourceTableMaps;
    }

    /**
     * get value as string
     *
     * @return
     */
    public String toStringValue(ResourceTable resourceTable, Locale locale) {
        if (resourceTableMaps.length > 0) {
            return resourceTableMaps[0].toString();
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return "ResourceMapEntry{" +
                "parent=" + parent +
                ", count=" + count +
                ", resourceTableMaps=" + Arrays.toString(resourceTableMaps) +
                '}';
    }
}
