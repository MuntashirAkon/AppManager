// SPDX-License-Identifier: Apache-2.0

package net.dongliu.apk.parser.utils.xml;

/**
 * Class holding various entity data for HTML and XML - generally for use with
 * the LookupTranslator.
 * All arrays are of length [*][2].
 */
// Copyright 2010 pbenedict
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
