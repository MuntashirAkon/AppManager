// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import android.content.Context;

import androidx.annotation.NonNull;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.Objects;

import io.github.muntashirakon.AppManager.apk.parser.encoder.Chunk;
import io.github.muntashirakon.AppManager.apk.parser.encoder.IntWriter;
import io.github.muntashirakon.AppManager.apk.parser.encoder.TagChunk;
import io.github.muntashirakon.AppManager.apk.parser.encoder.XmlChunk;

public class AndroidBinXmlEncoder {
    @NonNull
    public static byte[] encodeFile(@NonNull Context context, @NonNull File filename)
            throws XmlPullParserException, IOException {
        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        XmlPullParser p = f.newPullParser();
        p.setInput(new FileInputStream(filename), "UTF-8");
        return encode(context, p);
    }

    @NonNull
    public static byte[] encodeString(@NonNull Context context, @NonNull String xml)
            throws XmlPullParserException, IOException {
        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        XmlPullParser p = f.newPullParser();
        p.setInput(new StringReader(xml));
        return encode(context, p);
    }

    @NonNull
    public static byte[] encode(@NonNull Context context, @NonNull XmlPullParser p)
            throws XmlPullParserException, IOException {
        XmlChunk chunk = new XmlChunk(context);
        TagChunk current = null;
        for (int type = p.getEventType(); type != XmlPullParser.END_DOCUMENT; type = p.next()) {
            switch (type) {
                case XmlPullParser.START_TAG:
                    current = new TagChunk(current == null ? chunk : current, p);
                    break;
                case XmlPullParser.END_TAG:
                    Chunk<? extends Chunk.Header> c = Objects.requireNonNull(current).getParent();
                    current = c instanceof TagChunk ? (TagChunk) c : null;
                    break;
                case XmlPullParser.TEXT:
                case XmlPullParser.START_DOCUMENT:
                default:
                    break;
            }
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        IntWriter intWriter = new IntWriter(os);
        chunk.write(intWriter);
        intWriter.close();
        return os.toByteArray();
    }
}
