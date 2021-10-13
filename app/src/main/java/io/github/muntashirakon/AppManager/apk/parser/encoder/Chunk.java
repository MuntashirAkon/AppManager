// SPDX-License-Identifier: MIT

package io.github.muntashirakon.AppManager.apk.parser.encoder;

import android.content.Context;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

// Copyright 2015 Roy
public abstract class Chunk<H extends Chunk.Header> {

    public abstract static class Header {
        public short type;
        public short headerSize;
        public int size;

        public Header(ChunkType ct) {
            type = ct.type;
            headerSize = ct.headerSize;
        }

        public void write(IntWriter w) throws IOException {
            w.write(type);
            w.write(headerSize);
            w.write(size);
            writeEx(w);
        }

        public abstract void writeEx(IntWriter w) throws IOException;
    }

    public abstract static class NodeHeader extends Header {
        public int lineNo = 1;
        public int comment = -1;

        public NodeHeader(ChunkType ct) {
            super(ct);
            headerSize = 0x10;
        }

        @Override
        public void write(IntWriter w) throws IOException {
            w.write(type);
            w.write(headerSize);
            w.write(size);
            w.write(lineNo);
            w.write(comment);
            writeEx(w);
        }

        @Override
        public void writeEx(IntWriter w) throws IOException {
        }
    }

    public static class EmptyHeader extends Header {
        public EmptyHeader() {
            super(ChunkType.Null);
        }

        @Override
        public void writeEx(IntWriter w) throws IOException {
        }

        @Override
        public void write(IntWriter w) throws IOException {
        }
    }


    public Chunk(@Nullable Chunk<? extends Header> parent) {
        this.parent = parent;
        try {
            Class<H> t = (Class<H>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
            Constructor[] cs = t.getConstructors();
            for (Constructor c : cs) {
                Type[] ts = c.getParameterTypes();
                if (ts.length == 1 && Chunk.class.isAssignableFrom((Class<?>) ts[0]))
                    header = (H) c.newInstance(this);
            }
            if (header == null) header = (H) new EmptyHeader();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected Context context;
    private final Chunk<? extends Header> parent;
    public H header;

    public void write(IntWriter w) throws IOException {
        int pos = w.getPos();
        calc();
        header.write(w);
        writeEx(w);
        assert w.getPos() - pos == header.size : (w.getPos() - pos) + " instead of " + header.size + " bytes were written:" + getClass().getName();
    }

    public Chunk<? extends Header> getParent() {
        return parent;
    }

    public Context getContext() {
        if (context != null) return context;
        return getParent().getContext();
    }

    private boolean isCalculated = false;

    public int calc() {
        if (!isCalculated) {
            preWrite();
            isCalculated = true;
        }
        return header.size;
    }

    private XmlChunk root;

    public XmlChunk root() {
        if (root != null) return root;
        return getParent().root();
    }

    public int stringIndex(String namespace, String s) {
        return stringPool().stringIndex(namespace, s);
    }

    private final StringPoolChunk stringPool = null;

    public StringPoolChunk stringPool() {
        return root().stringPool;
    }

    public void preWrite() {
    }

    public abstract void writeEx(IntWriter w) throws IOException;

}
