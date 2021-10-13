// SPDX-License-Identifier: MIT AND GPL-3.0-or-later
package io.github.muntashirakon.AppManager.apk.parser.encoder;

import android.graphics.Color;
import android.util.TypedValue;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Copyright 2015 Roy
public class ValueChunk extends Chunk<Chunk.EmptyHeader> {

    static class ValPair {
        int pos;
        String val;

        public ValPair(Matcher m) {
            int c = m.groupCount();
            for (int i = 1; i <= c; ++i) {
                String s = m.group(i);
                if (s == null || s.isEmpty()) continue;
                pos = i;
                val = s;
                return;
            }
            pos = -1;
            val = m.group();
        }
    }

    private final AttrChunk attrChunk;
    private String realString;
    short size = 8;
    byte res0 = 0;
    byte type = -1;
    int data = -1;

    Pattern explicitType = Pattern.compile("!(?:(\\w+)!)?(.*)");
    Pattern types = Pattern.compile(("^(?:" +
            "(@null)" +  // NULL
            "|@([0-9a-zA-Z]+)" +  // RESOURCE
            "|\\?([0-9a-zA-Z]+)" +  // ATTRIBUTE
            "|(true|false)" +  // INT_BOOLEAN
            "|([-+]?\\d+)" +  // INT_DEC
            "|(0x[0-9a-zA-Z]+)" +  // INT_HEX
            "|([-+]?\\d+(?:\\.\\d+)?)" +  // FLOAT
            "|([-+]?\\d+(?:\\.\\d+)?(?:dp|dip|in|px|sp|pt|mm))" +  // DIMENSION
            "|([-+]?\\d+(?:\\.\\d+)?(?:%))" +  // FRACTION
            "|(\\#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8}))" +  // COLORS
            // String values
            "|(@\\+?(?:\\w+:)?\\w+/\\w+)" + // RESOURCE e.g. @android:id/list
            "|(\\?\\+?(?:\\w+:)?\\w+/\\w+)" + // ATTRIBUTE e.g. ?android:attr/colorAccent
            ")$").replaceAll("\\s+", ""));

    public ValueChunk(AttrChunk parent) {
        super(parent);
        header.size = 8;
        this.attrChunk = parent;
    }

    @Override
    public void preWrite() {
        evaluate();
    }

    @Override
    public void writeEx(IntWriter w) throws IOException {
        w.write(size);
        w.write(res0);
        if (type == TypedValue.TYPE_STRING) {
            data = stringIndex(null, realString);
        }
        w.write(type);
        w.write(data);
    }

    public int evalcomplex(String val) {
        int unit;
        int radix;
        int base;
        String num;

        if (val.endsWith("%")) {
            num = val.substring(0, val.length() - 1);
            unit = TypedValue.COMPLEX_UNIT_FRACTION;
        } else if (val.endsWith("dp")) {
            unit = TypedValue.COMPLEX_UNIT_DIP;
            num = val.substring(0, val.length() - 2);
        } else if (val.endsWith("dip")) {
            unit = TypedValue.COMPLEX_UNIT_DIP;
            num = val.substring(0, val.length() - 3);
        } else if (val.endsWith("sp")) {
            unit = TypedValue.COMPLEX_UNIT_SP;
            num = val.substring(0, val.length() - 2);
        } else if (val.endsWith("px")) {
            unit = TypedValue.COMPLEX_UNIT_PX;
            num = val.substring(0, val.length() - 2);
        } else if (val.endsWith("pt")) {
            unit = TypedValue.COMPLEX_UNIT_PT;
            num = val.substring(0, val.length() - 2);
        } else if (val.endsWith("in")) {
            unit = TypedValue.COMPLEX_UNIT_IN;
            num = val.substring(0, val.length() - 2);
        } else if (val.endsWith("mm")) {
            unit = TypedValue.COMPLEX_UNIT_MM;
            num = val.substring(0, val.length() - 2);
        } else {
            throw new RuntimeException("invalid unit");
        }
        double f = Double.parseDouble(num);
        if (f < 1 && f > -1) {
            base = (int) (f * (1 << 23));
            radix = TypedValue.COMPLEX_RADIX_0p23;
        } else if (f < 0x100 && f > -0x100) {
            base = (int) (f * (1 << 15));
            radix = TypedValue.COMPLEX_RADIX_8p15;
        } else if (f < 0x10000 && f > -0x10000) {
            base = (int) (f * (1 << 7));
            radix = TypedValue.COMPLEX_RADIX_16p7;
        } else {
            base = (int) f;
            radix = TypedValue.COMPLEX_RADIX_23p0;
        }
        return (base << 8) | (radix << 4) | unit;
    }

    public void evaluate() {
        Matcher m = explicitType.matcher(attrChunk.rawValue);
        if (m.find()) {
            String t = m.group(1);
            String v = m.group(2);
            if (t == null || t.isEmpty() || t.equals("string") || t.equals("str")) {
                type = TypedValue.TYPE_STRING;
                realString = v;
                stringPool().addString(realString);
                //data = stringIndex(null, v);
            } else {
                //TODO resolve other type
                throw new NotImplementedException();
            }
        } else {
            m = types.matcher(attrChunk.rawValue);
            if (m.find()) {
                ValPair vp = new ValPair(m);
                switch (vp.pos) {
                    case 1:
                        type = TypedValue.TYPE_NULL;
                        data = 0; // FIXME: 13/10/21 Should be 1?
                        break;
                    case 2:
                        type = TypedValue.TYPE_REFERENCE;
                        data = Integer.decode("0x" + vp.val);
                        break;
                    case 3:
                        type = TypedValue.TYPE_ATTRIBUTE;
                        data = Integer.decode("0x" + vp.val);
                        break;
                    case 4:
                        type = TypedValue.TYPE_INT_BOOLEAN;
                        data = "true".equalsIgnoreCase(vp.val) ? 1 : 0;
                        break;
                    case 5:
                        type = TypedValue.TYPE_INT_DEC;
                        data = Integer.parseInt(vp.val);
                        break;
                    case 6:
                        type = TypedValue.TYPE_INT_HEX;
                        data = Integer.parseInt(vp.val.substring(2), 16);
                        break;
                    case 7:
                        type = TypedValue.TYPE_FLOAT;
                        data = Float.floatToIntBits(Float.parseFloat(vp.val));
                        break;
                    case 8:
                        type = TypedValue.TYPE_DIMENSION;
                        data = evalcomplex(vp.val);
                        break;
                    case 9:
                        type = TypedValue.TYPE_FRACTION;
                        data = evalcomplex(vp.val);
                        break;
                    case 10:
                        type = TypedValue.TYPE_INT_COLOR_ARGB8;
                        data = Color.parseColor(vp.val);
                        break;
                    case 11:
                        type = TypedValue.TYPE_REFERENCE;
                        data = 0; // TODO: 13/10/21
                        throw new NotImplementedException();
                    case 12:
                        type = TypedValue.TYPE_ATTRIBUTE;
                        data = 0; // TODO: 13/10/21
                        throw new NotImplementedException();
                    default:
                        type = TypedValue.TYPE_STRING;
                        realString = vp.val;
                        stringPool().addString(realString);
                        //data = stringIndex(null, attrChunk.rawValue);
                        break;
                }
            } else {
                type = TypedValue.TYPE_STRING;
                realString = attrChunk.rawValue;
                stringPool().addString(realString);
                //data = stringIndex(null, attrChunk.rawValue);
            }
        }
    }
}
