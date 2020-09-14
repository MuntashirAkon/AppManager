package net.dongliu.apk.parser.struct.resource;

import net.dongliu.apk.parser.struct.ResourceValue;

/**
 * @author dongliu
 */
public class ResourceTableMap {
    // ...elided
    // ResTable_ref; unit32
    private long nameRef;

    private ResourceValue resValue;
    private String data;

    public long getNameRef() {
        return nameRef;
    }

    public void setNameRef(long nameRef) {
        this.nameRef = nameRef;
    }

    public ResourceValue getResValue() {
        return resValue;
    }

    public void setResValue(ResourceValue resValue) {
        this.resValue = resValue;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return data;
    }

    public static class MapAttr {
        public static final int TYPE = 0x01000000 | (0 & 0xFFFF);

        // For integral attributes; this is the minimum value it can hold.
        public static final int MIN = 0x01000000 | (1 & 0xFFFF);

        // For integral attributes; this is the maximum value it can hold.
        public static final int MAX = 0x01000000 | (2 & 0xFFFF);

        // Localization of this resource is can be encouraged or required with
        // an aapt flag if this is set
        public static final int L10N = 0x01000000 | (3 & 0xFFFF);

        // for plural support; see android.content.res.PluralRules#attrForQuantity(int)
        public static final int OTHER = 0x01000000 | (4 & 0xFFFF);
        public static final int ZERO = 0x01000000 | (5 & 0xFFFF);
        public static final int ONE = 0x01000000 | (6 & 0xFFFF);
        public static final int TWO = 0x01000000 | (7 & 0xFFFF);
        public static final int FEW = 0x01000000 | (8 & 0xFFFF);
        public static final int MANY = 0x01000000 | (9 & 0xFFFF);

        public static int makeArray(int entry) {
            return (0x02000000 | (entry & 0xFFFF));
        }

    }

    public static class AttributeType {
        // No type has been defined for this attribute; use generic
        // type handling.  The low 16 bits are for types that can be
        // handled generically; the upper 16 require additional information
        // in the bag so can not be handled generically for ANY.
        public static final int ANY = 0x0000FFFF;

        // Attribute holds a references to another resource.
        public static final int REFERENCE = 1;

        // Attribute holds a generic string.
        public static final int STRING = 1 << 1;

        // Attribute holds an integer value.  ATTR_MIN and ATTR_MIN can
        // optionally specify a constrained range of possible integer values.
        public static final int INTEGER = 1 << 2;

        // Attribute holds a boolean integer.
        public static final int BOOLEAN = 1 << 3;

        // Attribute holds a color value.
        public static final int COLOR = 1 << 4;

        // Attribute holds a floating point value.
        public static final int FLOAT = 1 << 5;

        // Attribute holds a dimension value; such as "20px".
        public static final int DIMENSION = 1 << 6;

        // Attribute holds a fraction value; such as "20%".
        public static final int FRACTION = 1 << 7;

        // Attribute holds an enumeration.  The enumeration values are
        // supplied as additional entries in the map.
        public static final int ENUM = 1 << 16;

        // Attribute holds a bitmaks of flags.  The flag bit values are
        // supplied as additional entries in the map.
        public static final int FLAGS = 1 << 17;
    }
}
