/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @bug 8193682 8278794
 * @summary Test Infinite loop while writing on closed Deflater and Inflater.
 * @run testng CloseInflaterDeflaterTest
 */
import java.io.*;
import java.util.Random;
import java.util.jar.JarOutputStream;
import java.util.zip.DeflaterInputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterOutputStream;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertThrows;


public class CloseInflaterDeflaterTest {

    // Number of bytes to write/read from Deflater/Inflater
    private static final int INPUT_LENGTH= 512;
    // OutputStream that will throw an exception during a write operation
    private static OutputStream outStream = new OutputStream() {
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            throw new IOException();
        }
        @Override
        public void write(byte[] b) throws IOException {}
        @Override
        public void write(int b) throws IOException {}
    };
    // InputStream that will throw an exception during a read operation
    private static InputStream inStream = new InputStream() {
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            throw new IOException();
        }
        @Override
        public int read(byte[] b) throws IOException { throw new IOException();}
        @Override
        public int read() throws IOException { throw new IOException();}
    };
    // Input bytes for read/write operation
    private static byte[] inputBytes = new byte[INPUT_LENGTH];
    // Random function to add bytes to inputBytes
    private static Random rand = new Random();

    /**
     * DataProvider to specify whether to use close() or finish() of OutputStream
     *
     * @return Entry object indicating which method to use for closing OutputStream
     */
    @DataProvider
    public Object[][] testOutputStreams() {
     return new Object[][] {
      { true },
      { false },
     };
    }

    /**
     * DataProvider to specify on which outputstream closeEntry() has to be called
     *
     * @return Entry object returning either JarOutputStream or ZipOutputStream
     */
    @DataProvider
    public Object[][] testZipAndJar() throws IOException{
     return new Object[][] {
      { new JarOutputStream(outStream)},
      { new ZipOutputStream(outStream)},
     };
    }

    /**
     * Add inputBytes array with random bytes to write into OutputStream
     */
    @BeforeTest
    public void before_test()
    {
       rand.nextBytes(inputBytes);
    }

    /**
     * Test for infinite loop by writing bytes to closed GZIPOutputStream
     *
     * @param useCloseMethod indicates whether to use Close() or finish() method
     * @throws IOException if an error occurs
     */
    @Test(dataProvider = "testOutputStreams")
    public void testGZip(boolean useCloseMethod) throws IOException {
        GZIPOutputStream gzip = new GZIPOutputStream(outStream);
        gzip.write(inputBytes, 0, INPUT_LENGTH);
        assertThrows(IOException.class, () -> {
            // Close GZIPOutputStream
            if (useCloseMethod) {
                gzip.close();
            } else {
                gzip.finish();
            }
        });
        // Write on a closed GZIPOutputStream, closed Deflater IOException expected
        assertThrows(NullPointerException.class , () -> gzip.write(inputBytes, 0, INPUT_LENGTH));
    }

    /**
     * Test for infinite loop by writing bytes to closed DeflaterOutputStream
     *
     * @param useCloseMethod indicates whether to use Close() or finish() method
     * @throws IOException if an error occurs
     */
    @Test(dataProvider = "testOutputStreams")
    public void testDeflaterOutputStream(boolean useCloseMethod) throws IOException {
        DeflaterOutputStream def = new DeflaterOutputStream(outStream);
        assertThrows(IOException.class , () -> def.write(inputBytes, 0, INPUT_LENGTH));
        assertThrows(IOException.class, () -> {
            // Close DeflaterOutputStream
            if (useCloseMethod) {
                def.close();
            } else {
                def.finish();
            }
        });
        // Write on a closed DeflaterOutputStream, 'Deflater has been closed' NPE is expected
        assertThrows(NullPointerException.class , () -> def.write(inputBytes, 0, INPUT_LENGTH));
    }

    /**
     * Test for infinite loop by reading bytes from closed DeflaterInputStream
     *
     * @throws IOException if an error occurs
     */
    @Test
    public void testDeflaterInputStream() throws IOException {
        DeflaterInputStream def = new DeflaterInputStream(inStream);
        assertThrows(IOException.class , () -> def.read(inputBytes, 0, INPUT_LENGTH));
        // Close DeflaterInputStream
        def.close();
        // Read from a closed DeflaterInputStream, closed Deflater IOException expected
        assertThrows(IOException.class , () -> def.read(inputBytes, 0, INPUT_LENGTH));
    }

    /**
     * Test for infinite loop by writing bytes to closed InflaterOutputStream
     *
     * @param useCloseMethod indicates whether to use Close() or finish() method
     * @throws IOException if an error occurs
     */
    @Test(dataProvider = "testOutputStreams")
    public void testInflaterOutputStream(boolean useCloseMethod) throws IOException {
        InflaterOutputStream inf = new InflaterOutputStream(outStream);
        assertThrows(IOException.class , () -> inf.write(inputBytes, 0, INPUT_LENGTH));
        assertThrows(IOException.class , () -> {
            // Close InflaterOutputStream
            if (useCloseMethod) {
                inf.close();
            } else {
                inf.finish();
            }
        });
        // Write on a closed InflaterOutputStream , closed Inflater IOException expected
        assertThrows(IOException.class , () -> inf.write(inputBytes, 0, INPUT_LENGTH));
    }

    /**
     * Test for infinite loop by writing bytes to closed ZipOutputStream/JarOutputStream
     *
     * @param zip will be the instance of either JarOutputStream or ZipOutputStream
     * @throws IOException if an error occurs
     */
    @Test(dataProvider = "testZipAndJar")
    public void testZipCloseEntry(ZipOutputStream zip) throws IOException {
        assertThrows(IOException.class , () -> zip.putNextEntry(new ZipEntry("")));
        zip.write(inputBytes, 0, INPUT_LENGTH);
        assertThrows(IOException.class , () -> zip.closeEntry());
        // Write on a closed ZipOutputStream , 'Deflater has been closed' NPE is expected
        assertThrows(NullPointerException.class , () -> zip.write(inputBytes, 0, INPUT_LENGTH));
    }

}
// SPDX-License-Identifier: Apache-2.0

