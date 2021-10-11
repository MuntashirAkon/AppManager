// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.adb;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.org.conscrypt.Conscrypt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;

// https://github.com/aosp-mirror/platform_system_core/blob/android-11.0.0_r1/adb/pairing_connection/pairing_connection.cpp
// Also based on Shizuku's implementation
@RequiresApi(Build.VERSION_CODES.R)
class PairingConnectionCtx implements AutoCloseable {
    public static final String TAG = PairingConnectionCtx.class.getSimpleName();

    public static final String EXPORTED_KEY_LABEL = "adb-label\u0000";
    public static final int EXPORT_KEY_SIZE = 64;

    private enum State {
        Ready,
        ExchangingMsgs,
        ExchangingPeerInfo,
        Stopped
    }

    enum Role {
        Client,
        Server,
    }

    private final String host;
    private final int port;
    private final byte[] pswd;
    private final PeerInfo peerInfo;
    private final SSLContext sslContext;
    private final Role role = Role.Client;

    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    private PairingAuthCtx pairingAuthCtx;
    private State state = State.Ready;

    public PairingConnectionCtx(@NonNull String host, int port, @NonNull byte[] pswd, @NonNull KeyPair keyPair)
            throws NoSuchAlgorithmException, KeyManagementException {
        this.host = host;
        this.port = port;
        this.pswd = pswd;
        this.peerInfo = new PeerInfo(PeerInfo.ADB_RSA_PUB_KEY, AdbCrypto.getAdbFormattedRsaPublicKey((RSAPublicKey)
                keyPair.getPublicKey(), "AppManager"));
        this.sslContext = AdbUtils.getSslContext(keyPair);
    }

    public void start() throws IOException {
        if (state != State.Ready) {
            throw new IOException("Connection is not ready yet.");
        }

        state = State.ExchangingMsgs;

        // Start worker
        setupTlsConnection();

        for (; ; ) {
            switch (state) {
                case ExchangingMsgs:
                    if (!doExchangeMsgs()) {
                        notifyResult();
                        throw new IOException("Exchanging message wasn't successful.");
                    }
                    state = State.ExchangingPeerInfo;
                    break;
                case ExchangingPeerInfo:
                    if (!doExchangePeerInfo()) {
                        notifyResult();
                        throw new IOException("Could not exchange peer info.");
                    }
                    notifyResult();
                    return;
                case Ready:
                case Stopped:
                    throw new IOException("Connection closed with errors.");
            }
        }
    }

    private void notifyResult() {
        state = State.Stopped;
    }

    private void setupTlsConnection() throws IOException {
        Socket socket;
        if (role == Role.Server) {
            SSLServerSocket sslServerSocket = (SSLServerSocket) sslContext.getServerSocketFactory().createServerSocket(port);
            socket = sslServerSocket.accept();
            // TODO: Write automated test scripts after removing Conscrypt dependency.
        } else { // role == Role.Client
            socket = new Socket(host, port);
        }
        socket.setTcpNoDelay(true);

        // We use custom SSLContext to allow any SSL certificates
        SSLSocket sslSocket = (SSLSocket) sslContext.getSocketFactory().createSocket(socket, host, port, true);
        sslSocket.startHandshake();
        Log.d(TAG, "Handshake succeeded.");

        inputStream = new DataInputStream(sslSocket.getInputStream());
        outputStream = new DataOutputStream(sslSocket.getOutputStream());

        // To ensure the connection is not stolen while we do the PAKE, append the exported key material from the
        // tls connection to the password.
        byte[] keyMaterial = exportKeyingMaterial(sslSocket, EXPORT_KEY_SIZE);
        byte[] passwordBytes = new byte[pswd.length + keyMaterial.length];
        System.arraycopy(pswd, 0, passwordBytes, 0, pswd.length);
        System.arraycopy(keyMaterial, 0, passwordBytes, pswd.length, keyMaterial.length);

        PairingAuthCtx pairingAuthCtx = PairingAuthCtx.createAlice(passwordBytes);
        if (pairingAuthCtx == null) {
            throw new IOException("Unable to create PairingAuthCtx.");
        }
        this.pairingAuthCtx = pairingAuthCtx;
    }

