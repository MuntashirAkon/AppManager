/*
 * Copyright (c) 2021 Muntashir Al-Islam
 * Copyright (c) 2020 Sam Palmer
 * Copyright (c) 2016 Anton Tananaev
 * Copyright (c) 2013 Cameron Gutman
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.adb;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * This class represents an ADB connection.
 */
public class AdbConnection implements Closeable {
    /**
     * The underlying socket that this class uses to
     * communicate with the target device.
     */
    private Socket socket;

    /**
     * The last allocated local stream ID. The ID
     * chosen for the next stream will be this value + 1.
     */
    private int lastLocalId;

    /**
     * The input stream that this class uses to read from
     * the socket.
     */
    private volatile InputStream inputStream;

    /**
     * The output stream that this class uses to read from
     * the socket.
     */
    volatile OutputStream outputStream;

    /**
     * The backend thread that handles responding to ADB packets.
     */
    private final Thread connectionThread;

    /**
     * Specifies whether a connect has been attempted
     */
    private volatile boolean connectAttempted;

    /**
     * Whether the connection thread should give up if the first authentication attempt fails
     */
    private volatile boolean abortOnUnauthorised;

    /**
     * Whether the the first authentication attempt failed and {@link #abortOnUnauthorised} was {@code true}
     */
    private volatile boolean authorisationFailed;

    /**
     * Specifies whether a CNXN packet has been received from the peer.
     */
    private volatile boolean connected;

    /**
     * Specifies the maximum amount data that can be sent to the remote peer.
     * This is only valid after connect() returns successfully.
     */
    private volatile int maxData;

    /**
     * An initialized ADB crypto object that contains a key pair.
     */
    private volatile AdbCrypto crypto;

    /**
     * Specifies whether this connection has already sent a signed token.
     */
    private boolean sentSignature;

    /**
     * A hash map of our open streams indexed by local ID.
     **/
    private final ConcurrentHashMap<Integer, AdbStream> openStreams;

    /**
     * Internal constructor to initialize some internal state
     */
    private AdbConnection() {
        openStreams = new ConcurrentHashMap<>();
        lastLocalId = 0;
        connectionThread = createConnectionThread();
    }

    /**
     * Creates a AdbConnection object associated with the socket and
     * crypto object specified.
     *
     * @param socket The socket that the connection will use for communcation.
     * @param crypto The crypto object that stores the key pair for authentication.
     * @return A new AdbConnection object.
     * @throws IOException If there is a socket error
     */
    public static AdbConnection create(Socket socket, AdbCrypto crypto) throws IOException {
        AdbConnection newConn = new AdbConnection();

        newConn.crypto = crypto;

        newConn.socket = socket;
        newConn.inputStream = socket.getInputStream();
        newConn.outputStream = socket.getOutputStream();

        /* Disable Nagle because we're sending tiny packets */
        socket.setTcpNoDelay(true);

        return newConn;
    }

