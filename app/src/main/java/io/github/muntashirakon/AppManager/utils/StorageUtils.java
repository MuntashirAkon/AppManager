// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.storage.StorageVolume;
import android.os.storage.StorageVolumeHidden;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.collection.ArrayMap;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.rikka.tools.refine.Refine;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.StorageManagerCompat;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.PathReader;
import io.github.muntashirakon.io.Paths;

public class StorageUtils {
    public static final String TAG = "StorageUtils";

    private static final String ENV_SECONDARY_STORAGE = "SECONDARY_STORAGE";

    @WorkerThread
    @NonNull
    public static ArrayMap<String, Uri> getAllStorageLocations(@NonNull Context context) {
        ArrayMap<String, Uri> storageLocations = new ArrayMap<>(10);
        Path sdCard = Paths.get(Environment.getExternalStorageDirectory());
        addStorage(context.getString(R.string.external_storage), sdCard, storageLocations);
        getStorageEnv(context, storageLocations);
        retrieveStorageManager(context, storageLocations);
        retrieveStorageFilesystem(storageLocations);
        getStorageExternalFilesDir(context, storageLocations);
        // Get SAF persisted directories
        ArrayMap<Uri, Long> grantedUrisAndDate = SAFUtils.getUrisWithDate(context);
        for (int i = 0; i < grantedUrisAndDate.size(); ++i) {
            Uri uri = grantedUrisAndDate.keyAt(i);
            long time = grantedUrisAndDate.valueAt(i);
            if (Paths.get(uri).isDirectory()) {
                // Only directories are locations
                String readableName = Paths.getLastPathSegment(uri.getPath()) + " " + DateUtils.formatDate(context, time);
                storageLocations.put(readableName, getFixedTreeUri(uri));
            }
        }
        return storageLocations;
    }

    /**
     * unified test function to add storage if fitting
     */
    private static void addStorage(@NonNull String label, @Nullable Path entry, @NonNull Map<String, Uri> storageLocations) {
        if (entry == null) {
            return;
        }
        if (entry.isSymbolicLink()) {
            // Use the real path
            Path finalEntry = entry;
            entry = ExUtils.requireNonNullElse(finalEntry::getRealPath, finalEntry);
        }
        Uri uri = entry.getUri();
        if (!storageLocations.containsValue(uri)) {
            storageLocations.put(label, uri);
        } else {
            Log.d(TAG, entry.getUri().toString());
        }
    }

    /**
     * Get storage from ENV, as recommended by 99%, doesn't detect external SD card, only internal ?!
     */
    private static void getStorageEnv(@NonNull Context context, Map<String, Uri> storageLocations) {
        final String rawSecondaryStorage = System.getenv(ENV_SECONDARY_STORAGE);
        if (!TextUtils.isEmpty(rawSecondaryStorage)) {
            //noinspection ConstantConditions
            String[] externalCards = rawSecondaryStorage.split(":");
            for (int i = 0; i < externalCards.length; i++) {
                String path = externalCards[i];
                addStorage(context.getString(R.string.sd_card) + (i == 0 ? "" : " " + i), Paths.get(path), storageLocations);
            }
        }
    }

    /**
     * Get storage indirect, best solution so far
     */
    private static void getStorageExternalFilesDir(Context context, Map<String, Uri> storageLocations) {
        // Get primary & secondary external device storage (internal storage & micro SDCARD slot...)
        File[] listExternalDirs = ContextCompat.getExternalFilesDirs(context, null);
        for (File listExternalDir : listExternalDirs) {
            if (listExternalDir != null) {
                String path = listExternalDir.getAbsolutePath();
                int indexMountRoot = path.indexOf("/Android/data/");
                if (indexMountRoot >= 0) {
                    // Get the root path for the external directory
                    Path file = Paths.get(path.substring(0, indexMountRoot));
                    addStorage(file.getName(), file, storageLocations);
                }
            }
        }
    }

    /**
     * Get storages via StorageManager
     */
    private static void retrieveStorageManager(Context context, Map<String, Uri> storageLocations) {
        Set<StorageVolume> storageVolumes = new HashSet<>();
        int[] users = Users.getUsersIds();
        for (int user : users) {
            try {
                storageVolumes.addAll(Arrays.asList(StorageManagerCompat.getVolumeList(context, user, 0)));
            } catch (SecurityException ignore) {
            }
        }
        try {
            for (@NonNull StorageVolume volume : storageVolumes) {
                StorageVolumeHidden vol = Refine.unsafeCast(volume);
                File dir = vol.getPathFile();
                if (dir == null) continue;
                String label = vol.getUserLabel();
                addStorage(label == null ? dir.getName() : label, Paths.get(dir), storageLocations);
            }
            Log.d(TAG, "used storagemanager");
        } catch (Exception e) {
            Log.w(TAG, "error during storage retrieval", e);
        }
    }

    /**
     * Get storage via /proc/mounts, probably never works
     */
    private static void retrieveStorageFilesystem(Map<String, Uri> storageLocations) {
        Path mountFile = Paths.get("/proc/mounts");
        if (!mountFile.isDirectory()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new PathReader(mountFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("/dev/block/vold/")) {
                    String[] lineElements = line.split(" ");
                    Path element = Paths.get(lineElements[1]);
                    // Don't add the default mount path since it's already in the list.
                    addStorage(element.getName(), element, storageLocations);
                }
            }
        } catch (IOException ignore) {
        }
    }

    @NonNull
    private static Uri getFixedTreeUri(@NonNull Uri uri) {
        List<String> paths = uri.getPathSegments();
        int size = paths.size();
        if (size < 2 || !"tree".equals(paths.get(0))) {
            throw new IllegalArgumentException("Not a tree URI.");
        }
        // FORMAT: /tree/<id>/document/<id>%2F<others>
        switch (size) {
            case 2:
                return uri.buildUpon()
                        .appendPath("document")
                        .appendPath(paths.get(1))
                        .build();
            case 3:
                if (!"document".equals(paths.get(2))) {
                    throw new IllegalArgumentException("Not a document URI.");
                }
                return uri.buildUpon()
                        .appendPath(paths.get(1))
                        .build();
            case 4:
                if (!"document".equals(paths.get(2))) {
                    throw new IllegalArgumentException("Not a document URI.");
                }
                return uri;
            default:
                throw new IllegalArgumentException("Malformed URI.");
        }
    }
}