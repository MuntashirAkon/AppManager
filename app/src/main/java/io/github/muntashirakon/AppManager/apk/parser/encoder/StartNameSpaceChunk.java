// SPDX-License-Identifier: MIT

package io.github.muntashirakon.AppManager.apk.parser.encoder;

import java.io.IOException;

// Copyright 2015 Roy
public class StartNameSpaceChunk extends Chunk<StartNameSpaceChunk.H> {

    public StartNameSpaceChunk(Chunk parent) {
        super(parent);
    }

    public class H extends NodeHeader {
        public H() {
            super(ChunkType.XmlStartNamespace);
            size = 0x18;
        }
    }

    public String prefix;
    public String uri;

    @Override
    public void writeEx(IntWriter w) throws IOException {
        w.write(stringIndex(null, prefix));
        w.write(stringIndex(null, uri));
    }
}
