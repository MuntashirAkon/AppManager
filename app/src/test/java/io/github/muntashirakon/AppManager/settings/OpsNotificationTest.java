// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.res.Configuration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import io.github.muntashirakon.AppManager.adb.AdbPairingService;
import io.github.muntashirakon.AppManager.utils.Utils;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class OpsNotificationTest {
    private Application mApplication;
    private NotificationManager mNotificationManager;

    @Before
    public void setUp() {
        mApplication = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(mApplication.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_TYPE_MASK)
                | Configuration.UI_MODE_TYPE_NORMAL;
        mApplication.getResources().updateConfiguration(configuration,
                mApplication.getResources().getDisplayMetrics());
        mNotificationManager = mApplication.getSystemService(NotificationManager.class);
        shadowOf(mApplication).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        shadowOf(mNotificationManager).setNotificationsEnabled(true);
    }

    @Test
    public void pairingAllowsUsableNotificationChannel() {
        assertTrue(Ops.canUseAdbPairingNotification(mApplication));
    }

    @Test
    public void pairingRejectsBlockedNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(AdbPairingService.CHANNEL_ID,
                "ADB Pairing", NotificationManager.IMPORTANCE_NONE);
        mNotificationManager.createNotificationChannel(channel);

        assertFalse(Ops.canUseAdbPairingNotification(mApplication));
    }

    @Test
    public void pairingNotificationUnavailableInVrHeadsetMode() {
        Configuration configuration = new Configuration(mApplication.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_TYPE_MASK)
                | Configuration.UI_MODE_TYPE_VR_HEADSET;
        mApplication.getResources().updateConfiguration(configuration,
                mApplication.getResources().getDisplayMetrics());

        assertTrue(Utils.isVrHeadset(mApplication));
        assertFalse(Ops.isAdbPairingNotificationAvailable(mApplication));
    }
}