package org.apache.commons.compress.compressors.gzip;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.compressors.CompressorOutputStream;

/**
 * Compressed output stream using the gzip format. This implementation improves
 * over the standard {@link GZIPOutputStream} class by allowing
 * the configuration of the compression level and the header metadata (file name,
 * comment, modification time, operating system and extra flags).
 *
 * @see <a href="https://tools.ietf.org/html/rfc1952">GZIP File Format Specification</a>
 */
// Copyright 2008 Torsten Curdt
public class GzipCompressorOutputStream extends CompressorOutputStream {

    /** Header flag indicating a file name follows the header */
    private static final int FNAME = 1 << 3;

    /** Header flag indicating a comment follows the header */
    private static final int FCOMMENT = 1 << 4;

    /** The underlying stream */
    private final OutputStream out;

    /** Deflater used to compress the data */
    private final Deflater deflater;

    /** The buffer receiving the compressed data from the deflater */
    private final byte[] deflateBuffer = new byte[512];

    /** Indicates if the stream has been closed */
    private boolean closed;

    /** The checksum of the uncompressed data */
    private final CRC32 crc = new CRC32();

    /**
     * Creates a gzip compressed output stream with the default parameters.
     * @param out the stream to compress to
     * @throws IOException if writing fails
     */
    public GzipCompressorOutputStream(final OutputStream out) throws IOException {
        this(out, new GzipParameters());
    }

    /**
     * Creates a gzip compressed output stream with the specified parameters.
     * @param out the stream to compress to
     * @param parameters the parameters to use
     * @throws IOException if writing fails
     *
     * @since 1.7
     */
    public GzipCompressorOutputStream(final OutputStream out, final GzipParameters parameters) throws IOException {
        this.out = out;
        this.deflater = new Deflater(parameters.getCompressionLevel(), true);

        writeHeader(parameters);
    }

    private void writeHeader(final GzipParameters parameters) throws IOException {
        final String filename = parameters.getFilename();
        final String comment = parameters.getComment();

        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) GZIPInputStream.GZIP_MAGIC);
        buffer.put((byte) Deflater.DEFLATED); // compression method (8: deflate)
        buffer.put((byte) ((filename != null ? FNAME : 0) | (comment != null ? FCOMMENT : 0))); // flags
        buffer.putInt((int) (parameters.getModificationTime() / 1000));

        // extra flags
        final int compressionLevel = parameters.getCompressionLevel();
        if (compressionLevel == Deflater.BEST_COMPRESSION) {
            buffer.put((byte) 2);
        } else if (compressionLevel == Deflater.BEST_SPEED) {
            buffer.put((byte) 4);
        } else {
            buffer.put((byte) 0);
        }

        buffer.put((byte) parameters.getOperatingSystem());

        out.write(buffer.array());

        if (filename != null) {
            out.write(filename.getBytes(StandardCharsets.ISO_8859_1));
            out.write(0);
        }

        if (comment != null) {
            out.write(comment.getBytes(StandardCharsets.ISO_8859_1));
            out.write(0);
        }
    }

    private void writeTrailer() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt((int) crc.getValue());
        buffer.putInt(deflater.getTotalIn());

        out.write(buffer.array());
    }

    @Override
    public void write(final int b) throws IOException {
        write(new byte[]{(byte) (b & 0xff)}, 0, 1);
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.1
     */
    @Override
    public void write(final byte[] buffer) throws IOException {
        write(buffer, 0, buffer.length);
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.1
     */
    @Override
    public void write(final byte[] buffer, final int offset, final int length) throws IOException {
        if (deflater.finished()) {
            throw new IOException("Cannot write more data, the end of the compressed data stream has been reached");
        }
        if (length > 0) {
            deflater.setInput(buffer, offset, length);

            while (!deflater.needsInput()) {
                deflate();
            }

            crc.update(buffer, offset, length);
        }
    }

    private void deflate() throws IOException {
        final int length = deflater.deflate(deflateBuffer, 0, deflateBuffer.length);
        if (length > 0) {
            out.write(deflateBuffer, 0, length);
        }
    }

    /**
     * Finishes writing compressed data to the underlying stream without closing it.
     *
     * @since 1.7
     * @throws IOException on error
     */
    public void finish() throws IOException {
        if (!deflater.finished()) {
            deflater.finish();

            while (!deflater.finished()) {
                deflate();
            }

            writeTrailer();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.7
     */
    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            try {
                finish();
            } finally {
                deflater.end();
                out.close();
                closed = true;
            }
        }
    }

}