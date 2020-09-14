package net.dongliu.apk.parser.bean;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * basic certificate info.
 *
 * @author dongliu
 */
public class CertificateMeta {

    /**
     * the sign algorithm name
     */
    private final String signAlgorithm;

    /**
     * the signature algorithm OID string.
     * An OID is represented by a set of non-negative whole numbers separated by periods.
     * For example, the string "1.2.840.10040.4.3" identifies the SHA-1 with DSA signature algorithm defined in
     * <a href="http://www.ietf.org/rfc/rfc3279.txt">
     * RFC 3279: Algorithms and Identifiers for the Internet X.509 Public Key Infrastructure Certificate and CRL Profile
     * </a>.
     */
    private final String signAlgorithmOID;

    /**
     * the start date of the validity period.
     */
    private final Date startDate;

    /**
     * the end date of the validity period.
     */
    private final Date endDate;

    /**
     * certificate binary data.
     */
    private final byte[] data;

    /**
     * first use base64 to encode certificate binary data, and then calculate md5 of base64b string.
     * some programs use this as the certMd5 of certificate
     */
    private final String certBase64Md5;

    /**
     * use md5 to calculate certificate's certMd5.
     */
    private final String certMd5;

    public CertificateMeta(String signAlgorithm, String signAlgorithmOID, Date startDate, Date endDate,
                           byte[] data, String certBase64Md5, String certMd5) {
        this.signAlgorithm = signAlgorithm;
        this.signAlgorithmOID = signAlgorithmOID;
        this.startDate = startDate;
        this.endDate = endDate;
        this.data = data;
        this.certBase64Md5 = certBase64Md5;
        this.certMd5 = certMd5;
    }

    public byte[] getData() {
        return data;
    }

    public String getCertBase64Md5() {
        return certBase64Md5;
    }

    public String getCertMd5() {
        return certMd5;
    }

    public String getSignAlgorithm() {
        return signAlgorithm;
    }

    public Date getStartDate() {
        return startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public String getSignAlgorithmOID() {
        return signAlgorithmOID;
    }

    @Override
    public String toString() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return "CertificateMeta{signAlgorithm=" + signAlgorithm + ", " +
                "certBase64Md5=" + certBase64Md5 + ", " +
                "startDate=" + df.format(startDate) + ", " + "endDate=" + df.format(endDate) + "}";
    }
}

