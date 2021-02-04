/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.commons.compress.archivers.tar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class represents a sparse entry in a Tar archive.
 *
 * <p>
 * The C structure for a sparse entry is:
 * <pre>
 * struct posix_header {
 * struct sparse sp[21]; // TarConstants.SPARSELEN_GNU_SPARSE     - offset 0
 * char isextended;      // TarConstants.ISEXTENDEDLEN_GNU_SPARSE - offset 504
 * };
 * </pre>
 * Whereas, "struct sparse" is:
 * <pre>
 * struct sparse {
 * char offset[12];   // offset 0
 * char numbytes[12]; // offset 12
 * };
 * </pre>
 */

public class TarArchiveSparseEntry implements TarConstants {
    /** If an extension sparse header follows. */
    private final boolean isExtended;

    private final List<TarArchiveStructSparse> sparseHeaders;

    /**
     * Construct an entry from an archive's header bytes. File is set
     * to null.
     *
     * @param headerBuf The header bytes from a tar archive entry.
     * @throws IOException on unknown format
     */
    public TarArchiveSparseEntry(final byte[] headerBuf) throws IOException {
        int offset = 0;
        sparseHeaders = new ArrayList<>();
        for(int i = 0; i < SPARSE_HEADERS_IN_EXTENSION_HEADER;i++) {
            final TarArchiveStructSparse sparseHeader = TarUtils.parseSparse(headerBuf,
                    offset + i * (SPARSE_OFFSET_LEN + SPARSE_NUMBYTES_LEN));

            // some sparse headers are empty, we need to skip these sparse headers
            if(sparseHeader.getOffset() > 0 || sparseHeader.getNumbytes() > 0) {
                sparseHeaders.add(sparseHeader);
            }
        }

        offset += SPARSELEN_GNU_SPARSE;
        isExtended = TarUtils.parseBoolean(headerBuf, offset);
    }

    public boolean isExtended() {
        return isExtended;
    }

    /**
     * Obtains information about the configuration for the sparse entry.
     * @since 1.20
     * @return information about the configuration for the sparse entry.
     */
    public List<TarArchiveStructSparse> getSparseHeaders() {
        return sparseHeaders;
    }
}