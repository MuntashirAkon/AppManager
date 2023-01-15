// SPDX-License-Identifier: Apache-2.0

package io.github.muntashirakon.compat.xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;

/**
 * Specialization of {@link XmlSerializer} which adds explicit methods to
 * support consistent and efficient conversion of primitive data types.
 */
public interface TypedXmlSerializer extends XmlSerializer {
    /**
     * Functionally equivalent to {@link #attribute(String, String, String)} but
     * with the additional signal that the given value is a candidate for being
     * canonicalized, similar to {@link String#intern()}.
     */
    @NonNull
    XmlSerializer attributeInterned(@Nullable String namespace, @NonNull String name,
                                    @NonNull String value) throws IOException;

    /**
     * Encode the given strongly-typed value and serialize using
     * {@link #attribute(String, String, String)}.
     */
    @NonNull
    XmlSerializer attributeBytesHex(@Nullable String namespace, @NonNull String name,
                                    @NonNull byte[] value) throws IOException;

    /**
     * Encode the given strongly-typed value and serialize using
     * {@link #attribute(String, String, String)}.
     */
    @NonNull
    XmlSerializer attributeBytesBase64(@Nullable String namespace, @NonNull String name,
                                       @NonNull byte[] value) throws IOException;

    /**
     * Encode the given strongly-typed value and serialize using
     * {@link #attribute(String, String, String)}.
     */
    @NonNull
    XmlSerializer attributeInt(@Nullable String namespace, @NonNull String name,
                               int value) throws IOException;

    /**
     * Encode the given strongly-typed value and serialize using
     * {@link #attribute(String, String, String)}.
     */
    @NonNull
    XmlSerializer attributeIntHex(@Nullable String namespace, @NonNull String name,
                                  int value) throws IOException;

    /**
     * Encode the given strongly-typed value and serialize using
     * {@link #attribute(String, String, String)}.
     */
    @NonNull
    XmlSerializer attributeLong(@Nullable String namespace, @NonNull String name,
                                long value) throws IOException;

    /**
     * Encode the given strongly-typed value and serialize using
     * {@link #attribute(String, String, String)}.
     */
    @NonNull
    XmlSerializer attributeLongHex(@Nullable String namespace, @NonNull String name,
                                   long value) throws IOException;

    /**
     * Encode the given strongly-typed value and serialize using
     * {@link #attribute(String, String, String)}.
     */
    @NonNull
    XmlSerializer attributeFloat(@Nullable String namespace, @NonNull String name,
                                 float value) throws IOException;

    /**
     * Encode the given strongly-typed value and serialize using
     * {@link #attribute(String, String, String)}.
     */
    @NonNull
    XmlSerializer attributeDouble(@Nullable String namespace, @NonNull String name,
                                  double value) throws IOException;

    /**
     * Encode the given strongly-typed value and serialize using
     * {@link #attribute(String, String, String)}.
     */
    @NonNull
    XmlSerializer attributeBoolean(@Nullable String namespace, @NonNull String name,
                                   boolean value) throws IOException;
}