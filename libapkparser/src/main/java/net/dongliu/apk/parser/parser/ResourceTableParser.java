// SPDX-License-Identifier: BSD-2-Clause

package net.dongliu.apk.parser.parser;

import android.util.Pair;

import androidx.annotation.NonNull;

import net.dongliu.apk.parser.exception.ParserException;
import net.dongliu.apk.parser.struct.ChunkHeader;
import net.dongliu.apk.parser.struct.ChunkType;
import net.dongliu.apk.parser.struct.StringPool;
import net.dongliu.apk.parser.struct.StringPoolHeader;
import net.dongliu.apk.parser.struct.resource.LibraryEntry;
import net.dongliu.apk.parser.struct.resource.LibraryHeader;
import net.dongliu.apk.parser.struct.resource.NullHeader;
import net.dongliu.apk.parser.struct.resource.PackageHeader;
import net.dongliu.apk.parser.struct.resource.ResTableConfig;
import net.dongliu.apk.parser.struct.resource.ResourcePackage;
import net.dongliu.apk.parser.struct.resource.ResourceTable;
import net.dongliu.apk.parser.struct.resource.ResourceTableHeader;
import net.dongliu.apk.parser.struct.resource.Type;
import net.dongliu.apk.parser.struct.resource.TypeHeader;
import net.dongliu.apk.parser.struct.resource.TypeSpec;
import net.dongliu.apk.parser.struct.resource.TypeSpecHeader;
import net.dongliu.apk.parser.utils.Buffers;
import net.dongliu.apk.parser.utils.ParseUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Parse android resource table file.
 *
 * @see <a href="https://github.com/aosp-mirror/platform_frameworks_base/blob/master/libs/androidfw/include/androidfw/ResourceTypes.h">ResourceTypes.h</a>
 * @see <a href="https://github.com/aosp-mirror/platform_frameworks_base/blob/master/libs/androidfw/ResourceTypes.cpp">ResourceTypes.cpp</a>
 */
// Copyright 2014 Liu Dong
public class ResourceTableParser {

    /**
     * By default, the data buffer Chunks is buffer little-endian byte order both at runtime and when stored buffer files.
     */
    private final ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
    private StringPool stringPool;
    private final ByteBuffer buffer;
    // The resource table file size
    private ResourceTable resourceTable;

    public ResourceTableParser(ByteBuffer buffer) {
        this.buffer = buffer.duplicate();
        this.buffer.order(byteOrder);
    }

    public ResourceTable getResourceTable() {
        return resourceTable;
    }

    /**
     * parse resource table file.
     */
    public void parse() {
        // read resource file header.
        ResourceTableHeader resourceTableHeader = (ResourceTableHeader) readChunkHeader();

        // read string pool chunk
        stringPool = ParseUtils.readStringPool(buffer, (StringPoolHeader) readChunkHeader());

        resourceTable = new ResourceTable();
        resourceTable.setStringPool(stringPool);

        if (resourceTableHeader.getPackageCount() != 0) {
            PackageHeader packageHeader = (PackageHeader) readChunkHeader();
            for (int i = 0; i < resourceTableHeader.getPackageCount(); i++) {
                Pair<ResourcePackage, PackageHeader> pair = readPackage(packageHeader);
                resourceTable.addPackage(pair.first);
                packageHeader = pair.second;
            }
        }
    }

