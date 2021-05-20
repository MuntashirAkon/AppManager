// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.utils;

/**
 * This interface provides statistics on the current decompression stream.
 * The stream consumer can use that statistics to handle abnormal
 * compression ratios, i.e. to prevent zip bombs.
 *
 * @since 1.17
 */
// Copyright 2018 Stefan Bodewig
public interface InputStreamStatistics {
    /**
     * @return the amount of raw or compressed bytes read by the stream
     */
    long getCompressedCount();

    /**
     * @return the amount of decompressed bytes returned by the stream
     */
    long getUncompressedCount();
}