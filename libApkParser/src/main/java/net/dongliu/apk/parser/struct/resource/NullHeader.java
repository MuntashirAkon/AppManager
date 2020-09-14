package net.dongliu.apk.parser.struct.resource;

import net.dongliu.apk.parser.struct.ChunkHeader;
import net.dongliu.apk.parser.struct.ChunkType;

public class NullHeader extends ChunkHeader {
    public NullHeader(int headerSize, int chunkSize) {
        super(ChunkType.NULL, headerSize, chunkSize);
    }
}
