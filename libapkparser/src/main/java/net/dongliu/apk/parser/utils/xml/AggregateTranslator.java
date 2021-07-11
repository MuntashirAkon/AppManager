// SPDX-License-Identifier: Apache-2.0

package net.dongliu.apk.parser.utils.xml;

import java.io.IOException;
import java.io.Writer;

/**
 * Executes a sequence of translators one after the other. Execution ends whenever 
 * the first translator consumes codepoints from the input.
 */
// Copyright 2010 pbenedict
class AggregateTranslator extends CharSequenceTranslator {

    private final CharSequenceTranslator[] translators;

    /**
     * Specify the translators to be used at creation time. 
     *
     * @param translators CharSequenceTranslator array to aggregate
     */
    public AggregateTranslator(final CharSequenceTranslator... translators) {
        this.translators = translators;
    }

    /**
     * The first translator to consume codepoints from the input is the 'winner'. 
     * Execution stops with the number of consumed codepoints being returned. 
     * {@inheritDoc}
     */
    @Override
    public int translate(final CharSequence input, final int index, final Writer out) throws IOException {
        for (final CharSequenceTranslator translator : translators) {
            final int consumed = translator.translate(input, index, out);
            if(consumed != 0) {
                return consumed;
            }
        }
        return 0;
    }

}
