// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.compat.system;

import androidx.annotation.Keep;

@Keep
public class StructGroup {
    public final String gr_name;
    public final String gr_passwd;
    public final int gr_id;
    public final String[] gr_mem;

    public StructGroup(String grName, String grPasswd, int grId, String[] grMem) {
        gr_name = grName;
        gr_passwd = grPasswd;
        gr_id = grId;
        gr_mem = grMem;
    }
}
