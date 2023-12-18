// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self.life;

public class FundingCampaignChecker {
    private static final long FUNDING_CAMPAIGN_START = 1703160000000L;
    private static final long FUNDING_CAMPAIGN_END = 1711972800000L;

    public static boolean campaignRunning() {
        long currentTime = System.currentTimeMillis();
        return currentTime >= FUNDING_CAMPAIGN_START && currentTime <= FUNDING_CAMPAIGN_END;
    }
}
