// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server;

import android.net.LocalServerSocket;
import android.net.LocalSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.server.common.DataTransmission;
import io.github.muntashirakon.AppManager.server.common.FLog;

// Copyright 2017 Zheng Li
class Server {
    private boolean running = true;
    @NonNull
    private final IServer server;
    private DataTransmission dataTransmission;
    private final DataTransmission.OnReceiveCallback callback;
    @NonNull
    private final String token;
    boolean runInBackground = false;

    /**
     * Constructor for starting a local server
     *
     * @param name              Socket address
     * @param token             Token for handshaking
     * @param onReceiveCallback Callback for sending message (received by the calling class)
     * @throws IOException On failing to create a socket connection
     */
    Server(String name, @NonNull String token, DataTransmission.OnReceiveCallback onReceiveCallback)
            throws IOException {
        this.server = new LocalServerImpl(name);
        this.token = token;
        this.callback = onReceiveCallback;
    }

    /**
     * Constructor for starting a local server
     *
     * @param port              Port number
     * @param token             Token for handshaking
     * @param onReceiveCallback Callback for sending message (received by the calling class)
     * @throws IOException On failing to create a socket connection
     */
    Server(int port, @NonNull String token, DataTransmission.OnReceiveCallback onReceiveCallback)
            throws IOException {
        this.server = new NetSocketServerImpl(port);
        this.token = token;
        this.callback = onReceiveCallback;
    }

    /**
     * Run the server
     *
     * @throws IOException When server has failed to shake hands or the connection cannot be made
     */
    void run() throws IOException, RuntimeException {
        while (running) {
            try {
                // Allow only one client
                server.accept();
                // Prepare input and output streams for data interchange
                dataTransmission = new DataTransmission(server.getOutputStream(), server.getInputStream(), callback);
                // Handshake: check if tokens matched
                dataTransmission.shakeHands(token, true);
                // Send broadcast message to the system that the server has connected
                LifecycleAgent.onConnected();
                // Handle the data received initially from the client
                dataTransmission.handleReceive();
            } catch (DataTransmission.ProtocolVersionException e) {
                FLog.log(e);
                throw e;
            } catch (IOException e) {
                FLog.log(e);
                FLog.log("Run in background: " + runInBackground);
                // Send broadcast message to the system that the server has disconnected
                LifecycleAgent.onDisconnected();
                // Throw exception only when run in background is not requested
                if (!runInBackground) {
                    running = false;
                    throw e;
                }
            } catch (RuntimeException e) {
                FLog.log(e);
                // Send broadcast message to the system that the server has disconnected
                LifecycleAgent.onDisconnected();
                // Re-throw the exception
                runInBackground = false;
                running = false;
                throw e;
            }
        }
    }

    public void sendResult(byte[] bytes) throws IOException {
        if (running && dataTransmission != null) {
            LifecycleAgent.serverRunInfo.txBytes += bytes.length;
            dataTransmission.sendMessage(bytes);
        }
    }

    public void setStop() {
        running = false;
        if (dataTransmission != null) {
            dataTransmission.stop();
        }
        try {
            server.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     *
     */
    private interface IServer {
        InputStream getInputStream() throws IOException;

        OutputStream getOutputStream() throws IOException;

        void accept() throws IOException;

        void close() throws IOException;
    }

    private static class LocalServerImpl implements IServer {
        private final LocalServerSocket serverSocket;
        private LocalSocket socket;

        public LocalServerImpl(String name) throws IOException {
            this.serverSocket = new LocalServerSocket(name);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return socket.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return socket.getOutputStream();
        }

        @Override
        public void accept() throws IOException {
            socket = serverSocket.accept();
        }

        @Override
        public void close() throws IOException {
            socket.close();
            serverSocket.close();
        }
    }

    private static class NetSocketServerImpl implements IServer {
        private final ServerSocket serverSocket;
        private Socket socket;

        public NetSocketServerImpl(int port) throws IOException {
            this.serverSocket = new ServerSocket(port);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return socket.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return socket.getOutputStream();
        }

        @Override
        public void accept() throws IOException {
            socket = serverSocket.accept();
        }

        @Override
        public void close() throws IOException {
            socket.close();
            serverSocket.close();
        }
    }
}
