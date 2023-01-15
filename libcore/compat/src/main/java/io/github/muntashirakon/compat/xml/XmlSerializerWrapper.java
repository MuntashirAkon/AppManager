// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.compat.xml;

import androidx.annotation.NonNull;

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Objects;

/**
 * Wrapper which delegates all calls through to the given {@link XmlSerializer}.
 */
public class XmlSerializerWrapper implements XmlSerializer {
    private final XmlSerializer mWrapped;

    public XmlSerializerWrapper(@NonNull XmlSerializer wrapped) {
        mWrapped = Objects.requireNonNull(wrapped);
    }

    public void setFeature(String name, boolean state) {
        mWrapped.setFeature(name, state);
    }

    public boolean getFeature(String name) {
        return mWrapped.getFeature(name);
    }

    public void setProperty(String name, Object value) {
        mWrapped.setProperty(name, value);
    }

    public Object getProperty(String name) {
        return mWrapped.getProperty(name);
    }

    public void setOutput(OutputStream os, String encoding) throws IOException {
        mWrapped.setOutput(os, encoding);
    }

    public void setOutput(Writer writer)
            throws IOException, IllegalArgumentException, IllegalStateException {
        mWrapped.setOutput(writer);
    }

    public void startDocument(String encoding, Boolean standalone) throws IOException {
        mWrapped.startDocument(encoding, standalone);
    }

    public void endDocument() throws IOException {
        mWrapped.endDocument();
    }

    public void setPrefix(String prefix, String namespace) throws IOException {
        mWrapped.setPrefix(prefix, namespace);
    }

    public String getPrefix(String namespace, boolean generatePrefix) {
        return mWrapped.getPrefix(namespace, generatePrefix);
    }

    public int getDepth() {
        return mWrapped.getDepth();
    }

    public String getNamespace() {
        return mWrapped.getNamespace();
    }

    public String getName() {
        return mWrapped.getName();
    }

    public XmlSerializer startTag(String namespace, String name) throws IOException {
        return mWrapped.startTag(namespace, name);
    }

    public XmlSerializer attribute(String namespace, String name, String value)
            throws IOException {
        return mWrapped.attribute(namespace, name, value);
    }

    public XmlSerializer endTag(String namespace, String name) throws IOException {
        return mWrapped.endTag(namespace, name);
    }

    public XmlSerializer text(String text) throws IOException{
        return mWrapped.text(text);
    }

    public XmlSerializer text(char[] buf, int start, int len) throws IOException {
        return mWrapped.text(buf, start, len);
    }

    public void cdsect(String text)
            throws IOException, IllegalArgumentException, IllegalStateException {
        mWrapped.cdsect(text);
    }

    public void entityRef(String text) throws IOException {
        mWrapped.entityRef(text);
    }

    public void processingInstruction(String text) throws IOException {
        mWrapped.processingInstruction(text);
    }

    public void comment(String text) throws IOException {
        mWrapped.comment(text);
    }

    public void docdecl(String text) throws IOException {
        mWrapped.docdecl(text);
    }

    public void ignorableWhitespace(String text) throws IOException {
        mWrapped.ignorableWhitespace(text);
    }

    public void flush() throws IOException {
        mWrapped.flush();
    }
}