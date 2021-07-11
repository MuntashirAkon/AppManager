// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.collection.ArrayMap;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.io.ProxyFile;
import io.github.muntashirakon.io.ProxyFileReader;

public class StorageUtils {
    public static final String TAG = "StorageUtils";

    private static final String ENV_SECONDARY_STORAGE = "SECONDARY_STORAGE";

    @WorkerThread
    @NonNull
    public static ArrayMap<String, Uri> getAllStorageLocations(@NonNull Context context, boolean includeInternal) {
        ArrayMap<String, Uri> storageLocations = new ArrayMap<>(10);
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
        // Get SAF persisted directories
        ArrayMap<Uri, Long> grantedUrisAndDate = SAFUtils.getUrisWithDate(context);
        for (int i = 0; i < grantedUrisAndDate.size(); ++i) {
            Uri uri = grantedUrisAndDate.keyAt(i);
            long time = grantedUrisAndDate.valueAt(i);
            storageLocations.put(IOUtils.getLastPathComponent(uri.getPath()) + " " + DateUtils.formatDate(time), uri);
        }
        return storageLocations;
    }

    /**
     * unified test function to add storage if fitting
     */
    private static void addStorage(String label, ProxyFile entry, Map<String, Uri> storageLocations) {
        Uri uri = Uri.fromFile(entry);
        if (entry != null && entry.listFiles() != null && !storageLocations.containsValue(uri)) {
            storageLocations.put(label, uri);
        } else if (entry != null) {
            Log.d(TAG, entry.getAbsolutePath());
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
                storageLocations.put(context.getString(R.string.sd_card) + (i == 0 ? "" : " " + i), new Uri.Builder()
                        .scheme(ContentResolver.SCHEME_FILE).path(path).build());
            }
        }
    }

    /**
     * Get storage indirect, best solution so far
     */
    private static void getStorageExternalFilesDir(Context context, Map<String, Uri> storageLocations) {
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
    private static void retrieveStorageManager(Context context, Map<String, Uri> storageLocations) {
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
    private static void retrieveStorageFilesystem(Map<String, Uri> storageLocations) {
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
    private static <T> T callReflectionFunction(@NonNull Object obj, @NonNull String function)
            throws ClassCastException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Method method = obj.getClass().getDeclaredMethod(function);
        method.setAccessible(true);
        Object r = method.invoke(obj);
        //noinspection unchecked
        return (T) r;
    }
}