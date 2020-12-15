package net.dongliu.apk.parser.bean;

import java.util.List;

/**
 * ApkSignV1 certificate file.
 */
public class ApkV2Signer {
    /**
     * The meta info of certificate contained in this cert file.
     */
    private List<CertificateMeta> certificateMetas;

    public ApkV2Signer(List<CertificateMeta> certificateMetas) {
        this.certificateMetas = certificateMetas;
    }

    public List<CertificateMeta> getCertificateMetas() {
        return certificateMetas;
    }

}
