// SPDX-License-Identifier: BSD-2-Clause

package net.dongliu.apk.parser.struct;

/**
 * Resource type
 * see https://android.googlesource.com/platform/frameworks/base/+/master/libs/androidfw/include/androidfw/ResourceTypes.h
 */
// Copyright 2014 Liu Dong
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
    public static final int TABLE_LIBRARY = 0x0203;
    public static final int TABLE_OVERLAYABLE = 0x0204; // TODO: 12/10/21
    public static final int TABLE_OVERLAYABLE_POLICY = 0x0205; // TODO: 12/10/21
    public static final int TABLE_STAGED_ALIAS = 0x0206; // TODO: 12/10/21
}
