// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.runner.Runner;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 27, shadows = FileUtilsTest.ShadowRunner.class)
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

    @Test
    public void copyReportsProgressWithinLargerOperation() throws Exception {
        byte[] first = new byte[1 << 20];
        byte[] second = new byte[1 << 20];
        long totalSize = first.length + second.length;
        RecordingProgressHandler progressHandler = new RecordingProgressHandler();

        long firstCopied = FileUtils.copy(new ByteArrayInputStream(first), new ByteArrayOutputStream(),
                totalSize, 0, progressHandler);
        FileUtils.copy(new ByteArrayInputStream(second), new ByteArrayOutputStream(),
                totalSize, firstCopied, progressHandler);

        assertFalse(progressHandler.updates.isEmpty());
        float previous = 0;
        for (float update : progressHandler.updates) {
            assertTrue(update >= previous);
            previous = update;
        }
        assertEquals(100f, previous, 0f);
    }

    private static class RecordingProgressHandler extends ProgressHandler {
        final List<Float> updates = new ArrayList<>();
        private int mMax;
        private float mProgress;

        @Override
        public void onAttach(@Nullable android.app.Service service, @NonNull Object message) {
        }

        @Override
        public void onProgressStart(int max, float current, @Nullable Object message) {
            onProgressUpdate(max, current, message);
        }

        @Override
        public void onProgressUpdate(int max, float current, @Nullable Object message) {
            mMax = max;
            mProgress = current;
            updates.add(current);
        }

        @Override
        public void onResult(@Nullable Object message) {
        }

        @Override
        public void onDetach(@Nullable android.app.Service service) {
        }

        @NonNull
        @Override
        public ProgressHandler newSubProgressHandler() {
            return this;
        }

        @Nullable
        @Override
        public Object getLastMessage() {
            return null;
        }

        @Override
        public int getLastMax() {
            return mMax;
        }

        @Override
        public float getLastProgress() {
            return mProgress;
        }
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
