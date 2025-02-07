// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.magisk.MagiskProcess;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;

@RunWith(RobolectricTestRunner.class)
public class RuleEntryTest {
    private static final String PACKAGE_NAME = "sample.package";

    @Test
    public void flattenActivityToString() {
        RuleEntry rule = new ComponentRule(PACKAGE_NAME, ".activity", RuleType.ACTIVITY,
                ComponentRule.COMPONENT_BLOCKED_IFW_DISABLE);
        assertEquals(PACKAGE_NAME + "\t.activity\tACTIVITY\ttrue", rule.flattenToString(true));
        assertEquals(".activity\tACTIVITY\ttrue", rule.flattenToString(false));
    }

    @Test
    public void flattenProviderToString() {
        RuleEntry rule = new ComponentRule(PACKAGE_NAME, ".provider", RuleType.PROVIDER,
                ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW_DISABLE);
        assertEquals(PACKAGE_NAME + "\t.provider\tPROVIDER\tdis_false", rule.flattenToString(true));
        assertEquals(".provider\tPROVIDER\tdis_false", rule.flattenToString(false));
    }

    @Test
    public void flattenReceiverToString() {
        RuleEntry rule = new ComponentRule(PACKAGE_NAME, ".receiver", RuleType.RECEIVER,
                ComponentRule.COMPONENT_TO_BE_DEFAULTED);
        assertEquals(PACKAGE_NAME + "\t.receiver\tRECEIVER\tunblocked", rule.flattenToString(true));
        assertEquals(".receiver\tRECEIVER\tunblocked", rule.flattenToString(false));
    }

    @Test
    public void flattenServiceToString() {
        RuleEntry rule = new ComponentRule(PACKAGE_NAME, ".service", RuleType.SERVICE,
                ComponentRule.COMPONENT_TO_BE_DEFAULTED);
        assertEquals(PACKAGE_NAME + "\t.service\tSERVICE\tunblocked", rule.flattenToString(true));
        assertEquals(".service\tSERVICE\tunblocked", rule.flattenToString(false));
    }

    @Test
    public void flattenAppOpToString() {
        RuleEntry rule = new AppOpRule(PACKAGE_NAME, 55, AppOpsManager.MODE_DEFAULT);
        assertEquals(PACKAGE_NAME + "\t55\tAPP_OP\t3", rule.flattenToString(true));
        assertEquals("55\tAPP_OP\t3", rule.flattenToString(false));
    }

    @Test
    public void flattenPermissionToString() {
        RuleEntry rule = new PermissionRule(PACKAGE_NAME, ".permission", true, 32);
        assertEquals(PACKAGE_NAME + "\t.permission\tPERMISSION\ttrue\t32", rule.flattenToString(true));
        assertEquals(".permission\tPERMISSION\ttrue\t32", rule.flattenToString(false));
    }

    @Test
    public void flattenMagiskHideToString() {
        MagiskProcess mp = new MagiskProcess(PACKAGE_NAME, "pkg:process");
        mp.setEnabled(true);
        RuleEntry rule = new MagiskHideRule(mp);
        assertEquals(PACKAGE_NAME + "\tpkg:process\tMAGISK_HIDE\ttrue\tfalse", rule.flattenToString(true));
        assertEquals("pkg:process\tMAGISK_HIDE\ttrue\tfalse", rule.flattenToString(false));
    }

    @Test
    public void flattenMagiskHideIsolatedToString() {
        MagiskProcess mp = new MagiskProcess(PACKAGE_NAME, "pkg:process");
        mp.setEnabled(true);
        mp.setIsolatedProcess(true);
        RuleEntry rule = new MagiskHideRule(mp);
        assertEquals(PACKAGE_NAME + "\tpkg:process\tMAGISK_HIDE\ttrue\ttrue", rule.flattenToString(true));
        assertEquals("pkg:process\tMAGISK_HIDE\ttrue\ttrue", rule.flattenToString(false));
    }

