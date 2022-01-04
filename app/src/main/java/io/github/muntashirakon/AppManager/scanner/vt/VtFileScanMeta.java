// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.scanner.vt;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

public class VtFileScanMeta {
    @SerializedName("scan_id")
    private String scanId;
    @SerializedName("sha1")
    private String sha1;
    @SerializedName("resource")
    private String resource;
    @SerializedName("response_code")
    private int responseCode;
    @SerializedName("sha256")
    private String sha256;
    @SerializedName("permalink")
    private String permalink;
    @SerializedName("md5")
    private String md5;
    @SerializedName("verbose_msg")
    private String verboseMessage;
    @SerializedName("scan_date")
    private String scanDate;

    public String getScanId() {
        return scanId;
    }

    public String getSha1() {
        return sha1;
    }

    public String getResource() {
        return resource;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public String getSha256() {
        return sha256;
    }

    public String getPermalink() {
        return permalink;
    }

    public String getMd5() {
        return md5;
    }

    public String getVerboseMessage() {
        return verboseMessage;
    }

    public String getScanDate() {
        return scanDate;
    }

    @NonNull
    @Override
    public String toString() {
        return "VtFileScanMeta{" +
                "scanId='" + scanId + '\'' +
                ", sha1='" + sha1 + '\'' +
                ", resource='" + resource + '\'' +
                ", responseCode=" + responseCode +
                ", sha256='" + sha256 + '\'' +
                ", permalink='" + permalink + '\'' +
                ", md5='" + md5 + '\'' +
                ", verboseMessage='" + verboseMessage + '\'' +
                ", scanDate='" + scanDate + '\'' +
                '}';
    }
}