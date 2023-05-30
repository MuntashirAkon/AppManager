// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;

final class LocalFileOverlay {
    private static final String[] ROOT_FILES = new String[]{"apex", "bin", "d", "data", "data_mirror", "data_ramdisk",
            "dev", "dsp", "etc", "linkerconfig", "metadata", "mnt", "odm", "odm_dklm", "oem", "oneplus", "op1",
            "postinstall", "proc", "product", "sdcard", "second_state_resources", "storage", "sys", "system",
            "system_ext", "vendor", "vendor_dlkm"};
    // Read-only here means whether this should be accessed by ReadOnlyDirectory, it has nothing to do with the actual mode of the file.
    private static final HashMap<String, String[]> pathReadOnlyMap = new HashMap<String, String[]>() {{
        int userId = UserHandleHidden.myUserId();
        String appId;
        try {
            appId = (String) Class.forName("io.github.muntashirakon.AppManager.BuildConfig").getDeclaredField("APPLICATION_ID").get(null);
        } catch (Exception e) {
            appId = "io.github.muntashirakon.AppManager" + (BuildConfig.DEBUG ? ".debug" : "");
        }
        put("/", ROOT_FILES); // Permission denied
        put("/data", new String[]{"app", "data", "user", "user_ce", "user_de"}); // Permission denied
        put("/data/app", null); // Permission denied TODO: We need customisation here to list all apps
        put("/data/data", new String[]{appId}); // Permission denied
        put("/data/user", new String[]{String.valueOf(userId)}); // Permission denied
        put("/data/user/" + userId, new String[]{appId}); // Permission denied
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            put("/data/user_de", new String[]{String.valueOf(userId)}); // Permission denied
            put("/data/user_de/" + userId, new String[]{appId}); // Permission denied
        }
        put("/mnt/sdcard", new String[]{"/storage/emulated/" + userId}); // Permission denied, but redirects to /storage/emulated/<user_id>
        put("/storage", new String[]{"emulated", "self"}); // Permission denied, but we have more customisation
        put("/storage/emulated", new String[]{String.valueOf(userId)}); // Permission denied
    }};

    @NonNull
    public static ExtendedFile getOverlayFile(@NonNull ExtendedFile file) {
        String path = getSanitizedPath(file.getAbsolutePath());
        if (path == null) {
            return file;
        }
        String[] children = listChildrenInternal(path);
        if (children != null) {
            // Check for potential alias
            for (String child : children) {
                if (child.startsWith("/")) {
                    return FileSystemManager.getLocal().getFile(child);
                }
            }
            // No alias found
            return new ReadOnlyLocalFile(path);
        }
        // No overlay needed
        return file;
    }

    @Nullable
    public static String[] listChildren(@NonNull File file) {
        return listChildrenInternal(getSanitizedPath(file.getAbsolutePath()));
    }

    @Nullable
    private static String[] listChildrenInternal(@Nullable String path) {
        if (path == null) {
            return null;
        }
        if (path.equals("/data/app")) {
            fetchDataAppPaths();
        }
        return pathReadOnlyMap.get(path);
    }

    @SuppressWarnings("SuspiciousRegexArgument") // Not on windows
    public static void fetchDataAppPaths() {
        if (pathReadOnlyMap.get("/data/app") != null) {
            return;
        }
        List<ApplicationInfo> applicationInfoList = null;
        try {
            applicationInfoList = PackageManagerCompat.getInstalledApplications(MATCH_STATIC_SHARED_AND_SDK_LIBRARIES | MATCH_UNINSTALLED_PACKAGES, UserHandleHidden.myUserId());
        } catch (RemoteException ignore) {
        }
        if (applicationInfoList == null) {
            return;
        }
        Map<String, List<String>> paths = new HashMap<>();
        for (ApplicationInfo info : applicationInfoList) {
            if (info.publicSourceDir == null) {
                continue;
            }
            String path = new File(info.publicSourceDir).getParent();
            if (path == null || !path.startsWith("/data/app/")) {
                continue;
            }
            path = path.substring(10); // "/data/app/".length()
            // Remaining path is required one. It can contain either two or one paths
            String[] pathParts = path.split(File.separator);
            switch (pathParts.length) {
                case 1:
                    paths.put(pathParts[0], null);
                    break;
                case 2: {
                    String part1 = pathParts[0];
                    List<String> part2 = paths.get(part1);
                    if (part2 == null) {
                        part2 = new ArrayList<>();
                        paths.put(part1, part2);
                    }
                    part2.add(pathParts[1]);
                    break;
                }
            }
        }
        // Update pathReadOnlyMap
        pathReadOnlyMap.put("/data/app", paths.keySet().toArray(new String[0]));
        for (String part1 : paths.keySet()) {
            List<String> part2 = paths.get(part1);
            if (part2 == null) {
                continue;
            }
            pathReadOnlyMap.put("/data/app/" + part1, part2.toArray(new String[0]));
        }
    }

    @Nullable
    private static String getSanitizedPath(@NonNull String name) {
        // Replace multiple separators with a single separator
        //noinspection RegExpRedundantEscape,RegExpSimplifiable
        name = name.replaceAll("[\\/\\\\]+", File.separator);
        if (name.equals(File.separator)) {
            // Name is a separator AKA root
            return File.separator;
        }
        // Name isn't a root but could still be ../ or ./, we only consider ./ because we cannot allow it
        if (name.startsWith("./")) {
            // Omit ./
            name = name.substring(2);
        }
        // Omit last separator if present, this also means ../ will become ..
        if (name.endsWith(File.separator)) {
            name = name.substring(0, name.length() - 1);
        }
        // At this point, name could contain nothing at all
        return name.isEmpty() ? null : name;
    }
}
