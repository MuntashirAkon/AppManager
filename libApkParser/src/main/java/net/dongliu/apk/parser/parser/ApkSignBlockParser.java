package net.dongliu.apk.parser.parser;

import net.dongliu.apk.parser.struct.signingv2.ApkSigningBlock;
import net.dongliu.apk.parser.struct.signingv2.Digest;
import net.dongliu.apk.parser.struct.signingv2.Signature;
import net.dongliu.apk.parser.struct.signingv2.SignerBlock;
import net.dongliu.apk.parser.utils.Buffers;
import net.dongliu.apk.parser.utils.Unsigned;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

// see https://source.android.com/security/apksigning/v2

/**
 * The Apk Sign Block V2 Parser.
 */
public class ApkSignBlockParser {
    private ByteBuffer data;

    public ApkSignBlockParser(ByteBuffer data) {
        this.data = data.order(ByteOrder.LITTLE_ENDIAN);
    }

    public ApkSigningBlock parse() throws CertificateException {
        // sign block found, read pairs
        List<SignerBlock> signerBlocks = new ArrayList<>();
        while (data.remaining() >= 8) {
            int id = data.getInt();
            int size = Unsigned.ensureUInt(data.getInt());
            if (id == ApkSigningBlock.SIGNING_V2_ID) {
                ByteBuffer signingV2Buffer = Buffers.sliceAndSkip(data, size);
                // now only care about apk signing v2 entry
                while (signingV2Buffer.hasRemaining()) {
                    SignerBlock signerBlock = readSigningV2(signingV2Buffer);
                    signerBlocks.add(signerBlock);
                }
            } else {
                // just ignore now
                Buffers.position(data, data.position() + size);
            }
        }
        return new ApkSigningBlock(signerBlocks);
    }

    private SignerBlock readSigningV2(ByteBuffer buffer) throws CertificateException {
        buffer = readLenPrefixData(buffer);

        ByteBuffer signedData = readLenPrefixData(buffer);
        ByteBuffer digestsData = readLenPrefixData(signedData);
        List<Digest> digests = readDigests(digestsData);
        ByteBuffer certificateData = readLenPrefixData(signedData);
        List<X509Certificate> certificates = readCertificates(certificateData);
        ByteBuffer attributesData = readLenPrefixData(signedData);
        readAttributes(attributesData);

        ByteBuffer signaturesData = readLenPrefixData(buffer);
        List<Signature> signatures = readSignatures(signaturesData);

        ByteBuffer publicKeyData = readLenPrefixData(buffer);
        return new SignerBlock(digests, certificates, signatures);
    }

    private List<Digest> readDigests(ByteBuffer buffer) {
        List<Digest> list = new ArrayList<>();
        while (buffer.hasRemaining()) {
            ByteBuffer digestData = readLenPrefixData(buffer);
            int algorithmID = digestData.getInt();
            byte[] digest = Buffers.readBytes(digestData);
            list.add(new Digest(algorithmID, digest));
        }
        return list;
    }

    private List<X509Certificate> readCertificates(ByteBuffer buffer) throws CertificateException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> certificates = new ArrayList<>();
        while (buffer.hasRemaining()) {
            ByteBuffer certificateData = readLenPrefixData(buffer);
            Certificate certificate = certificateFactory.generateCertificate(
                    new ByteArrayInputStream(Buffers.readBytes(certificateData)));
            certificates.add((X509Certificate) certificate);
        }
        return certificates;
    }

    private void readAttributes(ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
            ByteBuffer attributeData = readLenPrefixData(buffer);
            int id = attributeData.getInt();
//            byte[] value = Buffers.readBytes(attributeData);
        }
    }

    private List<Signature> readSignatures(ByteBuffer buffer) {
        List<Signature> signatures = new ArrayList<>();
        while (buffer.hasRemaining()) {
            ByteBuffer signatureData = readLenPrefixData(buffer);
            int algorithmID = signatureData.getInt();
            int signatureDataLen = Unsigned.ensureUInt(signatureData.getInt());
            byte[] signature = Buffers.readBytes(signatureData, signatureDataLen);
            signatures.add(new Signature(algorithmID, signature));
        }
        return signatures;
    }


    private ByteBuffer readLenPrefixData(ByteBuffer buffer) {
        int len = Unsigned.ensureUInt(buffer.getInt());
        return Buffers.sliceAndSkip(buffer, len);
    }

    // 0x0101—RSASSA-PSS with SHA2-256 digest, SHA2-256 MGF1, 32 bytes of salt, trailer: 0xbc
    private static final int PSS_SHA_256 = 0x0101;
    // 0x0102—RSASSA-PSS with SHA2-512 digest, SHA2-512 MGF1, 64 bytes of salt, trailer: 0xbc
    private static final int PSS_SHA_512 = 0x0102;
    // 0x0103—RSASSA-PKCS1-v1_5 with SHA2-256 digest. This is for build systems which require deterministic signatures.
    private static final int PKCS1_SHA_256 = 0x0103;
    // 0x0104—RSASSA-PKCS1-v1_5 with SHA2-512 digest. This is for build systems which require deterministic signatures.
    private static final int PKCS1_SHA_512 = 0x0104;
    // 0x0201—ECDSA with SHA2-256 digest
    private static final int ECDSA_SHA_256 = 0x0201;
    // 0x0202—ECDSA with SHA2-512 digest
    private static final int ECDSA_SHA_512 = 0x0202;
    // 0x0301—DSA with SHA2-256 digest
    private static final int DSA_SHA_256 = 0x0301;

}
