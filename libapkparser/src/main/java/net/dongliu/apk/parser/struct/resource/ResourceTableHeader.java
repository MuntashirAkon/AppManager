// SPDX-License-Identifier: BSD-2-Clause

package net.dongliu.apk.parser.struct.resource;

import net.dongliu.apk.parser.struct.ChunkHeader;
import net.dongliu.apk.parser.struct.ChunkType;
import net.dongliu.apk.parser.utils.Unsigned;

/**
 * resource file header
 */
// Copyright 2014 Liu Dong
public class ResourceTableHeader extends ChunkHeader {
    // The number of ResTable_package structures. uint32
    private int packageCount;

    public ResourceTableHeader(int headerSize, int chunkSize) {
        super(ChunkType.TABLE, headerSize, chunkSize);
    }

    public long getPackageCount() {
        return Unsigned.toLong(packageCount);
    }

    public void setPackageCount(long packageCount) {
        this.packageCount = Unsigned.toUInt(packageCount);
    }
}
