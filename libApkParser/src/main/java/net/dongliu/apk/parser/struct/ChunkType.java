package net.dongliu.apk.parser.struct;

/**
 * Resource type
 * see https://android.googlesource.com/platform/frameworks/base/+/master/libs/androidfw/include/androidfw/ResourceTypes.h
 *
 * @author dongliu
 */
public class ChunkType {
    public static final int NULL = 0x0000;
    public static final int STRING_POOL = 0x0001;
    public static final int TABLE = 0x0002;
    public static final int XML = 0x0003;

    // Chunk types in XML
    public static final int XML_FIRST_CHUNK = 0x0100;
    public static final int XML_START_NAMESPACE = 0x0100;
    public static final int XML_END_NAMESPACE = 0x0101;
    public static final int XML_START_ELEMENT = 0x0102;
    public static final int XML_END_ELEMENT = 0x0103;
    public static final int XML_CDATA = 0x0104;
    public static final int XML_LAST_CHUNK = 0x017f;
    // This contains a uint32_t array mapping strings in the string
    // pool back to resource identifiers.  It is optional.
    public static final int XML_RESOURCE_MAP = 0x0180;

    // Chunk types in RES_TABLE_TYPE
    public static final int TABLE_PACKAGE = 0x0200;
    public static final int TABLE_TYPE = 0x0201;
    public static final int TABLE_TYPE_SPEC = 0x0202;
    // android5.0+
    // DynamicRefTable
    public static final int TABLE_LIBRARY = 0x0203;
    //TODO: fix this later. Do not found definition for chunk type 0x0204 in android source yet...
    public static final int UNKNOWN_YET = 0x0204;
}
