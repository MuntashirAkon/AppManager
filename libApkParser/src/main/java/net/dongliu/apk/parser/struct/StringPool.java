package net.dongliu.apk.parser.struct;

/**
 * String pool.
 *
 * @author dongliu
 */
public class StringPool {
    private String[] pool;

    public StringPool(int poolSize) {
        pool = new String[poolSize];
    }

    public String get(int idx) {
        return pool[idx];
    }

    public void set(int idx, String value) {
        pool[idx] = value;
    }
}
