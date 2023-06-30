// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.users;

import android.os.UserHandleHidden;
import android.system.ErrnoException;
import android.system.StructPasswd;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.compat.system.OsCompat;

public class Owners {
    private static final Map<Integer, String> sUidOwnerMap = new HashMap<>();

    public static Map<Integer, String> getUidOwnerMap(boolean reload) {
        synchronized (sUidOwnerMap) {
            if (sUidOwnerMap.isEmpty() || reload) {
                try {
                    OsCompat.setpwent();
                    StructPasswd passwd;
                    while ((passwd = OsCompat.getpwent()) != null) {
                        sUidOwnerMap.put(passwd.pw_uid, passwd.pw_name);
                    }
                } catch (ErrnoException e) {
                    e.printStackTrace();
                } finally {
                    ExUtils.exceptionAsIgnored(OsCompat::endpwent);
                }
            }
            return sUidOwnerMap;
        }
    }

    @NonNull
    public static String getOwnerName(int uid) {
        String name = getUidOwnerMap(false).get(uid);
        if (name != null) {
            return name;
        }
        return formatUid(uid);
    }

    @NonNull
    public static String formatUid(int uid) {
        StringBuilder sb = new StringBuilder();
        UserHandleHidden.formatUid(sb, uid);
        if (sb.indexOf("u") == 0) {
            // u-prefixed name, index 1 is a mandatory integer, but not so sure about others
            int i = 2;
            while (TextUtils.isDigitsOnly(String.valueOf(sb.charAt(i)))) {
                ++i;
            }
            sb.insert(i, '_');
        }
        return sb.toString();
    }
}
