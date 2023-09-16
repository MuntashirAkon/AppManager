// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

import io.github.muntashirakon.AppManager.logs.Logger;
import io.github.muntashirakon.io.Paths;

public class ProfileLogger extends Logger {
    @NonNull
    public static File getLogFile(@NonNull String profileId) {
        return new File(getLoggingDirectory(), "profile_" + profileId + ".log");
    }

    public ProfileLogger(@NonNull String profileId) throws IOException {
        super(getLogFile(profileId), true);
    }

    @NonNull
    public static String getAllLogs(@NonNull String profileId) {
        return Paths.get(getLogFile(profileId)).getContentAsString();
    }

    public static void clearLogs(@NonNull String profileId) {
        getLogFile(profileId).delete();
    }
}