    private byte[] exportKeyingMaterial(SSLSocket sslSocket, int length) throws SSLException {
        // FIXME: Remove Conscrypt dependencies
        return Conscrypt.exportKeyingMaterial(sslSocket, EXPORTED_KEY_LABEL, null, length);
    }

    private void writeHeader(@NonNull PairingPacketHeader header, @NonNull byte[] payload) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(PairingPacketHeader.PAIRING_PACKET_HEADER_SIZE)
                .order(ByteOrder.BIG_ENDIAN);
        header.writeTo(buffer);

        outputStream.write(buffer.array());
        outputStream.write(payload);
    }

    @Nullable
    private PairingPacketHeader readHeader() throws IOException {
        byte[] bytes = new byte[PairingPacketHeader.PAIRING_PACKET_HEADER_SIZE];
        inputStream.readFully(bytes);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        return PairingPacketHeader.readFrom(buffer);
    }

    @NonNull
    private PairingPacketHeader createHeader(byte type, int payloadSize) {
        return new PairingPacketHeader(PairingPacketHeader.CURRENT_KEY_HEADER_VERSION, type, payloadSize);
    }

    private boolean checkHeaderType(byte expected, byte actual) {
        if (expected != actual) {
            Log.e(TAG, "Unexpected header type (expected=" + expected + " actual=" + actual + ")");
            return false;
        }
        return true;
    }

    private boolean doExchangeMsgs() throws IOException {
        byte[] msg = pairingAuthCtx.getMsg();

        PairingPacketHeader ourHeader = createHeader(PairingPacketHeader.SPAKE2_MSG, msg.length);
        // Write our SPAKE2 msg
        writeHeader(ourHeader, msg);

        // Read the peer's SPAKE2 msg header
        PairingPacketHeader theirHeader = readHeader();
        if (theirHeader == null || !checkHeaderType(PairingPacketHeader.SPAKE2_MSG, theirHeader.type)) return false;

        // Read the SPAKE2 msg payload and initialize the cipher for encrypting the PeerInfo and certificate.
        byte[] theirMsg = new byte[theirHeader.payloadSize];
        inputStream.readFully(theirMsg);

        try {
            return pairingAuthCtx.initCipher(theirMsg);
        } catch (Exception e) {
            Log.e(TAG, "Unable to initialize pairing cipher");
            return ExUtils.rethrowAsIOException(e);
        }
    }

    private boolean doExchangePeerInfo() throws IOException {
        // Encrypt PeerInfo
        ByteBuffer buffer = ByteBuffer.allocate(PeerInfo.MAX_PEER_INFO_SIZE).order(ByteOrder.BIG_ENDIAN);
        peerInfo.writeTo(buffer);
        byte[] outBuffer = pairingAuthCtx.encrypt(buffer.array());
        if (outBuffer == null) {
            Log.e(TAG, "Failed to encrypt peer info");
            return false;
        }

        // Write out the packet header
        PairingPacketHeader ourHeader = createHeader(PairingPacketHeader.PEER_INFO, outBuffer.length);
        // Write out the encrypted payload
        writeHeader(ourHeader, outBuffer);

        // Read in the peer's packet header
        PairingPacketHeader theirHeader = readHeader();
        if (theirHeader == null || !checkHeaderType(PairingPacketHeader.PEER_INFO, theirHeader.type)) return false;

        // Read in the encrypted peer certificate
        byte[] theirMsg = new byte[theirHeader.payloadSize];
        inputStream.readFully(theirMsg);

        // Try to decrypt the certificate
        byte[] decryptedMsg = pairingAuthCtx.decrypt(theirMsg);
        if (decryptedMsg == null) {
            Log.e(TAG, "Unsupported payload while decrypting peer info.");
            return false;
        }

        // The decrypted message should contain the PeerInfo.
        if (decryptedMsg.length != PeerInfo.MAX_PEER_INFO_SIZE) {
            Log.e(TAG, "Got size=" + decryptedMsg.length + " PeerInfo.size=" + PeerInfo.MAX_PEER_INFO_SIZE);
            return false;
        }

        PeerInfo theirPeerInfo = PeerInfo.readFrom(ByteBuffer.wrap(decryptedMsg));
        Log.d(TAG, theirPeerInfo.toString());
        return true;
    }

    @Override
    public void close() throws Exception {
        Arrays.fill(pswd, (byte) 0);
        FileUtils.closeQuietly(inputStream);
        FileUtils.closeQuietly(outputStream);
        if (state != State.Ready) {
            pairingAuthCtx.destroy();
        }
    }

    private static class PeerInfo {
        public static final int MAX_PEER_INFO_SIZE = 1 << 13;

        public static final byte ADB_RSA_PUB_KEY = 0;
        public static final byte ADB_DEVICE_GUID = 0;

        @NonNull
        public static PeerInfo readFrom(@NonNull ByteBuffer buffer) {
            byte type = buffer.get();
            byte[] data = new byte[MAX_PEER_INFO_SIZE - 1];
            buffer.get(data);
            return new PeerInfo(type, data);
        }

        private final byte type;
        private final byte[] data = new byte[MAX_PEER_INFO_SIZE - 1];

        public PeerInfo(byte type, byte[] data) {
            this.type = type;
            System.arraycopy(data, 0, this.data, 0, Math.min(data.length, MAX_PEER_INFO_SIZE - 1));
        }

        public void writeTo(@NonNull ByteBuffer buffer) {
            buffer.put(type).put(data);
        }

        @NonNull
        @Override
        public String toString() {
            return "PeerInfo{" +
                    "type=" + type +
                    ", data=" + Arrays.toString(data) +
                    '}';
        }
    }

    private static class PairingPacketHeader {
        public static final byte CURRENT_KEY_HEADER_VERSION = 1;
        public static final byte MIN_SUPPORTED_KEY_HEADER_VERSION = 1;
        public static final byte MAX_SUPPORTED_KEY_HEADER_VERSION = 1;

        public static final int MAX_PAYLOAD_SIZE = 2 * PeerInfo.MAX_PEER_INFO_SIZE;
        public static final byte PAIRING_PACKET_HEADER_SIZE = 6;

        public static final byte SPAKE2_MSG = 0;
        public static final byte PEER_INFO = 1;

        @Nullable
        public static PairingPacketHeader readFrom(@NonNull ByteBuffer buffer) {
            byte version = buffer.get();
            byte type = buffer.get();
            int payload = buffer.getInt();
            if (version < MIN_SUPPORTED_KEY_HEADER_VERSION || version > MAX_SUPPORTED_KEY_HEADER_VERSION) {
                Log.e(TAG, "PairingPacketHeader version mismatch (us=" + CURRENT_KEY_HEADER_VERSION
                        + " them=" + version + ")");
                return null;
            }
            if (type != SPAKE2_MSG && type != PEER_INFO) {
                Log.e(TAG, "Unknown PairingPacket type " + type);
                return null;
            }
            if (payload <= 0 || payload > MAX_PAYLOAD_SIZE) {
                Log.e(TAG, "header payload not within a safe payload size (size=" + payload + ")");
                return null;
            }
            return new PairingPacketHeader(version, type, payload);
        }

        private final byte version;
        private final byte type;
        private final int payloadSize;

        public PairingPacketHeader(byte version, byte type, int payloadSize) {
            this.version = version;
            this.type = type;
            this.payloadSize = payloadSize;
        }

        public void writeTo(@NonNull ByteBuffer buffer) {
            buffer.put(version).put(type).putInt(payloadSize);
        }

        @NonNull
        @Override
        public String toString() {
            return "PairingPacketHeader{" +
                    "version=" + version +
                    ", type=" + type +
                    ", payloadSize=" + payloadSize +
                    '}';
        }
    }
}