    /**
     * Creates a new connection thread.
     *
     * @return A new connection thread.
     */
    private Thread createConnectionThread() {
        @SuppressWarnings("resource") final AdbConnection conn = this;
        return new Thread(() -> {
            while (!connectionThread.isInterrupted()) {
                try {
                    /* Read and parse a message off the socket's input stream */
                    AdbProtocol.AdbMessage msg = AdbProtocol.AdbMessage.parseAdbMessage(inputStream);

                    /* Verify magic and checksum */
                    if (!AdbProtocol.validateMessage(msg))
                        continue;

                    switch (msg.command) {
                        /* Stream-oriented commands */
                        case AdbProtocol.CMD_OKAY:
                        case AdbProtocol.CMD_WRTE:
                        case AdbProtocol.CMD_CLSE:
                            /* We must ignore all packets when not connected */
                            if (!conn.connected)
                                continue;

                            /* Get the stream object corresponding to the packet */
                            AdbStream waitingStream = openStreams.get(msg.arg1);
                            if (waitingStream == null)
                                continue;

                            synchronized (waitingStream) {
                                if (msg.command == AdbProtocol.CMD_OKAY) {
                                    /* We're ready for writes */
                                    waitingStream.updateRemoteId(msg.arg0);
                                    waitingStream.readyForWrite();

                                    /* Unwait an open/write */
                                    waitingStream.notify();
                                } else if (msg.command == AdbProtocol.CMD_WRTE) {
                                    /* Got some data from our partner */
                                    waitingStream.addPayload(msg.payload);

                                    /* Tell it we're ready for more */
                                    waitingStream.sendReady();
                                } else if (msg.command == AdbProtocol.CMD_CLSE) {
                                    /* He doesn't like us anymore :-( */
                                    conn.openStreams.remove(msg.arg1);

                                    /* Notify readers and writers */
                                    waitingStream.notifyClose(true);
                                }
                            }

                            break;

                        case AdbProtocol.CMD_AUTH:

                            byte[] packet;

                            if (msg.arg0 == AdbProtocol.AUTH_TYPE_TOKEN) {
                                /* This is an authentication challenge */
                                if (conn.sentSignature) {
                                    if (abortOnUnauthorised) {
                                        authorisationFailed = true;
                                        /* Throwing an exception to break out of the loop */
                                        throw new RuntimeException();
                                    }

                                    /* We've already tried our signature, so send our public key */
                                    packet = AdbProtocol.generateAuth(AdbProtocol.AUTH_TYPE_RSA_PUBLIC,
                                            conn.crypto.getAdbPublicKeyPayload());
                                } else {
                                    /* We'll sign the token */
                                    packet = AdbProtocol.generateAuth(AdbProtocol.AUTH_TYPE_SIGNATURE,
                                            conn.crypto.signAdbTokenPayload(msg.payload));
                                    conn.sentSignature = true;
                                }

                                /* Write the AUTH reply */
                                synchronized (conn.outputStream) {
                                    conn.outputStream.write(packet);
                                    conn.outputStream.flush();
                                }
                            }
                            break;

                        case AdbProtocol.CMD_CNXN:
                            synchronized (conn) {
                                /* We need to store the max data size */
                                conn.maxData = msg.arg1;

                                /* Mark us as connected and unwait anyone waiting on the connection */
                                conn.connected = true;
                                conn.notifyAll();
                            }
                            break;

                        default:
                            /* Unrecognized packet, just drop it */
                            break;
                    }
                } catch (Exception e) {
                    /* The cleanup is taken care of by a combination of this thread
                     * and close() */
                    break;
                }
            }

            /* This thread takes care of cleaning up pending streams */
            synchronized (conn) {
                cleanupStreams();
                conn.notifyAll();
                conn.connectAttempted = false;
            }
        });
    }

