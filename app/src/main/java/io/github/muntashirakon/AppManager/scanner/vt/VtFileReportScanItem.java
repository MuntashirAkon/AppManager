// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.scanner.vt;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

public class VtFileReportScanItem {
    @SerializedName("detected")
    private boolean mDetected;
    @SerializedName("version")
    private String mVersion;
    @SerializedName("result")
    private String mMalware;
    @SerializedName("update")
    private String mUpdate;

    public boolean isDetected() {
        return mDetected;
    }

    public String getVersion() {
        return mVersion;
    }

    public String getMalware() {
        return mMalware;
    }

    public String getUpdate() {
        return mUpdate;
    }

    @NonNull
    @Override
    public String toString() {
        return "(detected=" + mDetected +
                ", version=" + mVersion +
                ", malware=" + mMalware +
                ", update=" + mUpdate + ')';
    }
}