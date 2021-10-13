// SPDX-License-Identifier: MIT

package io.github.muntashirakon.AppManager.apk.parser.encoder;

import android.content.Context;

import java.io.IOException;

// Copyright 2015 Roy
public class XmlChunk extends Chunk<XmlChunk.H> {
    public XmlChunk(Context context) {
        super(null);
        this.header = new H();
        this.context = context;
    }

    public static class H extends Header {

        public H() {
            super(ChunkType.Xml);
        }

        @Override
        public void writeEx(IntWriter w) throws IOException {

        }
    }

    public StringPoolChunk stringPool = new StringPoolChunk(this);
    public ResourceMapChunk resourceMap = new ResourceMapChunk(this);
    public TagChunk content;

    @Override
    public void preWrite() {
        header.size = header.headerSize + content.calc() + stringPool.calc() + resourceMap.calc();
    }

    @Override
    public void writeEx(IntWriter w) throws IOException {
        stringPool.write(w);
        resourceMap.write(w);
        content.write(w);
    }

    @Override
    public XmlChunk root() {
        return this;
    }
}
