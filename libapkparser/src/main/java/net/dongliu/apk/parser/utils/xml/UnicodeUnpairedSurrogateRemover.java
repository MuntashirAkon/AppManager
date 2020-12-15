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

import net.dongliu.apk.parser.utils.xml.CodePointTranslator;

import java.io.IOException;
import java.io.Writer;

/**
 * Helper subclass to CharSequenceTranslator to remove unpaired surrogates.
 */
class UnicodeUnpairedSurrogateRemover extends CodePointTranslator {
    /**
     * Implementation of translate that throws out unpaired surrogates. 
     * {@inheritDoc}
     */
    @Override
    public boolean translate(int codepoint, Writer out) throws IOException {
        if (codepoint >= Character.MIN_SURROGATE && codepoint <= Character.MAX_SURROGATE) {
            // It's a surrogate. Write nothing and say we've translated.
            return true;
        } else {
            // It's not a surrogate. Don't translate it.
            return false;
        }
    }
}

