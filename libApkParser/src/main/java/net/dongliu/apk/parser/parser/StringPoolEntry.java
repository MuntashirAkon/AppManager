package net.dongliu.apk.parser.parser;

/**
 * class for sort string pool indexes
 */
public class StringPoolEntry {
    private int idx;
    private long offset;

    public StringPoolEntry(int idx, long offset) {
        this.idx = idx;
        this.offset = offset;
    }

    public int getIdx() {
        return idx;
    }

    public void setIdx(int idx) {
        this.idx = idx;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

}
