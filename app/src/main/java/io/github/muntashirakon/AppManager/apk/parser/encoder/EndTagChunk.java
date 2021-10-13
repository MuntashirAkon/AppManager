// SPDX-License-Identifier: MIT

package io.github.muntashirakon.AppManager.apk.parser.encoder;

import androidx.annotation.NonNull;

import java.io.IOException;

// Copyright 2015 Roy
public class EndTagChunk extends Chunk<EndTagChunk.H> {
    public class H extends NodeHeader {

        public H() {
            super(ChunkType.XmlEndElement);
            this.size = 24;
        }
    }

    public StartTagChunk start;

    public EndTagChunk(@NonNull Chunk<EmptyHeader> parent, StartTagChunk start) {
        super(parent);
        this.start = start;
    }

    @Override
    public void writeEx(IntWriter w) throws IOException {
        w.write(stringIndex(null, start.namespace));
        w.write(stringIndex(null, start.name));
    }
}
