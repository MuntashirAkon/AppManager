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

package org.apache.commons.compress.archivers.zip;

import java.nio.charset.Charset;

/**
 * An interface added to allow access to the character set associated with an {@link NioZipEncoding},
 * without requiring a new method to be added to {@link ZipEncoding}.
 * <p>
 * This avoids introducing a
 * potentially breaking change, or making {@link NioZipEncoding} a public class.
 * </p>
 * @since 1.15
 */
public interface CharsetAccessor {

    /**
     * Provides access to the character set associated with an object.
     * <p>
     *     This allows nio oriented code to use more natural character encoding/decoding methods,
     *     whilst allowing existing code to continue to rely on special-case error handling for UTF-8.
     * </p>
     * @return the character set associated with this object
     */
    Charset getCharset();
}