    @Test
    public void flattenMagiskDenyListToString() {
        MagiskProcess mp = new MagiskProcess(PACKAGE_NAME, "pkg:process");
        mp.setEnabled(true);
        RuleEntry rule = new MagiskDenyListRule(mp);
        assertEquals(PACKAGE_NAME + "\tpkg:process\tMAGISK_DENY_LIST\ttrue\tfalse", rule.flattenToString(true));
        assertEquals("pkg:process\tMAGISK_DENY_LIST\ttrue\tfalse", rule.flattenToString(false));
    }

    @Test
    public void flattenMagiskDenyListIsolatedToString() {
        MagiskProcess mp = new MagiskProcess(PACKAGE_NAME, "pkg:process");
        mp.setEnabled(true);
        mp.setIsolatedProcess(true);
        RuleEntry rule = new MagiskDenyListRule(mp);
        assertEquals(PACKAGE_NAME + "\tpkg:process\tMAGISK_DENY_LIST\ttrue\ttrue", rule.flattenToString(true));
        assertEquals("pkg:process\tMAGISK_DENY_LIST\ttrue\ttrue", rule.flattenToString(false));
    }

    @Test
    public void flattenBatteryOptToString() {
        RuleEntry rule = new BatteryOptimizationRule(PACKAGE_NAME, true);
        assertEquals(PACKAGE_NAME + "\tSTUB\tBATTERY_OPT\ttrue", rule.flattenToString(true));
        assertEquals("STUB\tBATTERY_OPT\ttrue", rule.flattenToString(false));
    }

    @Test
    public void flattenNetPolicyToString() {
        RuleEntry rule = new NetPolicyRule(PACKAGE_NAME, 4);
        assertEquals(PACKAGE_NAME + "\tSTUB\tNET_POLICY\t4", rule.flattenToString(true));
        assertEquals("STUB\tNET_POLICY\t4", rule.flattenToString(false));
    }

    @Test
    public void flattenNotificationListenerToString() {
        RuleEntry rule = new NotificationListenerRule(PACKAGE_NAME, ".notif", true);
        assertEquals(PACKAGE_NAME + "\t.notif\tNOTIFICATION\ttrue", rule.flattenToString(true));
        assertEquals(".notif\tNOTIFICATION\ttrue", rule.flattenToString(false));
    }

    @Test
    public void flattenSsaidToString() {
        RuleEntry rule = new SsaidRule(PACKAGE_NAME, "bc9948c6");
        assertEquals(PACKAGE_NAME + "\tSTUB\tSSAID\tbc9948c6", rule.flattenToString(true));
        assertEquals("STUB\tSSAID\tbc9948c6", rule.flattenToString(false));
    }

    @Test
    public void flattenFreezeToString() {
        RuleEntry rule = new FreezeRule(PACKAGE_NAME, FreezeUtils.FREEZE_DISABLE);
        assertEquals(PACKAGE_NAME + "\tSTUB\tFREEZE\t" + FreezeUtils.FREEZE_DISABLE, rule.flattenToString(true));
        assertEquals("STUB\tFREEZE\t" + FreezeUtils.FREEZE_DISABLE, rule.flattenToString(false));
    }

    @Test
    public void addPackageWithTab() {
        ComponentRule rule = new ComponentRule(PACKAGE_NAME, ".activity", RuleType.ACTIVITY,
                ComponentRule.COMPONENT_BLOCKED_IFW_DISABLE);
        assertEquals(PACKAGE_NAME + "\t", rule.addPackageWithTab(true));
        assertEquals("", rule.addPackageWithTab(false));
    }

