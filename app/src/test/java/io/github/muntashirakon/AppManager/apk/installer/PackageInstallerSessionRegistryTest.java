// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 27)
public class PackageInstallerSessionRegistryTest {
    private Context mContext;
    private SharedPreferences mPreferences;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        mPreferences = PackageInstallerSessionRegistry.getPreferences(mContext);
        mPreferences.edit().clear().commit();
    }

    @After
    public void tearDown() {
        mPreferences.edit().clear().commit();
    }

    @Test
    public void cleanupAbandonsOnlyLiveSessionsFromAnOlderProcess() {
        mPreferences.edit()
                .putString(PackageInstallerSessionRegistry.KEY_PREFIX + 41, "old-process")
                .putString(PackageInstallerSessionRegistry.KEY_PREFIX + 42, "old-process")
                .putString(PackageInstallerSessionRegistry.KEY_PREFIX + 43,
                        PackageInstallerSessionRegistry.getProcessToken())
                .commit();
        Set<Integer> abandoned = new HashSet<>();

        PackageInstallerSessionRegistry.cleanupOrphanedSessions(mContext,
                new PackageInstallerSessionRegistry.SessionBackend() {
                    @Override
                    public Set<Integer> getSessionIds() {
                        return new HashSet<>(Arrays.asList(41, 43));
                    }

                    @Override
                    public void abandonSession(int sessionId) {
                        abandoned.add(sessionId);
                    }
                });

        assertEquals(Collections.singleton(41), abandoned);
        assertFalse(mPreferences.contains(PackageInstallerSessionRegistry.KEY_PREFIX + 41));
        assertFalse(mPreferences.contains(PackageInstallerSessionRegistry.KEY_PREFIX + 42));
        assertTrue(mPreferences.contains(PackageInstallerSessionRegistry.KEY_PREFIX + 43));
    }

    @Test
    public void failedAbandonmentRemainsTrackedForTheNextCleanup() {
        String key = PackageInstallerSessionRegistry.KEY_PREFIX + 51;
        mPreferences.edit().putString(key, "old-process").commit();
        AtomicInteger attempts = new AtomicInteger();

        PackageInstallerSessionRegistry.cleanupOrphanedSessions(mContext,
                new PackageInstallerSessionRegistry.SessionBackend() {
                    @Override
                    public Set<Integer> getSessionIds() {
                        return Collections.singleton(51);
                    }

                    @Override
                    public void abandonSession(int sessionId) throws Exception {
                        attempts.incrementAndGet();
                        throw new Exception("still active");
                    }
                });

        assertEquals(1, attempts.get());
        assertTrue(mPreferences.contains(key));
    }
}
