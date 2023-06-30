// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.users;

import android.os.UserHandleHidden;
import android.system.ErrnoException;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.compat.system.OsCompat;
import io.github.muntashirakon.compat.system.StructGroup;

public class Groups {
    private static final int AID_USER_OFFSET = 100000;
    private static final int AID_APP_START = 10000;
    private static final int AID_APP_END = 19999;
    private static final int AID_CACHE_GID_START = 20000;
    private static final int AID_CACHE_GID_END = 29999;
    private static final int AID_EXT_GID_START = 30000;
    private static final int AID_EXT_GID_END = 39999;
    private static final int AID_EXT_CACHE_GID_START = 40000;
    private static final int AID_EXT_CACHE_GID_END = 49999;
    private static final int AID_SHARED_GID_START = 50000;
    private static final int AID_SHARED_GID_END = 59999;
    private static final int AID_ISOLATED_START = 99000;

    private static final Map<Integer, String> sGidGroupMap = new HashMap<>();

    public static Map<Integer, String> getGidGroupMap(boolean reload) {
        synchronized (sGidGroupMap) {
            if (sGidGroupMap.isEmpty() || reload) {
                try {
                    OsCompat.setgrent();
                    StructGroup passwd;
                    while ((passwd = OsCompat.getgrent()) != null) {
                        sGidGroupMap.put(passwd.gr_id, passwd.gr_name);
                    }
                } catch (ErrnoException e) {
                    e.printStackTrace();
                } finally {
                    ExUtils.exceptionAsIgnored(OsCompat::endgrent);
                }
            }
            return sGidGroupMap;
        }
    }

    @NonNull
    public static String getGroupName(int uid) {
        String name = getGidGroupMap(false).get(uid);
        if (name != null) {
            return name;
        }
        return formatGid(uid);
    }

    @NonNull
    public static String formatGid(int gid) { // print_app_name_from_gid
        int appid = gid % AID_USER_OFFSET;
        int userid = gid / AID_USER_OFFSET;
        if (appid >= AID_ISOLATED_START) {
            return String.format(Locale.ROOT, "u%d_i%d", userid, appid - AID_ISOLATED_START);
        } else if (userid == 0 && appid >= AID_SHARED_GID_START && appid <= AID_SHARED_GID_END) {
            return String.format(Locale.ROOT, "all_a%d", appid - AID_SHARED_GID_START);
        } else if (appid >= AID_EXT_CACHE_GID_START && appid <= AID_EXT_CACHE_GID_END) {
            return String.format(Locale.ROOT, "u%d_a%d_ext_cache", userid, appid - AID_EXT_CACHE_GID_START);
        } else if (appid >= AID_EXT_GID_START && appid <= AID_EXT_GID_END) {
            return String.format(Locale.ROOT, "u%d_a%d_ext", userid, appid - AID_EXT_GID_START);
        } else if (appid >= AID_CACHE_GID_START && appid <= AID_CACHE_GID_END) {
            return String.format(Locale.ROOT, "u%d_a%d_cache", userid, appid - AID_CACHE_GID_START);
        } else if (appid < AID_APP_START) {
            return String.valueOf(gid); // As per UserHandle
        } else {
            return String.format(Locale.ROOT, "u%d_a%d", userid, appid - AID_APP_START);
        }
    }

    public static int getCacheAppGid(int uid) {
        int appId = uid % AID_USER_OFFSET;
        int userId = uid / AID_USER_OFFSET;
        if (appId >= AID_APP_START && appId <= AID_APP_END) {
            return UserHandleHidden.getUid(userId, (appId - AID_APP_START) + AID_CACHE_GID_START);
        } else {
            return -1;
        }
    }
}
