/*
 * Copyright (c) 2020, Muntashir Al-Islam
 * Copyright (c) 2015, Jared Rummler
 * Copyright (c) 2015, Liu Dong
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package net.dongliu.apk.parser.bean;

import java.util.Date;

import androidx.annotation.NonNull;

public class CertificateMeta {

    public static Builder newCertificateMeta() {
        return new Builder();
    }

    /**
     * The sign algorithm name
     */
    public final String signAlgorithm;

    /**
     * <p>The signature algorithm OID string.</p>
     *
     * <p>An OID is represented by a set of non-negative whole numbers separated by periods.</p>
     *
     * <p>For example, the string "1.2.840.10040.4.3" identifies the SHA-1 with DSA signature
     * algorithm defined in <a href="http://www.ietf.org/rfc/rfc3279.txt">RFC 3279: Algorithms and
     * Identifiers for the Internet X.509 Public Key Infrastructure Certificate and CRL
     * Profile</a>.</p>
     */
    public final String signAlgorithmOID;

    /**
     * The start date of the validity period.
     */
    public final Date startDate;

    /**
     * The end date of the validity period.
     */
    public final Date endDate;

    /**
     * Certificate binary data.
     */
    public final byte[] data;

    /**
     * First use base64 to encode certificate binary data, and then calculate md5 of base64b string.
     * some programs use this as the certMd5 of certificate
     */
    public final String certBase64Md5;

    /**
     * Use md5 to calculate certificate's certMd5.
     */
    public final String certMd5;

    private CertificateMeta(Builder builder) {
        this.signAlgorithm = builder.signAlgorithm;
        this.signAlgorithmOID = builder.signAlgorithmOID;
        this.startDate = builder.startDate;
        this.endDate = builder.endDate;
        this.data = builder.data;
        this.certBase64Md5 = builder.certBase64Md5;
        this.certMd5 = builder.certMd5;
    }

    @NonNull
    @Override
    public String toString() {
        return "signAlgorithm:\t" + signAlgorithm +
                "\ncertBase64Md5:\t" + certBase64Md5 +
                "\ncertMd5:\t" + certMd5;
    }

    public static final class Builder {

        private String signAlgorithm;
        private String signAlgorithmOID;
        private Date startDate;
        private Date endDate;
        private byte[] data;
        private String certBase64Md5;
        private String certMd5;

        public Builder() {
        }

        public CertificateMeta build() {
            return new CertificateMeta(this);
        }

        public Builder signAlgorithm(String signAlgorithm) {
            this.signAlgorithm = signAlgorithm;
            return this;
        }

        public Builder signAlgorithmOID(String signAlgorithmOID) {
            this.signAlgorithmOID = signAlgorithmOID;
            return this;
        }

        public Builder startDate(Date startDate) {
            this.startDate = startDate;
            return this;
        }

        public Builder endDate(Date endDate) {
            this.endDate = endDate;
            return this;
        }

        public Builder data(byte[] data) {
            this.data = data;
            return this;
        }

        public Builder certBase64Md5(String certBase64Md5) {
            this.certBase64Md5 = certBase64Md5;
            return this;
        }

        public Builder certMd5(String certMd5) {
            this.certMd5 = certMd5;
            return this;
        }
    }
}

