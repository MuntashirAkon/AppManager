package net.dongliu.apk.parser.struct.dex;

/**
 * dex file header.
 * see http://dexandroid.googlecode.com/svn/trunk/dalvik/libdex/DexFile.h
 *
 * @author dongliu
 */
public class DexHeader {

    public static final int kSHA1DigestLen = 20;
    public static final int kSHA1DigestOutputLen = kSHA1DigestLen * 2 + 1;

    // includes version number. 8 bytes.
    //public short magic;
    private int version;
    // adler32 checksum. u4
    //public long checksum;
    // SHA-1 hash len = kSHA1DigestLen
    private byte signature[];
    // length of entire file. u4
    private long fileSize;
    // len of header.offset to start of next section. u4
    private long headerSize;
    // u4
    //public long endianTag;
    // u4
    private long linkSize;
    // u4
    private long linkOff;
    // u4
    private long mapOff;
    // u4
    private int stringIdsSize;
    // u4
    private long stringIdsOff;
    // u4
    private int typeIdsSize;
    // u4
    private long typeIdsOff;
    // u4
    private int protoIdsSize;
    // u4
    private long protoIdsOff;
    // u4
    private int fieldIdsSize;
    // u4
    private long fieldIdsOff;
    // u4
    private int methodIdsSize;
    // u4
    private long methodIdsOff;
    // u4
    private int classDefsSize;
    // u4
    private long classDefsOff;
    // u4
    private int dataSize;
    // u4
    private long dataOff;


    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getHeaderSize() {
        return headerSize;
    }

    public void setHeaderSize(long headerSize) {
        this.headerSize = headerSize;
    }

    public long getLinkSize() {
        return linkSize;
    }

    public void setLinkSize(long linkSize) {
        this.linkSize = linkSize;
    }

    public long getLinkOff() {
        return linkOff;
    }

    public void setLinkOff(long linkOff) {
        this.linkOff = linkOff;
    }

    public long getMapOff() {
        return mapOff;
    }

    public void setMapOff(long mapOff) {
        this.mapOff = mapOff;
    }

    public int getStringIdsSize() {
        return stringIdsSize;
    }

    public void setStringIdsSize(int stringIdsSize) {
        this.stringIdsSize = stringIdsSize;
    }

    public long getStringIdsOff() {
        return stringIdsOff;
    }

    public void setStringIdsOff(long stringIdsOff) {
        this.stringIdsOff = stringIdsOff;
    }

    public int getTypeIdsSize() {
        return typeIdsSize;
    }

    public void setTypeIdsSize(int typeIdsSize) {
        this.typeIdsSize = typeIdsSize;
    }

    public long getTypeIdsOff() {
        return typeIdsOff;
    }

    public void setTypeIdsOff(long typeIdsOff) {
        this.typeIdsOff = typeIdsOff;
    }

    public int getProtoIdsSize() {
        return protoIdsSize;
    }

    public void setProtoIdsSize(int protoIdsSize) {
        this.protoIdsSize = protoIdsSize;
    }

    public long getProtoIdsOff() {
        return protoIdsOff;
    }

    public void setProtoIdsOff(long protoIdsOff) {
        this.protoIdsOff = protoIdsOff;
    }

    public int getFieldIdsSize() {
        return fieldIdsSize;
    }

    public void setFieldIdsSize(int fieldIdsSize) {
        this.fieldIdsSize = fieldIdsSize;
    }

    public long getFieldIdsOff() {
        return fieldIdsOff;
    }

    public void setFieldIdsOff(long fieldIdsOff) {
        this.fieldIdsOff = fieldIdsOff;
    }

    public int getMethodIdsSize() {
        return methodIdsSize;
    }

    public void setMethodIdsSize(int methodIdsSize) {
        this.methodIdsSize = methodIdsSize;
    }

    public long getMethodIdsOff() {
        return methodIdsOff;
    }

    public void setMethodIdsOff(long methodIdsOff) {
        this.methodIdsOff = methodIdsOff;
    }

    public int getClassDefsSize() {
        return classDefsSize;
    }

    public void setClassDefsSize(int classDefsSize) {
        this.classDefsSize = classDefsSize;
    }

    public long getClassDefsOff() {
        return classDefsOff;
    }

    public void setClassDefsOff(long classDefsOff) {
        this.classDefsOff = classDefsOff;
    }

    public int getDataSize() {
        return dataSize;
    }

    public void setDataSize(int dataSize) {
        this.dataSize = dataSize;
    }

    public long getDataOff() {
        return dataOff;
    }

    public void setDataOff(long dataOff) {
        this.dataOff = dataOff;
    }
}
