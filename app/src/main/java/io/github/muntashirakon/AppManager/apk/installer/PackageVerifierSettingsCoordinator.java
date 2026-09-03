// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Prevents a temporary process-owned verifier override from affecting another install.
 * Installs using the normal verifier setting may run together. An install that changes
 * the global verifier setting runs exclusively until that setting has been restored.
 */
final class PackageVerifierSettingsCoordinator {
    private static final ReentrantReadWriteLock SETTINGS_LOCK = new ReentrantReadWriteLock(true);

    private PackageVerifierSettingsCoordinator() {
    }

    static void acquire(boolean disableVerification) {
        if (disableVerification) {
            SETTINGS_LOCK.writeLock().lock();
        } else {
            SETTINGS_LOCK.readLock().lock();
        }
    }

    static void release(boolean disableVerification) {
        if (disableVerification) {
            SETTINGS_LOCK.writeLock().unlock();
        } else {
            SETTINGS_LOCK.readLock().unlock();
        }
    }
}
