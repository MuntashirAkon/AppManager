// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * <code>DataTransmission</code> class handles the data sent and received by server or client.
 */
// Copyright 2017 Zheng Li
public final class DataTransmission implements Closeable {
    /**
     * Protocol version. Specification: <code>protocol-version,token</code>
     */
    public static final String PROTOCOL_VERSION = "1.2.4";

    public enum Role {
        Server,
        Client
    }

    @NonNull
    private final DataOutputStream mOutputStream;
    @NonNull
    private final DataInputStream mInputStream;
    private final boolean mAsync;

    @Nullable
    private OnReceiveCallback mOnReceiveCallback;
    private boolean mRunning = true;

    public DataTransmission(@NonNull OutputStream outputStream, @NonNull InputStream inputStream,
                            @Nullable OnReceiveCallback onReceiveCallback, boolean async) {
        mOutputStream = new DataOutputStream(outputStream);
        mInputStream = new DataInputStream(inputStream);
        mOnReceiveCallback = onReceiveCallback;
        mAsync = async;
    }

    /**
     * Create a new asynchronous data transfer object with receiver callback
     *
     * @param outputStream      Stream where new messages will be written
     * @param inputStream       Stream where new messages will be read from
     * @param onReceiveCallback The callback object whose method is called after receiving new messages
     */
    public DataTransmission(@NonNull OutputStream outputStream, @NonNull InputStream inputStream,
                            @Nullable OnReceiveCallback onReceiveCallback) {
        this(outputStream, inputStream, onReceiveCallback, true);
    }

    /**
     * Create a new asynchronous data transfer object
     *
     * @param outputStream Stream where new messages will be written
     * @param inputStream  Stream where new messages will be read from
     */
    public DataTransmission(@NonNull OutputStream outputStream, @NonNull InputStream inputStream) {
        this(outputStream, inputStream, true);
    }

    /**
     * Create a new data transfer object
     *
     * @param outputStream Stream where new messages will be written
     * @param inputStream  Stream where new messages will be read from
     * @param async        Whether the transfer should be asynchronous or synchronous
     */
    public DataTransmission(@NonNull OutputStream outputStream, @NonNull InputStream inputStream, boolean async) {
        this(outputStream, inputStream, null, async);
    }

    /**
     * Set custom callback for receiving message.
     *
     * @param onReceiveCallback Callback that wants to receive message.
     */
    public void setOnReceiveCallback(@Nullable OnReceiveCallback onReceiveCallback) {
        mOnReceiveCallback = onReceiveCallback;
    }

    /**
     * Send text message
     *
     * @param text Text to be sent
     * @throws IOException When it fails to send the message
     * @see #sendMessage(byte[])
     * @see #sendAndReceiveMessage(byte[])
     */
    public void sendMessage(@Nullable String text) throws IOException {
        if (text != null) {
            sendMessage(text.getBytes());
        }
    }

    /**
     * Send message as bytes
     *
     * @param messageBytes Bytes to be sent
     * @throws IOException When it fails to send the message
     * @see #sendMessage(String)
     * @see #sendAndReceiveMessage(byte[])
     */
    public void sendMessage(@Nullable byte[] messageBytes) throws IOException {
        if (messageBytes != null) {
            mOutputStream.writeInt(messageBytes.length);
            mOutputStream.write(messageBytes);
            mOutputStream.flush();
        }
    }

    /**
     * Read response as bytes after sending a message
     *
     * @return The bytes to be read
     * @throws IOException When it fails to read the message
     */
    @NonNull
    private byte[] readMessage() throws IOException {
        int len = mInputStream.readInt();
        byte[] bytes = new byte[len];
        mInputStream.readFully(bytes, 0, len);
        return bytes;
    }

    /**
     * Send and receive messages at the same time (half-duplex)
     *
     * @param messageBytes Bytes to be sent
     * @return Bytes to be read
     * @throws IOException When it fails to send or read the message
     * @see #sendMessage(String)
     * @see #sendMessage(byte[])
     */
    @Nullable
    public synchronized byte[] sendAndReceiveMessage(byte[] messageBytes) throws IOException {
        if (messageBytes != null) {
            sendMessage(messageBytes);
            return readMessage();
        }
        return null;
    }

    /**
     * Handshake: verify tokens
     *
     * @param token Token supplied by server or client based
     * @param role  Whether the supplied token is from server or client
     * @throws IOException              When it fails to verify the token
     * @throws ProtocolVersionException When the {@link #PROTOCOL_VERSION} mismatch occurs
     */
    public void shakeHands(@NonNull String token, Role role) throws IOException {
        Objects.requireNonNull(token);
        if (role == Role.Server) {
            FLog.log("DataTransmission#shakeHands: Server protocol: " + PROTOCOL_VERSION);
            String auth = new String(readMessage());  // <protocol-version>,<token>
            FLog.log("Received authentication: " + auth);
            String[] split = auth.split(",");
            String clientToken = split[1];
            // Match tokens
            if (token.equals(clientToken)) {
                // Connection is authorised
                FLog.log("DataTransmission#shakeHands: Authentication successful.");
            } else {
                FLog.log("DataTransmission#shakeHands: Authentication failed.");
                throw new IOException("Unauthorized client, token: " + token);
            }
            // Check protocol version
            String protocolVersion = split[0];
            if (!PROTOCOL_VERSION.equals(protocolVersion)) {
                throw new ProtocolVersionException("Client protocol version: " + protocolVersion + ", " +
                        "Server protocol version: " + PROTOCOL_VERSION);
            }
        } else if (role == Role.Client) {
            Log.e("DataTransmission", "shakeHands: Client protocol: " + PROTOCOL_VERSION);
            sendMessage(PROTOCOL_VERSION + "," + token);
        }
    }

    /**
     * Handle for messages received. For asynchronous operations or when the socket is not active,
     * nothing is done. But when server is running {@link #onReceiveMessage(byte[])} is called.
     *
     * @throws IOException When it fails to read the message received
     */
    public void handleReceive() throws IOException {
        if (!mAsync) return;
        while (mRunning) {
            onReceiveMessage(readMessage());
        }
    }

    /**
     * Calls the callback function {@link OnReceiveCallback#onMessage(byte[])}.
     *
     * @param bytes Bytes that was received earlier
     */
    private void onReceiveMessage(@NonNull byte[] bytes) {
        if (mOnReceiveCallback != null) {
            mOnReceiveCallback.onMessage(bytes);
        }
    }

    /**
     * Stop data transmission, called when socket connection is being closed
     */
    @Override
    public void close() {
        mRunning = false;
        try {
            mOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            mInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * The callback that executes when a new message is received
     */
    public interface OnReceiveCallback {
        /**
         * Implement this method to handle the received message
         *
         * @param bytes The message that was received
         */
        void onMessage(@NonNull byte[] bytes);
    }

    /**
     * Indicates that a protocol version mismatch has been occurred
     */
    public static class ProtocolVersionException extends IOException {
        public ProtocolVersionException(String message) {
            super(message);
        }
    }
}
