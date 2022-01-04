// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.scanner.vt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

import static java.util.Collections.unmodifiableMap;

public class VtFileReport {
    @SerializedName("scans")
    private Map<String, VtFileReportScanItem> scans;
    @SerializedName("scan_id")
    private String scanId;
    @SerializedName("sha1")
    private String sha1;
    @SerializedName("resource")
    private String resource;
    @SerializedName("response_code")
    private Integer responseCode;
    @SerializedName("scan_date")
    private String scanDate;
    @SerializedName("permalink")
    private String permalink;
    @SerializedName("verbose_msg")
    private String verboseMessage;
    @SerializedName("total")
    private Integer total;
    @SerializedName("positives")
    private Integer positives;
    @SerializedName("sha256")
    private String sha256;
    @SerializedName("md5")
    private String md5;

    @Nullable
    public Map<String, VtFileReportScanItem> getScans() {
        if (scans == null) return null;
        return unmodifiableMap(scans);
    }

    @Nullable
    public String getScanId() {
        return scanId;
    }

    @Nullable
    public String getSha1() {
        return sha1;
    }

    public String getResource() {
        return resource;
    }

    public Integer getResponseCode() {
        return responseCode;
    }

    @Nullable
    public String getScanDate() {
        return scanDate;
    }

    @Nullable
    public String getPermalink() {
        return permalink;
    }

    public String getVerboseMessage() {
        return verboseMessage;
    }

    @Nullable
    public Integer getTotal() {
        return total;
    }

    @Nullable
    public Integer getPositives() {
        return positives;
    }

    @Nullable
    public String getSha256() {
        return sha256;
    }

    @Nullable
    public String getMd5() {
        return md5;
    }

    @NonNull
    @Override
    public String toString() {
        return "VtFileReport{" +
                "scans=" + scans +
                ", scanId='" + scanId + '\'' +
                ", sha1='" + sha1 + '\'' +
                ", resource='" + resource + '\'' +
                ", responseCode=" + responseCode +
                ", scanDate='" + scanDate + '\'' +
                ", permalink='" + permalink + '\'' +
                ", verboseMessage='" + verboseMessage + '\'' +
                ", total=" + total +
                ", positives=" + positives +
                ", sha256='" + sha256 + '\'' +
                ", md5='" + md5 + '\'' +
                '}';
    }
}