package net.dongliu.apk.parser.struct.signingv2;

import java.util.List;

import javax.security.cert.X509Certificate;

public class SignerBlock {
    private List<Digest> digests;
    private List<X509Certificate> certificates;
    private List<Signature> signatures;

    public SignerBlock(List<Digest> digests, List<X509Certificate> certificates, List<Signature> signatures) {
        this.digests = digests;
        this.certificates = certificates;
        this.signatures = signatures;
    }

    public List<Digest> getDigests() {
        return digests;
    }

    public List<X509Certificate> getCertificates() {
        return certificates;
    }

    public List<Signature> getSignatures() {
        return signatures;
    }
}
