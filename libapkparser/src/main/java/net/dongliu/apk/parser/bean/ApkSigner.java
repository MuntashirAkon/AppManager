package net.dongliu.apk.parser.bean;

import androidx.annotation.NonNull;

import static java.util.Objects.requireNonNull;

/**
 * ApkSignV1 certificate file.
 */
public class ApkSigner {
    /**
     * The cert file path in apk file
     */
    private String path;
    /**
     * The meta info of certificate contained in this cert file.
     */
    private CertificateMeta certificateMeta;

    public ApkSigner(String path, @NonNull CertificateMeta certificateMeta) {
        this.path = path;
        this.certificateMeta = certificateMeta;
    }

    public String getPath() {
        return path;
    }

    public CertificateMeta getCertificateMeta() {
        return certificateMeta;
    }

    @NonNull
    @Override
    public String toString() {
        return "ApkSigner{" +
                "path='" + path + '\'' +
                ", certificateMetas=" + certificateMeta +
                '}';
    }
}
