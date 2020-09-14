/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.dongliu.apk.parser.utils.xml;

/**
 * Class holding various entity data for HTML and XML - generally for use with
 * the LookupTranslator.
 * All arrays are of length [*][2].
 */
public class EntityArrays {
    /**
     * Mapping to escape the basic XML and HTML character entities.
     *
     * Namely: {@code " & < >}
     * @return the mapping table
     */
    public static String[][] BASIC_ESCAPE() { return BASIC_ESCAPE.clone(); }
    private static final String[][] BASIC_ESCAPE = {
            {"\"", "&quot;"}, // " - double-quote
            {"&", "&amp;"},   // & - ampersand
            {"<", "&lt;"},    // < - less-than
            {">", "&gt;"},    // > - greater-than
    };

        /**
     * Mapping to escape the apostrophe character to its XML character entity.
     * @return the mapping table
     */
    public static String[][] APOS_ESCAPE() { return APOS_ESCAPE.clone(); }
    private static final String[][] APOS_ESCAPE = {
            {"'", "&apos;"}, // XML apostrophe
    };

}
