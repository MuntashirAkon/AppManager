// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.scanner.vt;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

public class VtFileReportScanItem {
    @SerializedName("detected")
    private boolean detected;
    @SerializedName("version")
    private String version;
    @SerializedName("result")
    private String malware;
    @SerializedName("update")
    private String update;

    public boolean isDetected() {
        return detected;
    }

    public String getVersion() {
        return version;
    }

    public String getMalware() {
        return malware;
    }

    public String getUpdate() {
        return update;
    }

    @NonNull
    @Override
    public String toString() {
        return "(detected=" + detected +
                ", version=" + version +
                ", malware=" + malware +
                ", update=" + update + ')';
    }
}