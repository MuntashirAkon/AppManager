// SPDX-License-Identifier: GPL-3.0-or-later

package android.system;

public class OsHidden {
    public static StructPasswd getpwuid(int uid) throws ErrnoException {
        throw new ErrnoException("pwuid", OsConstants.EACCES);
    }
}
