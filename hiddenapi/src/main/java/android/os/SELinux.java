// SPDX-License-Identifier: Apache-2.0

package android.os;

import java.io.File;

import misc.utils.HiddenUtil;

public class SELinux {
    /**
     * Determine whether SELinux is disabled or enabled.
     *
     * @return a boolean indicating whether SELinux is enabled.
     */
    public static final native boolean isSELinuxEnabled();

    /**
     * Determine whether SELinux is permissive or enforcing.
     *
     * @return a boolean indicating whether SELinux is enforcing.
     */
    public static final native boolean isSELinuxEnforced();

    /**
     * Change the security context of an existing file object.
     *
     * @param path    representing the path of file object to relabel.
     * @param context new security context given as a String.
     * @return a boolean indicating whether the operation succeeded.
     */
    public static final native boolean setFileContext(String path, String context);

    /**
     * Get the security context of a file object.
     *
     * @param path the pathname of the file object.
     * @return a security context given as a String.
     */
    public static final native String getFileContext(String path);

    /**
     * Gets the security context of the current process.
     *
     * @return a String representing the security context of the current process.
     */
    public static final native String getContext();

    /**
     * Gets the security context of a given process id.
     *
     * @param pid an int representing the process id to check.
     * @return a String representing the security context of the given pid.
     */
    public static final native String getPidContext(int pid);

    /**
     * Check permissions between two security contexts.
     *
     * @param scon   The source or subject security context.
     * @param tcon   The target or object security context.
     * @param tclass The object security class name.
     * @param perm   The permission name.
     * @return a boolean indicating whether permission was granted.
     */
    public static final native boolean checkSELinuxAccess(String scon, String tcon, String tclass, String perm);

    /**
     * Restores a file to its default SELinux security context.
     * If the system is not compiled with SELinux, then {@code true}
     * is automatically returned.
     * If SELinux is compiled in, but disabled, then {@code true} is
     * returned.
     *
     * @param pathname The pathname of the file to be relabeled.
     * @return a boolean indicating whether the relabeling succeeded.
     * @throws NullPointerException if the pathname is a null object.
     */
    public static boolean restorecon(String pathname) throws NullPointerException {
        return HiddenUtil.throwUOE(pathname);
    }

    /**
     * Restores a file to its default SELinux security context.
     * If the system is not compiled with SELinux, then {@code true}
     * is automatically returned.
     * If SELinux is compiled in, but disabled, then {@code true} is
     * returned.
     *
     * @param file The File object representing the path to be relabeled.
     * @return a boolean indicating whether the relabeling succeeded.
     * @throws NullPointerException if the file is a null object.
     */
    public static boolean restorecon(File file) throws NullPointerException {
        return HiddenUtil.throwUOE(file);
    }

    /**
     * Recursively restores all files under the given path to their default
     * SELinux security context. If the system is not compiled with SELinux,
     * then {@code true} is automatically returned. If SELinux is compiled in,
     * but disabled, then {@code true} is returned.
     *
     * @return a boolean indicating whether the relabeling succeeded.
     */
    public static boolean restoreconRecursive(File file) {
        return HiddenUtil.throwUOE(file);
    }
}
