// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.os.Build;

import androidx.biometric.BiometricManager.Authenticators;

public class BiometricAuthenticatorsCompat {
    public static final class Builder {
        private boolean mAllowWeak = false;
        private boolean mAllowStrong = false;
        private boolean mAllowDeviceCredential = false;
        private boolean mDeviceCredentialOnly = false;

        public Builder() {
        }

        public Builder allowEverything(boolean allow) {
            mAllowWeak = allow;
            mAllowDeviceCredential = allow;
            return this;
        }

        public Builder allowWeakBiometric(boolean allow) {
            mAllowWeak = allow;
            return this;
        }

        public Builder allowStrongBiometric(boolean allow) {
            mAllowStrong = allow;
            return this;
        }

        public Builder allowDeviceCredential(boolean allow) {
            mAllowDeviceCredential = allow;
            return this;
        }

        public Builder deviceCredentialOnly(boolean only) {
            mDeviceCredentialOnly = only;
            return this;
        }

        public int build() {
            if (mDeviceCredentialOnly) {
                return getDeviceCredentialOnlyFlags();
            }
            int flags;
            if (mAllowWeak) {
                flags = Authenticators.BIOMETRIC_WEAK;
            } else if (mAllowStrong) {
                flags = Authenticators.BIOMETRIC_STRONG;
            } else flags = 0;
            if (mAllowDeviceCredential) {
                if (flags == 0) {
                    return getDeviceCredentialOnlyFlags();
                }
                if (flags == Authenticators.BIOMETRIC_STRONG && (
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.P
                        || Build.VERSION.SDK_INT > Build.VERSION_CODES.Q)) {
                    flags = Authenticators.BIOMETRIC_WEAK;
                }
                return flags | Authenticators.DEVICE_CREDENTIAL;
            }
            return flags;
        }

        private int getDeviceCredentialOnlyFlags() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return Authenticators.DEVICE_CREDENTIAL;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return Authenticators.BIOMETRIC_WEAK | Authenticators.DEVICE_CREDENTIAL;
            }
            return Authenticators.BIOMETRIC_STRONG | Authenticators.DEVICE_CREDENTIAL;
        }
    }
}
