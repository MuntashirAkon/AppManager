// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.scanner.vt;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

public class VtFileScanMeta {
    @SerializedName("scan_id")
    private String mScanId;
    @SerializedName("sha1")
    private String mSha1;
    @SerializedName("resource")
    private String mResource;
    @SerializedName("response_code")
    private int mResponseCode;
    @SerializedName("sha256")
    private String mSha256;
    @SerializedName("permalink")
    private String mPermalink;
    @SerializedName("md5")
    private String mMd5;
    @SerializedName("verbose_msg")
    private String mVerboseMessage;
    @SerializedName("scan_date")
    private String mScanDate;

    public String getScanId() {
        return mScanId;
    }

    public String getSha1() {
        return mSha1;
    }

    public String getResource() {
        return mResource;
    }

    public int getResponseCode() {
        return mResponseCode;
    }

    public String getSha256() {
        return mSha256;
    }

    public String getPermalink() {
        return mPermalink;
    }

    public String getMd5() {
        return mMd5;
    }

    public String getVerboseMessage() {
        return mVerboseMessage;
    }

    public String getScanDate() {
        return mScanDate;
    }

    @NonNull
    @Override
    public String toString() {
        return "VtFileScanMeta{" +
                "scanId='" + mScanId + '\'' +
                ", sha1='" + mSha1 + '\'' +
                ", resource='" + mResource + '\'' +
                ", responseCode=" + mResponseCode +
                ", sha256='" + mSha256 + '\'' +
                ", permalink='" + mPermalink + '\'' +
                ", md5='" + mMd5 + '\'' +
                ", verboseMessage='" + mVerboseMessage + '\'' +
                ", scanDate='" + mScanDate + '\'' +
                '}';
    }
}