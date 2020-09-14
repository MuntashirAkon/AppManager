package net.dongliu.apk.parser.struct.resource;

import net.dongliu.apk.parser.struct.ChunkHeader;
import net.dongliu.apk.parser.struct.ChunkType;
import net.dongliu.apk.parser.utils.Unsigned;

/**
 * @author dongliu
 */
public class TypeHeader extends ChunkHeader {

    public static final long NO_ENTRY = 0xFFFFFFFFL;

    // The type identifier this chunk is holding.  Type IDs start at 1 (corresponding to the value
    // of the type bits in a resource identifier).  0 is invalid.
    // uint8_t
    private byte id;

    // Must be 0. uint8_t
    private byte res0;
    // Must be 0. uint16_t
    private short res1;

    // Number of uint32_t entry indices that follow. uint32
    private int entryCount;

    // Offset from header where ResTable_entry data starts.uint32_t
    private int entriesStart;

    // Configuration this collection of entries is designed for.
    private ResTableConfig config;

    public TypeHeader(int headerSize, long chunkSize) {
        super(ChunkType.TABLE_TYPE, headerSize, chunkSize);
    }

    public short getId() {
        return Unsigned.toShort(id);
    }

    public void setId(short id) {
        this.id = Unsigned.toUByte(id);
    }

    public short getRes0() {
        return Unsigned.toUShort(res0);
    }

    public void setRes0(short res0) {
        this.res0 = Unsigned.toUByte(res0);
    }

    public int getRes1() {
        return Unsigned.toInt(res1);
    }

    public void setRes1(int res1) {
        this.res1 = Unsigned.toUShort(res1);
    }

    public int getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(long entryCount) {
        this.entryCount = Unsigned.ensureUInt(entryCount);
    }

    public int getEntriesStart() {
        return entriesStart;
    }

    public void setEntriesStart(long entriesStart) {
        this.entriesStart = Unsigned.ensureUInt(entriesStart);
    }

    public ResTableConfig getConfig() {
        return config;
    }

    public void setConfig(ResTableConfig config) {
        this.config = config;
    }
}
