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

import java.util.Objects;

/**
 * This class represents struct sparse in a Tar archive.
 * <p>
 * Whereas, "struct sparse" is:
 * <pre>
 * struct sparse {
 * char offset[12];   // offset 0
 * char numbytes[12]; // offset 12
 * };
 * </pre>
 * @since 1.20
 */
public final class TarArchiveStructSparse {
    private final long offset;
    private final long numbytes;

    public TarArchiveStructSparse(final long offset, final long numbytes) {
        this.offset = offset;
        this.numbytes = numbytes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TarArchiveStructSparse that = (TarArchiveStructSparse) o;
        return offset == that.offset &&
                numbytes == that.numbytes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset, numbytes);
    }

    @Override
    public String toString() {
        return "TarArchiveStructSparse{" +
                "offset=" + offset +
                ", numbytes=" + numbytes +
                '}';
    }

    public long getOffset() {
        return offset;
    }

    public long getNumbytes() {
        return numbytes;
    }
}