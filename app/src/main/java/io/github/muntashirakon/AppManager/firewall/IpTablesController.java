/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.firewall;

import java.util.Locale;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.runner.Runner;

/**
 * iptables -N am_wifi_reject_list iptables -N am_mobile_reject_list iptables -N am_bw
 * <p>
 * iptables -A INPUT -j am_bw iptables -A OUTPUT -j am_bw
 * <p>
 * iptables -A am_bw -o wlan+ -j am_wifi_reject_list iptables -A am_bw -o tiwlan+ -j am_wifi_reject_list
 * <p>
 * iptables -A am_bw -o rmnet+ -j am_mobile_reject_list
 * iptables -A am_bw -o rmnet_data+ -j am_mobile_reject_list
 * iptables -A am_bw -o pdp+ -j am_mobile_reject_list
 * iptables -A am_bw -o ppp+ -j am_mobile_reject_list
 * iptables -A am_bw -o uwbr+ -j am_mobile_reject_list
 * iptables -A am_bw -o wimax+ -j am_mobile_reject_list
 * iptables -A am_bw -o vsnet+ -j am_mobile_reject_list
 * iptables -A am_bw -o ccmni+ -j am_mobile_reject_list
 * iptables -A am_bw -o eth_x+ -j am_mobile_reject_list
 * <p>
 * iptables -A am_wifi_reject_list -m owner --uid-owner 10255 -j REJECT --reject-with icmp-port-unreachable
 * <p>
 * iptables -D am_wifi_reject_list -m owner --uid-owner 10255 -j REJECT --reject-with icmp-port-unreachable
 * <p>
 */

class IpTablesController {
    private static final String RULE_REJECT = " -m owner --uid-owner %d -j REJECT --reject-with icmp-port-unreachable";

    private static final String BW_CHAIN = "am_bw";
    private static final String MOBILE_DATA = "am_mobile_reject_list";
    private static final String WIFI_DATA = "am_wifi_reject_list";

    private static final String[] INIT_CHAIN;
    private static final String[] CHAIN_NAMES = {"-N " + MOBILE_DATA, "-N " + WIFI_DATA, "-N " + BW_CHAIN};

    static {
        INIT_CHAIN = new String[]{
                "INPUT -j " + BW_CHAIN,
                "OUTPUT -j " + BW_CHAIN,
                BW_CHAIN + " -o wlan+ -j " + WIFI_DATA,
                BW_CHAIN + " -o tiwlan+ -j " + WIFI_DATA,
                BW_CHAIN + " -o rmnet+ -j " + MOBILE_DATA,
                BW_CHAIN + " -o rmnet_data+ -j " + MOBILE_DATA,
                BW_CHAIN + " -o pdp+ -j " + MOBILE_DATA,
                BW_CHAIN + " -o ppp+ -j " + MOBILE_DATA,
                BW_CHAIN + " -o uwbr+ -j " + MOBILE_DATA,
                BW_CHAIN + " -o wimax+ -j " + MOBILE_DATA,
                BW_CHAIN + " -o vsnet+ -j " + MOBILE_DATA,
                BW_CHAIN + " -o ccmni+ -j " + MOBILE_DATA,
                BW_CHAIN + " -o eth_x+ -j " + MOBILE_DATA,
        };
    }

    IpTablesController() {
        Runner.Result result = runIptablesCmd("-C INPUT -j " + BW_CHAIN);
        if (!result.isSuccessful()) {
            //
            for (String chainName : CHAIN_NAMES) {
                runIptablesCmd(chainName);
            }

            for (String s : INIT_CHAIN) {
                //first delete
                runIptablesCmd("-D " + s);
                //second add
                runIptablesCmd("-A " + s);
            }
        }

        try {
            saveIptables();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private boolean check(String rejectList, int uid) {
        Runner.Result result = runIptablesCmd(
                " -C " + String.format(Locale.ENGLISH, rejectList + RULE_REJECT, uid));
        System.out.println("check  " + result.getOutput() + "   " + result.getExitCode());
        return result.isSuccessful();
    }

    @NonNull
    private Runner.Result runIptablesCmd(String cmd) {
        return Runner.runCommand("iptables " + cmd);
    }

    boolean isMobileDataEnable(int uid) {
        return !check(MOBILE_DATA, uid);
    }

    boolean isWifiDataEnable(int uid) {
        return !check(WIFI_DATA, uid);
    }

    void setMobileData(int uid, boolean enable) {
        change(MOBILE_DATA, uid, enable);
    }

    void setWifiData(int uid, boolean enable) {
        change(WIFI_DATA, uid, enable);
    }

    private void change(String list, int uid, boolean enable) {
        if (enable) {
            // Accept connections (delete rules)
            runIptablesCmd("-D " + String.format(Locale.ROOT, list + RULE_REJECT, uid));
        } else {
            // Reject connection (add rules)
            if (!check(list, uid)) {
                runIptablesCmd("-A " + String.format(Locale.ROOT, list + RULE_REJECT, uid));
            }
        }
    }

    private boolean saveIptables() {
        System.out.println("saveIptables  -->>> ");
        Runner.Result result = Runner.runCommand("iptables-save");
        return result.isSuccessful();
    }

    private void restoreIptables(String rules) {
        // TODO: 28/1/21
    }
}
