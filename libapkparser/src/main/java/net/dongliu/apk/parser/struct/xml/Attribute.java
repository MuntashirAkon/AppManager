package net.dongliu.apk.parser.struct.xml;

import android.util.SparseArray;

import net.dongliu.apk.parser.struct.ResourceValue;
import net.dongliu.apk.parser.struct.resource.ResourceTable;
import net.dongliu.apk.parser.utils.ResourceLoader;

import java.util.Locale;
import java.util.Map;

/**
 * xml node attribute
 *
 * @author dongliu
 */
public class Attribute {
    private String namespace;
    private String name;
    // The original raw string value of Attribute
    private String rawValue;
    // Processed typed value of Attribute
    private ResourceValue typedValue;
    // the final value as string
    private String value;

    public String toStringValue(ResourceTable resourceTable, Locale locale) {
        if (rawValue != null) {
            return rawValue;
        } else if (typedValue != null) {
            return typedValue.toStringValue(resourceTable, locale);
        } else {
            // something happen;
            return "";
        }
    }

    /**
     * These are attribute resource constants for the platform; as found in android.R.attr
     *
     * @author dongliu
     */
    public static class AttrIds {

        private static final SparseArray<String> ids = ResourceLoader.loadSystemAttrIds();

        public static String getString(long id) {
            String value = ids.get((int) id);
            if (value == null) {
                value = "AttrId:0x" + Long.toHexString(id);
            }
            return value;
        }

    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRawValue() {
        return rawValue;
    }

    public void setRawValue(String rawValue) {
        this.rawValue = rawValue;
    }

    public ResourceValue getTypedValue() {
        return typedValue;
    }

    public void setTypedValue(ResourceValue typedValue) {
        this.typedValue = typedValue;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "Attribute{" +
                "name='" + name + '\'' +
                ", namespace='" + namespace + '\'' +
                '}';
    }
}
