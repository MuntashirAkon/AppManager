// SPDX-License-Identifier: MIT

package io.github.muntashirakon.AppManager.apk.parser.encoder;

import android.text.TextUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;

// Copyright 2015 Roy
public class StringPoolChunk extends Chunk<StringPoolChunk.H> {

    public StringPoolChunk(Chunk parent) {
        super(parent);
    }

    public class H extends Header {
        public int stringCount;
        public int styleCount;
        public int flags;
        public int stringPoolOffset;
        public int stylePoolOffset;

        public H() {
            super(ChunkType.StringPool);
        }

        @Override
        public void writeEx(IntWriter w) throws IOException {
            w.write(stringCount);
            w.write(styleCount);
            w.write(flags);
            w.write(stringPoolOffset);
            w.write(stylePoolOffset);
        }
    }

    public static class RawString {

        StringItem origin;
        int byteLength;
        char[] cdata;
        byte[] bdata;

        int length() {
            if (cdata != null) return cdata.length;
            return origin.string.length();
        }

        ;

        int padding() {
            if (cdata != null) {
                return (cdata.length * 2 + 4) & 2;
            } else {
                //return (4-((bdata.length+3)&3))&3;
                return 0;
            }
        }

        int size() {
            if (cdata != null) {
                return cdata.length * 2 + 4 + padding();
            } else {
                return bdata.length + 3 + padding();
            }
        }

        void write(IntWriter w) throws IOException {
            if (cdata != null) {
                int pos = w.getPos();
                w.write((short) length());
                for (char c : cdata) w.write(c);
                w.write((short) 0);
                if (padding() == 2) w.write((short) 0);
                assert size() == w.getPos() - pos : size() + "," + (w.getPos() - pos);
            } else {
                int pos = w.getPos();
                w.write((byte) length());
                w.write((byte) bdata.length);
                for (byte c : bdata) w.write(c);
                w.write((byte) 0);
                int p = padding();
                for (int i = 0; i < p; ++i) w.write((byte) 0);
                assert size() == w.getPos() - pos : size() + "," + (w.getPos() - pos);
            }
        }
    }

    public enum Encoding {UNICODE, UTF8}

    public int[] stringsOffset;
    public int[] stylesOffset;
    public ArrayList<RawString> rawStrings;

    public Encoding encoding = Encoding.UNICODE;

    @Override
    public void preWrite() {
        rawStrings = new ArrayList<RawString>();
        LinkedList<Integer> offsets = new LinkedList<Integer>();
        int off = 0;
        int i = 0;
        if (encoding == Encoding.UNICODE) {
            for (LinkedList<StringItem> ss : map.values()) {
                for (StringItem s : ss) {
                    RawString r = new RawString();
                    r.cdata = s.string.toCharArray();
                    r.origin = s;
                    rawStrings.add(r);
                }
            }
        } else {
            for (LinkedList<StringItem> ss : map.values()) {
                for (StringItem s : ss) {
                    RawString r = new RawString();
                    r.bdata = s.string.getBytes(Charset.forName("UTF-8"));
                    r.origin = s;
                    rawStrings.add(r);
                }
            }

        }
        Collections.sort(rawStrings, new Comparator<RawString>() {

            public int compare(RawString lhs, RawString rhs) {
                int l = lhs.origin.id;
                int r = rhs.origin.id;
                if (l == -1) l = Integer.MAX_VALUE;
                if (r == -1) r = Integer.MAX_VALUE;
                return l - r;
            }
        });
        for (RawString r : rawStrings) {
            offsets.add(off);
            off += r.size();
        }
        header.stringCount = rawStrings.size();
        header.styleCount = 0;
        header.size = off + header.headerSize + header.stringCount * 4 + header.styleCount * 4;
        header.stringPoolOffset = offsets.size() * 4 + header.headerSize;
        header.stylePoolOffset = 0;
        stringsOffset = new int[offsets.size()];
        if (encoding == Encoding.UTF8) header.flags |= 0x100;
        i = 0;
        for (int x : offsets) stringsOffset[i++] = x;
        stylesOffset = new int[0];
    }

    @Override
    public void writeEx(IntWriter w) throws IOException {
        for (int i : stringsOffset) w.write(i);
        for (int i : stylesOffset) w.write(i);
        for (RawString r : rawStrings) r.write(w);
        //TODO styles;
    }


    public class StringItem {
        public String namespace;
        public String string;
        public int id = -1;
        ;

        public StringItem(String s) {
            string = s;
            namespace = null;
        }

        public StringItem(String namespace, String s) {
            string = s;
            this.namespace = namespace;
            genId();
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
            genId();
        }

        public void genId() {
            if (namespace == null) return;
            String pkg = "http://schemas.android.com/apk/res-auto".equals(namespace) ? getContext().getPackageName() :
                    namespace.startsWith("http://schemas.android.com/apk/res/") ? namespace.substring("http://schemas.android.com/apk/res/".length()) : null;
            if (pkg == null) return;
            id = getContext().getResources().getIdentifier(string, "attr", pkg);
        }
    }

    private HashMap<String, LinkedList<StringItem>> map = new HashMap<String, LinkedList<StringItem>>();

    private String preHandleString(String s) {
        return s;
    }

    public void addString(String s) {
        s = preHandleString(s);
        LinkedList<StringItem> list = map.get(s);
        if (list == null) map.put(s, list = new LinkedList<StringItem>());
        if (!list.isEmpty()) return;
        StringItem item = new StringItem(s);
        list.add(item);
    }

    public void addString(String namespace, String s) {
        namespace = preHandleString(namespace);
        s = preHandleString(s);
        LinkedList<StringItem> list = map.get(s);
        if (list == null) map.put(s, list = new LinkedList<StringItem>());
        for (StringItem e : list)
            if (e.namespace == null || e.namespace.equals(namespace)) {
                e.setNamespace(namespace);
                return;
            }
        StringItem item = new StringItem(namespace, s);
        list.add(item);
    }

    @Override
    public int stringIndex(String namespace, String s) {
        namespace = preHandleString(namespace);
        s = preHandleString(s);
        // 放行空字符串，仅拦截null
        if (s == null) return -1;
        int l = rawStrings.size();
        for (int i = 0; i < l; ++i) {
            StringItem item = rawStrings.get(i).origin;
            if (s.equals(item.string) && (TextUtils.isEmpty(namespace) || namespace.equals(item.namespace))) return i;
        }
        // 拦截空串
        if (TextUtils.isEmpty(s)) {
            return -1;
        }
        throw new RuntimeException("String: '" + s + "' not found");
        //return -1;
    }
}
