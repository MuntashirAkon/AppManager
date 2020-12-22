/*
 * Copyright (C) 2020 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.server.common;

import android.text.TextUtils;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * <code>DataTransmission</code> class handles the data sent and received by server or client.
 */
public final class DataTransmission {
    /**
     * Protocol version. Specification: <code>protocol-version,token</code>
     */
    public static final String PROTOCOL_VERSION = "1.2.4";

    private final DataOutputStream outputStream;
    private final DataInputStream inputStream;
    private OnReceiveCallback callback;

    private boolean running = true;
    private boolean async = true;

    /**
     * Create a new asynchronous data transfer object with receiver callback
     * @param outputStream Stream where new messages will be written
     * @param inputStream Stream where new messages will be read from
     * @param callback The callback object whose method is called after receiving new messages
     */
    public DataTransmission(@NonNull OutputStream outputStream, @NonNull InputStream inputStream,
                            @Nullable OnReceiveCallback callback) {
        this.outputStream = new DataOutputStream(outputStream);
        this.inputStream = new DataInputStream(inputStream);
        this.callback = callback;
    }

    /**
     * Create a new asynchronous data transfer object
     * @param outputStream Stream where new messages will be written
     * @param inputStream Stream where new messages will be read from
     */
    public DataTransmission(@NonNull OutputStream outputStream, @NonNull InputStream inputStream) {
        this(outputStream, inputStream, true);
    }

    /**
     * Create a new data transfer object
     * @param outputStream Stream where new messages will be written
     * @param inputStream Stream where new messages will be read from
     * @param async Whether the transfer should be asynchronous or synchronous
     */
    public DataTransmission(@NonNull OutputStream outputStream, @NonNull InputStream inputStream, boolean async) {
        this(outputStream, inputStream, null);
        this.async = async;
    }

    /**
     * Set custom callback for receiving message.
     *
     * @param callback Callback that wants to receive message.
     */
    public void setCallback(OnReceiveCallback callback) {
        this.callback = callback;
    }

    /**
     * Send text message
     *
     * @param text Text to be sent
     * @throws IOException When it fails to send the message
     * @see #sendMessage(byte[])
     * @see #sendAndReceiveMessage(byte[])
     */
    public void sendMessage(String text) throws IOException {
        if (text != null) {
            sendMessage(text.getBytes());
        }
    }

    /**
     * Send message as as bytes
     *
     * @param messageBytes Bytes to be sent
     * @throws IOException When it fails to send the message
     * @see #sendMessage(String)
     * @see #sendAndReceiveMessage(byte[])
     */
    public void sendMessage(byte[] messageBytes) throws IOException {
        if (messageBytes != null) {
            outputStream.writeInt(messageBytes.length);
            outputStream.write(messageBytes);
            outputStream.flush();
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
        int len = inputStream.readInt();
        byte[] bytes = new byte[len];
        inputStream.readFully(bytes, 0, len);
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
     * @param token    Token supplied by server or client based
     * @param isServer Whether the supplied token is from server (<code>true</code>) or client (<code>false</code>)
     * @throws IOException              When it fails to verify the token
     * @throws ProtocolVersionException When the {@link #PROTOCOL_VERSION} mismatch occurs
     */
    public void shakeHands(String token, boolean isServer) throws IOException {
        if (token == null) {
            return;
        }
        if (isServer) {
            FLog.log("DataTransmission#shakeHands: Token: " + token + ", Server protocol: " + PROTOCOL_VERSION);
            String auth = new String(readMessage());  // <protocol-version>,<token>
            FLog.log("Received authentication: " + auth);
            String[] split = auth.split(",");
            String clientToken = split[1];
            // Match tokens
            if (TextUtils.equals(token, clientToken)) {
                // Connection is authorised
                FLog.log("DataTransmission#shakeHands: Authentication successful.");
            } else {
                FLog.log("DataTransmission#shakeHands: Authentication failed.");
                throw new RuntimeException("Unauthorized client, token: " + token);
            }
            // Check protocol version
            String protocolVersion = split[0];
            if (!TextUtils.equals(protocolVersion, PROTOCOL_VERSION)) {
                throw new ProtocolVersionException("Client protocol version: " + protocolVersion + ", " +
                        "Server protocol version: " + PROTOCOL_VERSION);
            }
        } else {  // Client
            Log.e("DataTransmission", "shakeHands: Token: " + token + ", Client protocol: " + PROTOCOL_VERSION);
            sendMessage(PROTOCOL_VERSION + "," + token);
        }
    }

    /**
     * Handle for messages received. For asynchronous operations or when the socket is not active,
     * nothing is done. But when server is running {@link #onReceiveMessage(byte[])} is called.
     * @throws IOException When it fails to read the message received
     */
    public void handleReceive() throws IOException {
        if (!async) return;
        while (running) onReceiveMessage(readMessage());
    }

    /**
     * Calls the callback function {@link OnReceiveCallback#onMessage(byte[])}.
     * @param bytes Bytes that was received earlier
     */
    private void onReceiveMessage(byte[] bytes) {
        if (callback != null) {
            callback.onMessage(bytes);
        }
    }

    /**
     * Stop data transmission, called when socket connection is being closed
     */
    public void stop() {
        running = false;
        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (inputStream != null) {
                inputStream.close();
            }
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
         * @param bytes The message that was received
         */
        void onMessage(byte[] bytes);
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
