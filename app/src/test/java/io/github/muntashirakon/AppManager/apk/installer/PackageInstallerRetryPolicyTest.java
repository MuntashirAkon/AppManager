// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;

import android.content.pm.PackageInstaller;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 27)
public class PackageInstallerRetryPolicyTest {
    private static final String MIUI_FAILURE = "INSTALL_FAILED_ABORTED: Permission denied";
    private static final String HYPER_OS_FAILURE =
            "INSTALL_FAILED_HYPEROS_ISOLATION_VIOLATION: system package";

    @Test
    public void miuiRetriesUntilTheAttemptLimit() {
        for (int attempt = 1; attempt < PackageInstallerRetryPolicy.MAX_MIUI_ATTEMPTS; ++attempt) {
            assertEquals(PackageInstallerRetryPolicy.Action.RETRY,
                    getAction(MIUI_FAILURE, false, true, attempt));
        }
        assertEquals(PackageInstallerRetryPolicy.Action.FINISH,
                getAction(MIUI_FAILURE, false, true,
                        PackageInstallerRetryPolicy.MAX_MIUI_ATTEMPTS));
    }

    @Test
    public void miuiRetryRequiresAffectedRomAndUnprivilegedInstaller() {
        assertEquals(PackageInstallerRetryPolicy.Action.FINISH,
                getAction(MIUI_FAILURE, false, false, 1));
        assertEquals(PackageInstallerRetryPolicy.Action.FINISH,
                getAction(MIUI_FAILURE, true, true, 1));
    }

    @Test
    public void hyperOsRetriesWithShellInstallerUntilTheAttemptLimit() {
        for (int attempt = 1; attempt < PackageInstallerRetryPolicy.MAX_HYPER_OS_ATTEMPTS; ++attempt) {
            assertEquals(PackageInstallerRetryPolicy.Action.RETRY_WITH_SHELL_INSTALLER,
                    getAction(HYPER_OS_FAILURE, true, false, attempt));
        }
        assertEquals(PackageInstallerRetryPolicy.Action.FINISH,
                getAction(HYPER_OS_FAILURE, true, false,
                        PackageInstallerRetryPolicy.MAX_HYPER_OS_ATTEMPTS));
    }

    @Test
    public void retryOptionsDoNotMutateCallerSnapshot() {
        InstallerOptions callerOptions = InstallerOptions.getDefault();
        callerOptions.setInstallerName("original.installer");
        InstallerOptions retryOptions = InstallerOptions.copyOf(callerOptions);

        PackageInstallerCompat.applyRetryOptions(
                PackageInstallerRetryPolicy.Action.RETRY_WITH_SHELL_INSTALLER, retryOptions);

        assertEquals("original.installer", callerOptions.getInstallerName());
        assertEquals("com.android.shell", retryOptions.getInstallerName());
    }

    @Test
    public void successfulAttemptAlwaysFinishes() {
        assertEquals(PackageInstallerRetryPolicy.Action.FINISH,
                PackageInstallerRetryPolicy.getAction(PackageInstaller.STATUS_SUCCESS,
                        MIUI_FAILURE, false, true, 1));
    }

    private static PackageInstallerRetryPolicy.Action getAction(String message, boolean privileged,
                                                                 boolean affectedMiui,
                                                                 int attemptNumber) {
        return PackageInstallerRetryPolicy.getAction(PackageInstaller.STATUS_FAILURE_ABORTED,
                message, privileged, affectedMiui, attemptNumber);
    }
}
