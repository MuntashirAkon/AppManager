// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.users;

import android.os.UserHandleHidden;
import android.system.ErrnoException;
import android.system.StructPasswd;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import io.github.muntashirakon.AppManager.compat.ProcessCompat;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.compat.system.OsCompat;

public class Owners {
    private static final Map<Integer, String> sUidOwnerMap = new HashMap<>();
    private static final Map<String, Integer> sOwnerUidMap = new HashMap<>();

    public static Map<Integer, String> getUidOwnerMap(boolean reload) {
        synchronized (sUidOwnerMap) {
            if (sUidOwnerMap.isEmpty() || reload) {
                try {
                    OsCompat.setpwent();
                    StructPasswd passwd;
                    while ((passwd = OsCompat.getpwent()) != null) {
                        sUidOwnerMap.put(passwd.pw_uid, passwd.pw_name);
                        sOwnerUidMap.put(passwd.pw_name, passwd.pw_uid);
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

    public static int parseUid(String uidString) {
        // This is effectively the reverse of #formatUid(int)
        if (TextUtils.isDigitsOnly(uidString)) {
            return Integer.parseInt(uidString);
        }
        getUidOwnerMap(false);
        Integer uid = sOwnerUidMap.get(uidString);
        if (uid != null) {
            return uid;
        }
        if (!uidString.isEmpty() && uidString.charAt(0) == 'u') {
            int i = 1;
            while (TextUtils.isDigitsOnly(String.valueOf(uidString.charAt(i)))) {
                ++i;
            }
            int userId = Integer.parseInt(uidString.substring(1, i));
            // Skip any underscore
            if (uidString.charAt(i) == '_') {
                ++i;
            }
            int appId;
            String type;
            if (uidString.charAt(i+1) == 'i') {
                type = uidString.substring(i, i + 2);
                i += 2;
            } else {
                type = uidString.substring(i, i + 1);
                ++i;
            }
            int shortAppId = Integer.parseInt(uidString.substring(i));
            switch (type) {
                case "s":
                    appId = shortAppId;
                    break;
                case "a":
                    appId = ProcessCompat.FIRST_APPLICATION_UID + shortAppId;
                    break;
                case "i":
                    appId = ProcessCompat.FIRST_ISOLATED_UID + shortAppId;
                    break;
                case "ai":
                    appId = ProcessCompat.FIRST_APP_ZYGOTE_ISOLATED_UID + shortAppId;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid u-prefixed string: " + uidString);
            }
            return UserHandleHidden.getUid(userId, appId);
        }
        throw new IllegalArgumentException("Malformed UID string: " + uidString);
    }

    @NonNull
    public static String formatUid(int uid) {
        StringBuilder sb = new StringBuilder();
        UserHandleHidden.formatUid(sb, uid);
        if (sb.indexOf("u") == 0) { // u\d+([ais]|ai)\d+
            // u-prefixed name, add _ (underscore) after u\d+
            int i = 1;
            while (TextUtils.isDigitsOnly(String.valueOf(sb.charAt(i)))) {
                ++i;
            }
            sb.insert(i, '_');
        }
        return sb.toString();
    }
}
