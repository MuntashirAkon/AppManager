// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.app.ActivityManager;
import android.content.ComponentName;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.io.IoUtils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class ActivityManagerCompatTest {
    private final ClassLoader classLoader = getClass().getClassLoader();

    @Test
    public void parseRunningAppProcesses() throws IOException {
        assert classLoader != null;
        File dumpSysFile = new File(classLoader.getResource("dumpsys_app_processes.txt").getFile());
        List<ActivityManager.RunningAppProcessInfo> expectedRunningAppProcesses = new ArrayList<>(3);
        expectedRunningAppProcesses.add(new ActivityManager.RunningAppProcessInfo() {
            {
                processName = "com.qualcomm.qti.services.systemhelper:systemhelper_service";
                pid = 22828;
                uid = 10213;
                pkgList = new String[]{"com.qualcomm.qti.services.systemhelper"};
            }
        });
        expectedRunningAppProcesses.add(new ActivityManager.RunningAppProcessInfo() {
            {
                processName = "org.mozilla.fenix:tab6";
                pid = 13763;
                uid = 10262;
                pkgList = new String[]{"org.mozilla.fenix"};
            }
        });
        expectedRunningAppProcesses.add(new ActivityManager.RunningAppProcessInfo() {
            {
                processName = "system";
                pid = 1879;
                uid = 1000;
                pkgList = new String[]{"com.android.networkstack.inprocess", "android", "com.android.service.settingsobserver", "com.android.providers.settings", "com.android.server.telecom", "com.android.networkstack.tethering.inprocess"};
            }
        });
        Collections.sort(expectedRunningAppProcesses, (o1, o2) -> Integer.compare(o1.pid, o2.pid));
        try (InputStream is = new FileInputStream(dumpSysFile)) {
            byte[] allBytes = IoUtils.readFully(is, -1, true);
            String s = new String(allBytes);
            List<String> sysDump = Arrays.asList(s.split("\n"));
            List<ActivityManager.RunningAppProcessInfo> actualRunningAppProcess = ActivityManagerCompat.parseRunningAppProcesses(sysDump);
            Collections.sort(actualRunningAppProcess, (o1, o2) -> Integer.compare(o1.pid, o2.pid));
            assertEquals(expectedRunningAppProcesses.size(), actualRunningAppProcess.size());
            for (int i = 0; i < expectedRunningAppProcesses.size(); ++i) {
                ActivityManager.RunningAppProcessInfo expected = expectedRunningAppProcesses.get(i);
                ActivityManager.RunningAppProcessInfo actual = actualRunningAppProcess.get(i);
                assertEquals(expected.processName, actual.processName);
                assertEquals(expected.uid, actual.uid);
                assertEquals(expected.pid, actual.pid);
                assertArrayEquals(expected.pkgList, actual.pkgList);
            }
        }
    }

    @Test
    public void parseRunningServices() throws IOException {
        assert classLoader != null;
        File dumpSysFile = new File(classLoader.getResource("dumpsys_services.txt").getFile());
        List<ActivityManager.RunningServiceInfo> expectedRunningServices = new ArrayList<>(3);
        expectedRunningServices.add(new ActivityManager.RunningServiceInfo() {
            {
                service = ComponentName.unflattenFromString("org.bromite.bromite/org.chromium.content.app.SandboxedProcessService0");
                process = "org.bromite.bromite:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0:1";
                uid = 10143;
                pid = 6072;
                clientCount = 1;
                activeSince = 86235304L;
                lastActivityTime = 51116115L;
            }
        });
        expectedRunningServices.add(new ActivityManager.RunningServiceInfo() {
            {
                service = ComponentName.unflattenFromString("org.bromite.bromite/org.chromium.content.app.SandboxedProcessService0");
                process = "org.bromite.bromite:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0:2";
                uid = 10143;
                pid = 6082;
                clientCount = 2;
                activeSince = 86235636L;
                lastActivityTime = 52902756L;
            }
        });
        expectedRunningServices.add(new ActivityManager.RunningServiceInfo() {
            {
                service = ComponentName.unflattenFromString("org.bromite.bromite/org.chromium.content.app.PrivilegedProcessService0");
                process = "org.bromite.bromite:privileged_process0";
                uid = 10143;
                pid = 4604;
                clientCount = 2;
                activeSince = 84180869L;
                lastActivityTime = 50297542L;
            }
        });
        Collections.sort(expectedRunningServices, (o1, o2) -> Integer.compare(o1.pid, o2.pid));
        try (InputStream is = new FileInputStream(dumpSysFile)) {
            byte[] allBytes = IoUtils.readFully(is, -1, true);
            String s = new String(allBytes);
            List<String> sysDump = Arrays.asList(s.split("\n"));
            List<ActivityManager.RunningServiceInfo> actualRunningServices = ActivityManagerCompat.parseRunningServices(sysDump);
            Collections.sort(actualRunningServices, (o1, o2) -> Integer.compare(o1.pid, o2.pid));
            assertEquals(expectedRunningServices.size(), actualRunningServices.size());
            for (int i = 0; i < expectedRunningServices.size(); ++i) {
                // Currently, on service, process, UID, PID should be matched
                ActivityManager.RunningServiceInfo expected = expectedRunningServices.get(i);
                ActivityManager.RunningServiceInfo actual = actualRunningServices.get(i);
                assertEquals(expected.service, actual.service);
                assertEquals(expected.process, actual.process);
                assertEquals(expected.uid, actual.uid);
                assertEquals(expected.pid, actual.pid);
            }
        }
    }
}