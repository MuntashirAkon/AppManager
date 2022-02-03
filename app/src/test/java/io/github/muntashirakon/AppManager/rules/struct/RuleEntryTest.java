// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import org.junit.Test;

import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.rules.RuleType;

import static org.junit.Assert.assertEquals;

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
        assertEquals(PACKAGE_NAME + "\t.provider\tPROVIDER\tfalse", rule.flattenToString(true));
        assertEquals(".provider\tPROVIDER\tfalse", rule.flattenToString(false));
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
        RuleEntry rule = new MagiskHideRule(PACKAGE_NAME, "pkg:process", true);
        assertEquals(PACKAGE_NAME + "\tpkg:process\tMAGISK_HIDE\ttrue", rule.flattenToString(true));
        assertEquals("pkg:process\tMAGISK_HIDE\ttrue", rule.flattenToString(false));
    }

    @Test
    public void flattenMagiskDenyListToString() {
        RuleEntry rule = new MagiskDenyListRule(PACKAGE_NAME, "pkg:process", true);
        assertEquals(PACKAGE_NAME + "\tpkg:process\tMAGISK_DENY_LIST\ttrue", rule.flattenToString(true));
        assertEquals("pkg:process\tMAGISK_DENY_LIST\ttrue", rule.flattenToString(false));
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
                ComponentRule.COMPONENT_TO_BE_BLOCKED_IFW_DISABLE);
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
        RuleEntry rule = new MagiskHideRule(PACKAGE_NAME, PACKAGE_NAME, true);
        // Old
        assertEquals(RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\tSTUB\tMAGISK_HIDE\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\tSTUB\tMAGISK_HIDE\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, "STUB\tMAGISK_HIDE\ttrue", false), rule);
    }

    @Test
    public void unflattenMagiskHideFromStringNew() {
        RuleEntry rule = new MagiskHideRule(PACKAGE_NAME, "pkg:process", true);
        assertEquals(RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\tpkg:process\tMAGISK_HIDE\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\tpkg:process\tMAGISK_HIDE\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, "pkg:process\tMAGISK_HIDE\ttrue", false), rule);
    }

    @Test
    public void unflattenMagiskDenyListFromString() {
        RuleEntry rule = new MagiskDenyListRule(PACKAGE_NAME, "pkg:process", true);
        assertEquals(RuleEntry.unflattenFromString(null, PACKAGE_NAME + "\tpkg:process\tMAGISK_DENY_LIST\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, PACKAGE_NAME + "\tpkg:process\tMAGISK_DENY_LIST\ttrue", true), rule);
        assertEquals(RuleEntry.unflattenFromString(PACKAGE_NAME, "pkg:process\tMAGISK_DENY_LIST\ttrue", false), rule);
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
}