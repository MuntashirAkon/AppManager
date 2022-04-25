// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

import io.github.muntashirakon.AppManager.logs.Logger;
import io.github.muntashirakon.AppManager.utils.FileUtils;

public class ProfileLogger extends Logger {
    @NonNull
    public static File getLogFile(@NonNull String profileName) {
        profileName = ProfileMetaManager.getCleanedProfileName(profileName);
        return new File(getLoggingDirectory(), "profile_" + profileName + ".log");
    }

    public ProfileLogger(@NonNull String profileName) throws IOException {
        super(getLogFile(profileName), true);
    }

    @NonNull
    public static String getAllLogs(@NonNull String profileName) {
        return FileUtils.getFileContent(getLogFile(profileName));
    }

    public static void clearLogs(@NonNull String profileName) {
        getLogFile(profileName).delete();
    }
}
