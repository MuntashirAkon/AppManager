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
    private static final String[] ROOT_FILES = new String[]{"acct", "apex", "audit_filter_table", "bin", "bugreports",
            "cache", "carrier", "config", "d", "data", "data_mirror", "debug_ramdisk", "default.prop", "dev", "dpolicy",
            "dsp", "efs", "etc", "init", "init.container.rc", "init.environ.rc", "lib", "linkerconfig", "lost+found",
            "metadata", "mnt", "odm", "odm_dklm", "oem", "omr", "oneplus", "op1", "postinstall", "proc", "product",
            "sdcard", "second_state_resources", "sepolicy_version", "spu", "storage", "sys", "system", "system_ext",
            "vendor", "vendor_dlkm"};
    // There might be more, but these are what I've got for now (also, /system/apex)
    private static final String[] APEX_PKGS = new String[]{
            "com.android.adbd",
            "com.android.adservices",
            "com.android.appsearch",
            "com.android.art",
            "com.android.art.debug",
            "com.android.art.release",
            "com.android.bluetooth",
            "com.android.bootanimation",
            "com.android.btservices",
            "com.android.car.framework",
            "com.android.cellbroadcast",
            "com.android.conscrypt",
            "com.android.devicelock",
            "com.android.extservices",
            "com.android.extservices.gms",
            "com.android.federatedcompute",
            "com.android.geotz",
            "com.android.gki",
            "com.android.healthfitness",
            "com.android.i18n",
            "com.android.ipsec",
            "com.android.media",
            "com.android.media.swcodec",
            "com.android.mediaprovider",
            "com.android.neuralnetworks",
            "com.android.ondevicepersonalization",
            "com.android.os.statsd",
            "com.android.permission",
            "com.android.permission.gms",
            "com.android.resolv",
            "com.android.rkpd",
            "com.android.runtime",
            "com.android.scheduling",
            "com.android.sdkext",
            "com.android.sepolicy",
            "com.android.telephony",
            "com.android.tethering",
            "com.android.tethering.inprocess",
            "com.android.tzdata",
            "com.android.uwb",
            "com.android.virt",
            "com.android.vndk",
            "com.android.vndk.current",
            "com.android.vndk.v" + Build.VERSION.SDK_INT,
            "com.android.wifi",
    };
    private static final String[] DATA_FILES = new String[]{"app", "app-ephemeral", "app-lib", "cache", "dalvik-cache",
            "data", "local", "media", "misc", "misc_ce", "misc_de", "per_boot", "resource-cache", "rollback",
            "rollback-observer", "ss", "system", "system_ce", "system_de", "user", "user_ce", "user_de", "vendor",
            "vendor_ce", "vendor_de"};
    // Read-only here means whether this should be accessed by ReadOnlyDirectory, it has nothing to do with the actual mode of the file.
    private static final HashMap<String, String[]> sPathReadOnlyMap = new HashMap<String, String[]>() {{
        int userId = UserHandleHidden.myUserId();
        String appId;
        try {
            appId = (String) Class.forName("io.github.muntashirakon.AppManager.BuildConfig").getDeclaredField("APPLICATION_ID").get(null);
        } catch (Exception e) {
            appId = "io.github.muntashirakon.AppManager" + (BuildConfig.DEBUG ? ".debug" : "");
        }
        put("/", ROOT_FILES); // Permission denied
        put("/apex", APEX_PKGS); // Permission denied
        put("/data", DATA_FILES); // Permission denied
        put("/data/app", null); // Permission denied
        put("/data/data", new String[]{appId}); // Permission denied
        put("/data/local", new String[]{"tmp"}); // Permission denied
        put("/data/misc", new String[]{"ethernet", "gcov", "keychain", "profiles", "textclassifier", "user", "zoneinfo"});
        put("/data/misc/user", new String[]{appId});
        put("/data/misc/profiles", new String[]{"cur"});
        put("/data/misc/profiles/cur", new String[]{appId});
        put("/data/misc_ce", new String[]{String.valueOf(userId)}); // Permission denied
        put("/data/misc_de", new String[]{String.valueOf(userId)}); // Permission denied
        put("/data/user", new String[]{String.valueOf(userId)}); // Permission denied
        put("/data/user/" + userId, new String[]{appId}); // Permission denied
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            put("/data/user_de", new String[]{String.valueOf(userId)}); // Permission denied
            put("/data/user_de/" + userId, new String[]{appId}); // Permission denied
        }
        put("/mnt/sdcard", new String[]{"/storage/self/primary"}); // Permission denied, but redirects to /storage/self/primary
        put("/storage", new String[]{"emulated", "self"}); // Permission denied
        put("/storage/emulated", new String[]{String.valueOf(userId)}); // Permission denied
    }};

    @NonNull
    public static ExtendedFile getOverlayFile(@NonNull ExtendedFile file) {
        ExtendedFile file1 = getOverlayFileOrNull(file);
        return file1 != null ? file1 : file;
    }

    @Nullable
    public static ExtendedFile getOverlayFileOrNull(@NonNull ExtendedFile file) {
        String path = Paths.sanitize(file.getAbsolutePath(), false);
        if (path == null) {
            return null;
        }
        String[] children = listChildrenInternal(path);
        if (children != null) {
            // Check for potential alias
            for (String child : children) {
                if (child.startsWith(File.separator)) {
                    return ReadOnlyLocalFile.getAliasInstance(path, child);
                }
            }
            // No alias found
            return new ReadOnlyLocalFile(path);
        }
        // No overlay needed
        return null;
    }

    @Nullable
    public static String[] listChildren(@NonNull File file) {
        return listChildrenInternal(Paths.sanitize(file.getAbsolutePath(), false));
    }

    @Nullable
    private static String[] listChildrenInternal(@Nullable String path) {
        if (path == null) {
            return null;
        }
        if (path.equals("/data/app")) {
            fetchDataAppPaths();
        }
        return sPathReadOnlyMap.get(path);
    }

    @SuppressWarnings("SuspiciousRegexArgument") // Not on windows
    public static void fetchDataAppPaths() {
        if (sPathReadOnlyMap.get("/data/app") != null) {
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
        sPathReadOnlyMap.put("/data/app", paths.keySet().toArray(new String[0]));
        for (String part1 : paths.keySet()) {
            List<String> part2 = paths.get(part1);
            if (part2 == null) {
                continue;
            }
            sPathReadOnlyMap.put("/data/app/" + part1, part2.toArray(new String[0]));
        }
    }
}