    /**
     * Gets the max data size that the remote client supports.
     * A connection must have been attempted before calling this routine.
     * This routine will block if a connection is in progress.
     *
     * @return The maximum data size indicated in the connect packet.
     * @throws InterruptedException If a connection cannot be waited on.
     * @throws IOException          if the connection fails
     */
    public int getMaxData() throws InterruptedException, IOException {
        if (!connectAttempted)
            throw new IllegalStateException("connect() must be called first");

        waitForConnection(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

        return maxData;
    }

    /**
     * Same as {@code connect(Long.MAX_VALUE, TimeUnit.MILLISECONDS, false)}
     *
     * @throws IOException          If the socket fails while connecting
     * @throws InterruptedException If we are unable to wait for the connection to finish
     */
    public void connect() throws IOException, InterruptedException {
        connect(Long.MAX_VALUE, TimeUnit.MILLISECONDS, false);
    }

    /**
     * Connects to the remote device. This routine will block until the connection
     * completes or the timeout elapses.
     *
     * @param timeout             the time to wait for the lock
     * @param unit                the time unit of the timeout argument
     * @param throwOnUnauthorised Whether to throw an {@link AdbAuthenticationFailedException}
     *                            if the peer rejects out first authentication attempt
     * @return {@code true} if the connection was established, or {@code false} if the connection timed out
     * @throws IOException                      If the socket fails while connecting
     * @throws InterruptedException             If we are unable to wait for the connection to finish
     * @throws AdbAuthenticationFailedException If {@code throwOnUnauthorised} is {@code true}
     *                                          and the peer rejects the first authentication attempt, which indicates that the peer has
     *                                          not saved our public key from a previous connection
     */
    public boolean connect(long timeout, TimeUnit unit, boolean throwOnUnauthorised) throws IOException, InterruptedException, AdbAuthenticationFailedException {
        if (connected)
            throw new IllegalStateException("Already connected");

        /* Write the CONNECT packet */
        synchronized (outputStream) {
            outputStream.write(AdbProtocol.generateConnect());
            outputStream.flush();
        }

        /* Start the connection thread to respond to the peer */
        connectAttempted = true;
        abortOnUnauthorised = throwOnUnauthorised;
        authorisationFailed = false;
        connectionThread.start();

        return waitForConnection(timeout, unit);
    }

    /**
     * Opens an AdbStream object corresponding to the specified destination.
     * This routine will block until the connection completes.
     *
     * @param destination The destination to open on the target
     * @return AdbStream object corresponding to the specified destination
     * @throws UnsupportedEncodingException If the destination cannot be encoded to UTF-8
     * @throws IOException                  If the stream fails while sending the packet
     * @throws InterruptedException         If we are unable to wait for the connection to finish
     */
    public AdbStream open(String destination) throws UnsupportedEncodingException, IOException, InterruptedException {
        int localId = ++lastLocalId;

        if (!connectAttempted)
            throw new IllegalStateException("connect() must be called first");

        waitForConnection(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

        /* Add this stream to this list of half-open streams */
        AdbStream stream = new AdbStream(this, localId);
        openStreams.put(localId, stream);

        /* Send the open */
        synchronized (outputStream) {
            outputStream.write(AdbProtocol.generateOpen(localId, destination));
            outputStream.flush();
        }

        /* Wait for the connection thread to receive the OKAY */
        synchronized (stream) {
            stream.wait();
        }

        /* Check if the open was rejected */
        if (stream.isClosed())
            throw new ConnectException("Stream open actively rejected by remote peer");

        /* We're fully setup now */
        return stream;
    }

    private boolean waitForConnection(long timeout, TimeUnit unit) throws InterruptedException, IOException {
        synchronized (this) {
            /* Block if a connection is pending, but not yet complete */
            long timeoutEndMillis = System.currentTimeMillis() + unit.toMillis(timeout);
            while (!connected && connectAttempted && timeoutEndMillis - System.currentTimeMillis() > 0) {
                wait(timeoutEndMillis - System.currentTimeMillis());
            }

            if (!connected) {
                if (connectAttempted)
                    return false;
                else if (authorisationFailed)
                    throw new AdbAuthenticationFailedException();
                else
                    throw new IOException("Connection failed");
            }
        }

        return true;
    }

    /**
     * This function terminates all I/O on streams associated with this ADB connection
     */
    private void cleanupStreams() {
        /* Close all streams on this connection */
        for (AdbStream s : openStreams.values()) {
            /* We handle exceptions for each close() call to avoid
             * terminating cleanup for one failed close(). */
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }

        /* No open streams anymore */
        openStreams.clear();
    }

    /**
     * This routine closes the Adb connection and underlying socket
     *
     * @throws IOException if the socket fails to close
     */
    @Override
    public void close() throws IOException {
        /* If the connection thread hasn't spawned yet, there's nothing to do */
        if (connectionThread == null)
            return;

        /* Closing the socket will kick the connection thread */
        socket.close();

        /* Wait for the connection thread to die */
        connectionThread.interrupt();
        try {
            connectionThread.join();
        } catch (InterruptedException ignored) {
        }
    }

}
