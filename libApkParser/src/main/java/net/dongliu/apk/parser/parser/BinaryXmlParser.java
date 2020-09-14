package net.dongliu.apk.parser.parser;

import net.dongliu.apk.parser.exception.ParserException;
import net.dongliu.apk.parser.struct.*;
import net.dongliu.apk.parser.struct.resource.ResourceTable;
import net.dongliu.apk.parser.struct.xml.*;
import net.dongliu.apk.parser.utils.Buffers;
import net.dongliu.apk.parser.utils.Locales;
import net.dongliu.apk.parser.utils.ParseUtils;
import net.dongliu.apk.parser.utils.Strings;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Android Binary XML format
 * see http://justanapplication.wordpress.com/category/android/android-binary-xml/
 *
 * @author dongliu
 */
public class BinaryXmlParser {

    /**
     * By default the data buffer Chunks is buffer little-endian byte order both at runtime and when stored buffer
     * files.
     */
    private ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
    private StringPool stringPool;
    // some attribute name stored by resource id
    private String[] resourceMap;
    private ByteBuffer buffer;
    private XmlStreamer xmlStreamer;
    private final ResourceTable resourceTable;
    /**
     * default locale.
     */
    private Locale locale = Locales.any;

    public BinaryXmlParser(ByteBuffer buffer, ResourceTable resourceTable) {
        this.buffer = buffer.duplicate();
        this.buffer.order(byteOrder);
        this.resourceTable = resourceTable;
    }

    /**
     * Parse binary xml.
     */
    public void parse() {
        ChunkHeader firstChunkHeader = readChunkHeader();
        if (firstChunkHeader == null) {
            return;
        }

        switch (firstChunkHeader.getChunkType()) {
            case ChunkType.XML:
            case ChunkType.NULL:
                break;
            case ChunkType.STRING_POOL:
            default:
                // strange chunk header type, just skip this chunk header?
        }

        // read string pool chunk
        ChunkHeader stringPoolChunkHeader = readChunkHeader();
        if (stringPoolChunkHeader == null) {
            return;
        }

        ParseUtils.checkChunkType(ChunkType.STRING_POOL, stringPoolChunkHeader.getChunkType());
        stringPool = ParseUtils.readStringPool(buffer, (StringPoolHeader) stringPoolChunkHeader);

        // read on chunk, check if it was an optional XMLResourceMap chunk
        ChunkHeader chunkHeader = readChunkHeader();
        if (chunkHeader == null) {
            return;
        }

        if (chunkHeader.getChunkType() == ChunkType.XML_RESOURCE_MAP) {
            long[] resourceIds = readXmlResourceMap((XmlResourceMapHeader) chunkHeader);
            resourceMap = new String[resourceIds.length];
            for (int i = 0; i < resourceIds.length; i++) {
                resourceMap[i] = Attribute.AttrIds.getString(resourceIds[i]);
            }
            chunkHeader = readChunkHeader();
        }

        while (chunkHeader != null) {
                /*if (chunkHeader.chunkType == ChunkType.XML_END_NAMESPACE) {
                    break;
                }*/
            long beginPos = buffer.position();
            switch (chunkHeader.getChunkType()) {
                case ChunkType.XML_END_NAMESPACE:
                    XmlNamespaceEndTag xmlNamespaceEndTag = readXmlNamespaceEndTag();
                    xmlStreamer.onNamespaceEnd(xmlNamespaceEndTag);
                    break;
                case ChunkType.XML_START_NAMESPACE:
                    XmlNamespaceStartTag namespaceStartTag = readXmlNamespaceStartTag();
                    xmlStreamer.onNamespaceStart(namespaceStartTag);
                    break;
                case ChunkType.XML_START_ELEMENT:
                    XmlNodeStartTag xmlNodeStartTag = readXmlNodeStartTag();
                    break;
                case ChunkType.XML_END_ELEMENT:
                    XmlNodeEndTag xmlNodeEndTag = readXmlNodeEndTag();
                    break;
                case ChunkType.XML_CDATA:
                    XmlCData xmlCData = readXmlCData();
                    break;
                default:
                    if (chunkHeader.getChunkType() >= ChunkType.XML_FIRST_CHUNK &&
                            chunkHeader.getChunkType() <= ChunkType.XML_LAST_CHUNK) {
                        Buffers.skip(buffer, chunkHeader.getBodySize());
                    } else {
                        throw new ParserException("Unexpected chunk type:" + chunkHeader.getChunkType());
                    }
            }
            Buffers.position(buffer, beginPos + chunkHeader.getBodySize());
            chunkHeader = readChunkHeader();
        }
    }