    @NonNull // read one package
    private Pair<ResourcePackage, PackageHeader> readPackage(PackageHeader packageHeader) {
        //read packageHeader
        ResourcePackage resourcePackage = new ResourcePackage(packageHeader);
        PackageHeader header = null;

        long beginPos = buffer.position();
        // read type string pool
        if (packageHeader.getTypeStrings() > 0) {
            Buffers.position(buffer, beginPos + packageHeader.getTypeStrings() - packageHeader.getHeaderSize());
            resourcePackage.setTypeStringPool(ParseUtils.readStringPool(buffer,
                    (StringPoolHeader) readChunkHeader()));
        }

        //read key string pool
        if (packageHeader.getKeyStrings() > 0) {
            Buffers.position(buffer, beginPos + packageHeader.getKeyStrings() - packageHeader.getHeaderSize());
            resourcePackage.setKeyStringPool(ParseUtils.readStringPool(buffer,
                    (StringPoolHeader) readChunkHeader()));
        }


        outer:
        while (buffer.hasRemaining()) {
            ChunkHeader chunkHeader = readChunkHeader();
            long chunkBegin = buffer.position();
            switch (chunkHeader.getChunkType()) {
                case ChunkType.TABLE_TYPE_SPEC:
                    TypeSpecHeader typeSpecHeader = (TypeSpecHeader) chunkHeader;
                    long[] entryFlags = new long[(int) typeSpecHeader.getEntryCount()];
                    for (int i = 0; i < typeSpecHeader.getEntryCount(); i++) {
                        entryFlags[i] = Buffers.readUInt(buffer);
                    }

                    TypeSpec typeSpec = new TypeSpec(typeSpecHeader);


                    typeSpec.setEntryFlags(entryFlags);
                    //id start from 1
                    typeSpec.setName(resourcePackage.getTypeStringPool()
                            .get(typeSpecHeader.getId() - 1));

                    resourcePackage.addTypeSpec(typeSpec);
                    Buffers.position(buffer, chunkBegin + typeSpecHeader.getBodySize());
                    break;
                case ChunkType.TABLE_TYPE:
                    TypeHeader typeHeader = (TypeHeader) chunkHeader;
                    // read offsets table
                    long[] offsets = new long[(int) typeHeader.getEntryCount()];
                    for (int i = 0; i < typeHeader.getEntryCount(); i++) {
                        offsets[i] = Buffers.readUInt(buffer);
                    }

                    Type type = new Type(typeHeader);
                    type.setName(resourcePackage.getTypeStringPool().get(typeHeader.getId() - 1));
                    long entryPos = chunkBegin + typeHeader.getEntriesStart() - typeHeader.getHeaderSize();
                    Buffers.position(buffer, entryPos);
                    ByteBuffer b = buffer.slice();
                    b.order(byteOrder);
                    type.setBuffer(b);
                    type.setKeyStringPool(resourcePackage.getKeyStringPool());
                    type.setOffsets(offsets);
                    type.setStringPool(stringPool);
                    resourcePackage.addType(type);
                    Buffers.position(buffer, chunkBegin + typeHeader.getBodySize());
                    break;
                case ChunkType.TABLE_PACKAGE:
                    // another package. we should read next package here
                    header = (PackageHeader) chunkHeader;
                    break outer;
                case ChunkType.TABLE_LIBRARY:
                    // read entries
                    LibraryHeader libraryHeader = (LibraryHeader) chunkHeader;
                    for (long i = 0; i < libraryHeader.getCount(); i++) {
                        int packageId = buffer.getInt();
                        String name = Buffers.readZeroTerminatedString(buffer, 128);
                        LibraryEntry entry = new LibraryEntry(packageId, name);
                        //TODO: now just skip it..
                    }
                    Buffers.position(buffer, chunkBegin + chunkHeader.getBodySize());
                    break;
                case ChunkType.NULL:
//                    Buffers.position(buffer, chunkBegin + chunkHeader.getBodySize());
                    Buffers.position(buffer, buffer.position() + buffer.remaining());
                    break;
                default:
                    throw new ParserException("unexpected chunk type: 0x" + chunkHeader.getChunkType());
            }
        }

        return new Pair<>(resourcePackage, header);

    }

