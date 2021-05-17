// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.io.ProxyFile;

public final class KeyStoreUtils {
    public static boolean hasKeyStore(int uid) {
        ProxyFile keyStorePath = getKeyStorePath(Users.getUserHandle(uid));
        String[] fileNames = keyStorePath.list();
        if (fileNames != null) {
            String uidStr = uid + "_";
            for (String fileName : fileNames) {
                if (fileName.startsWith(uidStr)) return true;
            }
        }
        return false;
    }

    public static boolean hasMasterKey(int uid) {
        return getMasterKey(Users.getUserHandle(uid)).exists();
    }

    @NonNull
    public static ProxyFile getKeyStorePath(int userHandle) {
        return new ProxyFile("/data/misc/keystore", "user_" + userHandle);
    }

    @NonNull
    public static List<String> getKeyStoreFiles(int uid, int userHandle) {
        // For any app, the key path is as follows:
        // /data/misc/keystore/user_{user_handle}/{uid}_{KEY_NAME}_{alias}
        ProxyFile keyStorePath = getKeyStorePath(userHandle);
        String[] fileNames = keyStorePath.list();
        List<String> keyStoreFiles = new ArrayList<>();
        if (fileNames != null) {
            String uidStr = uid + "_";
            for (String fileName : fileNames) {
                if (fileName.startsWith(uidStr) || fileName.startsWith("." + uidStr)) {
                    keyStoreFiles.add(fileName);
                }
            }
        }
        return keyStoreFiles;
    }

    @NonNull
    public static ProxyFile getMasterKey(int userHandle) {
        return new ProxyFile(getKeyStorePath(userHandle), ".masterkey");
    }
}