    private XmlCData readXmlCData() {
        XmlCData xmlCData = new XmlCData();
        int dataRef = buffer.getInt();
        if (dataRef > 0) {
            xmlCData.setData(stringPool.get(dataRef));
        }
        xmlCData.setTypedData(ParseUtils.readResValue(buffer, stringPool));
        if (xmlStreamer != null) {
            //TODO: to know more about cdata. some cdata appears buffer xml tags
//            String value = xmlCData.toStringValue(resourceTable, locale);
//            xmlCData.setValue(value);
//            xmlStreamer.onCData(xmlCData);
        }
        return xmlCData;
    }

    private XmlNodeEndTag readXmlNodeEndTag() {
        XmlNodeEndTag xmlNodeEndTag = new XmlNodeEndTag();
        int nsRef = buffer.getInt();
        int nameRef = buffer.getInt();
        if (nsRef > 0) {
            xmlNodeEndTag.setNamespace(stringPool.get(nsRef));
        }
        xmlNodeEndTag.setName(stringPool.get(nameRef));
        if (xmlStreamer != null) {
            xmlStreamer.onEndTag(xmlNodeEndTag);
        }
        return xmlNodeEndTag;
    }

    private XmlNodeStartTag readXmlNodeStartTag() {
        int nsRef = buffer.getInt();
        int nameRef = buffer.getInt();
        XmlNodeStartTag xmlNodeStartTag = new XmlNodeStartTag();
        if (nsRef > 0) {
            xmlNodeStartTag.setNamespace(stringPool.get(nsRef));
        }
        xmlNodeStartTag.setName(stringPool.get(nameRef));

        // read attributes.
        // attributeStart and attributeSize are always 20 (0x14)
        int attributeStart = Buffers.readUShort(buffer);
        int attributeSize = Buffers.readUShort(buffer);
        int attributeCount = Buffers.readUShort(buffer);
        int idIndex = Buffers.readUShort(buffer);
        int classIndex = Buffers.readUShort(buffer);
        int styleIndex = Buffers.readUShort(buffer);

        // read attributes
        Attributes attributes = new Attributes(attributeCount);
        for (int count = 0; count < attributeCount; count++) {
            Attribute attribute = readAttribute();
            if (xmlStreamer != null) {
                String value = attribute.toStringValue(resourceTable, locale);
                if (intAttributes.contains(attribute.getName()) && Strings.isNumeric(value)) {
                    try {
                        value = getFinalValueAsString(attribute.getName(), value);
                    } catch (Exception ignore) {
                    }
                }
                attribute.setValue(value);
                attributes.set(count, attribute);
            }
        }
        xmlNodeStartTag.setAttributes(attributes);

        if (xmlStreamer != null) {
            xmlStreamer.onStartTag(xmlNodeStartTag);
        }

        return xmlNodeStartTag;
    }

    private static final Set<String> intAttributes = new HashSet<>(
            Arrays.asList("screenOrientation", "configChanges", "windowSoftInputMode",
                    "launchMode", "installLocation", "protectionLevel"));

    //trans int attr value to string
    private String getFinalValueAsString(String attributeName, String str) {
        int value = Integer.parseInt(str);
        switch (attributeName) {
            case "screenOrientation":
                return AttributeValues.getScreenOrientation(value);
            case "configChanges":
                return AttributeValues.getConfigChanges(value);
            case "windowSoftInputMode":
                return AttributeValues.getWindowSoftInputMode(value);
            case "launchMode":
                return AttributeValues.getLaunchMode(value);
            case "installLocation":
                return AttributeValues.getInstallLocation(value);
            case "protectionLevel":
                return AttributeValues.getProtectionLevel(value);
            default:
                return str;
        }
    }

