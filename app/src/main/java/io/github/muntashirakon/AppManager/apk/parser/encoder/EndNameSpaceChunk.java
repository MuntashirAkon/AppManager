// SPDX-License-Identifier: MIT

package io.github.muntashirakon.AppManager.apk.parser.encoder;

import java.io.IOException;

// Copyright 2015 Roy
public class EndNameSpaceChunk extends Chunk<EndNameSpaceChunk.H> {


    public class H extends NodeHeader {
        public H() {
            super(ChunkType.XmlEndNamespace);
            size = 0x18;
        }
    }

    public StartNameSpaceChunk start;

    public EndNameSpaceChunk(Chunk parent, StartNameSpaceChunk start) {
        super(parent);
        this.start = start;
    }

    @Override
    public void writeEx(IntWriter w) throws IOException {
        start.writeEx(w);
    }
}