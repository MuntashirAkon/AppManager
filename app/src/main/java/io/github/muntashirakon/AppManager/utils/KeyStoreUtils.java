/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.types.PrivilegedFile;

public final class KeyStoreUtils {
    public static boolean hasKeyStore(int uid) {
        PrivilegedFile keyStorePath = getMasterKey(Users.getUserHandle(uid));
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
    public static PrivilegedFile getKeyStorePath(int userHandle) {
        return new PrivilegedFile("/data/misc/keystore", "user_" + userHandle);
    }

    @NonNull
    public static List<String> getKeyStoreFiles(int uid, int userHandle) {
        // For any app, the key path is as follows:
        // /data/misc/keystore/user_{user_handle}/{uid}_{KEY_NAME}_{alias}
        PrivilegedFile keyStorePath = getMasterKey(userHandle);
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
    public static PrivilegedFile getMasterKey(int userHandle) {
        return new PrivilegedFile(new File("/data/misc/keystore/", "user_" + userHandle), ".masterkey");
    }
}
