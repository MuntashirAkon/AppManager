// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.appops.AppOpsService;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.utils.IOUtils;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagDisabledComponents;

/**
 * Import components from external apps like Blocker, Watt
 */
public class ExternalComponentsImporter {
    @WorkerThread
    @NonNull
    public static List<UserPackagePair> setModeToFilteredAppOps(@NonNull Collection<UserPackagePair> userPackagePairs,
                                                                int[] appOps,
                                                                @AppOpsManager.Mode int mode) {
        List<UserPackagePair> failedPkgList = new ArrayList<>();
        Collection<Integer> appOpList;
        AppOpsService appOpsService = new AppOpsService();
        for (UserPackagePair pair : userPackagePairs) {
            appOpList = PackageUtils.getFilteredAppOps(pair.getPackageName(), pair.getUserHandle(), appOps, mode);
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(pair.getPackageName(),
                    pair.getUserHandle())) {
                for (int appOp : appOpList) {
                    try {
                        appOpsService.setMode(appOp, PackageUtils.getAppUid(pair), pair.getPackageName(), mode);
                        cb.setAppOp(appOp, mode);
                    } catch (Exception ignore) {
                    }
                }
                cb.applyRules(true);
            } catch (Exception e) {
                e.printStackTrace();
                failedPkgList.add(pair);
            }
        }
        return failedPkgList;
    }

    @WorkerThread
    @NonNull
    public static List<String> applyFromExistingBlockList(@NonNull List<String> packageNames, int userHandle) {
        List<String> failedPkgList = new ArrayList<>();
        HashMap<String, RulesStorageManager.Type> components;
        for (String packageName : packageNames) {
            components = PackageUtils.getUserDisabledComponentsForPackage(packageName, userHandle);
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(packageName, userHandle)) {
                for (String componentName : components.keySet()) {
                    cb.addComponent(componentName, components.get(componentName));
                }
                // Remove IFW blocking rules if exists
                Runner.runCommand(Runner.getRootInstance(), String.format("rm %s/%s*.xml",
                        ComponentsBlocker.SYSTEM_RULES_PATH, packageName));
                cb.applyRules(true);
            } catch (Exception e) {
                e.printStackTrace();
                failedPkgList.add(packageName);
            }
        }
        return failedPkgList;
    }

    @WorkerThread
    @NonNull
    public static List<String> applyFromBlocker(@NonNull Context context,
                                                @NonNull List<Uri> uriList,
                                                int[] userHandles) {
        List<String> failedFiles = new ArrayList<>();
        for (Uri uri : uriList) {
            String filename = IOUtils.getFileName(context.getContentResolver(), uri);
            try {
                if (filename == null) {
                    throw new FileNotFoundException("The requested content is not found.");
                }
                for (int userHandle : userHandles) {
                    applyFromBlocker(context, uri, userHandle);
                }
            } catch (Exception e) {
                failedFiles.add(filename == null ? uri.toString() : filename);
                e.printStackTrace();
            }
        }
        return failedFiles;
    }

    @WorkerThread
    @NonNull
    public static List<String> applyFromWatt(@NonNull Context context, @NonNull List<Uri> uriList, int[] userHandles) {
        List<String> failedFiles = new ArrayList<>();
        for (Uri uri : uriList) {
            String filename = IOUtils.getFileName(context.getContentResolver(), uri);
            try {
                if (filename == null) {
                    throw new FileNotFoundException("The requested content is not found.");
                }
                for (int userHandle : userHandles) {
                    applyFromWatt(context, IOUtils.trimExtension(filename), uri, userHandle);
                }
            } catch (IOException e) {
                failedFiles.add(filename == null ? uri.toString() : filename);
                e.printStackTrace();
            }
        }
        return failedFiles;
    }

    /**
     * Watt only supports IFW, so copy them directly
     *
     * @param context Application context
     * @param fileUri File URI
     */
    @WorkerThread
    private static void applyFromWatt(@NonNull Context context, String packageName, Uri fileUri, int userHandle)
            throws IOException {
        try (InputStream rulesStream = context.getContentResolver().openInputStream(fileUri)) {
            if (rulesStream == null) throw new IOException("Failed to open input stream.");
            try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(packageName, userHandle)) {
                HashMap<String, RulesStorageManager.Type> components = ComponentUtils.readIFWRules(rulesStream,
                        packageName);
                for (String componentName : components.keySet()) {
                    // Overwrite rules if exists
                    cb.addComponent(componentName, components.get(componentName));
                }
                cb.applyRules(true);
            }
        }
    }

    /**
     * Apply from blocker
     *
     * @param context Application context
     * @param uri     File URI
     */
    @WorkerThread
    @SuppressLint("WrongConstant")
    private static void applyFromBlocker(@NonNull Context context, Uri uri, int userHandle) throws Exception {
        String jsonString = IOUtils.getFileContent(context.getContentResolver(), uri);
        HashMap<String, HashMap<String, RulesStorageManager.Type>> packageComponents = new HashMap<>();
        HashMap<String, PackageInfo> packageInfoList = new HashMap<>();
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONArray components = jsonObject.getJSONArray("components");
        List<String> uninstalledApps = new ArrayList<>();
        for (int i = 0; i < components.length(); ++i) {
            JSONObject component = (JSONObject) components.get(i);
            String packageName = component.getString("packageName");
            if (uninstalledApps.contains(packageName)) continue;
            if (!packageInfoList.containsKey(packageName)) {
                try {
                    packageInfoList.put(packageName, PackageManagerCompat.getPackageInfo(packageName,
                            PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS
                                    | PackageManager.GET_PROVIDERS | PackageManager.GET_SERVICES
                                    | flagDisabledComponents, userHandle));
                } catch (Exception e) {
                    // App not installed
                    uninstalledApps.add(packageName);
                    continue;
                }
            }
            String componentName = component.getString("name");
            if (!packageComponents.containsKey(packageName)) {
                packageComponents.put(packageName, new HashMap<>());
            }
            // Fetch package components using PackageInfo since the type used in Blocker can be wrong
            //noinspection ConstantConditions
            packageComponents.get(packageName).put(componentName, getType(componentName,
                    packageInfoList.get(packageName)));
        }
        if (packageComponents.size() > 0) {
            for (String packageName : packageComponents.keySet()) {
                HashMap<String, RulesStorageManager.Type> disabledComponents = packageComponents.get(packageName);
                //noinspection ConstantConditions
                if (disabledComponents.size() > 0) {
                    try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(packageName, userHandle)) {
                        for (String component : disabledComponents.keySet()) {
                            cb.addComponent(component, disabledComponents.get(component));
                        }
                        cb.applyRules(true);
                        if (!cb.isRulesApplied())
                            throw new Exception("Rules not applied for package " + packageName);
                    }
                }
            }
        }
    }

    @Nullable
    private static RulesStorageManager.Type getType(@NonNull String name, @NonNull PackageInfo packageInfo) {
        for (ActivityInfo activityInfo : packageInfo.activities)
            if (activityInfo.name.equals(name)) return RulesStorageManager.Type.ACTIVITY;
        for (ProviderInfo providerInfo : packageInfo.providers)
            if (providerInfo.name.equals(name)) return RulesStorageManager.Type.PROVIDER;
        for (ActivityInfo receiverInfo : packageInfo.receivers)
            if (receiverInfo.name.equals(name)) return RulesStorageManager.Type.RECEIVER;
        for (ServiceInfo serviceInfo : packageInfo.services)
            if (serviceInfo.name.equals(name)) return RulesStorageManager.Type.SERVICE;
        return null;
    }
}
