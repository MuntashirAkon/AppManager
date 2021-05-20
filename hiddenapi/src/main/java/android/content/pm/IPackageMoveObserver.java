// SPDX-License-Identifier: Apache-2.0

package android.content.pm;

interface IPackageMoveObserver {
    void packageMoved(String packageName, int returnCode);
}