    private ChunkHeader readChunkHeader() {
        long begin = buffer.position();

        int chunkType = Buffers.readUShort(buffer);
        int headerSize = Buffers.readUShort(buffer);
        int chunkSize = (int) Buffers.readUInt(buffer);

        switch (chunkType) {
            case ChunkType.TABLE:
                ResourceTableHeader resourceTableHeader = new ResourceTableHeader(headerSize, chunkSize);
                resourceTableHeader.setPackageCount(Buffers.readUInt(buffer));
                Buffers.position(buffer, begin + headerSize);
                return resourceTableHeader;
            case ChunkType.STRING_POOL:
                StringPoolHeader stringPoolHeader = new StringPoolHeader(headerSize, chunkSize);
                stringPoolHeader.setStringCount(Buffers.readUInt(buffer));
                stringPoolHeader.setStyleCount(Buffers.readUInt(buffer));
                stringPoolHeader.setFlags(Buffers.readUInt(buffer));
                stringPoolHeader.setStringsStart(Buffers.readUInt(buffer));
                stringPoolHeader.setStylesStart(Buffers.readUInt(buffer));
                Buffers.position(buffer, begin + headerSize);
                return stringPoolHeader;
            case ChunkType.TABLE_PACKAGE:
                PackageHeader packageHeader = new PackageHeader(headerSize, chunkSize);
                packageHeader.setId(Buffers.readUInt(buffer));
                packageHeader.setName(ParseUtils.readStringUTF16(buffer, 128));
                packageHeader.setTypeStrings(Buffers.readUInt(buffer));
                packageHeader.setLastPublicType(Buffers.readUInt(buffer));
                packageHeader.setKeyStrings(Buffers.readUInt(buffer));
                packageHeader.setLastPublicKey(Buffers.readUInt(buffer));
                Buffers.position(buffer, begin + headerSize);
                return packageHeader;
            case ChunkType.TABLE_TYPE_SPEC:
                TypeSpecHeader typeSpecHeader = new TypeSpecHeader(headerSize, chunkSize);
                typeSpecHeader.setId(Buffers.readUByte(buffer));
                typeSpecHeader.setRes0(Buffers.readUByte(buffer));
                typeSpecHeader.setRes1(Buffers.readUShort(buffer));
                typeSpecHeader.setEntryCount(Buffers.readUInt(buffer));
                Buffers.position(buffer, begin + headerSize);
                return typeSpecHeader;
            case ChunkType.TABLE_TYPE:
                TypeHeader typeHeader = new TypeHeader(headerSize, chunkSize);
                typeHeader.setId(Buffers.readUByte(buffer));
                typeHeader.setRes0(Buffers.readUByte(buffer));
                typeHeader.setRes1(Buffers.readUShort(buffer));
                typeHeader.setEntryCount(Buffers.readUInt(buffer));
                typeHeader.setEntriesStart(Buffers.readUInt(buffer));
                typeHeader.setConfig(readResTableConfig());
                Buffers.position(buffer, begin + headerSize);
                return typeHeader;
            case ChunkType.TABLE_LIBRARY:
                //DynamicRefTable
                LibraryHeader libraryHeader = new LibraryHeader(headerSize, chunkSize);
                libraryHeader.setCount(Buffers.readUInt(buffer));
                Buffers.position(buffer, begin + headerSize);
                return libraryHeader;
            case ChunkType.NULL:
                Buffers.position(buffer, begin + headerSize);
                return new NullHeader(headerSize, chunkSize);
            default:
                throw new ParserException("Unexpected chunk Type: 0x" + Integer.toHexString(chunkType));
        }
    }

    private ResTableConfig readResTableConfig() {
        long beginPos = buffer.position();
        ResTableConfig config = new ResTableConfig();
        long size = Buffers.readUInt(buffer);

        // imsi
        config.setMcc(buffer.getShort());
        config.setMnc(buffer.getShort());
        //read locale
        config.setLanguage(new String(Buffers.readBytes(buffer, 2)).replace("\0", ""));
        config.setCountry(new String(Buffers.readBytes(buffer, 2)).replace("\0", ""));
        //screen type
        config.setOrientation(Buffers.readUByte(buffer));
        config.setTouchscreen(Buffers.readUByte(buffer));
        config.setDensity(Buffers.readUShort(buffer));
        // now just skip the others...
        long endPos = buffer.position();
        Buffers.skip(buffer, (int) (size - (endPos - beginPos)));
        return config;
    }
}
