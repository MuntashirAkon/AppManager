package net.dongliu.apk.parser.struct.zip;

/**
 * End of central directory record
 */
public class EOCD {
    public static final int SIGNATURE = 0x06054b50;
    //    private int signature;
    // Number of this disk
    private short diskNum;
    // Disk where central directory starts
    private short cdStartDisk;
    // Number of central directory records on this disk
    private short cdRecordNum;
    // Total number of central directory records
    private short totalCDRecordNum;
    // Size of central directory (bytes)
    private int cdSize;
    // Offset of start of central directory, relative to start of archive
    private int cdStart;
    // Comment length (n)
    private short commentLen;
//    private List<String> commentList;

    public short getDiskNum() {
        return diskNum;
    }

    public void setDiskNum(int diskNum) {
        this.diskNum = (short) diskNum;
    }

    public int getCdStartDisk() {
        return cdStartDisk & 0xffff;
    }

    public void setCdStartDisk(int cdStartDisk) {
        this.cdStartDisk = (short) cdStartDisk;
    }

    public int getCdRecordNum() {
        return cdRecordNum & 0xffff;
    }

    public void setCdRecordNum(int cdRecordNum) {
        this.cdRecordNum = (short) cdRecordNum;
    }

    public int getTotalCDRecordNum() {
        return totalCDRecordNum & 0xffff;
    }

    public void setTotalCDRecordNum(int totalCDRecordNum) {
        this.totalCDRecordNum = (short) totalCDRecordNum;
    }

    public long getCdSize() {
        return cdSize & 0xffffffffL;
    }

    public void setCdSize(long cdSize) {
        this.cdSize = (int) cdSize;
    }

    public long getCdStart() {
        return cdStart & 0xffffffffL;
    }

    public void setCdStart(long cdStart) {
        this.cdStart = (int) cdStart;
    }

    public int getCommentLen() {
        return commentLen & 0xffff;
    }

    public void setCommentLen(int commentLen) {
        this.commentLen = (short) commentLen;
    }

}
