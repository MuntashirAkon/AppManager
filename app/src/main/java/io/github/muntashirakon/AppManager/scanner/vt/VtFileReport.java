// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.scanner.vt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

import static java.util.Collections.unmodifiableMap;

public class VtFileReport {
    @SerializedName("scans")
    private Map<String, VtFileReportScanItem> mScans;
    @SerializedName("scan_id")
    private String mScanId;
    @SerializedName("sha1")
    private String mSha1;
    @SerializedName("resource")
    private String mResource;
    @SerializedName("response_code")
    private Integer mResponseCode;
    @SerializedName("scan_date")
    private String mScanDate;
    @SerializedName("permalink")
    private String mPermalink;
    @SerializedName("verbose_msg")
    private String mVerboseMessage;
    @SerializedName("total")
    private Integer mTotal;
    @SerializedName("positives")
    private Integer mPositives;
    @SerializedName("sha256")
    private String mSha256;
    @SerializedName("md5")
    private String mMd5;

    @Nullable
    public Map<String, VtFileReportScanItem> getScans() {
        if (mScans == null) return null;
        return unmodifiableMap(mScans);
    }

    @Nullable
    public String getScanId() {
        return mScanId;
    }

    @Nullable
    public String getSha1() {
        return mSha1;
    }

    public String getResource() {
        return mResource;
    }

    public Integer getResponseCode() {
        return mResponseCode;
    }

    @Nullable
    public String getScanDate() {
        return mScanDate;
    }

    @Nullable
    public String getPermalink() {
        return mPermalink;
    }

    public String getVerboseMessage() {
        return mVerboseMessage;
    }

    @Nullable
    public Integer getTotal() {
        return mTotal;
    }

    @Nullable
    public Integer getPositives() {
        return mPositives;
    }

    @Nullable
    public String getSha256() {
        return mSha256;
    }

    @Nullable
    public String getMd5() {
        return mMd5;
    }

    @NonNull
    @Override
    public String toString() {
        return "VtFileReport{" +
                "scans=" + mScans +
                ", scanId='" + mScanId + '\'' +
                ", sha1='" + mSha1 + '\'' +
                ", resource='" + mResource + '\'' +
                ", responseCode=" + mResponseCode +
                ", scanDate='" + mScanDate + '\'' +
                ", permalink='" + mPermalink + '\'' +
                ", verboseMessage='" + mVerboseMessage + '\'' +
                ", total=" + mTotal +
                ", positives=" + mPositives +
                ", sha256='" + mSha256 + '\'' +
                ", md5='" + mMd5 + '\'' +
                '}';
    }
}