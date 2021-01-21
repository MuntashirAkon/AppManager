/*
 * Copyright (C) 2021 Muntashir Al-Islam
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

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.core.content.ContextCompat;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.io.ProxyFile;
import io.github.muntashirakon.io.ProxyFileReader;

import java.io.BufferedReader;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

public class StorageUtils {
    public static final String TAG = "StorageUtils";

    private static final String ENV_SECONDARY_STORAGE = "SECONDARY_STORAGE";

    @NonNull
    public static ArrayMap<String, ProxyFile> getAllStorageLocations(@NonNull Context context, boolean includeInternal) {
        ArrayMap<String, ProxyFile> storageLocations = new ArrayMap<>(10);
        if (includeInternal) {
            ProxyFile internal = new ProxyFile(Environment.getDataDirectory());
            addStorage(context.getString(R.string.internal_storage), internal, storageLocations);
        }
        @SuppressWarnings("deprecation")
        ProxyFile sdCard = new ProxyFile(Environment.getExternalStorageDirectory());
        addStorage(context.getString(R.string.external_storage), sdCard, storageLocations);
        getStorageEnv(context, storageLocations);
        retrieveStorageManager(context, storageLocations);
        retrieveStorageFilesystem(storageLocations);
        getStorageExternalFilesDir(context, storageLocations);
        return storageLocations;
    }

    /**
     * unified test function to add storage if fitting
     */
    private static void addStorage(String label, ProxyFile entry, Map<String, ProxyFile> storageLocations) {
        if (entry != null && entry.listFiles() != null && !storageLocations.containsValue(entry)) {
            storageLocations.put(label, entry);
        } else if (entry != null) {
            Log.d(TAG, entry.getAbsolutePath());
        }
    }

    /**
     * Get storage from ENV, as recommended by 99%, doesn't detect external SD card, only internal ?!
     */
    private static void getStorageEnv(@NonNull Context context, Map<String, ProxyFile> storageLocations) {
        final String rawSecondaryStorage = System.getenv(ENV_SECONDARY_STORAGE);
        if (!TextUtils.isEmpty(rawSecondaryStorage)) {
            //noinspection ConstantConditions
            String[] externalCards = rawSecondaryStorage.split(":");
            for (int i = 0; i < externalCards.length; i++) {
                String path = externalCards[i];
                storageLocations.put(context.getString(R.string.sd_card) + (i == 0 ? "" : " " + i), new ProxyFile(path));
            }
        }
    }

    /**
     * Get storage indirect, best solution so far
     */
    private static void getStorageExternalFilesDir(Context context, Map<String, ProxyFile> storageLocations) {
        //Get primary & secondary external device storage (internal storage & micro SDCARD slot...)
        File[] listExternalDirs = ContextCompat.getExternalFilesDirs(context, null);
        for (File listExternalDir : listExternalDirs) {
            if (listExternalDir != null) {
                String path = listExternalDir.getAbsolutePath();
                int indexMountRoot = path.indexOf("/Android/data/");
                if (indexMountRoot >= 0 && indexMountRoot <= path.length()) {
                    //Get the root path for the external directory
                    ProxyFile file = new ProxyFile(path.substring(0, indexMountRoot));
                    addStorage(file.getName(), file, storageLocations);
                }
            }
        }
    }

    /**
     * Get storages via StorageManager & reflection hacks, probably never works
     */
    private static void retrieveStorageManager(Context context, Map<String, ProxyFile> storageLocations) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            StorageManager storage = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            try {
                for (StorageVolume volume : storage.getStorageVolumes()) {
                    // reflection attack to get volumes
                    File dir = callReflectionFunction(volume, "getPathFile");
                    if (dir == null) continue;
                    String label = callReflectionFunction(volume, "getUserLabel");
                    addStorage(label, new ProxyFile(dir), storageLocations);
                }
                Log.d(TAG, "used storagemanager");
            } catch (Exception e) {
                Log.w(TAG, "error during storage retrieval", e);
            }
        }
    }

    /**
     * Get storage via /proc/mounts, probably never works
     */
    private static void retrieveStorageFilesystem(Map<String, ProxyFile> storageLocations) {
        try {
            ProxyFile mountFile = new ProxyFile("/proc/mounts");
            if (mountFile.exists()) {
                BufferedReader reader = new BufferedReader(new ProxyFileReader(mountFile));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("/dev/block/vold/")) {
                        String[] lineElements = line.split(" ");
                        ProxyFile element = new ProxyFile(lineElements[1]);
                        // Don't add the default mount path since it's already in the list.
                        addStorage(element.getName(), element, storageLocations);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reflection helper function, to invoke private functions
     */
    @Nullable
    private static <T> T callReflectionFunction(@NonNull Object obj, @NonNull String function) throws ClassCastException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Method method = obj.getClass().getDeclaredMethod(function);
        method.setAccessible(true);
        Object r = method.invoke(obj);
        //noinspection unchecked
        return (T) r;
    }
}