    private Attribute readAttribute() {
        int nsRef = buffer.getInt();
        int nameRef = buffer.getInt();
        Attribute attribute = new Attribute();
        if (nsRef > 0) {
            attribute.setNamespace(stringPool.get(nsRef));
        }

        attribute.setName(stringPool.get(nameRef));
        if (attribute.getName().isEmpty() && resourceMap != null && nameRef < resourceMap.length) {
            // some processed apk file make the string pool value empty, if it is a xmlmap attr.
            attribute.setName(resourceMap[nameRef]);
            //TODO: how to get the namespace of attribute
        }

        int rawValueRef = buffer.getInt();
        if (rawValueRef > 0) {
            attribute.setRawValue(stringPool.get(rawValueRef));
        }
        ResourceValue resValue = ParseUtils.readResValue(buffer, stringPool);
        attribute.setTypedValue(resValue);

        return attribute;
    }

    private XmlNamespaceStartTag readXmlNamespaceStartTag() {
        int prefixRef = buffer.getInt();
        int uriRef = buffer.getInt();
        XmlNamespaceStartTag nameSpace = new XmlNamespaceStartTag();
        if (prefixRef > 0) {
            nameSpace.setPrefix(stringPool.get(prefixRef));
        }
        if (uriRef > 0) {
            nameSpace.setUri(stringPool.get(uriRef));
        }
        return nameSpace;
    }

    private XmlNamespaceEndTag readXmlNamespaceEndTag() {
        int prefixRef = buffer.getInt();
        int uriRef = buffer.getInt();
        XmlNamespaceEndTag nameSpace = new XmlNamespaceEndTag();
        if (prefixRef > 0) {
            nameSpace.setPrefix(stringPool.get(prefixRef));
        }
        if (uriRef > 0) {
            nameSpace.setUri(stringPool.get(uriRef));
        }
        return nameSpace;
    }

    private long[] readXmlResourceMap(XmlResourceMapHeader chunkHeader) {
        int count = chunkHeader.getBodySize() / 4;
        long[] resourceIds = new long[count];
        for (int i = 0; i < count; i++) {
            resourceIds[i] = Buffers.readUInt(buffer);
        }
        return resourceIds;
    }


    private ChunkHeader readChunkHeader() {
        // finished
        if (!buffer.hasRemaining()) {
            return null;
        }

        long begin = buffer.position();
        int chunkType = Buffers.readUShort(buffer);
        int headerSize = Buffers.readUShort(buffer);
        long chunkSize = Buffers.readUInt(buffer);

        switch (chunkType) {
            case ChunkType.XML:
                return new XmlHeader(chunkType, headerSize, chunkSize);
            case ChunkType.STRING_POOL:
                StringPoolHeader stringPoolHeader = new StringPoolHeader(headerSize, chunkSize);
                stringPoolHeader.setStringCount(Buffers.readUInt(buffer));
                stringPoolHeader.setStyleCount(Buffers.readUInt(buffer));
                stringPoolHeader.setFlags(Buffers.readUInt(buffer));
                stringPoolHeader.setStringsStart(Buffers.readUInt(buffer));
                stringPoolHeader.setStylesStart(Buffers.readUInt(buffer));
                Buffers.position(buffer, begin + headerSize);
                return stringPoolHeader;
            case ChunkType.XML_RESOURCE_MAP:
                Buffers.position(buffer, begin + headerSize);
                return new XmlResourceMapHeader(chunkType, headerSize, chunkSize);
            case ChunkType.XML_START_NAMESPACE:
            case ChunkType.XML_END_NAMESPACE:
            case ChunkType.XML_START_ELEMENT:
            case ChunkType.XML_END_ELEMENT:
            case ChunkType.XML_CDATA:
                XmlNodeHeader header = new XmlNodeHeader(chunkType, headerSize, chunkSize);
                header.setLineNum((int) Buffers.readUInt(buffer));
                header.setCommentRef((int) Buffers.readUInt(buffer));
                Buffers.position(buffer, begin + headerSize);
                return header;
            case ChunkType.NULL:
                return new NullHeader(chunkType, headerSize, chunkSize);
            default:
                throw new ParserException("Unexpected chunk type:" + chunkType);
        }
    }

    public void setLocale(Locale locale) {
        if (locale != null) {
            this.locale = locale;
        }
    }

    public Locale getLocale() {
        return locale;
    }

    public XmlStreamer getXmlStreamer() {
        return xmlStreamer;
    }

    public void setXmlStreamer(XmlStreamer xmlStreamer) {
        this.xmlStreamer = xmlStreamer;
    }
}
