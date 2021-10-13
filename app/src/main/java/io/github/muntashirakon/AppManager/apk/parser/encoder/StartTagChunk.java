// SPDX-License-Identifier: MIT

package io.github.muntashirakon.AppManager.apk.parser.encoder;

import androidx.annotation.NonNull;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

// Copyright 2015 Roy
public class StartTagChunk extends Chunk<StartTagChunk.H> {
    public class H extends NodeHeader {

        public H() {
            super(ChunkType.XmlStartElement);
        }
    }

    public String name;
    public String prefix;
    public String namespace;
    public short attrStart = 20;
    public short attrSize = 20;
    public short idIndex = 0;
    public short styleIndex = 0;
    public short classIndex = 0;
    public LinkedList<AttrChunk> attrs = new LinkedList<>();
    public List<StartNameSpaceChunk> startNameSpace = new Stack<>();

    public StartTagChunk(@NonNull Chunk<EmptyHeader> parent, XmlPullParser p) throws XmlPullParserException {
        super(parent);
        name = p.getName();
        stringPool().addString(name);
        prefix = p.getPrefix();
        namespace = p.getNamespace();
        int ac = p.getAttributeCount();
        for (short i = 0; i < ac; ++i) {
            String prefix = p.getAttributePrefix(i);
            String namespace = p.getAttributeNamespace(i);
            String name = p.getAttributeName(i);
            String val = p.getAttributeValue(i);
            AttrChunk attr = new AttrChunk(this);
            attr.prefix = prefix;
            attr.namespace = namespace;
            attr.rawValue = val;
            attr.name = name;
            stringPool().addString(namespace, name);
            attrs.add(attr);
            if ("id".equals(name) && "http://schemas.android.com/apk/res/android".equals(namespace)) {
                idIndex = i;
            } else if (prefix == null && "style".equals(name)) {
                styleIndex = i;
            } else if (prefix == null && "class".equals(name)) {
                classIndex = i;
            }
        }
        int nsStart = p.getNamespaceCount(p.getDepth() - 1);
        int nsEnd = p.getNamespaceCount(p.getDepth());
        for (int i = nsStart; i < nsEnd; i++) {
            StartNameSpaceChunk snc = new StartNameSpaceChunk(parent);
            snc.prefix = p.getNamespacePrefix(i);
            stringPool().addString(null, snc.prefix);
            snc.uri = p.getNamespaceUri(i);
            stringPool().addString(null, snc.uri);
            startNameSpace.add(snc);
        }
    }

    @Override
    public void preWrite() {
        for (AttrChunk a : attrs) a.calc();
        header.size = 36 + 20 * attrs.size();
    }

    @Override
    public void writeEx(IntWriter w) throws IOException {
        w.write(stringIndex(null, namespace));
        w.write(stringIndex(null, name));
        w.write(attrStart);
        w.write(attrSize);
        w.write((short) attrs.size());
        w.write(idIndex);
        w.write(classIndex);
        w.write(styleIndex);
        for (AttrChunk a : attrs) {
            a.write(w);
        }
    }
}
