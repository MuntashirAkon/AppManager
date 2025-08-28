// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.algo;

public class AhoCorasick implements AutoCloseable {
    static {
        System.loadLibrary("am");
    }

    private long nativeInstanceId;

    /**
     * Creates the native Aho-Corasick instance with the given patterns.
     */
    public AhoCorasick(String[] patterns) {
        nativeInstanceId = createNative(patterns);
        if (nativeInstanceId == 0) {
            throw new RuntimeException("Failed to create native AhoCorasick instance");
        }
    }

    private native long createNative(String[] patterns);

    private native int[] searchNative(long instanceId, String text);

    private native void destroyNative(long instanceId);

    /**
     * Search the text for matching patterns.
     */
    public int[] search(String text) {
        if (nativeInstanceId == 0) throw new IllegalStateException("Instance already closed");
        return searchNative(nativeInstanceId, text);
    }

    /**
     * Releases native resources automatically when try-with-resources ends.
     */
    @Override
    public void close() {
        if (nativeInstanceId != 0) {
            destroyNative(nativeInstanceId);
            nativeInstanceId = 0;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close(); // Backup safety to release native resources
        } finally {
            super.finalize();
        }
    }
}