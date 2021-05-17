// SPDX-License-Identifier: BSD-3-Clause AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class abstracts the underlying ADB streams
 */
// Copyright 2013 Cameron Gutman
public class AdbStream implements Closeable {

    /**
     * The AdbConnection object that the stream communicates over
     */
    private final AdbConnection adbConn;

    /**
     * The local ID of the stream
     */
    private final int localId;

    /**
     * The remote ID of the stream
     */
    private volatile int remoteId;

    /**
     * Indicates whether a write is currently allowed
     */
    private final AtomicBoolean writeReady;

    /**
     * A queue of data from the target's write packets
     */
    private final Queue<byte[]> readQueue;

    /**
     * Indicates whether the connection is closed already
     */
    private volatile boolean isClosed;

    /**
     * Whether the remote peer has closed but we still have unread data in the queue
     */
    private volatile boolean pendingClose;

    /**
     * Creates a new AdbStream object on the specified AdbConnection
     * with the given local ID.
     *
     * @param adbConn AdbConnection that this stream is running on
     * @param localId Local ID of the stream
     */
    public AdbStream(AdbConnection adbConn, int localId) {
        this.adbConn = adbConn;
        this.localId = localId;
        this.readQueue = new ConcurrentLinkedQueue<>();
        this.writeReady = new AtomicBoolean(false);
        this.isClosed = false;
    }

    /**
     * Called by the connection thread to indicate newly received data.
     *
     * @param payload Data inside the write message
     */
    void addPayload(byte[] payload) {
        synchronized (readQueue) {
            readQueue.add(payload);
            readQueue.notifyAll();
        }
    }

    /**
     * Called by the connection thread to send an OKAY packet, allowing the
     * other side to continue transmission.
     *
     * @throws IOException If the connection fails while sending the packet
     */
    void sendReady() throws IOException {
        /* Generate and send a READY packet */
        byte[] packet = AdbProtocol.generateReady(localId, remoteId);

        synchronized (adbConn.lock) {
            adbConn.getOutputStream().write(packet);
            adbConn.getOutputStream().flush();
        }
    }

    /**
     * Called by the connection thread to update the remote ID for this stream
     *
     * @param remoteId New remote ID
     */
    void updateRemoteId(int remoteId) {
        this.remoteId = remoteId;
    }

    /**
     * Called by the connection thread to indicate the stream is okay to send data.
     */
    void readyForWrite() {
        writeReady.set(true);
    }

    /**
     * Called by the connection thread to notify that the stream was closed by the peer.
     */
    void notifyClose(boolean closedByPeer) {
        /* We don't call close() because it sends another CLOSE */
        if (closedByPeer && !readQueue.isEmpty()) {
            /* The remote peer closed the stream but we haven't finished reading the remaining data */
            pendingClose = true;
        } else {
            isClosed = true;
        }

        /* Unwait readers and writers */
        synchronized (this) {
            notifyAll();
        }
        synchronized (readQueue) {
            readQueue.notifyAll();
        }
    }

    /**
     * Reads a pending write payload from the other side.
     *
     * @return Byte array containing the payload of the write
     * @throws InterruptedException If we are unable to wait for data
     * @throws IOException          If the stream fails while waiting
     */
    public byte[] read() throws InterruptedException, IOException {
        byte[] data;

        synchronized (readQueue) {
            /* Wait for the connection to close or data to be received */
            while ((data = readQueue.poll()) == null && !isClosed) {
                readQueue.wait();
            }

            if (isClosed) {
                throw new IOException("Stream closed");
            }

            if (pendingClose && readQueue.isEmpty()) {
                /* The peer closed the stream, and we've finished reading the stream data, so this stream is finished */
                isClosed = true;
            }
        }

        return data;
    }

    /**
     * Sends a write packet with a given String payload.
     *
     * @param payload Payload in the form of a String
     * @throws IOException          If the stream fails while sending data
     * @throws InterruptedException If we are unable to wait to send data
     */
    public void write(String payload) throws IOException, InterruptedException {
        /* ADB needs null-terminated strings */
        write(payload.getBytes(StandardCharsets.UTF_8), false);
        write(new byte[]{0}, true);
    }

    /**
     * Sends a write packet with a given byte array payload.
     *
     * @param payload Payload in the form of a byte array
     * @throws IOException          If the stream fails while sending data
     * @throws InterruptedException If we are unable to wait to send data
     */
    public void write(byte[] payload) throws IOException, InterruptedException {
        write(payload, true);
    }

    /**
     * Queues a write packet and optionally sends it immediately.
     *
     * @param payload Payload in the form of a byte array
     * @param flush   Specifies whether to send the packet immediately
     * @throws IOException          If the stream fails while sending data
     * @throws InterruptedException If we are unable to wait to send data
     */
    public void write(byte[] payload, boolean flush) throws IOException, InterruptedException {
        synchronized (this) {
            /* Make sure we're ready for a write */
            while (!isClosed && !writeReady.compareAndSet(true, false))
                wait();

            if (isClosed) {
                throw new IOException("Stream closed");
            }
        }

        /* Generate a WRITE packet and send it */
        byte[] packet = AdbProtocol.generateWrite(localId, remoteId, payload);

        synchronized (adbConn.lock) {
            adbConn.getOutputStream().write(packet);

            if (flush) {
                adbConn.getOutputStream().flush();
            }
        }
    }

    /**
     * Closes the stream. This sends a close message to the peer.
     *
     * @throws IOException If the stream fails while sending the close message.
     */
    @Override
    public void close() throws IOException {
        synchronized (this) {
            /* This may already be closed by the remote host */
            if (isClosed)
                return;

            /* Notify readers/writers that we've closed */
            notifyClose(false);
        }

        byte[] packet = AdbProtocol.generateClose(localId, remoteId);

        synchronized (adbConn.lock) {
            adbConn.getOutputStream().write(packet);
            adbConn.getOutputStream().flush();
        }
    }

    /**
     * Retreives whether the stream is closed or not
     *
     * @return True if the stream is close, false if not
     */
    public boolean isClosed() {
        return isClosed;
    }

}
