// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server;

import android.net.LocalServerSocket;
import android.net.LocalSocket;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import io.github.muntashirakon.AppManager.server.common.DataTransmission;
import io.github.muntashirakon.AppManager.server.common.FLog;

// Copyright 2017 Zheng Li
class Server implements Closeable {
    @NonNull
    private final LifecycleAgent mLifecycleAgent;
    @NonNull
    private final IServer mServer;
    @NonNull
    private final String mToken;
    @Nullable
    private final DataTransmission.OnReceiveCallback mOnReceiveCallback;

    private DataTransmission mDataTransmission;
    private boolean mRunning = true;
    boolean mRunInBackground = false;

    /**
     * Constructor for starting a local server
     *
     * @param name              Socket address
     * @param token             Token for handshaking
     * @param onReceiveCallback Callback for sending message (received by the calling class)
     * @throws IOException On failing to create a socket connection
     */
    Server(String name, @NonNull String token, @NonNull LifecycleAgent lifecycleAgent,
           @Nullable DataTransmission.OnReceiveCallback onReceiveCallback)
            throws IOException {
        mToken = token;
        mLifecycleAgent = lifecycleAgent;
        mServer = new LocalServerImpl(name);
        mOnReceiveCallback = onReceiveCallback;
    }

    /**
     * Constructor for starting a local server
     *
     * @param port              Port number
     * @param token             Token for handshaking
     * @param onReceiveCallback Callback for sending message (received by the calling class)
     * @throws IOException On failing to create a socket connection
     */
    Server(int port, @NonNull String token, @NonNull LifecycleAgent lifecycleAgent,
           @Nullable DataTransmission.OnReceiveCallback onReceiveCallback)
            throws IOException {
        mToken = token;
        mLifecycleAgent = lifecycleAgent;
        mServer = new NetSocketServerImpl(port);
        mOnReceiveCallback = onReceiveCallback;
    }

    /**
     * Run the server
     *
     * @throws IOException When server has failed to shake hands or the connection cannot be made
     */
    void run() throws IOException, RuntimeException {
        while (mRunning) {
            try {
                // Allow only one client
                mServer.accept();
                // Prepare input and output streams for data interchange
                mDataTransmission = new DataTransmission(mServer.getOutputStream(), mServer.getInputStream(),
                        mOnReceiveCallback);
                // Handshake: check if tokens matched
                mDataTransmission.shakeHands(mToken, DataTransmission.Role.Server);
                // Send broadcast message to the system that the server has connected
                mLifecycleAgent.onConnected();
                // Handle the data received initially from the client
                mDataTransmission.handleReceive();
            } catch (DataTransmission.ProtocolVersionException e) {
                FLog.log(e);
                throw e;
            } catch (IOException e) {
                FLog.log(e);
                FLog.log("Run in background: " + mRunInBackground);
                // Send broadcast message to the system that the server has disconnected
                mLifecycleAgent.onDisconnected();
                // Throw exception only when run in background is not requested
                if (!mRunInBackground) {
                    mRunning = false;
                    throw e;
                }
            } catch (RuntimeException e) {
                FLog.log(e);
                // Send broadcast message to the system that the server has disconnected
                mLifecycleAgent.onDisconnected();
                // Re-throw the exception
                mRunInBackground = false;
                mRunning = false;
                throw e;
            }
        }
    }

    public void sendResult(byte[] bytes) throws IOException {
        if (mRunning && mDataTransmission != null) {
            LifecycleAgent.sServerInfo.txBytes += bytes.length;
            mDataTransmission.sendMessage(bytes);
        }
    }

    @Override
    public void close() throws IOException {
        mRunning = false;
        if (mDataTransmission != null) {
            mDataTransmission.close();
        }
        mServer.close();
    }

    private interface IServer extends Closeable {
        InputStream getInputStream() throws IOException;

        OutputStream getOutputStream() throws IOException;

        void accept() throws IOException;

        @Override
        void close() throws IOException;
    }

    private static class LocalServerImpl implements IServer {
        private final LocalServerSocket mServerSocket;
        private LocalSocket mLocalSocket;

        public LocalServerImpl(String name) throws IOException {
            mServerSocket = new LocalServerSocket(name);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return mLocalSocket.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return mLocalSocket.getOutputStream();
        }

        @Override
        public void accept() throws IOException {
            mLocalSocket = mServerSocket.accept();
        }

        @Override
        public void close() throws IOException {
            mLocalSocket.close();
            mServerSocket.close();
        }
    }

    private static class NetSocketServerImpl implements IServer {
        private final ServerSocket mServerSocket;
        private Socket mSocket;

        public NetSocketServerImpl(int port) throws IOException {
            mServerSocket = new ServerSocket(port);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return mSocket.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return mSocket.getOutputStream();
        }

        @Override
        public void accept() throws IOException {
            mSocket = mServerSocket.accept();
        }

        @Override
        public void close() throws IOException {
            mSocket.close();
            mServerSocket.close();
        }
    }
}
