// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Process;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.runner.Runner;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = FileUtilsTest.ShadowRunner.class)
public class FileUtilsTest {
    @Before
    public void setUp() {
        ShadowRunner.commands.clear();
    }

    @Test
    public void forceCreateExternalDataSubDirCreatesMissingPackageDirectory() {
        File packageDir = new File(RuntimeEnvironment.getApplication().getCacheDir(), "missing-package");
        File cacheDir = new File(packageDir, "cache");

        assertTrue(FileUtils.forceCreateExternalDataSubDir(cacheDir));

        String uid = String.valueOf(Process.myUid());
        assertEquals(4, ShadowRunner.commands.size());
        assertArrayEquals(new String[]{"mkdir", "-p", cacheDir.getAbsolutePath()}, ShadowRunner.commands.get(0));
        assertArrayEquals(new String[]{"chmod", "-R", "770", packageDir.getAbsolutePath()}, ShadowRunner.commands.get(1));
        assertArrayEquals(new String[]{"chown", "-R", uid + ":" + uid, packageDir.getAbsolutePath()},
                ShadowRunner.commands.get(2));
        assertArrayEquals(new String[]{"restorecon", "-R", packageDir.getAbsolutePath()}, ShadowRunner.commands.get(3));
    }

    @Implements(Runner.class)
    public static class ShadowRunner {
        static final List<String[]> commands = new ArrayList<>();

        @Implementation
        public static Runner.Result runCommand(String[] command) {
            commands.add(command);
            // restorecon is best-effort and should not make directory creation fail.
            return new Runner.Result("restorecon".equals(command[0]) ? 1 : 0);
        }
    }
}
