// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import android.util.Log;
import android.util.TypedValue;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * XML pull style parser of Android binary XML resources, such as {@code AndroidManifest.xml}.
 *
 * <p>For an input document, the parser outputs an event stream (see {@code EVENT_... constants} via
 * {@link #getEventType()} and {@link #next()} methods. Additional information about the current
 * event can be obtained via an assortment of getters, for example, {@link #getName()} or
 * {@link #getAttributeNameResourceId(int)}.
 */
// Copyright 2016 The Android Open Source Project
public class AndroidBinXmlParser {
    public static final String TAG = AndroidBinXmlParser.class.getSimpleName();

    @IntDef({
            EVENT_START_DOCUMENT,
            EVENT_END_DOCUMENT,
            EVENT_START_ELEMENT,
            EVENT_END_ELEMENT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Event {
    }

    /**
     * Event: start of document.
     */
    public static final int EVENT_START_DOCUMENT = XmlPullParser.START_DOCUMENT;

    /**
     * Event: end of document.
     */
    public static final int EVENT_END_DOCUMENT = XmlPullParser.END_DOCUMENT;
    /**
     * Event: start of an element.
     */
    public static final int EVENT_START_ELEMENT = XmlPullParser.START_TAG;
    /**
     * Event: end of an element.
     */
    public static final int EVENT_END_ELEMENT = XmlPullParser.END_TAG;

    private static final long NO_NAMESPACE = 0xffffffffL;

    private final ByteBuffer mXml;
    private final NamespaceStack mNamespaces = new NamespaceStack();
    private StringPool mStringPool;
    private ResourceMap mResourceMap;
    private int mDepth;
    @Event
    private int mCurrentEvent = EVENT_START_DOCUMENT;
    private String mCurrentElementName;
    private String mCurrentElementNamespace;
    private long mCurrentNsId;
    private int mCurrentElementAttributeCount;
    private List<Attribute> mCurrentElementAttributes;
    private ByteBuffer mCurrentElementAttributesContents;
    private int mCurrentElementAttrSizeBytes;

    /**
     * Constructs a new parser for the provided document.
     */
    public AndroidBinXmlParser(@NonNull ByteBuffer xml) throws XmlParserException {
        xml.order(ByteOrder.LITTLE_ENDIAN);
        Chunk resXmlChunk = null;
        while (xml.hasRemaining()) {
            Chunk chunk = Chunk.get(xml);
            if (chunk == null) {
                break;
            }
            if (chunk.getType() == Chunk.TYPE_RES_XML) {
                resXmlChunk = chunk;
                break;
            }
        }
        if (resXmlChunk == null) {
            throw new XmlParserException("No XML chunk in file");
        }
        mXml = resXmlChunk.getContents();
    }

    /**
     * Returns the depth of the current element. Outside of the root of the document the depth is
     * {@code 0}. The depth is incremented by {@code 1} before each {@code start element} event and
     * is decremented by {@code 1} after each {@code end element} event.
     */
    public int getDepth() {
        return mDepth;
    }

    /**
     * Returns the type of the current event. See {@code EVENT_...} constants.
     */
    @Event
    public int getEventType() {
        return mCurrentEvent;
    }

    /**
     * Returns the local name of the current element or {@code null} if the current event does not
     * pertain to an element.
     */
    public String getName() {
        if ((mCurrentEvent != EVENT_START_ELEMENT) && (mCurrentEvent != EVENT_END_ELEMENT)) {
            return null;
        }
        return mCurrentElementName;
    }

    public String getPrefix() throws XmlParserException {
        if (mCurrentNsId == NO_NAMESPACE) return "";
        long prefix = mNamespaces.findPrefix(mCurrentNsId);
        return mStringPool.getString(prefix);
    }

    /**
     * Returns the namespace of the current element or {@code null} if the current event does not
     * pertain to an element. Returns an empty string if the element is not associated with a
     * namespace.
     */
    public String getNamespace() {
        if ((mCurrentEvent != EVENT_START_ELEMENT) && (mCurrentEvent != EVENT_END_ELEMENT)) {
            return null;
        }
        return mCurrentElementNamespace;
    }

    public long getNamespaceCount(int depth) {
        return mNamespaces.getAccumulatedCount(depth);
    }

    public String getNamespacePrefix(long pos) throws XmlParserException {
        long prefix = mNamespaces.getPrefix(pos);
        return mStringPool.getString(prefix);
    }

    public String getNamespaceUri(long pos) throws XmlParserException {
        long uri = mNamespaces.getUri(pos);
        return mStringPool.getString(uri);
    }

    /**
     * Returns the number of attributes of the element associated with the current event or
     * {@code -1} if no element is associated with the current event.
     */
    public int getAttributeCount() {
        if (mCurrentEvent != EVENT_START_ELEMENT) {
            return -1;
        }
        return mCurrentElementAttributeCount;
    }

    /**
     * Returns the resource ID corresponding to the name of the specified attribute of the current
     * element or {@code 0} if the name is not associated with a resource ID.
     *
     * @throws IndexOutOfBoundsException if the index is out of range or the current event is not a
     *                                   {@code start element} event
     * @throws XmlParserException        if a parsing error is occurred
     */
    public int getAttributeNameResourceId(int index) throws XmlParserException {
        return getAttribute(index).getNameResourceId();
    }

    /**
     * Returns the name of the specified attribute of the current element.
     *
     * @throws IndexOutOfBoundsException if the index is out of range or the current event is not a
     *                                   {@code start element} event
     * @throws XmlParserException        if a parsing error is occurred
     */
    public String getAttributeName(int index) throws XmlParserException {
        return getAttribute(index).getName();
    }

    public String getAttributePrefix(int index) throws XmlParserException {
        long prefix = mNamespaces.findPrefix(getAttribute(index).mNsId);
        if (prefix == -1) {
            return "";
        }
        return mStringPool.getString(prefix);
    }

    /**
     * Returns the name of the specified attribute of the current element or an empty string if
     * the attribute is not associated with a namespace.
     *
     * @throws IndexOutOfBoundsException if the index is out of range or the current event is not a
     *                                   {@code start element} event
     * @throws XmlParserException        if a parsing error is occurred
     */
    public String getAttributeNamespace(int index) throws XmlParserException {
        return getAttribute(index).getNamespace();
    }

    /**
     * Returns the value type of the specified attribute of the current element. See
     * {@code VALUE_TYPE_...} constants.
     */
    @Attribute.Type
    public int getAttributeValueType(int index) {
        return getAttribute(index).getValueType();
    }

    /**
     * Returns the integer value of the specified attribute of the current element. See
     * {@code VALUE_TYPE_...} constants.
     *
     * @throws IndexOutOfBoundsException if the index is out of range or the current event is not a
     *                                   {@code start element} event.
     * @throws XmlParserException        if a parsing error is occurred
     */
    public int getAttributeIntValue(int index) throws XmlParserException {
        return getAttribute(index).getIntValue();
    }

    /**
     * Returns the boolean value of the specified attribute of the current element. See
     * {@code VALUE_TYPE_...} constants.
     *
     * @throws IndexOutOfBoundsException if the index is out of range or the current event is not a
     *                                   {@code start element} event.
     * @throws XmlParserException        if a parsing error is occurred
     */
    public boolean getAttributeBooleanValue(int index) throws XmlParserException {
        return getAttribute(index).getBooleanValue();
    }

    /**
     * Returns the float value of the specified attribute of the current element. See
     * {@code VALUE_TYPE_...} constants.
     *
     * @throws IndexOutOfBoundsException if the index is out of range or the current event is not a
     *                                   {@code start element} event.
     * @throws XmlParserException        if a parsing error is occurred
     */
    public float getAttributeFloatValue(int index) throws XmlParserException {
        return getAttribute(index).getFloatValue();
    }

    /**
     * Returns the string value of the specified attribute of the current element. See
     * {@code VALUE_TYPE_...} constants.
     *
     * @throws IndexOutOfBoundsException if the index is out of range or the current event is not a
     *                                   {@code start element} event.
     * @throws XmlParserException        if a parsing error is occurred
     */
    public String getAttributeStringValue(int index) throws XmlParserException {
        return getAttribute(index).getStringValue();
    }

    public Attribute getAttribute(int index) {
        if (mCurrentEvent != EVENT_START_ELEMENT) {
            throw new IndexOutOfBoundsException("Current event not a START_ELEMENT");
        }
        if (index < 0) {
            throw new IndexOutOfBoundsException("index must be >= 0");
        }
        if (index >= mCurrentElementAttributeCount) {
            throw new IndexOutOfBoundsException(
                    "index must be <= attr count (" + mCurrentElementAttributeCount + ")");
        }
        parseCurrentElementAttributesIfNotParsed();
        return mCurrentElementAttributes.get(index);
    }

    /**
     * Advances to the next parsing event and returns its type. See {@code EVENT_...} constants.
     */
    public int next() throws XmlParserException {
        // Decrement depth if the previous event was "end element".
        if (mCurrentEvent == EVENT_END_ELEMENT) {
            mDepth--;
            mNamespaces.decreaseDepth();
        }
        // Read events from document, ignoring events that we don't report to caller. Stop at the
        // earliest event which we report to caller.
        while (mXml.hasRemaining()) {
            Chunk chunk = Chunk.get(mXml);
            if (chunk == null) {
                break;
            }
            switch (chunk.getType()) {
                case Chunk.TYPE_STRING_POOL:
                    if (mStringPool != null) {
                        throw new XmlParserException("Multiple string pools not supported");
                    }
                    mStringPool = new StringPool(chunk);
                    break;
                case Chunk.RES_XML_TYPE_START_NAMESPACE: {
                    ByteBuffer contents = chunk.getContents();
                    long prefix = getUnsignedInt32(contents);
                    long uri = getUnsignedInt32(contents);
                    mNamespaces.push(prefix, uri);
                    break;
                }
                case Chunk.RES_XML_TYPE_END_NAMESPACE: {
                    mNamespaces.pop();
                    break;
                }
                case Chunk.RES_XML_TYPE_START_ELEMENT: {
                    if (mStringPool == null) {
                        throw new XmlParserException(
                                "Named element encountered before string pool");
                    }
                    ByteBuffer contents = chunk.getContents();
                    if (contents.remaining() < 20) {
                        throw new XmlParserException(
                                "Start element chunk too short. Need at least 20 bytes. Available: "
                                        + contents.remaining() + " bytes");
                    }
                    mCurrentNsId = getUnsignedInt32(contents);
                    long nameId = getUnsignedInt32(contents);
                    int attrStartOffset = getUnsignedInt16(contents);
                    int attrSizeBytes = getUnsignedInt16(contents);
                    int attrCount = getUnsignedInt16(contents);
                    long attrEndOffset = attrStartOffset + ((long) attrCount) * attrSizeBytes;
                    contents.position(0);
                    if (attrStartOffset > contents.remaining()) {
                        throw new XmlParserException(
                                "Attributes start offset out of bounds: " + attrStartOffset
                                        + ", max: " + contents.remaining());
                    }
                    if (attrEndOffset > contents.remaining()) {
                        throw new XmlParserException(
                                "Attributes end offset out of bounds: " + attrEndOffset
                                        + ", max: " + contents.remaining());
                    }
                    mCurrentElementName = mStringPool.getString(nameId);
                    mCurrentElementNamespace = (mCurrentNsId == NO_NAMESPACE) ? "" : mStringPool.getString(mCurrentNsId);
                    mCurrentElementAttributeCount = attrCount;
                    mCurrentElementAttributes = null;
                    mCurrentElementAttrSizeBytes = attrSizeBytes;
                    mCurrentElementAttributesContents =
                            sliceFromTo(contents, attrStartOffset, attrEndOffset);
                    mDepth++;
                    mNamespaces.increaseDepth();
                    mCurrentEvent = EVENT_START_ELEMENT;
                    return mCurrentEvent;
                }
                case Chunk.RES_XML_TYPE_END_ELEMENT: {
                    if (mStringPool == null) {
                        throw new XmlParserException(
                                "Named element encountered before string pool");
                    }
                    ByteBuffer contents = chunk.getContents();
                    if (contents.remaining() < 8) {
                        throw new XmlParserException(
                                "End element chunk too short. Need at least 8 bytes. Available: "
                                        + contents.remaining() + " bytes");
                    }
                    mCurrentNsId = getUnsignedInt32(contents);
                    long nameId = getUnsignedInt32(contents);
                    mCurrentElementName = mStringPool.getString(nameId);
                    mCurrentElementNamespace = (mCurrentNsId == NO_NAMESPACE) ? "" : mStringPool.getString(mCurrentNsId);
                    mCurrentEvent = EVENT_END_ELEMENT;
                    mCurrentElementAttributes = null;
                    mCurrentElementAttributesContents = null;
                    return mCurrentEvent;
                }
                case Chunk.RES_XML_TYPE_RESOURCE_MAP:
                    if (mResourceMap != null) {
                        throw new XmlParserException("Multiple resource maps not supported");
                    }
                    mResourceMap = new ResourceMap(chunk);
                    break;
                case Chunk.TYPE_RES_XML:
                default:
                    Log.w(TAG, String.format("Unknown chunk type = 0x%X", chunk.getType()));
                    // Unknown/impossible chunk type -- ignore
                    break;
            }
        }
        mCurrentEvent = EVENT_END_DOCUMENT;
        return mCurrentEvent;
    }

    private void parseCurrentElementAttributesIfNotParsed() {
        if (mCurrentElementAttributes != null) {
            return;
        }
        mCurrentElementAttributes = new ArrayList<>(mCurrentElementAttributeCount);
        for (int i = 0; i < mCurrentElementAttributeCount; i++) {
            int startPosition = i * mCurrentElementAttrSizeBytes;
            ByteBuffer attr =
                    sliceFromTo(
                            mCurrentElementAttributesContents,
                            startPosition,
                            startPosition + mCurrentElementAttrSizeBytes);
            long nsId = getUnsignedInt32(attr);
            long nameId = getUnsignedInt32(attr);
            attr.position(attr.position() + 7); // skip ignored fields
            int valueType = getUnsignedInt8(attr);
            long valueData = getUnsignedInt32(attr);
            mCurrentElementAttributes.add(
                    new Attribute(
                            nsId,
                            nameId,
                            valueType,
                            (int) valueData,
                            mStringPool,
                            mResourceMap));
        }
    }

    public static class Attribute {
        @IntDef({
                TYPE_NULL,
                TYPE_REFERENCE,
                TYPE_ATTRIBUTE,
                TYPE_STRING,
                TYPE_FLOAT,
                TYPE_DIMENSION,
                TYPE_FRACTION,
                TYPE_INT_DEC,
                TYPE_INT_HEX,
                TYPE_INT_BOOLEAN,
                TYPE_INT_COLOR_ARGB8,
                TYPE_INT_COLOR_RGB8,
                TYPE_INT_COLOR_ARGB4,
                TYPE_INT_COLOR_RGB4,
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface Type {
        }

        public static final int TYPE_NULL = TypedValue.TYPE_NULL;
        public static final int TYPE_REFERENCE = TypedValue.TYPE_REFERENCE;
        public static final int TYPE_ATTRIBUTE = TypedValue.TYPE_ATTRIBUTE;
        public static final int TYPE_STRING = TypedValue.TYPE_STRING;
        public static final int TYPE_FLOAT = TypedValue.TYPE_FLOAT;
        public static final int TYPE_DIMENSION = TypedValue.TYPE_DIMENSION;
        public static final int TYPE_FRACTION = TypedValue.TYPE_FRACTION;
        public static final int TYPE_INT_DEC = TypedValue.TYPE_INT_DEC;
        public static final int TYPE_INT_HEX = TypedValue.TYPE_INT_HEX;
        public static final int TYPE_INT_BOOLEAN = TypedValue.TYPE_INT_BOOLEAN;
        public static final int TYPE_INT_COLOR_ARGB8 = TypedValue.TYPE_INT_COLOR_ARGB8;
        public static final int TYPE_INT_COLOR_RGB8 = TypedValue.TYPE_INT_COLOR_RGB8;
        public static final int TYPE_INT_COLOR_ARGB4 = TypedValue.TYPE_INT_COLOR_ARGB4;
        public static final int TYPE_INT_COLOR_RGB4 = TypedValue.TYPE_INT_COLOR_RGB4;

        private static final int TYPE_FIRST_INT = TypedValue.TYPE_FIRST_INT;
        private static final int TYPE_LAST_INT = TypedValue.TYPE_LAST_INT;
        private static final int TYPE_FIRST_COLOR_INT = TypedValue.TYPE_FIRST_COLOR_INT;
        private static final int TYPE_LAST_COLOR_INT = TypedValue.TYPE_LAST_COLOR_INT;

        private static final int COMPLEX_UNIT_MASK = TypedValue.COMPLEX_UNIT_MASK;

        private final long mNsId;
        private final long mNameId;
        @Type
        private final int mValueType;
        private final int mValueData;
        private final StringPool mStringPool;
        private final ResourceMap mResourceMap;

        private Attribute(
                long nsId,
                long nameId,
                @Type int valueType,
                int valueData,
                StringPool stringPool,
                ResourceMap resourceMap) {
            mNsId = nsId;
            mNameId = nameId;
            mValueType = valueType;
            mValueData = valueData;
            mStringPool = stringPool;
            mResourceMap = resourceMap;
        }

        public int getNameResourceId() {
            return (mResourceMap != null) ? mResourceMap.getResourceId(mNameId) : 0;
        }

        public String getName() throws XmlParserException {
            return mStringPool.getString(mNameId);
        }

        public String getNamespace() throws XmlParserException {
            return (mNsId != NO_NAMESPACE) ? mStringPool.getString(mNsId) : "";
        }

        @Type
        public int getValueType() {
            return mValueType;
        }

        public int getIntValue() throws XmlParserException {
            if (mValueType == TYPE_REFERENCE || mValueType == TYPE_ATTRIBUTE ||
                    (mValueType >= TYPE_FIRST_INT && mValueType <= TYPE_LAST_INT)) {
                return mValueData;
            }
            throw new XmlParserException("Cannot coerce to int: value type " + mValueType);
        }

        public float getFloatValue() throws XmlParserException {
            if (mValueType == TYPE_FLOAT) {
                return Float.intBitsToFloat(mValueData);
            }
            throw new XmlParserException("Cannot coerce to float: value type " + mValueType);
        }

        public boolean getBooleanValue() throws XmlParserException {
            if (mValueType == TYPE_INT_BOOLEAN) {
                return mValueData != 0;
            }
            throw new XmlParserException("Cannot coerce to boolean: value type " + mValueType);
        }

        /**
         * @see TypedValue#coerceToString()
         * @see TypedValue#coerceToString(int, int)
         */
        public String getStringValue() throws XmlParserException {
            switch (mValueType) {
                case TYPE_NULL:
                    return null;
                case TYPE_REFERENCE:
                    return String.format("@%08X", mValueData);
                case TYPE_ATTRIBUTE:
                    return String.format("?%08X", mValueData);
                case TYPE_STRING:
                    return mStringPool.getString(mValueData & 0xffffffffL);
                case TYPE_FLOAT:
                    return Float.toString(Float.intBitsToFloat(mValueData));
                case TYPE_DIMENSION:
                    return complexToFloat(mValueData) + DIMENSION_UNITS[mValueData & COMPLEX_UNIT_MASK];
                case TYPE_FRACTION:
                    return complexToFloat(mValueData) + FRACTION_UNITS[mValueData & COMPLEX_UNIT_MASK];
                case TYPE_INT_HEX:
                    return String.format("0x%08X", mValueData);
                case TYPE_INT_BOOLEAN:
                    return mValueData != 0 ? "true" : "false";
                case TYPE_INT_COLOR_ARGB4:
                case TYPE_INT_COLOR_ARGB8:
                case TYPE_INT_COLOR_RGB4:
                case TYPE_INT_COLOR_RGB8:
                case TYPE_INT_DEC:
                    // Fallthrough
            }
            if (mValueType >= TYPE_FIRST_COLOR_INT && mValueType <= TYPE_LAST_COLOR_INT) {
                return String.format("#%08X", mValueData);
            }
            if (mValueType >= TYPE_FIRST_INT && mValueType <= TYPE_LAST_INT) {
                return String.valueOf(mValueData);
            }
            throw new XmlParserException("Cannot coerce to string: value type " + mValueType);
        }

        private static float complexToFloat(int complex) {
            return (float) (complex & 0xFFFFFF00) * RADIX_MULTS[(complex >> 4) & 3];
        }

        private static final float[] RADIX_MULTS = {
                0.00390625F, 3.051758E-005F, 1.192093E-007F, 4.656613E-010F
        };
        private static final String[] DIMENSION_UNITS = {
                "px", "dip", "sp", "pt", "in", "mm", "", ""
        };
        private static final String[] FRACTION_UNITS = {
                "%", "%p", "", "", "", "", "", ""
        };
    }

    /**
     * Chunk of a document. Each chunk is tagged with a type and consists of a header followed by
     * contents.
     */
    private static class Chunk {
        @IntDef({
                TYPE_STRING_POOL,
                TYPE_RES_XML,
                RES_XML_TYPE_START_NAMESPACE,
                RES_XML_TYPE_END_NAMESPACE,
                RES_XML_TYPE_START_ELEMENT,
                RES_XML_TYPE_END_ELEMENT,
                RES_XML_TYPE_XML_TEXT,
                RES_XML_TYPE_RESOURCE_MAP,
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface Type {
        }

        public static final int TYPE_STRING_POOL = 1;
        public static final int TYPE_RES_XML = 3;
        public static final int RES_XML_TYPE_START_NAMESPACE = 0x0100;
        public static final int RES_XML_TYPE_END_NAMESPACE = 0x0101;
        public static final int RES_XML_TYPE_START_ELEMENT = 0x0102;
        public static final int RES_XML_TYPE_END_ELEMENT = 0x0103;
        public static final int RES_XML_TYPE_XML_TEXT = 0x0104; // TODO: 12/10/21 Add support for text
        public static final int RES_XML_TYPE_RESOURCE_MAP = 0x0180;

        static final int HEADER_MIN_SIZE_BYTES = 8;

        @Type
        private final int mType;
        private final ByteBuffer mHeader;
        private final ByteBuffer mContents;

        public Chunk(@Type int type, ByteBuffer header, ByteBuffer contents) {
            mType = type;
            mHeader = header;
            mContents = contents;
        }

        public ByteBuffer getContents() {
            ByteBuffer result = mContents.slice();
            result.order(mContents.order());
            return result;
        }

        public ByteBuffer getHeader() {
            ByteBuffer result = mHeader.slice();
            result.order(mHeader.order());
            return result;
        }

        @Type
        public int getType() {
            return mType;
        }

        /**
         * Consumes the chunk located at the current position of the input and returns the chunk
         * or {@code null} if there is no chunk left in the input.
         *
         * @throws XmlParserException if the chunk is malformed
         */
        @Nullable
        public static Chunk get(@NonNull ByteBuffer input) throws XmlParserException {
            if (input.remaining() < HEADER_MIN_SIZE_BYTES) {
                // Android ignores the last chunk if its header is too big to fit into the file
                input.position(input.limit());
                return null;
            }
            int originalPosition = input.position();
            int type = getUnsignedInt16(input);
            int headerSize = getUnsignedInt16(input);
            long chunkSize = getUnsignedInt32(input);
            long chunkRemaining = chunkSize - 8;
            if (chunkRemaining > input.remaining()) {
                // Android ignores the last chunk if it's too big to fit into the file
                input.position(input.limit());
                return null;
            }
            if (headerSize < HEADER_MIN_SIZE_BYTES) {
                throw new XmlParserException(
                        "Malformed chunk: header too short: " + headerSize + " bytes");
            } else if (headerSize > chunkSize) {
                throw new XmlParserException(
                        "Malformed chunk: header too long: " + headerSize + " bytes. Chunk size: "
                                + chunkSize + " bytes");
            }
            int contentStartPosition = originalPosition + headerSize;
            long chunkEndPosition = originalPosition + chunkSize;
            Chunk chunk = new Chunk(
                    type,
                    sliceFromTo(input, originalPosition, contentStartPosition),
                    sliceFromTo(input, contentStartPosition, chunkEndPosition));
            input.position((int) chunkEndPosition);
            return chunk;
        }
    }

    /**
     * String pool of a document. Strings are referenced by their {@code 0}-based index in the pool.
     */
    private static class StringPool {
        private static final int FLAG_UTF8 = 1 << 8;

        private final ByteBuffer mChunkContents;
        private final ByteBuffer mStringsSection;
        private final int mStringCount;
        private final boolean mUtf8Encoded;
        private final Map<Integer, String> mCachedStrings = new HashMap<>();

        /**
         * Constructs a new string pool from the provided chunk.
         *
         * @throws XmlParserException if a parsing error occurred
         */
        public StringPool(@NonNull Chunk chunk) throws XmlParserException {
            ByteBuffer header = chunk.getHeader();
            int headerSizeBytes = header.remaining();
            header.position(Chunk.HEADER_MIN_SIZE_BYTES);
            if (header.remaining() < 20) {
                throw new XmlParserException(
                        "XML chunk's header too short. Required at least 20 bytes. Available: "
                                + header.remaining() + " bytes");
            }
            long stringCount = getUnsignedInt32(header);
            if (stringCount > Integer.MAX_VALUE) {
                throw new XmlParserException("Too many strings: " + stringCount);
            }
            mStringCount = (int) stringCount;
            long styleCount = getUnsignedInt32(header);
            if (styleCount > Integer.MAX_VALUE) {
                throw new XmlParserException("Too many styles: " + styleCount);
            }
            long flags = getUnsignedInt32(header);
            long stringsStartOffset = getUnsignedInt32(header);
            long stylesStartOffset = getUnsignedInt32(header);
            ByteBuffer contents = chunk.getContents();
            if (mStringCount > 0) {
                int stringsSectionStartOffsetInContents =
                        (int) (stringsStartOffset - headerSizeBytes);
                int stringsSectionEndOffsetInContents;
                if (styleCount > 0) {
                    // Styles section follows the strings section
                    if (stylesStartOffset < stringsStartOffset) {
                        throw new XmlParserException(
                                "Styles offset (" + stylesStartOffset + ") < strings offset ("
                                        + stringsStartOffset + ")");
                    }
                    stringsSectionEndOffsetInContents = (int) (stylesStartOffset - headerSizeBytes);
                } else {
                    stringsSectionEndOffsetInContents = contents.remaining();
                }
                mStringsSection =
                        sliceFromTo(
                                contents,
                                stringsSectionStartOffsetInContents,
                                stringsSectionEndOffsetInContents);
            } else {
                mStringsSection = ByteBuffer.allocate(0);
            }
            mUtf8Encoded = (flags & FLAG_UTF8) != 0;
            mChunkContents = contents;
        }

        /**
         * Returns the string located at the specified {@code 0}-based index in this pool.
         *
         * @throws XmlParserException if the string does not exist or cannot be decoded
         */
        public String getString(long index) throws XmlParserException {
            if (index < 0) {
                throw new XmlParserException("Unsuported string index: " + index);
            } else if (index >= mStringCount) {
                throw new XmlParserException(
                        "Unsuported string index: " + index + ", max: " + (mStringCount - 1));
            }
            int idx = (int) index;
            String result = mCachedStrings.get(idx);
            if (result != null) {
                return result;
            }
            long offsetInStringsSection = getUnsignedInt32(mChunkContents, idx * 4);
            if (offsetInStringsSection >= mStringsSection.capacity()) {
                throw new XmlParserException(
                        "Offset of string idx " + idx + " out of bounds: " + offsetInStringsSection
                                + ", max: " + (mStringsSection.capacity() - 1));
            }
            mStringsSection.position((int) offsetInStringsSection);
            result =
                    (mUtf8Encoded)
                            ? getLengthPrefixedUtf8EncodedString(mStringsSection)
                            : getLengthPrefixedUtf16EncodedString(mStringsSection);
            mCachedStrings.put(idx, result);
            return result;
        }

        @NonNull
        private static String getLengthPrefixedUtf16EncodedString(ByteBuffer encoded)
                throws XmlParserException {
            // If the length (in uint16s) is 0x7fff or lower, it is stored as a single uint16.
            // Otherwise, it is stored as a big-endian uint32 with highest bit set. Thus, the range
            // of supported values is 0 to 0x7fffffff inclusive.
            int lengthChars = getUnsignedInt16(encoded);
            if ((lengthChars & 0x8000) != 0) {
                lengthChars = ((lengthChars & 0x7fff) << 16) | getUnsignedInt16(encoded);
            }
            if (lengthChars > Integer.MAX_VALUE / 2) {
                throw new XmlParserException("String too long: " + lengthChars + " uint16s");
            }
            int lengthBytes = lengthChars * 2;
            byte[] arr;
            int arrOffset;
            if (encoded.hasArray()) {
                arr = encoded.array();
                arrOffset = encoded.arrayOffset() + encoded.position();
                encoded.position(encoded.position() + lengthBytes);
            } else {
                arr = new byte[lengthBytes];
                arrOffset = 0;
                encoded.get(arr);
            }
            // Reproduce the behavior of Android runtime which requires that the UTF-16 encoded
            // array of bytes is NULL terminated.
            if ((arr[arrOffset + lengthBytes] != 0)
                    || (arr[arrOffset + lengthBytes + 1] != 0)) {
                throw new XmlParserException("UTF-16 encoded form of string not NULL terminated");
            }
            return new String(arr, arrOffset, lengthBytes, StandardCharsets.UTF_16LE);
        }

        @NonNull
        private static String getLengthPrefixedUtf8EncodedString(ByteBuffer encoded)
                throws XmlParserException {
            // If the length (in bytes) is 0x7f or lower, it is stored as a single uint8. Otherwise,
            // it is stored as a big-endian uint16 with highest bit set. Thus, the range of
            // supported values is 0 to 0x7fff inclusive.
            // Skip UTF-16 encoded length (in uint16s)
            int lengthBytes = getUnsignedInt8(encoded);
            if ((lengthBytes & 0x80) != 0) {
                lengthBytes = ((lengthBytes & 0x7f) << 8) | getUnsignedInt8(encoded);
            }
            // Read UTF-8 encoded length (in bytes)
            lengthBytes = getUnsignedInt8(encoded);
            if ((lengthBytes & 0x80) != 0) {
                lengthBytes = ((lengthBytes & 0x7f) << 8) | getUnsignedInt8(encoded);
            }
            byte[] arr;
            int arrOffset;
            if (encoded.hasArray()) {
                arr = encoded.array();
                arrOffset = encoded.arrayOffset() + encoded.position();
                encoded.position(encoded.position() + lengthBytes);
            } else {
                arr = new byte[lengthBytes];
                arrOffset = 0;
                encoded.get(arr);
            }
            // Reproduce the behavior of Android runtime which requires that the UTF-8 encoded array
            // of bytes is NULL terminated.
            if (arr[arrOffset + lengthBytes] != 0) {
                throw new XmlParserException("UTF-8 encoded form of string not NULL terminated");
            }
            return new String(arr, arrOffset, lengthBytes, StandardCharsets.UTF_8);
        }
    }

    /**
     * Namespace stack holds namespace prefix and corresponding URI.
     */
    private static final class NamespaceStack {
        private long[] mData;
        private int mDataLength;
        private int mDepth;

        public NamespaceStack() {
            mData = new long[32];
        }

        public long getAccumulatedCount(int depth) {
            if (mDataLength == 0 || depth < 0) {
                return 0;
            }
            if (depth > mDepth) {
                depth = mDepth;
            }
            long accumulatedCount = 0;
            long offset = 0;
            for (; depth != 0; --depth) {
                long count = mData[(int) offset];
                accumulatedCount += count;
                offset += (2 + count * 2);
            }
            return accumulatedCount;
        }

        public void push(long prefix, long uri) {
            if (mDepth == 0) {
                increaseDepth();
            }
            ensureDataCapacity(2);
            int offset = mDataLength - 1;
            long count = mData[offset];
            // Count stored twice to allow bottom-up traversal
            mData[(int) (offset - 1 - count * 2)] = count + 1;
            mData[offset] = prefix;
            mData[offset + 1] = uri;
            mData[offset + 2] = count + 1;
            mDataLength += 2;
        }

        public boolean pop() {
            if (mDataLength == 0) {
                return false;
            }
            int offset = mDataLength - 1;
            long count = mData[offset];
            if (count == 0) {
                return false;
            }
            count -= 1;
            offset -= 2;
            mData[offset] = count;
            offset -= (1 + count * 2);
            mData[offset] = count;
            mDataLength -= 2;
            return true;
        }

        public long getPrefix(long index) {
            return get(index, true);
        }

        public long getUri(long index) {
            return get(index, false);
        }

        public long findPrefix(long uri) {
            return find(uri, false);
        }

        public long findUri(long prefix) {
            return find(prefix, true);
        }

        public void increaseDepth() {
            ensureDataCapacity(2);
            int offset = mDataLength;
            mData[offset] = 0;
            mData[offset + 1] = 0;
            mDataLength += 2;
            mDepth += 1;
        }

        public void decreaseDepth() {
            if (mDataLength == 0) {
                return;
            }
            int offset = mDataLength - 1;
            long count = mData[offset];
            if ((offset - 1 - count * 2) == 0) {
                return;
            }
            mDataLength -= 2 + count * 2;
            mDepth -= 1;
        }

        private void ensureDataCapacity(int capacity) {
            int available = (mData.length - mDataLength);
            if (available > capacity) {
                return;
            }
            int newLength = (mData.length + available) * 2;
            long[] newData = new long[newLength];
            System.arraycopy(mData, 0, newData, 0, mDataLength);
            mData = newData;
        }

        private long find(long prefixOrUri, boolean prefix) {
            if (mDataLength == 0) {
                return -1;
            }
            int offset = mDataLength - 1;
            for (int i = mDepth; i != 0; --i) {
                long count = mData[offset];
                offset -= 2;
                for (; count != 0; --count) {
                    if (prefix) {
                        if (mData[offset] == prefixOrUri) {
                            return mData[offset + 1];
                        }
                    } else {
                        if (mData[offset + 1] == prefixOrUri) {
                            return mData[offset];
                        }
                    }
                    offset -= 2;
                }
            }
            return -1;
        }

        private long get(long index, boolean prefix) {
            if (mDataLength == 0 || index < 0) {
                return -1;
            }
            int offset = 0;
            for (int i = mDepth; i != 0; --i) {
                long count = mData[offset];
                if (index >= count) {
                    index -= count;
                    offset += (2 + count * 2);
                    continue;
                }
                offset += (1 + index * 2);
                if (!prefix) {
                    offset += 1;
                }
                return mData[offset];
            }
            return -1;
        }
    }

    /**
     * Resource map of a document. Resource IDs are referenced by their {@code 0}-based index in the
     * map.
     */
    private static class ResourceMap {
        private final ByteBuffer mChunkContents;
        private final int mEntryCount;

        /**
         * Constructs a new resource map from the provided chunk.
         */
        public ResourceMap(@NonNull Chunk chunk) {
            mChunkContents = chunk.getContents().slice();
            mChunkContents.order(chunk.getContents().order());
            // Each entry of the map is four bytes long, containing the int32 resource ID.
            mEntryCount = mChunkContents.remaining() / 4;
        }

        /**
         * Returns the resource ID located at the specified {@code 0}-based index in this pool or
         * {@code 0} if the index is out of range.
         */
        public int getResourceId(long index) {
            if ((index < 0) || (index >= mEntryCount)) {
                return 0;
            }
            int idx = (int) index;
            // Each entry of the map is four bytes long, containing the int32 resource ID.
            return mChunkContents.getInt(idx * 4);
        }
    }

    /**
     * Returns new byte buffer whose content is a shared subsequence of this buffer's content
     * between the specified start (inclusive) and end (exclusive) positions. As opposed to
     * {@link ByteBuffer#slice()}, the returned buffer's byte order is the same as the source
     * buffer's byte order.
     */
    @NonNull
    private static ByteBuffer sliceFromTo(ByteBuffer source, long start, long end) {
        if (start < 0) {
            throw new IllegalArgumentException("start: " + start);
        }
        if (end < start) {
            throw new IllegalArgumentException("end < start: " + end + " < " + start);
        }
        int capacity = source.capacity();
        if (end > source.capacity()) {
            throw new IllegalArgumentException("end > capacity: " + end + " > " + capacity);
        }
        return sliceFromTo(source, (int) start, (int) end);
    }

    /**
     * Returns new byte buffer whose content is a shared subsequence of this buffer's content
     * between the specified start (inclusive) and end (exclusive) positions. As opposed to
     * {@link ByteBuffer#slice()}, the returned buffer's byte order is the same as the source
     * buffer's byte order.
     */
    @NonNull
    private static ByteBuffer sliceFromTo(ByteBuffer source, int start, int end) {
        if (start < 0) {
            throw new IllegalArgumentException("start: " + start);
        }
        if (end < start) {
            throw new IllegalArgumentException("end < start: " + end + " < " + start);
        }
        int capacity = source.capacity();
        if (end > source.capacity()) {
            throw new IllegalArgumentException("end > capacity: " + end + " > " + capacity);
        }
        int originalLimit = source.limit();
        int originalPosition = source.position();
        try {
            source.position(0);
            source.limit(end);
            source.position(start);
            ByteBuffer result = source.slice();
            result.order(source.order());
            return result;
        } finally {
            source.position(0);
            source.limit(originalLimit);
            source.position(originalPosition);
        }
    }

    private static int getUnsignedInt8(@NonNull ByteBuffer buffer) {
        return buffer.get() & 0xff;
    }

    private static int getUnsignedInt16(@NonNull ByteBuffer buffer) {
        return buffer.getShort() & 0xffff;
    }

    private static long getUnsignedInt32(@NonNull ByteBuffer buffer) {
        return buffer.getInt() & 0xffffffffL;
    }

    private static long getUnsignedInt32(@NonNull ByteBuffer buffer, int position) {
        return buffer.getInt(position) & 0xffffffffL;
    }

    /**
     * Indicates that an error occurred while parsing a document.
     */
    public static class XmlParserException extends Exception {
        private static final long serialVersionUID = 1L;

        public XmlParserException(String message) {
            super(message);
        }

        public XmlParserException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}