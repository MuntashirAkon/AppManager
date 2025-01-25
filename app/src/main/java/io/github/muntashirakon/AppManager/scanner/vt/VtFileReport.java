// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.muntashirakon.AppManager.scanner.vt;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;

public class VtFileReport {
    @NonNull
    public final ArrayList<VtAvEngineResult> results;
    @NonNull
    public final VtAvEngineStats stats;
    @NonNull
    public final String scanId;
    public final long scanDate;
    @NonNull
    public final String permalink;

    public VtFileReport(@NonNull JSONObject jsonObject) throws JSONException {
        // Doc: https://docs.virustotal.com/reference/files
        // Currently, we are only interested in data.attributes.last_analysis_date,
        // data.attributes.last_analysis_stats, data.attributes.last_analysis_results.
        JSONObject data = jsonObject.getJSONObject("data");
        assert data.getString("type").equals("file");
        scanId = data.getString("id");
        permalink = VirusTotal.getPermalink(scanId);
        JSONObject attrs = data.getJSONObject("attributes");
        scanDate = attrs.optLong("last_analysis_date") * 1_000;
        stats = new VtAvEngineStats(attrs.getJSONObject("last_analysis_stats"));
        JSONObject jsonResults = attrs.getJSONObject("last_analysis_results");
        Iterator<String> avEnginesIt = jsonResults.keys();
        results = new ArrayList<>();
        while (avEnginesIt.hasNext()) {
            results.add(new VtAvEngineResult(jsonResults.getJSONObject(avEnginesIt.next())));
        }
    }

    public boolean hasReport() {
        return scanDate != 0;
    }

    public int getTotal() {
        return stats.getTotal();
    }

    public Integer getPositives() {
        return stats.getDetected();
    }

    @NonNull
    @Override
    public String toString() {
        return "VtFileReport{" +
                "results=" + results +
                ", stats=" + stats +
                ", scanId='" + scanId + '\'' +
                ", scanDate=" + scanDate +
                ", permalink='" + permalink + '\'' +
                '}';
    }
}