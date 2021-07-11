// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import io.github.muntashirakon.AppManager.rules.struct.AppOpRule;
import io.github.muntashirakon.AppManager.rules.struct.BatteryOptimizationRule;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.rules.struct.MagiskHideRule;
import io.github.muntashirakon.AppManager.rules.struct.NetPolicyRule;
import io.github.muntashirakon.AppManager.rules.struct.NotificationListenerRule;
import io.github.muntashirakon.AppManager.rules.struct.PermissionRule;
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry;
import io.github.muntashirakon.AppManager.rules.struct.SsaidRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class PseudoRulesTest {
    private static final String PACKAGE_NAME = "sample.package";

    private PseudoRules rules;

    @Before
    public void setUp() {
        rules = new PseudoRules(PACKAGE_NAME, 0);
    }

    @Test
    public void uniquenessOfActivitiesTest() {
        rules.setComponent(".activity", RuleType.ACTIVITY, ComponentRule.COMPONENT_BLOCKED);
        rules.setComponent(".activity", RuleType.ACTIVITY, ComponentRule.COMPONENT_TO_BE_UNBLOCKED);
        assertEquals(1, rules.getAll().size());
        assertNotEquals(new ComponentRule(PACKAGE_NAME, ".activity", RuleType.ACTIVITY,
                ComponentRule.COMPONENT_BLOCKED), rules.getAll().get(0));
        assertEquals(new ComponentRule(PACKAGE_NAME, ".activity", RuleType.ACTIVITY,
                ComponentRule.COMPONENT_TO_BE_UNBLOCKED), rules.getAll().get(0));
    }

    @Test
    public void uniquenessOfProvidersTest() {
        rules.setComponent(".activity", RuleType.PROVIDER, ComponentRule.COMPONENT_BLOCKED);
        rules.setComponent(".activity", RuleType.PROVIDER, ComponentRule.COMPONENT_TO_BE_UNBLOCKED);
        assertEquals(1, rules.getAll().size());
        assertNotEquals(new ComponentRule(PACKAGE_NAME, ".activity", RuleType.PROVIDER,
                ComponentRule.COMPONENT_BLOCKED), rules.getAll().get(0));
        assertEquals(new ComponentRule(PACKAGE_NAME, ".activity", RuleType.PROVIDER,
                ComponentRule.COMPONENT_TO_BE_UNBLOCKED), rules.getAll().get(0));
    }

    @Test
    public void uniquenessOfServicesTest() {
        rules.setComponent(".activity", RuleType.SERVICE, ComponentRule.COMPONENT_BLOCKED);
        rules.setComponent(".activity", RuleType.SERVICE, ComponentRule.COMPONENT_TO_BE_UNBLOCKED);
        assertEquals(1, rules.getAll().size());
        assertNotEquals(new ComponentRule(PACKAGE_NAME, ".activity", RuleType.SERVICE,
                ComponentRule.COMPONENT_BLOCKED), rules.getAll().get(0));
        assertEquals(new ComponentRule(PACKAGE_NAME, ".activity", RuleType.SERVICE,
                ComponentRule.COMPONENT_TO_BE_UNBLOCKED), rules.getAll().get(0));
    }

    @Test
    public void uniquenessOfReceiversTest() {
        rules.setComponent(".activity", RuleType.RECEIVER, ComponentRule.COMPONENT_BLOCKED);
        rules.setComponent(".activity", RuleType.RECEIVER, ComponentRule.COMPONENT_TO_BE_UNBLOCKED);
        assertEquals(1, rules.getAll().size());
        assertNotEquals(new ComponentRule(PACKAGE_NAME, ".activity", RuleType.RECEIVER,
                ComponentRule.COMPONENT_BLOCKED), rules.getAll().get(0));
        assertEquals(new ComponentRule(PACKAGE_NAME, ".activity", RuleType.RECEIVER,
                ComponentRule.COMPONENT_TO_BE_UNBLOCKED), rules.getAll().get(0));
    }

    @Test
    public void uniquenessOfAppOpsTest() {
        rules.setAppOp(55, 3);
        rules.setAppOp(55, 0);
        assertEquals(1, rules.getAll().size());
        assertNotEquals(new AppOpRule(PACKAGE_NAME, 55, 3), rules.getAll().get(0));
        assertEquals(new AppOpRule(PACKAGE_NAME, 55, 0), rules.getAll().get(0));
    }

    @Test
    public void uniquenessOfPermissionsTest() {
        rules.setPermission(".perm", true, 32);
        rules.setPermission(".perm", false, 4);
        assertEquals(1, rules.getAll().size());
        assertNotEquals(new PermissionRule(PACKAGE_NAME, ".perm", true, 32), rules.getAll().get(0));
        assertEquals(new PermissionRule(PACKAGE_NAME, ".perm", false, 4), rules.getAll().get(0));
    }

    @Test
    public void uniquenessOfMagiskHideTest() {
        rules.setMagiskHide(false);
        rules.setMagiskHide(true);
        assertEquals(1, rules.getAll().size());
        assertNotEquals(new MagiskHideRule(PACKAGE_NAME, false), rules.getAll().get(0));
        assertEquals(new MagiskHideRule(PACKAGE_NAME, true), rules.getAll().get(0));
    }

    @Test
    public void uniquenessOfBatteryOptimizationTest() {
        rules.setBatteryOptimization(false);
        rules.setBatteryOptimization(true);
        assertEquals(1, rules.getAll().size());
        assertNotEquals(new BatteryOptimizationRule(PACKAGE_NAME, false), rules.getAll().get(0));
        assertEquals(new BatteryOptimizationRule(PACKAGE_NAME, true), rules.getAll().get(0));
    }

    @Test
    public void uniquenessOfNetPolicyTest() {
        rules.setNetPolicy(4);
        rules.setNetPolicy(1 << 16);
        assertEquals(1, rules.getAll().size());
        assertNotEquals(new NetPolicyRule(PACKAGE_NAME, 4), rules.getAll().get(0));
        assertEquals(new NetPolicyRule(PACKAGE_NAME, 1 << 16), rules.getAll().get(0));
    }

    @Test
    public void uniquenessOfNotificationTest() {
        rules.setNotificationListener(".notif", true);
        rules.setNotificationListener(".notif", false);
        assertEquals(1, rules.getAll().size());
        assertNotEquals(new NotificationListenerRule(PACKAGE_NAME, ".notif", true), rules.getAll().get(0));
        assertEquals(new NotificationListenerRule(PACKAGE_NAME, ".notif", false), rules.getAll().get(0));
    }

    @Test
    public void uniquenessOfSsaidTest() {
        rules.setSsaid("bc9948c6");
        rules.setSsaid("f6740c90");
        assertEquals(1, rules.getAll().size());
        assertNotEquals(new SsaidRule(PACKAGE_NAME, "bc9948c6"), rules.getAll().get(0));
        assertEquals(new SsaidRule(PACKAGE_NAME, "f6740c90"), rules.getAll().get(0));
    }

    @Test
    public void interUniquenessTest() {
        rules.setComponent(".component", RuleType.ACTIVITY, ComponentRule.COMPONENT_BLOCKED);
        rules.setComponent(".component", RuleType.PROVIDER, ComponentRule.COMPONENT_BLOCKED);
        rules.setComponent(".component", RuleType.SERVICE, ComponentRule.COMPONENT_BLOCKED);
        rules.setComponent(".component", RuleType.RECEIVER, ComponentRule.COMPONENT_BLOCKED);
        rules.setPermission(".component", true, 4);
        rules.setNotificationListener(".component", true);
        List<RuleEntry> ruleEntries = rules.getAll();
        assertEquals(6, ruleEntries.size());
        assertEquals(new ComponentRule(PACKAGE_NAME, ".component", RuleType.ACTIVITY,
                ComponentRule.COMPONENT_BLOCKED), ruleEntries.get(0));
        assertEquals(new ComponentRule(PACKAGE_NAME, ".component", RuleType.PROVIDER,
                ComponentRule.COMPONENT_BLOCKED), ruleEntries.get(1));
        assertEquals(new ComponentRule(PACKAGE_NAME, ".component", RuleType.SERVICE,
                ComponentRule.COMPONENT_BLOCKED), ruleEntries.get(2));
        assertEquals(new ComponentRule(PACKAGE_NAME, ".component", RuleType.RECEIVER,
                ComponentRule.COMPONENT_BLOCKED), ruleEntries.get(3));
        assertEquals(new PermissionRule(PACKAGE_NAME, ".component", true, 4), ruleEntries.get(4));
        assertEquals(new NotificationListenerRule(PACKAGE_NAME, ".component", true), ruleEntries.get(5));
    }

    @Test
    public void interUniquenessStubTest() {
        rules.setComponent(RuleEntry.STUB, RuleType.ACTIVITY, ComponentRule.COMPONENT_BLOCKED);
        rules.setComponent(RuleEntry.STUB, RuleType.PROVIDER, ComponentRule.COMPONENT_BLOCKED);
        rules.setComponent(RuleEntry.STUB, RuleType.SERVICE, ComponentRule.COMPONENT_BLOCKED);
        rules.setComponent(RuleEntry.STUB, RuleType.RECEIVER, ComponentRule.COMPONENT_BLOCKED);
        rules.setPermission(RuleEntry.STUB, true, 4);
        rules.setNotificationListener(RuleEntry.STUB, true);
        rules.setNetPolicy(4);
        rules.setBatteryOptimization(true);
        rules.setMagiskHide(true);
        rules.setSsaid("bc9948c6");
        List<RuleEntry> ruleEntries = rules.getAll();
        assertEquals(10, ruleEntries.size());
        assertEquals(new ComponentRule(PACKAGE_NAME, RuleEntry.STUB, RuleType.ACTIVITY,
                ComponentRule.COMPONENT_BLOCKED), ruleEntries.get(0));
        assertEquals(new ComponentRule(PACKAGE_NAME, RuleEntry.STUB, RuleType.PROVIDER,
                ComponentRule.COMPONENT_BLOCKED), ruleEntries.get(1));
        assertEquals(new ComponentRule(PACKAGE_NAME, RuleEntry.STUB, RuleType.SERVICE,
                ComponentRule.COMPONENT_BLOCKED), ruleEntries.get(2));
        assertEquals(new ComponentRule(PACKAGE_NAME, RuleEntry.STUB, RuleType.RECEIVER,
                ComponentRule.COMPONENT_BLOCKED), ruleEntries.get(3));
        assertEquals(new PermissionRule(PACKAGE_NAME, RuleEntry.STUB, true, 4), ruleEntries.get(4));
        assertEquals(new NotificationListenerRule(PACKAGE_NAME, RuleEntry.STUB, true), ruleEntries.get(5));
        assertEquals(new NetPolicyRule(PACKAGE_NAME, 4), ruleEntries.get(6));
        assertEquals(new BatteryOptimizationRule(PACKAGE_NAME, true), ruleEntries.get(7));
        assertEquals(new MagiskHideRule(PACKAGE_NAME, true), ruleEntries.get(8));
        assertEquals(new SsaidRule(PACKAGE_NAME, "bc9948c6"), ruleEntries.get(9));
    }

    @After
    public void tearDown() {
        rules.setReadOnly();
        rules.close();
    }
}