    @Test
    public void unflattenActivityFromString() {
        RuleEntry rule = new ComponentRule(PACKAGE_NAME, ".activity", RuleType.ACTIVITY,
                ComponentRule.COMPONENT_BLOCKED_IFW_DISABLE);
        assertEquals(RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\t.activity\tACTIVITY\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\t.activity\tACTIVITY\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, ".activity\tACTIVITY\ttrue", false), rule);
    }

    @Test
    public void unflattenProviderFromString() {
        RuleEntry rule = new ComponentRule(PACKAGE_NAME, ".provider", RuleType.PROVIDER,
                ComponentRule.COMPONENT_TO_BE_DISABLED);
        assertEquals(RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\t.provider\tPROVIDER\tfalse", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\t.provider\tPROVIDER\tfalse", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, ".provider\tPROVIDER\tfalse", false), rule);
    }

    @Test
    public void unflattenReceiverFromString() {
        RuleEntry rule = new ComponentRule(PACKAGE_NAME, ".receiver", RuleType.RECEIVER,
                ComponentRule.COMPONENT_TO_BE_DEFAULTED);
        assertEquals(RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\t.receiver\tRECEIVER\tunblocked", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\t.receiver\tRECEIVER\tunblocked", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, ".receiver\tRECEIVER\tunblocked", false), rule);
    }

    @Test
    public void unflattenServiceFromString() {
        RuleEntry rule = new ComponentRule(PACKAGE_NAME, ".service", RuleType.SERVICE,
                ComponentRule.COMPONENT_TO_BE_DEFAULTED);
        assertEquals(RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\t.service\tSERVICE\tunblocked", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\t.service\tSERVICE\tunblocked", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, ".service\tSERVICE\tunblocked", false), rule);
    }

    @Test
    public void unflattenAppOpFromString() {
        RuleEntry rule = new AppOpRule(PACKAGE_NAME, 55, AppOpsManager.MODE_DEFAULT);
        assertEquals(RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\t55\tAPP_OP\t3", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\t55\tAPP_OP\t3", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, "55\tAPP_OP\t3", false), rule);
    }

    @Test
    public void unflattenPermissionFromString() {
        RuleEntry rule = new PermissionRule(PACKAGE_NAME, ".permission", true, 32);
        assertEquals(RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\t.permission\tPERMISSION\ttrue\t32", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\t.permission\tPERMISSION\ttrue\t32", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, ".permission\tPERMISSION\ttrue\t32", false), rule);
    }

    @Test
    public void unflattenPermissionWithoutFlagsFromString() {
        RuleEntry rule = new PermissionRule(PACKAGE_NAME, ".permission", true, 0);
        assertEquals(RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\t.permission\tPERMISSION\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\t.permission\tPERMISSION\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, ".permission\tPERMISSION\ttrue", false), rule);
    }

    @Test
    public void unflattenMagiskHideFromStringOld() {
        MagiskProcess mp = new MagiskProcess(PACKAGE_NAME, PACKAGE_NAME);
        mp.setEnabled(true);
        RuleEntry rule = new MagiskHideRule(mp);
        assertEquals(RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\tSTUB\tMAGISK_HIDE\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\tSTUB\tMAGISK_HIDE\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, "STUB\tMAGISK_HIDE\ttrue", false), rule);
    }

    @Test
    public void unflattenMagiskHideFromStringNew() {
        MagiskProcess mp = new MagiskProcess(PACKAGE_NAME, "pkg:process");
        mp.setEnabled(true);
        RuleEntry rule = new MagiskHideRule(mp);
        assertEquals(RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\tpkg:process\tMAGISK_HIDE\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\tpkg:process\tMAGISK_HIDE\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, "pkg:process\tMAGISK_HIDE\ttrue", false), rule);
    }

    @Test
    public void unflattenMagiskHideIsolatedFromString() {
        MagiskProcess mp = new MagiskProcess(PACKAGE_NAME, "pkg:process");
        mp.setEnabled(true);
        mp.setIsolatedProcess(true);
        RuleEntry rule = new MagiskHideRule(mp);
        assertEquals(RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\tpkg:process\tMAGISK_HIDE\ttrue\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\tpkg:process\tMAGISK_HIDE\ttrue\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, "pkg:process\tMAGISK_HIDE\ttrue\ttrue", false), rule);
    }

    @Test
    public void unflattenMagiskDenyListFromString() {
        MagiskProcess mp = new MagiskProcess(PACKAGE_NAME, "pkg:process");
        mp.setEnabled(true);
        RuleEntry rule = new MagiskDenyListRule(mp);
        assertEquals(RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\tpkg:process\tMAGISK_DENY_LIST\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\tpkg:process\tMAGISK_DENY_LIST\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, "pkg:process\tMAGISK_DENY_LIST\ttrue", false), rule);
    }

    @Test
    public void unflattenMagiskDenyListIsolatedFromString() {
        MagiskProcess mp = new MagiskProcess(PACKAGE_NAME, "pkg:process");
        mp.setEnabled(true);
        mp.setIsolatedProcess(true);
        RuleEntry rule = new MagiskDenyListRule(mp);
        assertEquals(RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\tpkg:process\tMAGISK_DENY_LIST\ttrue\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\tpkg:process\tMAGISK_DENY_LIST\ttrue\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, "pkg:process\tMAGISK_DENY_LIST\ttrue\ttrue", false), rule);
    }

    @Test
    public void unflattenMagiskDenyListZygoteFromString() {
        String processName = PACKAGE_NAME + "_zygote";
        MagiskProcess mp = new MagiskProcess(PACKAGE_NAME, processName);
        mp.setEnabled(true);
        mp.setIsolatedProcess(true);
        mp.setAppZygote(true);
        RuleEntry rule = new MagiskDenyListRule(mp);
        MagiskDenyListRule parsedRule = (MagiskDenyListRule) RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\t" + processName + "\tMAGISK_DENY_LIST\ttrue\ttrue", true);
        // Check if it automatically detects zygote process
        assertTrue(parsedRule.getMagiskProcess().isAppZygote());
        assertEquals(parsedRule, rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\t" + processName + "\tMAGISK_DENY_LIST\ttrue\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, processName + "\tMAGISK_DENY_LIST\ttrue\ttrue", false), rule);
    }

    @Test
    public void unflattenBatteryOptFromString() {
        RuleEntry rule = new BatteryOptimizationRule(PACKAGE_NAME, true);
        assertEquals(RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\tSTUB\tBATTERY_OPT\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\tSTUB\tBATTERY_OPT\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, "STUB\tBATTERY_OPT\ttrue", false), rule);
    }

    @Test
    public void unflattenNetPolicyFromString() {
        RuleEntry rule = new NetPolicyRule(PACKAGE_NAME, 4);
        assertEquals(RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\tSTUB\tNET_POLICY\t4", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\tSTUB\tNET_POLICY\t4", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, "STUB\tNET_POLICY\t4", false), rule);
    }

    @Test
    public void unflattenNotificationListenerFromString() {
        RuleEntry rule = new NotificationListenerRule(PACKAGE_NAME, ".notif", true);
        assertEquals(RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\t.notif\tNOTIFICATION\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\t.notif\tNOTIFICATION\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, ".notif\tNOTIFICATION\ttrue", false), rule);
    }

    @Test
    public void unflattenSsaidFromString() {
        RuleEntry rule = new SsaidRule(PACKAGE_NAME, "bc9948c6");
        assertEquals(RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\tSTUB\tSSAID\tbc9948c6", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\tSTUB\tSSAID\tbc9948c6", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, "STUB\tSSAID\tbc9948c6", false), rule);
    }

    @Test
    public void unflattenFreezeFromString() {
        RuleEntry rule = new FreezeRule(PACKAGE_NAME, FreezeUtils.FREEZE_DISABLE);
        assertEquals(RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\tSTUB\tFREEZE\t" + FreezeUtils.FREEZE_DISABLE, true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\tSTUB\tFREEZE\t" + FreezeUtils.FREEZE_DISABLE, true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, "STUB\tFREEZE\t" + FreezeUtils.FREEZE_DISABLE, false), rule);
    }
}