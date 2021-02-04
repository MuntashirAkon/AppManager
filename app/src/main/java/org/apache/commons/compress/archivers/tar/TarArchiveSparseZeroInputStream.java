/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.commons.compress.archivers.tar;

import java.io.IOException;
import java.io.InputStream;

/**
 * This is an inputstream that always return 0,
 * this is used when reading the "holes" of a sparse file
 */
class TarArchiveSparseZeroInputStream extends InputStream {

    /**
     * Just return 0
     */
    @Override
    public int read() throws IOException {
        return 0;
    }

    /**
     * these's nothing need to do when skipping
     *
     * @param n bytes to skip
     * @return bytes actually skipped
     */
    @Override
    public long skip(final long n) {
        return n;
    }
}