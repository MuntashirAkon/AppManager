// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.content.pm.PackageInstaller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Retry policy for vendor package-installer behavior.
 *
 * <p>MIUI 12.5+ may require more than one attempt for an unprivileged APK
 * installation. HyperOS 2.0+ may require the installer of a system app to be another
 * system app, so the retry uses {@code com.android.shell}. These workarounds apply only
 * to APK installation, not uninstallation or install-existing operations.</p>
 */
final class PackageInstallerRetryPolicy {
    // MIUI-begin: Multiple attempts may be required for a successful installation.
    static final int MAX_MIUI_ATTEMPTS = 4;
    // MIUI-end
    // HyperOS-begin: Retry system-app installation using another system app.
    static final int MAX_HYPER_OS_ATTEMPTS = 3;
    // HyperOS-end

    enum Action {
        FINISH,
        RETRY,
        RETRY_WITH_SHELL_INSTALLER
    }

    // MIUI-begin
    private static final String MIUI_PERMISSION_DENIED = "INSTALL_FAILED_ABORTED: Permission denied";
    // MIUI-end
    // HyperOS-begin
    private static final String HYPER_OS_ISOLATION_VIOLATION =
            "INSTALL_FAILED_HYPEROS_ISOLATION_VIOLATION: ";
    // HyperOS-end

    private PackageInstallerRetryPolicy() {
    }

    @NonNull
    static Action getAction(int status, @Nullable String statusMessage, boolean privileged,
                            boolean affectedMiuiVersion, int attemptNumber) {
        if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
            return Action.FINISH;
        }
        // MIUI-begin: MIUI 12.5 and 20.2.0 may require repeated unprivileged attempts.
        if (!privileged
                && affectedMiuiVersion
                && Objects.equals(statusMessage, MIUI_PERMISSION_DENIED)
                && attemptNumber < MAX_MIUI_ATTEMPTS) {
            return Action.RETRY;
        }
        // MIUI-end
        // HyperOS-begin: the installer for a system app must itself be a system app.
        if (privileged
                && statusMessage != null
                && statusMessage.startsWith(HYPER_OS_ISOLATION_VIOLATION)
                && attemptNumber < MAX_HYPER_OS_ATTEMPTS) {
            return Action.RETRY_WITH_SHELL_INSTALLER;
        }
        // HyperOS-end
        return Action.FINISH;
    }
}
