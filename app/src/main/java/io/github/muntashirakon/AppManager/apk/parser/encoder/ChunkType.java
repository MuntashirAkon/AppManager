// SPDX-License-Identifier: MIT

package io.github.muntashirakon.AppManager.apk.parser.encoder;

// Copyright 2015 Roy
enum ChunkType {
    Null(0x0000, -1),
    StringPool(0x0001, 28),
    Table(0x0002, -1),
    Xml(0x0003, 8),

    // Chunk types in XML
    XmlFirstChunk(0x0100, -1),
    XmlStartNamespace(0x0100, 0x10),
    XmlEndNamespace(0x0101, 0x10),
    XmlStartElement(0x0102, 0x10),
    XmlEndElement(0x0103, 0x10),
    XmlCdata(0x0104, -1),
    XmlLastChunk(0x017f, -1),
    // This contains a uint32_t array mapping strings in the string
    // pool back to resource identifiers.  It is optional.
    XmlResourceMap(0x0180, 8),

    // Chunk types in RES_TABLE_TYPE
    TablePackage(0x0200, -1),
    TableType(0x0201, -1),
    TableTypeSpec(0x0202, -1);

    public final short type;
    public final short headerSize;

    ChunkType(int type, int headerSize) {
        this.type = (short) type;
        this.headerSize = (short) headerSize;
    }
};

