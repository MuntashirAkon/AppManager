// SPDX-License-Identifier: Apache-2.0

package android.util;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.HiddenUtil;

@RefineAs(Xml.class)
@RequiresApi(31)
public class XmlHidden {
    /**
     * Creates a new {@link TypedXmlPullParser} which is optimized for use
     * inside the system, typically by supporting only a basic set of features.
     * <p>
     * In particular, the returned parser does not support namespaces, prefixes,
     * properties, or options.
     */
    @NonNull
    public static TypedXmlPullParser newFastPullParser() {
        return HiddenUtil.throwUOE();
    }

    /**
     * Creates a new {@link XmlPullParser} that reads XML documents using a
     * custom binary wire protocol which benchmarking has shown to be 8.5x
     * faster than {@code Xml.newFastPullParser()} for a typical
     * {@code packages.xml}.
     */
    @NonNull
    public static TypedXmlPullParser newBinaryPullParser() {
        return HiddenUtil.throwUOE();
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
     * {@link #resolvePullParser}.
     */
    public static @NonNull
    TypedXmlPullParser resolvePullParser(@NonNull InputStream in) throws IOException {
        return HiddenUtil.throwUOE(in);
    }

    /**
     * Creates a new {@link XmlSerializer} which is optimized for use inside the
     * system, typically by supporting only a basic set of features.
     * <p>
     * In particular, the returned parser does not support namespaces, prefixes,
     * properties, or options.
     */
    @SuppressWarnings("AndroidFrameworkEfficientXml")
    public static @NonNull
    TypedXmlSerializer newFastSerializer() {
        return HiddenUtil.throwUOE();
    }

    /**
     * Creates a new {@link XmlSerializer} that writes XML documents using a
     * custom binary wire protocol which benchmarking has shown to be 4.4x
     * faster and use 2.8x less disk space than {@code Xml.newFastSerializer()}
     * for a typical {@code packages.xml}.
     */
    public static @NonNull
    TypedXmlSerializer newBinarySerializer() {
        return HiddenUtil.throwUOE();
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
     * correctly, you must shift to using both {@link #resolveSerializer} and
     * {@link #resolvePullParser}.
     */
    public static @NonNull
    TypedXmlSerializer resolveSerializer(@NonNull OutputStream out)
            throws IOException {
        return HiddenUtil.throwUOE(out);
    }

    /**
     * Copy the first XML document into the second document.
     * <p>
     * Implemented by reading all events from the given {@link XmlPullParser}
     * and writing them directly to the given {@link XmlSerializer}. This can be
     * useful for transparently converting between underlying wire protocols.
     */
    public static void copy(@NonNull XmlPullParser in, @NonNull XmlSerializer out)
            throws XmlPullParserException, IOException {
        HiddenUtil.throwUOE(in, out);
    }
}
