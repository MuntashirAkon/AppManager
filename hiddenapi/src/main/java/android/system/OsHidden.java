// SPDX-License-Identifier: GPL-3.0-or-later

package android.system;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(Os.class)
public class OsHidden {
    public static StructPasswd getpwuid(int uid) throws ErrnoException {
        throw new UnsupportedOperationException();
    }
}
