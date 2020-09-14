package net.dongliu.apk.parser.struct;

import net.dongliu.apk.parser.utils.Unsigned;

/**
 * A Chunk is just a piece of memory split into two parts, a header and a body.
 * The exact structure of the header and the body of a given Chunk is determined by its type.
 * <pre>
 * chunk header struct.
 * struct ResChunk_header {
 *     uint16_t type;
 *     uint16_t headerSize;
 *     uint32_t size;
 * }
 * </pre>
 *
 * @author dongliu
 */
public class ChunkHeader {

    // Type identifier for this chunk.  The meaning of this value depends
    // on the containing chunk.
    private short chunkType;

    // Size of the chunk header (in bytes).  Adding this value to
    // the address of the chunk allows you to find its associated data
    // (if any).
    private short headerSize;

    // Total size of this chunk (in bytes).  This is the chunkSize plus
    // the size of any data associated with the chunk.  Adding this value
    // to the chunk allows you to completely skip its contents (including
    // any child chunks).  If this value is the same as chunkSize, there is
    // no data associated with the chunk.
    private int chunkSize;

    public ChunkHeader(int chunkType, int headerSize, long chunkSize) {
        this.chunkType = Unsigned.toUShort(chunkType);
        this.headerSize = Unsigned.toUShort(headerSize);
        this.chunkSize = Unsigned.ensureUInt(chunkSize);
    }

    public int getBodySize() {
        return this.chunkSize - this.headerSize;
    }

    public int getChunkType() {
        return chunkType;
    }

    public void setChunkType(int chunkType) {
        this.chunkType = Unsigned.toUShort(chunkType);
    }

    public int getHeaderSize() {
        return headerSize;
    }

    public void setHeaderSize(int headerSize) {
        this.headerSize = Unsigned.toUShort(headerSize);
    }

    public long getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(long chunkSize) {
        this.chunkSize = Unsigned.ensureUInt(chunkSize);
    }
}
