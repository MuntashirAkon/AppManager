package io.github.muntashirakon.AppManager.misc;

import java.io.File;

import androidx.annotation.NonNull;

public final class OsEnvironment {
    private static final File DIR_ANDROID_DATA = getDirectory("ANDROID_DATA", "/data");

    @NonNull
    static File getDirectory(String variableName, String defaultPath) {
        String path = System.getenv(variableName);
        return path == null ? new File(defaultPath) : new File(path);
    }

    @NonNull
    public static File getDataAppDirectory() {
        return new File(DIR_ANDROID_DATA, "app");
    }

    @NonNull
    public static File getDataDataDirectory() {
        return new File(DIR_ANDROID_DATA, "data");
    }
}
