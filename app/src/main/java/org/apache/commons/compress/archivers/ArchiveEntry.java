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
package org.apache.commons.compress.archivers;

import java.util.Date;

/**
 * Represents an entry of an archive.
 */
public interface ArchiveEntry {

    /**
     * Gets the name of the entry in this archive. May refer to a file or directory or other item.
     *
     * <p>This method returns the raw name as it is stored inside of the archive.</p>
     *
     * @return The name of this entry in the archive.
     */
    String getName();

    /**
     * Gets the uncompressed size of this entry. May be -1 (SIZE_UNKNOWN) if the size is unknown
     *
     * @return the uncompressed size of this entry.
     */
    long getSize();

    /** Special value indicating that the size is unknown */
    long SIZE_UNKNOWN = -1;

    /**
     * Returns true if this entry refers to a directory.
     *
     * @return true if this entry refers to a directory.
     */
    boolean isDirectory();

    /**
     * Gets the last modified date of this entry.
     *
     * @return the last modified date of this entry.
     * @since 1.1
     */
    Date getLastModifiedDate();
}
