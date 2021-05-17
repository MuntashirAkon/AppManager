// SPDX-License-Identifier: Apache-2.0

package net.dongliu.apk.parser.utils.xml;

import java.io.IOException;
import java.io.Writer;

/**
 * Helper subclass to CharSequenceTranslator to allow for translations that 
 * will replace up to one character at a time.
 */
// Copyright 2010 pbenedict
abstract class CodePointTranslator extends CharSequenceTranslator {

    /**
     * Implementation of translate that maps onto the abstract translate(int, Writer) method. 
     * {@inheritDoc}
     */
    @Override
    public final int translate(final CharSequence input, final int index, final Writer out) throws IOException {
        final int codepoint = Character.codePointAt(input, index);
        final boolean consumed = translate(codepoint, out);
        return consumed ? 1 : 0;
    }

    /**
     * Translate the specified codepoint into another. 
     *
     * @param codepoint int character input to translate
     * @param out Writer to optionally push the translated output to
     * @return boolean as to whether translation occurred or not
     * @throws IOException if and only if the Writer produces an IOException
     */
    public abstract boolean translate(int codepoint, Writer out) throws IOException;

}
