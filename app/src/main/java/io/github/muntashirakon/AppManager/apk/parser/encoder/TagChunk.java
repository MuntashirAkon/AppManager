// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser.encoder;

import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

// Copyright 2015 Roy
public class TagChunk extends Chunk<Chunk.EmptyHeader> {
    public List<StartNameSpaceChunk> startNameSpace;
    public StartTagChunk startTag;
    public List<TagChunk> content = new LinkedList<>();
    public EndTagChunk endTag;
    public List<EndNameSpaceChunk> endNameSpace;

    public TagChunk(@Nullable Chunk<? extends Header> parent, XmlPullParser p) throws XmlPullParserException {
        super(parent);
        if (parent instanceof TagChunk) {
            ((TagChunk) parent).content.add(this);
        } else if (parent instanceof XmlChunk) {
            ((XmlChunk) parent).content = this;
        } else {
            throw new IllegalArgumentException("parent must be XmlChunk or TagChunk");
        }
        startTag = new StartTagChunk(this, p);
        endTag = new EndTagChunk(this, startTag);
        startNameSpace = startTag.startNameSpace;
        endNameSpace = new LinkedList<>();
        for (StartNameSpaceChunk c : startNameSpace) endNameSpace.add(new EndNameSpaceChunk(this, c));
        endTag.header.lineNo = startTag.header.lineNo = p.getLineNumber();
    }

    @Override
    public void preWrite() {
        int sum = 0;
        for (StartNameSpaceChunk e : startNameSpace) sum += e.calc();
        for (EndNameSpaceChunk e : endNameSpace) sum += e.calc();
        sum += startTag.calc();
        sum += endTag.calc();
        for (TagChunk c : content) sum += c.calc();
        header.size = sum;
    }

    @Override
    public void writeEx(IntWriter writer) throws IOException {
        for (StartNameSpaceChunk c : startNameSpace) c.write(writer);
        startTag.write(writer);
        for (TagChunk c : content) c.write(writer);
        endTag.write(writer);
        for (EndNameSpaceChunk c : endNameSpace) c.write(writer);
    }
}
