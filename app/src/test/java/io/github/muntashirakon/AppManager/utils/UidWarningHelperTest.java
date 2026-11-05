// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Looper;
import android.os.UserHandleHidden;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.compat.ProcessCompat;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class UidWarningHelperTest {

    @Test
    public void testGetActivity() {
        assertNull(UidWarningHelper.getActivity(null));
        assertNull(UidWarningHelper.getActivity(ContextUtils.getContext()));

        Activity activity = new Activity();
        assertSame(activity, UidWarningHelper.getActivity(activity));

        ContextWrapper singleWrapper = new ContextWrapper(activity);
        assertSame(activity, UidWarningHelper.getActivity(singleWrapper));

        ContextWrapper doubleWrapper = new ContextWrapper(singleWrapper);
        assertSame(activity, UidWarningHelper.getActivity(doubleWrapper));
    }

    @Test
    public void testIsSystemUid() {
        // Negative / Invalid UIDs
        assertFalse(UidWarningHelper.isSystemUid(-1));
        assertFalse(UidWarningHelper.isSystemUid(-100));

        // Core system UIDs (AppId < 10000)
        assertTrue(UidWarningHelper.isSystemUid(0)); // root
        assertTrue(UidWarningHelper.isSystemUid(1000)); // system (shared by framework, settings, etc.)
        assertTrue(UidWarningHelper.isSystemUid(1001)); // phone
        assertTrue(UidWarningHelper.isSystemUid(1027)); // nfc
        assertTrue(UidWarningHelper.isSystemUid(ProcessCompat.FIRST_APPLICATION_UID - 1));

        // Normal Application UIDs (AppId >= 10000)
        assertFalse(UidWarningHelper.isSystemUid(ProcessCompat.FIRST_APPLICATION_UID));
        assertFalse(UidWarningHelper.isSystemUid(10001));
        assertFalse(UidWarningHelper.isSystemUid(10150));

        // Multi-user system UIDs (e.g. User 10, UID 1000 -> 1001000)
        int user10SystemUid = UserHandleHidden.getUid(10, 1000);
        assertTrue(UidWarningHelper.isSystemUid(user10SystemUid));

        // Multi-user app UIDs (e.g. User 10, UID 10050 -> 1010050)
        int user10AppUid = UserHandleHidden.getUid(10, 10050);
        assertFalse(UidWarningHelper.isSystemUid(user10AppUid));
    }

    @Test
    public void testWarnIfSharedUid_negativeUidExecutesImmediately() {
        boolean[] executed = new boolean[]{false};
        Context context = ContextUtils.getContext();
        UidWarningHelper.warnIfSharedUid(context, -1, () -> executed[0] = true);
        assertTrue(executed[0]);
    }

    @Test
    public void testWarnIfSharedUids_emptyListExecutesImmediately() {
        boolean[] executed = new boolean[]{false};
        Context context = ContextUtils.getContext();
        UidWarningHelper.warnIfSharedUids(context, Collections.emptyList(), () -> executed[0] = true);
        assertTrue(executed[0]);
    }

    @Test
    public void testWarnIfSharedUid_singlePackageProceeds() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] executed = new boolean[]{false};
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        UidWarningHelper.warnIfSharedUid(activity, 10000, null, () -> {
            executed[0] = true;
            latch.countDown();
        }, null);
        for (int i = 0; i < 10; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (latch.await(100, TimeUnit.MILLISECONDS)) break;
        }
        assertTrue(executed[0]);
    }

    @Test
    public void testWarnIfSharedUids_nonSharedPackagesProceeds() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] executed = new boolean[]{false};
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        UidWarningHelper.warnIfSharedUids(activity, Collections.singletonList("com.example.app"), null, () -> {
            executed[0] = true;
            latch.countDown();
        }, null);
        for (int i = 0; i < 10; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (latch.await(100, TimeUnit.MILLISECONDS)) break;
        }
        assertTrue(executed[0]);
    }

    @Test
    public void testWarnIfSharedUids_withUserIdsProceeds() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] executed = new boolean[]{false};
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        UidWarningHelper.warnIfSharedUids(activity, Collections.singletonList("com.example.app"),
                Collections.singletonList(10), () -> {
                    executed[0] = true;
                    latch.countDown();
                }, null);
        for (int i = 0; i < 10; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (latch.await(100, TimeUnit.MILLISECONDS)) break;
        }
        assertTrue(executed[0]);
    }

    @Test
    public void testWarnIfSharedUid_nonActivityContextProceedsDirectly() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] executed = new boolean[]{false};
        Context context = ContextUtils.getContext(); // Application Context
        UidWarningHelper.warnIfSharedUid(context, 1000, null, () -> {
            executed[0] = true;
            latch.countDown();
        }, null);
        for (int i = 0; i < 10; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (latch.await(100, TimeUnit.MILLISECONDS)) break;
        }
        assertTrue(executed[0]);
    }

    @Test
    public void testWarnIfSharedUid_destroyedActivityDoesNotProceed() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] executed = new boolean[]{false};
        Activity activity = Robolectric.buildActivity(Activity.class).create().destroy().get();
        UidWarningHelper.warnIfSharedUid(activity, 10000, null, () -> {
            executed[0] = true;
            latch.countDown();
        }, null);
        for (int i = 0; i < 10; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (latch.await(50, TimeUnit.MILLISECONDS)) break;
        }
        assertFalse(executed[0]);
    }
}
