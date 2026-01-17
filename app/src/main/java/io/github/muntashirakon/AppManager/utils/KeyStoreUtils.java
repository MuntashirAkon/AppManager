// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.os.Build;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public final class KeyStoreUtils {
    public static boolean hasKeyStore(int uid) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return hasKeyStoreV2(uid);
        }
        return hasKeyStoreV1(uid);
    }

    public static boolean hasMasterKey(int uid) {
        try {
            return getMasterKey(UserHandleHidden.getUserId(uid)).exists();
        } catch (FileNotFoundException e) {
            return false;
        }
    }

    @NonNull
    public static Path getKeyStorePath(int userHandle) {
        return Paths.get("/data/misc/keystore/user_" + userHandle);
    }

    @NonNull
    public static List<String> getKeyStoreFiles(int uid, int userHandle) {
        // For any app, the key path is as follows:
        // /data/misc/keystore/user_{user_handle}/{uid}_{KEY_NAME}_{alias}
        Path keyStorePath = getKeyStorePath(userHandle);
        String[] fileNames = keyStorePath.listFileNames();
        List<String> keyStoreFiles = new ArrayList<>();
        String uidStr = uid + "_";
        for (String fileName : fileNames) {
            if (fileName.startsWith(uidStr) || fileName.startsWith("." + uidStr)) {
                keyStoreFiles.add(fileName);
            }
        }
        return keyStoreFiles;
    }

    @NonNull
    public static Path getMasterKey(int userHandle) throws FileNotFoundException {
        return getKeyStorePath(userHandle).findFile(".masterkey");
    }

    @RequiresApi(Build.VERSION_CODES.S)
    public static boolean hasKeyStoreV2(int uid) {
        return Runner.runCommand(new String[]{"su", "" + uid, "-c", "keystore_cli_v2", "list"})
                .getOutputAsList().size() > 1;
    }

    public static boolean hasKeyStoreV1(int uid) {
        Path keyStorePath = getKeyStorePath(UserHandleHidden.getUserId(uid));
        String[] fileNames = keyStorePath.listFileNames();
        String uidStr = uid + "_";
        for (String fileName : fileNames) {
            if (fileName.startsWith(uidStr)) return true;
        }
        return false;
    }
}
