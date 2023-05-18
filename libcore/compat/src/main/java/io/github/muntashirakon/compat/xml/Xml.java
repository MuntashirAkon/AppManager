// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.compat.xml;

import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;

import androidx.annotation.NonNull;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

public class Xml {
    /**
     * Feature flag: when set, {@link #resolveSerializer(OutputStream)} will
     * emit binary XML by default.
     */
    public static final boolean ENABLE_BINARY_DEFAULT;

    static {
        boolean useAbx;
        try {
            useAbx = (boolean) Objects.requireNonNull(Class.forName("android.os.SystemProperties")
                    .getDeclaredMethod("getBoolean", String.class, boolean.class)
                    .invoke(null, "persist.sys.binary_xml", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S));
        } catch (Exception ignore) {
            useAbx = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        }
        ENABLE_BINARY_DEFAULT = useAbx;
    }

    public static boolean isBinaryXml(@NonNull InputStream in) throws IOException {
        final byte[] magic = new byte[4];
        if (in instanceof FileInputStream) {
            try {
                Os.pread(((FileInputStream) in).getFD(), magic, 0, magic.length, 0);
            } catch (ErrnoException e) {
                throw new IOException(e.getMessage(), e);
            }
        } else {
            if (!in.markSupported()) {
                in = new BufferedInputStream(in);
            }
            in.mark(8);
            in.read(magic);
            in.reset();
        }
        return Arrays.equals(magic, BinaryXmlSerializer.PROTOCOL_MAGIC_VERSION_0);
    }

    public static boolean isBinaryXml(@NonNull ByteBuffer buffer) {
        final byte[] magic = new byte[4];
        buffer.mark();
        buffer.get(magic);
        buffer.reset();
        return Arrays.equals(magic, BinaryXmlSerializer.PROTOCOL_MAGIC_VERSION_0);
    }

    /**
     * Creates a new {@link TypedXmlPullParser} which is optimized for use
     * inside the system, typically by supporting only a basic set of features.
     * <p>
     * In particular, the returned parser does not support namespaces, prefixes,
     * properties, or options.
     */
    public static @NonNull TypedXmlPullParser newFastPullParser() {
        return XmlUtils.makeTyped(android.util.Xml.newPullParser());
    }

    /**
     * Creates a new {@link XmlPullParser} that reads XML documents using a
     * custom binary wire protocol which benchmarking has shown to be 8.5x
     * faster than {@code Xml.newFastPullParser()} for a typical
     * {@code packages.xml}.
     */
    public static @NonNull TypedXmlPullParser newBinaryPullParser() {
        return new BinaryXmlPullParser();
    }

    /**
     * Creates a new {@link XmlPullParser} which is optimized for use inside the
     * system, typically by supporting only a basic set of features.
     * <p>
     * This returned instance may be configured to read using an efficient
     * binary format instead of a human-readable text format, depending on
     * device feature flags.
     * <p>
     * To ensure that both formats are detected and transparently handled
     * correctly, you must shift to using both {@link #resolveSerializer} and
     * {@code #resolvePullParser}.
     */
    public static @NonNull TypedXmlPullParser resolvePullParser(@NonNull InputStream in) throws IOException {
        if (!in.markSupported()) {
            in = new BufferedInputStream(in);
        }
        final TypedXmlPullParser xml;
        if (isBinaryXml(in)) {
            xml = newBinaryPullParser();
        } else {
            xml = newFastPullParser();
        }
        try {
            xml.setInput(in, StandardCharsets.UTF_8.name());
        } catch (XmlPullParserException e) {
            throw new IOException(e);
        }
        return xml;
    }

    /**
     * Creates a new {@link XmlSerializer} which is optimized for use inside the
     * system, typically by supporting only a basic set of features.
     * <p>
     * In particular, the returned parser does not support namespaces, prefixes,
     * properties, or options.
     */
    @SuppressWarnings("AndroidFrameworkEfficientXml")
    public static @NonNull TypedXmlSerializer newFastSerializer() {
        return XmlUtils.makeTyped(new FastXmlSerializer());
    }

    /**
     * Creates a new {@link XmlSerializer} that writes XML documents using a
     * custom binary wire protocol which benchmarking has shown to be 4.4x
     * faster and use 2.8x less disk space than {@code Xml.newFastSerializer()}
     * for a typical {@code packages.xml}.
     */
    public static @NonNull TypedXmlSerializer newBinarySerializer() {
        return new BinaryXmlSerializer();
    }

    /**
     * Creates a new {@link XmlSerializer} which is optimized for use inside the
     * system, typically by supporting only a basic set of features.
     * <p>
     * This returned instance may be configured to write using an efficient
     * binary format instead of a human-readable text format, depending on
     * device feature flags.
     * <p>
     * To ensure that both formats are detected and transparently handled
     * correctly, you must shift to using both {@code #resolveSerializer} and
     * {@link #resolvePullParser}.
     */
    public static @NonNull TypedXmlSerializer resolveSerializer(@NonNull OutputStream out)
            throws IOException {
        final TypedXmlSerializer xml;
        if (ENABLE_BINARY_DEFAULT) {
            xml = newBinarySerializer();
        } else {
            xml = newFastSerializer();
        }
        xml.setOutput(out, StandardCharsets.UTF_8.name());
        return xml;
    }
}
