// SPDX-License-Identifier: MIT

package io.github.muntashirakon.AppManager.apk.parser.encoder;

import android.text.TextUtils;
import android.util.TypedValue;

import java.io.IOException;

// Copyright 2015 Roy
public class AttrChunk extends Chunk<Chunk.EmptyHeader> {
    private final StartTagChunk startTagChunk;
    public String prefix;
    public String name;
    public String namespace;
    public String rawValue;

    public AttrChunk(StartTagChunk startTagChunk) {
        super(startTagChunk);
        this.startTagChunk = startTagChunk;
        header.size = 20;
    }


    public ValueChunk value = new ValueChunk(this);

    @Override
    public void preWrite() {
        value.calc();
    }

    @Override
    public void writeEx(IntWriter w) throws IOException {
        // If the namespace is an empty string, null is set to avoid setting empty string as the real value
        w.write(startTagChunk.stringIndex(null, TextUtils.isEmpty(namespace) ? null : namespace));
        w.write(startTagChunk.stringIndex(namespace, name));
        if (value.type == TypedValue.TYPE_STRING) {
            w.write(startTagChunk.stringIndex(null, rawValue));
        } else w.write(-1);
        value.write(w);
    }
}
